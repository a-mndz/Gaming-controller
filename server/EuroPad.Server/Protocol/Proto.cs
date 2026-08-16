namespace EuroPad.Server.Protocol;

public static class Proto
{
    public const ushort Magic = 0x01E0;
    public const byte Version = 1;
    public const int FrameSize = 30;
    public const int AxesCount = 8;

    public const ushort DefaultPort = 47910;

    public const byte FlagPingRequest = 1 << 0;
    public const byte FlagPingReply = 1 << 1;
    public const byte FlagHello = 1 << 2;
    public const byte FlagRumble = 1 << 3;
    public const byte FlagAck = 1 << 4;
    public const byte FlagReject = 1 << 5;

    public const int OffMagic = 0;
    public const int OffVersion = 2;
    public const int OffFlags = 3;
    public const int OffSeq = 4;
    public const int OffTimestamp = 6;
    public const int OffButtonsLo = 10;
    public const int OffButtonsHi = 12;
    public const int OffAxes = 14;

    public const byte RejectVersionMismatch = 1;
    public const byte RejectWrongPin = 2;
    public const byte RejectLobbyFull = 3;

    public const int MaxSlots = 4;
    public const ushort RumbleFrameIntervalMs = 33;
    public const int FailsafeMs = 300;
}
