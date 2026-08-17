using EuroPad.Server;
using EuroPad.Server.Protocol;
using Xunit;

namespace EuroPad.Server.Tests;

public class FrameCodecTests
{
    private static byte[] BuildFrame(byte flags, ushort seq, uint ts, ushort lo = 0, ushort hi = 0, short[]? axes = null)
    {
        axes ??= new short[8];
        var buf = new byte[Proto.FrameSize];
        buf[Proto.OffMagic] = (byte)(Proto.Magic & 0xFF);
        buf[Proto.OffMagic + 1] = (byte)(Proto.Magic >> 8);
        buf[Proto.OffVersion] = Proto.Version;
        buf[Proto.OffFlags] = flags;
        buf[Proto.OffSeq] = (byte)(seq & 0xFF);
        buf[Proto.OffSeq + 1] = (byte)(seq >> 8);
        buf[Proto.OffTimestamp] = (byte)(ts & 0xFF);
        buf[Proto.OffTimestamp + 1] = (byte)((ts >> 8) & 0xFF);
        buf[Proto.OffTimestamp + 2] = (byte)((ts >> 16) & 0xFF);
        buf[Proto.OffTimestamp + 3] = (byte)((ts >> 24) & 0xFF);
        buf[Proto.OffButtonsLo] = (byte)(lo & 0xFF);
        buf[Proto.OffButtonsLo + 1] = (byte)(lo >> 8);
        buf[Proto.OffButtonsHi] = (byte)(hi & 0xFF);
        buf[Proto.OffButtonsHi + 1] = (byte)(hi >> 8);
        for (int i = 0; i < 8; i++)
        {
            buf[Proto.OffAxes + i * 2] = (byte)(axes[i] & 0xFF);
            buf[Proto.OffAxes + i * 2 + 1] = (byte)((axes[i] >> 8) & 0xFF);
        }
        return buf;
    }

    [Fact]
    public void FrameSize_Is30Bytes()
    {
        Assert.Equal(30, Proto.FrameSize);
        Assert.Equal(30, BuildFrame(0, 0, 0).Length);
    }

    [Fact]
    public void RoundTrip_PreservesAllFields()
    {
        var axes = new short[] { 100, -200, 32767, -32768, 5000, 0, 900, 42 };
        var buf = BuildFrame(Proto.FlagPingRequest, 1234, 0xCAFEBABE, 0xABCD, 0x1234, axes);

        var result = FrameCodec.TryDecode(buf, out var frame);

        Assert.Equal(DecodeResult.Ok, result);
        Assert.Equal((byte)Proto.FlagPingRequest, frame.Flags);
        Assert.Equal((ushort)1234, frame.Seq);
        Assert.Equal(0xCAFEBABEu, frame.TimestampMs);
        Assert.Equal((ushort)0xABCD, frame.ButtonsLoRaw);
        Assert.Equal((ushort)0x1234, frame.ButtonsHiRaw);
        Assert.Equal(axes, frame.Axes);
    }

    [Fact]
    public void TooShort_ReturnsTooShort()
    {
        var result = FrameCodec.TryDecode(new byte[29], out _);
        Assert.Equal(DecodeResult.TooShort, result);
    }

    [Fact]
    public void BadMagic_ReturnsBadMagic()
    {
        var buf = BuildFrame(0, 1, 1);
        buf[Proto.OffMagic] = 0x00;
        var result = FrameCodec.TryDecode(buf, out _);
        Assert.Equal(DecodeResult.BadMagic, result);
    }

    [Fact]
    public void BadVersion_ReturnsBadVersion()
    {
        var buf = BuildFrame(0, 1, 1);
        buf[Proto.OffVersion] = 99;
        var result = FrameCodec.TryDecode(buf, out _);
        Assert.Equal(DecodeResult.BadVersion, result);
    }

    [Fact]
    public void EncodePingReply_CarriesClientTimestamp()
    {
        var buf = new byte[Proto.FrameSize];
        FrameCodec.EncodePingReply(buf, 0xDEADBEEF);

        var result = FrameCodec.TryDecode(buf, out var frame);
        Assert.Equal(DecodeResult.Ok, result);
        Assert.Equal((byte)Proto.FlagPingReply, frame.Flags);
        Assert.Equal(0xDEADBEEFu, frame.TimestampMs);
        Assert.Equal((ushort)0, frame.Seq);
    }

    [Fact]
    public void EncodeAck_CarriesSlot()
    {
        var buf = new byte[Proto.FrameSize];
        FrameCodec.EncodeAck(buf, 2, 12345);

        var result = FrameCodec.TryDecode(buf, out var frame);
        Assert.Equal(DecodeResult.Ok, result);
        Assert.Equal((byte)Proto.FlagAck, frame.Flags);
        Assert.Equal(2, frame.ButtonsLoRaw);
    }

    [Fact]
    public void EncodeReject_CarriesReasonCode()
    {
        var buf = new byte[Proto.FrameSize];
        FrameCodec.EncodeReject(buf, Proto.RejectLobbyFull);

        var result = FrameCodec.TryDecode(buf, out var frame);
        Assert.Equal(DecodeResult.Ok, result);
        Assert.Equal((byte)Proto.FlagReject, frame.Flags);
        Assert.Equal(Proto.RejectLobbyFull, frame.ButtonsLoRaw);
    }

    [Fact]
    public void EncodeRumble_PacksMotorsIntoButtonsLo()
    {
        var buf = new byte[Proto.FrameSize];
        FrameCodec.EncodeRumble(buf, 128, 64);

        var result = FrameCodec.TryDecode(buf, out var frame);
        Assert.Equal(DecodeResult.Ok, result);
        Assert.Equal((byte)Proto.FlagRumble, frame.Flags);
        Assert.Equal((ushort)(128 | (64 << 8)), frame.ButtonsLoRaw);
        Assert.Equal(128, frame.ButtonsLoRaw & 0xFF);
        Assert.Equal(64, frame.ButtonsLoRaw >> 8);
    }

    [Fact]
    public void Hello_PinInButtonsLo()
    {
        var buf = BuildFrame(Proto.FlagHello, 7, 999, lo: 1234);
        var result = FrameCodec.TryDecode(buf, out var frame);
        Assert.Equal(DecodeResult.Ok, result);
        Assert.True(frame.IsHello);
        Assert.Equal(1234, frame.Pin);
    }

    private static byte[] BuildConfigFrame(int bitIndex, string key)
    {
        var name = key.Length > 17 ? key[..17] : key;
        ushort lo = (ushort)(Proto.CfgSetBitKey | ((bitIndex & 0xFF) << 8));
        ushort hi = (ushort)(name.Length | ((name.Length == 0 ? 0 : name[0] & 0xFF) << 8));
        var axes = new short[8];
        for (int i = 0; i < 8; i++)
        {
            int loChar = 1 + i * 2 < name.Length ? name[1 + i * 2] & 0xFF : 0;
            int hiChar = 2 + i * 2 < name.Length ? name[2 + i * 2] & 0xFF : 0;
            axes[i] = (short)(loChar | (hiChar << 8));
        }
        return BuildFrame(Proto.FlagConfig, 42, 0, lo, hi, axes);
    }

    [Theory]
    [InlineData(3, "X")]
    [InlineData(0, "Space")]
    [InlineData(15, "Ctrl")]
    [InlineData(7, "F12")]
    public void ConfigFrame_MirrorsPhoneEncoder(int bit, string key)
    {
        var buf = BuildConfigFrame(bit, key);
        var result = FrameCodec.TryDecode(buf, out var frame);

        Assert.Equal(DecodeResult.Ok, result);
        Assert.True(frame.IsConfig);
        Assert.Equal((ushort)Proto.CfgSetBitKey, (ushort)(frame.ButtonsLoRaw & 0xFF));
        Assert.Equal(bit, frame.ButtonsLoRaw >> 8);
        Assert.Equal(key, frame.PayloadText);
    }

    [Fact]
    public void ConfigFrame_SeventeenCharMax()
    {
        var key = "ABCDEFGHIJKLMNOPQ"; // exactly 17
        var buf = BuildConfigFrame(1, key);
        var result = FrameCodec.TryDecode(buf, out var frame);

        Assert.Equal(DecodeResult.Ok, result);
        Assert.Equal(key, frame.PayloadText);
    }
}

public class SeqCompareTests
{
    [Fact]
    public void Equal_IsStale() => Assert.True(SeqCompare.IsStaleOrEqual(100, 100));

    [Fact]
    public void Older_IsStale() => Assert.True(SeqCompare.IsStaleOrEqual(99, 100));

    [Fact]
    public void Newer_IsFresh() => Assert.False(SeqCompare.IsStaleOrEqual(101, 100));

    [Fact]
    public void Wraparound_Handled()
    {
        Assert.False(SeqCompare.IsStaleOrEqual(2, 65534));
        Assert.True(SeqCompare.IsStaleOrEqual(65534, 2));
    }
}
