using System.Diagnostics;
using System.Net;
using System.Net.Sockets;
using EuroPad.Server.Emulation;
using EuroPad.Server.Net;
using EuroPad.Server.Profiles;
using EuroPad.Server.Protocol;
using Nefarius.ViGEm.Client;

namespace EuroPad.Server;

public sealed class EuroPadServer : IAsyncDisposable
{
    private ViGEmClient? _vigem;
    private SlotManager? _slots;
    private readonly KeyboardEmulator _keyboard = new();
    private ProfileManager? _profiles;
    private readonly MdnsAnnouncer _mdns = new();
    private readonly List<UdpClient> _listeners = new();
    private readonly CancellationTokenSource _cts = new();
    private readonly Stopwatch _clock = Stopwatch.StartNew();

    private bool _pinEnabled;
    private int _pin;

    public int? Pin { get; set; }

    public static string ProfilesDir() =>
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "EuroPad", "profiles");

    public async Task<int> RunAsync(string[] args)
    {
        int port = Proto.DefaultPort;
        for (int i = 0; i < args.Length - 1; i++)
            if (args[i] == "--port" && int.TryParse(args[i + 1], out var p)) port = p;
        for (int i = 0; i < args.Length - 1; i++)
            if (args[i] == "--pin" && int.TryParse(args[i + 1], out var pin) && pin is >= 0 and <= 9999)
                Pin = pin;

        _pinEnabled = Pin.HasValue && Pin.Value != 0;
        _pin = Pin ?? 0;

        try
        {
            _vigem = new ViGEmClient();
        }
        catch (Exception e)
        {
            Console.Error.WriteLine($"ViGEmBus driver unavailable: {e.Message}");
            Console.Error.WriteLine("Install ViGEmBus 1.22.0: https://github.com/nefarius/vigembus/releases");
            return 2;
        }

        _slots = new SlotManager(_vigem);
        _profiles = new ProfileManager(ProfilesDir());

        Console.WriteLine($"EuroPadServer — XInput slots 0-3 (P1-P4), UDP port {port}");
        Console.WriteLine($"Profiles: {ProfilesDir()}");
        if (_pinEnabled) Console.WriteLine($"PIN gate enabled ({_pin:D4})");

        try
        {
            BindAllInterfaces(port);
        }
        catch (SocketException e)
        {
            Console.Error.WriteLine($"Failed to bind UDP {port}: {e.Message} — allow inbound UDP on private networks (Windows firewall).");
            return 1;
        }

        var tasks = new List<Task>();
        foreach (var l in _listeners) tasks.Add(Task.Run(() => ListenLoopAsync(l, _cts.Token)));
        tasks.Add(Task.Run(() => HousekeepingLoopAsync(_cts.Token)));

        await _mdns.StartAsync(port);

        Console.WriteLine("Running. Press Ctrl+C to exit.");
        Console.CancelKeyPress += (_, e) => { e.Cancel = true; _cts.Cancel(); };

        try { await Task.WhenAll(tasks); } catch (OperationCanceledException) { }
        await DisposeAsync();
        Console.WriteLine("Bye.");
        return 0;
    }

    private void BindAllInterfaces(int port)
    {
        var any = new UdpClient(AddressFamily.InterNetwork);
        any.Client.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.ReuseAddress, true);
        any.Client.Bind(new IPEndPoint(IPAddress.Any, port));
        any.DontFragment = true;
        _listeners.Add(any);
    }

    private async Task ListenLoopAsync(UdpClient client, CancellationToken ct)
    {
        while (!ct.IsCancellationRequested)
        {
            UdpReceiveResult rx;
            try { rx = await client.ReceiveAsync(ct); }
            catch (OperationCanceledException) { break; }
            catch (SocketException) { continue; }

            try { HandlePacket(rx.Buffer, rx.Buffer.Length, rx.RemoteEndPoint, client); }
            catch (Exception e) { Console.Error.WriteLine($"dispatch error: {e.Message}"); }
        }
    }

    private void HandlePacket(byte[] data, int length, IPEndPoint remote, UdpClient socket)
    {
        var result = FrameCodec.TryDecode(data.AsSpan(0, length), out var frame);
        if (result != DecodeResult.Ok) return;

        long now = Environment.TickCount64;

        if (frame.IsHello)
        {
            HandleHello(frame, remote, now, socket);
            return;
        }

        var slot = _slots!.Find(remote);
        if (slot is null) return;

        ApplyInput(slot, frame, now);

        if ((frame.Flags & Proto.FlagPingRequest) != 0)
        {
            var reply = new byte[Proto.FrameSize];
            FrameCodec.EncodePingReply(reply, frame.TimestampMs);
            TrySend(socket, reply, remote);
        }
    }

    private void HandleHello(in InputFrame frame, IPEndPoint remote, long now, UdpClient socket)
    {
        var slots = _slots!;
        var existing = slots.Find(remote);
        if (existing is not null)
        {
            existing.LastPacketTicks = now;
            SendAck(existing.Slot, remote, socket);
            return;
        }

        if (slots.IsPinLocked(remote, now)) return;

        if (_pinEnabled && frame.Pin != _pin)
        {
            slots.RecordPinFail(remote, now);
            SendReject(Proto.RejectWrongPin, remote, socket);
            return;
        }

        var slot = slots.Allocate(remote);
        if (slot is null)
        {
            SendReject(Proto.RejectLobbyFull, remote, socket);
            return;
        }

        slots.ClearPinFails(remote);
        slot.LastPacketTicks = now;
        Console.WriteLine($"Phone connected -> slot {slot.Slot} (P{slot.Slot + 1}) from {remote}");
        SendAck(slot.Slot, remote, socket);
    }

    private void SendAck(int slotIndex, IPEndPoint remote, UdpClient socket)
    {
        var buf = new byte[Proto.FrameSize];
        FrameCodec.EncodeAck(buf, slotIndex, (uint)_clock.ElapsedMilliseconds);
        TrySend(socket, buf, remote);
    }

    private void SendReject(byte reason, IPEndPoint remote, UdpClient socket)
    {
        var buf = new byte[Proto.FrameSize];
        FrameCodec.EncodeReject(buf, reason);
        TrySend(socket, buf, remote);
    }

    private static void TrySend(UdpClient socket, byte[] buf, IPEndPoint remote)
    {
        try { socket.Send(buf, buf.Length, remote); }
        catch (SocketException) { }
    }

    private void ApplyInput(SlotState slot, in InputFrame frame, long now)
    {
        if (slot.HasSeq && SeqCompare.IsStaleOrEqual(frame.Seq, slot.LastSeq)) return;
        slot.LastSeq = frame.Seq;
        slot.HasSeq = true;
        slot.LastPacketTicks = now;

        if (slot.FailsafeEngaged)
        {
            slot.FailsafeEngaged = false;
            Console.WriteLine($"Slot {slot.Slot} (P{slot.Slot + 1}): link recovered");
        }

        var pad = slot.Pad;
        if (pad is null) return;

        bool axesChanged = frame.Axis(AxisIndex.Lx) != slot.PrevAxes[AxisIndex.Lx]
                           || frame.Axis(AxisIndex.Ly) != slot.PrevAxes[AxisIndex.Ly]
                           || frame.Axis(AxisIndex.Rx) != slot.PrevAxes[AxisIndex.Rx]
                           || frame.Axis(AxisIndex.Ry) != slot.PrevAxes[AxisIndex.Ry]
                           || frame.Axis(AxisIndex.Lt) != slot.PrevAxes[AxisIndex.Lt]
                           || frame.Axis(AxisIndex.Rt) != slot.PrevAxes[AxisIndex.Rt];
        bool loChanged = frame.ButtonsLoRaw != slot.ButtonsLoPrev;
        bool hiChanged = frame.ButtonsHiRaw != slot.ButtonsHiPrev;

        if (loChanged || axesChanged)
        {
            if (loChanged) pad.SetButtonsFull(X360Mapper.ButtonsLoToXusbMask(frame.ButtonsLoRaw));
            if (axesChanged)
            {
                pad.SetAxisValue(Xbox360AxisIds.LeftThumbX, (short)frame.Axis(AxisIndex.Lx));
                pad.SetAxisValue(Xbox360AxisIds.LeftThumbY, (short)frame.Axis(AxisIndex.Ly));
                pad.SetAxisValue(Xbox360AxisIds.RightThumbX, (short)frame.Axis(AxisIndex.Rx));
                pad.SetAxisValue(Xbox360AxisIds.RightThumbY, (short)frame.Axis(AxisIndex.Ry));
                pad.SetSliderValue(Xbox360SliderIds.LeftTrigger, X360Mapper.TriggerToByte((short)frame.Axis(AxisIndex.Lt)));
                pad.SetSliderValue(Xbox360SliderIds.RightTrigger, X360Mapper.TriggerToByte((short)frame.Axis(AxisIndex.Rt)));
            }
            pad.SubmitReport();
        }

        if (hiChanged)
            _keyboard.Apply(_profiles!.ActiveKeysByBit, slot.ButtonsHiPrev, frame.ButtonsHiRaw);

        frame.Axes.CopyTo(slot.PrevAxes, 0);
        slot.ButtonsLoPrev = frame.ButtonsLoRaw;
        slot.ButtonsHiPrev = frame.ButtonsHiRaw;
    }

    private async Task HousekeepingLoopAsync(CancellationToken ct)
    {
        var rumbleBuf = new byte[Proto.FrameSize];

        while (!ct.IsCancellationRequested)
        {
            long now = Environment.TickCount64;
            var slots = _slots!;

            for (int i = 0; i < Proto.MaxSlots; i++)
            {
                var slot = slots.ByIndex(i);
                if (slot is null) continue;

                if (!slot.FailsafeEngaged && now - slot.LastPacketTicks > Proto.FailsafeMs)
                {
                    slot.FailsafeEngaged = true;
                    slot.ButtonsLoPrev = 0;
                    slot.ButtonsHiPrev = 0;
                    Array.Clear(slot.PrevAxes);
                    slot.HasSeq = false;
                    var pad = slot.Pad;
                    if (pad is not null)
                    {
                        pad.ResetReport();
                        pad.SubmitReport();
                    }
                    _keyboard.ReleaseAll(_profiles!.ActiveKeysByBit);
                    Console.WriteLine($"Slot {slot.Slot} (P{slot.Slot + 1}): FAILSAFE - {Proto.FailsafeMs}ms silence, inputs neutralized");
                }

                if (slot.FailsafeEngaged && now - slot.LastPacketTicks > Proto.FailsafeMs + 2000)
                {
                    slots.Free(slot);
                    await slot.DisposeAsync();
                    Console.WriteLine($"Slot {slot.Slot} (P{slot.Slot + 1}): phone timed out, slot freed");
                    continue;
                }

                if (now - slot.LastRumbleSentTicks >= Proto.RumbleFrameIntervalMs &&
                    slots.TryTakeRumble(slot, out var large, out var small))
                {
                    FrameCodec.EncodeRumble(rumbleBuf, large, small);
                    foreach (var l in _listeners)
                    {
                        TrySend(l, rumbleBuf, (IPEndPoint)slot.Remote!);
                    }
                    slot.LastRumbleSentTicks = now;
                }
            }

            try { await Task.Delay(16, ct); } catch (OperationCanceledException) { break; }
        }
    }

    public async ValueTask DisposeAsync()
    {
        _cts.Cancel();
        await _mdns.DisposeAsync();
        foreach (var l in _listeners) l.Dispose();
        if (_slots is not null)
        {
            for (int i = 0; i < Proto.MaxSlots; i++)
            {
                var s = _slots.ByIndex(i);
                if (s is not null) { _slots.Free(s); await s.DisposeAsync(); }
            }
        }
        _vigem?.Dispose();
    }
}

public static class SeqCompare
{
    public static bool IsStaleOrEqual(ushort incoming, ushort last) => (short)(incoming - last) <= 0;
}
