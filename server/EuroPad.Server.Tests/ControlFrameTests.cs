using EuroPad.Server.Protocol;
using Xunit;

namespace EuroPad.Server.Tests;

/// <summary>
/// Regression guard for the defect that produced the in-game symptoms report of 2026-08-18:
/// steering stutter, a wheel that never held full lock, dead R/N/D gear selectors and a handbrake
/// that toggled itself.
///
/// Cause: <c>HandlePacket</c> fed *every* non-hello/non-config frame to <c>ApplyInput</c>, including
/// the keepalive ping the phone emits every 150 ms. <c>FrameEncoder.encodePing</c> builds that frame
/// from a freshly zeroed 30-byte buffer, so its axes and both button words are 0 — applying it
/// centred the wheel, released both pedals and lifted every held key roughly seven times a second.
///
/// These tests pin the invariant: a control frame must never look like input.
/// </summary>
public class ControlFrameTests
{
    /// <summary>Byte-for-byte mirror of the phone's <c>FrameEncoder.encodePing</c>.</summary>
    private static byte[] PhonePing(ushort seq, uint tsMs)
    {
        var b = new byte[Proto.FrameSize];
        b[Proto.OffMagic] = (byte)(Proto.Magic & 0xFF);
        b[Proto.OffMagic + 1] = (byte)(Proto.Magic >> 8);
        b[Proto.OffVersion] = Proto.Version;
        b[Proto.OffFlags] = Proto.FlagPingRequest;
        b[Proto.OffSeq] = (byte)(seq & 0xFF);
        b[Proto.OffSeq + 1] = (byte)(seq >> 8);
        b[Proto.OffTimestamp] = (byte)(tsMs & 0xFF);
        b[Proto.OffTimestamp + 1] = (byte)((tsMs >> 8) & 0xFF);
        b[Proto.OffTimestamp + 2] = (byte)((tsMs >> 16) & 0xFF);
        b[Proto.OffTimestamp + 3] = (byte)((tsMs >> 24) & 0xFF);
        return b;
    }

    private static byte[] PhoneSnapshot(ushort seq, ushort lo, ushort hi, short[] axes)
    {
        var b = new byte[Proto.FrameSize];
        b[Proto.OffMagic] = (byte)(Proto.Magic & 0xFF);
        b[Proto.OffMagic + 1] = (byte)(Proto.Magic >> 8);
        b[Proto.OffVersion] = Proto.Version;
        b[Proto.OffFlags] = 0; // snapshots carry no flags — that is what makes them input
        b[Proto.OffSeq] = (byte)(seq & 0xFF);
        b[Proto.OffSeq + 1] = (byte)(seq >> 8);
        b[Proto.OffButtonsLo] = (byte)(lo & 0xFF);
        b[Proto.OffButtonsLo + 1] = (byte)(lo >> 8);
        b[Proto.OffButtonsHi] = (byte)(hi & 0xFF);
        b[Proto.OffButtonsHi + 1] = (byte)(hi >> 8);
        for (int i = 0; i < Proto.AxesCount; i++)
        {
            b[Proto.OffAxes + i * 2] = (byte)(axes[i] & 0xFF);
            b[Proto.OffAxes + i * 2 + 1] = (byte)((axes[i] >> 8) & 0xFF);
        }
        return b;
    }

    [Fact]
    public void PhonePing_IsAllZeroPayload()
    {
        // The premise of the bug: there is nothing in a ping worth applying.
        Assert.Equal(DecodeResult.Ok, FrameCodec.TryDecode(PhonePing(1234, 999), out var frame));
        Assert.True(frame.IsPingRequest);
        Assert.Equal(0, frame.ButtonsLoRaw);
        Assert.Equal(0, frame.ButtonsHiRaw);
        Assert.All(frame.Axes, a => Assert.Equal(0, a));
    }

    [Fact]
    public void PhonePing_DoesNotCarryInput()
    {
        FrameCodec.TryDecode(PhonePing(7, 7), out var frame);
        Assert.False(frame.CarriesInput);
    }

    [Fact]
    public void Snapshot_CarriesInput()
    {
        var axes = new short[Proto.AxesCount];
        axes[AxisIndex.Steer] = short.MaxValue;
        axes[AxisIndex.Rt] = short.MaxValue;
        FrameCodec.TryDecode(PhoneSnapshot(8, 0, (ushort)ButtonsHi.Handbrake, axes), out var frame);

        Assert.True(frame.CarriesInput);
        Assert.Equal(short.MaxValue, frame.Axis(AxisIndex.Steer));
    }

    [Theory]
    [InlineData(Proto.FlagPingRequest)]
    [InlineData(Proto.FlagPingReply)]
    [InlineData(Proto.FlagHello)]
    [InlineData(Proto.FlagRumble)]
    [InlineData(Proto.FlagAck)]
    [InlineData(Proto.FlagReject)]
    [InlineData(Proto.FlagConfig)]
    public void EveryControlFlag_SuppressesInput(byte flag)
    {
        // Even a frame whose payload bytes happen to be full-scale must be ignored when a control
        // flag is set — the payload is repurposed (slot index, reject reason, motor levels, key name).
        var axes = new short[Proto.AxesCount];
        for (int i = 0; i < axes.Length; i++) axes[i] = short.MaxValue;
        var buf = PhoneSnapshot(9, 0xFFFF, 0xFFFF, axes);
        buf[Proto.OffFlags] = flag;

        FrameCodec.TryDecode(buf, out var frame);
        Assert.False(frame.CarriesInput);
    }

    [Fact]
    public void ControlFlags_CoversEveryDefinedFlag()
    {
        // If a future flag is added, ControlFlags must be updated or it will silently be treated as
        // input. Bits 0-6 are the whole flag space today; bit 7 is spare.
        byte union = Proto.FlagPingRequest | Proto.FlagPingReply | Proto.FlagHello | Proto.FlagRumble |
                     Proto.FlagAck | Proto.FlagReject | Proto.FlagConfig;
        // Argument order is deliberate: the analyzer (xUnit2000) wants the constant as 'expected'.
        Assert.Equal(Proto.ControlFlags, union);
        Assert.Equal(0x7F, Proto.ControlFlags);
    }

    [Fact]
    public void FailsafeWindow_SurvivesSeveralMissedSnapshots()
    {
        // The phone sends snapshots at 120 Hz (~8 ms) and pings every 150 ms. A failsafe window
        // narrower than a couple of Wi-Fi retransmit bursts neutralizes the pad mid-corner, which is
        // indistinguishable from steering stutter. Keep at least four keeper intervals of headroom.
        Assert.True(Proto.FailsafeMs >= 4 * 150,
            $"FailsafeMs={Proto.FailsafeMs} is under four keeper intervals (600 ms) of 2.4 GHz jitter headroom");

        // ...but not so wide that a dropped link leaves the truck steering itself for a full second.
        Assert.True(Proto.FailsafeMs <= 1000, $"FailsafeMs={Proto.FailsafeMs} leaves inputs latched too long");
    }
}
