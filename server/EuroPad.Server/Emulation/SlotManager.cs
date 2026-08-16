using System.Net;
using EuroPad.Server.Protocol;
using Nefarius.ViGEm.Client;
using Nefarius.ViGEm.Client.Targets;

namespace EuroPad.Server.Emulation;

public sealed class SlotState : IAsyncDisposable
{
    public int Slot;
    public EndPoint? Remote;
    public IXbox360Controller? Pad;
    public ushort LastSeq;
    public bool HasSeq;
    public long LastPacketTicks;
    public long LastRumbleSentTicks;
    public bool FailsafeEngaged;
    public ushort ButtonsLoPrev;
    public ushort ButtonsHiPrev;
    public readonly short[] PrevAxes = new short[Proto.AxesCount];

    public void ReleaseRumbleQueue() => RumbleOutbox = new();
    public Queue<(byte Large, byte Small)> RumbleOutbox { get; private set; } = new();

    public ValueTask DisposeAsync()
    {
        if (Pad is not null)
        {
            try { Pad.Disconnect(); } catch { }
            Pad = null;
        }
        return ValueTask.CompletedTask;
    }
}

public sealed class SlotManager
{
    private readonly ViGEmClient _client;
    private readonly object _gate = new();
    private readonly SlotState?[] _slots = new SlotState?[Proto.MaxSlots];
    private readonly Dictionary<string, long> _pinLockouts = new();
    private readonly Dictionary<string, int> _pinFailCounts = new();

    public SlotManager(ViGEmClient client) => _client = client;

    public SlotState? Find(EndPoint remote)
    {
        lock (_gate)
        {
            foreach (var s in _slots)
                if (s is not null && s.Remote!.Equals(remote)) return s;
            return null;
        }
    }

    public SlotState? Allocate(EndPoint remote)
    {
        lock (_gate)
        {
            foreach (var s in _slots)
                if (s is not null && s.Remote!.Equals(remote)) return s;

            for (int i = 0; i < Proto.MaxSlots; i++)
            {
                if (_slots[i] is null)
                {
                    var state = new SlotState { Slot = i, Remote = remote, LastPacketTicks = Environment.TickCount64 };
                    var pad = _client.CreateXbox360Controller();
                    pad.AutoSubmitReport = false;
                    pad.FeedbackReceived += (_, e) => EnqueueRumble(state, e.LargeMotor, e.SmallMotor);
                    pad.Connect();
                    state.Pad = pad;
                    _slots[i] = state;
                    return state;
                }
            }
            return null;
        }
    }

    private static void EnqueueRumble(SlotState s, byte large, byte small)
    {
        lock (s)
        {
            var q = s.RumbleOutbox;
            q.Enqueue((large, small));
            while (q.Count > 8) q.Dequeue();
        }
    }

    public bool TryTakeRumble(SlotState s, out byte large, out byte small)
    {
        lock (s)
        {
            if (s.RumbleOutbox.Count == 0) { large = small = 0; return false; }
            var last = ((byte)0, (byte)0);
            while (s.RumbleOutbox.Count > 0) last = s.RumbleOutbox.Dequeue();
            large = last.Item1;
            small = last.Item2;
            return true;
        }
    }

    public bool IsPinLocked(EndPoint remote, long now)
    {
        lock (_gate)
        {
            var key = IpKey(remote);
            return _pinLockouts.TryGetValue(key, out var until) && now < until;
        }
    }

    public void RecordPinFail(EndPoint remote, long now)
    {
        lock (_gate)
        {
            var key = IpKey(remote);
            _pinFailCounts[key] = _pinFailCounts.GetValueOrDefault(key) + 1;
            if (_pinFailCounts[key] >= 3)
            {
                _pinLockouts[key] = now + 60_000;
                _pinFailCounts.Remove(key);
            }
        }
    }

    public void ClearPinFails(EndPoint remote)
    {
        lock (_gate) { _pinFailCounts.Remove(IpKey(remote)); }
    }

    private static string IpKey(EndPoint remote) =>
        remote is IPEndPoint ip ? ip.Address.ToString() : remote.ToString()!;

    public void Free(SlotState s)
    {
        lock (_gate)
        {
            if (_slots[s.Slot] == s) _slots[s.Slot] = null;
        }
    }

    public SlotState? ByIndex(int i) => _slots[i];
}
