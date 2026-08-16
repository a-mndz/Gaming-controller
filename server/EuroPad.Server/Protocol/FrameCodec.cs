namespace EuroPad.Server.Protocol;

public enum DecodeResult
{
    Ok,
    TooShort,
    BadMagic,
    BadVersion,
}

public static class FrameCodec
{
    public static DecodeResult TryDecode(ReadOnlySpan<byte> buf, out InputFrame frame)
    {
        frame = default;
        if (buf.Length < Proto.FrameSize) return DecodeResult.TooShort;

        ushort magic = (ushort)(buf[Proto.OffMagic] | (buf[Proto.OffMagic + 1] << 8));
        if (magic != Proto.Magic) return DecodeResult.BadMagic;

        byte version = buf[Proto.OffVersion];
        if (version != Proto.Version) return DecodeResult.BadVersion;

        byte flags = buf[Proto.OffFlags];
        ushort seq = (ushort)(buf[Proto.OffSeq] | (buf[Proto.OffSeq + 1] << 8));
        uint ts = (uint)(buf[Proto.OffTimestamp] | (buf[Proto.OffTimestamp + 1] << 8) |
                         (buf[Proto.OffTimestamp + 2] << 16) | (buf[Proto.OffTimestamp + 3] << 24));
        ushort lo = (ushort)(buf[Proto.OffButtonsLo] | (buf[Proto.OffButtonsLo + 1] << 8));
        ushort hi = (ushort)(buf[Proto.OffButtonsHi] | (buf[Proto.OffButtonsHi + 1] << 8));

        var axes = new short[Proto.AxesCount];
        for (int i = 0; i < Proto.AxesCount; i++)
        {
            int o = Proto.OffAxes + i * 2;
            axes[i] = (short)(buf[o] | (buf[o + 1] << 8));
        }

        frame = new InputFrame(flags, seq, ts, lo, hi, axes);
        return DecodeResult.Ok;
    }

    public static void EncodePingReply(Span<byte> buf, uint clientTimestamp)
    {
        buf.Clear();
        buf[Proto.OffMagic] = (byte)(Proto.Magic & 0xFF);
        buf[Proto.OffMagic + 1] = (byte)(Proto.Magic >> 8);
        buf[Proto.OffVersion] = Proto.Version;
        buf[Proto.OffFlags] = Proto.FlagPingReply;
        PutU32(buf[Proto.OffTimestamp..], clientTimestamp);
    }

    public static void EncodeAck(Span<byte> buf, int slot, uint serverClockRef)
    {
        buf.Clear();
        buf[Proto.OffMagic] = (byte)(Proto.Magic & 0xFF);
        buf[Proto.OffMagic + 1] = (byte)(Proto.Magic >> 8);
        buf[Proto.OffVersion] = Proto.Version;
        buf[Proto.OffFlags] = Proto.FlagAck;
        buf[Proto.OffButtonsLo] = (byte)slot;
        PutU32(buf[Proto.OffTimestamp..], serverClockRef);
    }

    public static void EncodeReject(Span<byte> buf, byte reason)
    {
        buf.Clear();
        buf[Proto.OffMagic] = (byte)(Proto.Magic & 0xFF);
        buf[Proto.OffMagic + 1] = (byte)(Proto.Magic >> 8);
        buf[Proto.OffVersion] = Proto.Version;
        buf[Proto.OffFlags] = Proto.FlagReject;
        buf[Proto.OffButtonsLo] = reason;
    }

    public static void EncodeRumble(Span<byte> buf, byte largeMotor, byte smallMotor)
    {
        buf.Clear();
        buf[Proto.OffMagic] = (byte)(Proto.Magic & 0xFF);
        buf[Proto.OffMagic + 1] = (byte)(Proto.Magic >> 8);
        buf[Proto.OffVersion] = Proto.Version;
        buf[Proto.OffFlags] = Proto.FlagRumble;
        ushort motors = (ushort)(largeMotor | (smallMotor << 8));
        buf[Proto.OffButtonsLo] = (byte)(motors & 0xFF);
        buf[Proto.OffButtonsLo + 1] = (byte)(motors >> 8);
    }

    private static void PutU32(Span<byte> dst, uint v)
    {
        dst[0] = (byte)v;
        dst[1] = (byte)(v >> 8);
        dst[2] = (byte)(v >> 16);
        dst[3] = (byte)(v >> 24);
    }
}
