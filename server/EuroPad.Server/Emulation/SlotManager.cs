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
    public long LastRumbleEnqueueTicks;

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
            s.LastRumbleEnqueueTicks = Environment.TickCount64;
            if (_traceEnqueue) Console.WriteLine($"RUMBLE-ENQ slot={s.Slot} large={large} small={small}");
            var q = s.RumbleOutbox;
            q.Enqueue((large, small));
            while (q.Count > 8) q.Dequeue();
        }
    }

    private static readonly bool _traceEnqueue = Environment.GetEnvironmentVariable("EUROPAD_RUMBLE_TRACE") == "1";

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

    public void Free(SlotState s)
    {
        lock (_gate)
        {
            if (_slots[s.Slot] == s) _slots[s.Slot] = null;
        }
    }

    /// <summary>
    /// Under the same lock as <see cref="Allocate"/> and <see cref="Free"/>: the housekeeping loop
    /// walks every index each tick, and an unsynchronised read has no barrier against a slot that was
    /// freed or allocated on the receive thread a moment ago.
    /// </summary>
    public SlotState? ByIndex(int i)
    {
        lock (_gate) { return _slots[i]; }
    }
}
