namespace EuroPad.Server.Protocol;

public static class Proto
{
    public const ushort Magic = 0x01E0;
    public const byte Version = 2;
    public const int FrameSize = 30;
    public const int AxesCount = 8;

    public const ushort DefaultPort = 47910;

    public const byte FlagPingRequest = 1 << 0;
    public const byte FlagPingReply = 1 << 1;
    public const byte FlagHello = 1 << 2;
    public const byte FlagRumble = 1 << 3;
    public const byte FlagAck = 1 << 4;
    public const byte FlagReject = 1 << 5;
    public const byte FlagConfig = 1 << 6;

    public const byte CfgSetBitKey = 1;

    /// <summary>
    /// Every flag that marks a frame as control-only. Such frames carry no axis/button payload, so
    /// they must never reach the pad — see <see cref="InputFrame.CarriesInput"/>.
    /// </summary>
    public const byte ControlFlags =
        FlagPingRequest | FlagPingReply | FlagHello | FlagRumble | FlagAck | FlagReject | FlagConfig;


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

    /// <summary>
    /// Silence after which a slot's inputs are neutralized. 300 ms was too tight: on 2.4 GHz Wi-Fi a
    /// single retransmit burst or a background AP scan swallows ~5 snapshots and 2 keeper pings, and
    /// every spurious trip slams the wheel to centre and lifts every held key — in game that reads as
    /// steering stutter and a wheel that will not hold full lock. 800 ms clears four keeper intervals
    /// while still bounded well under the 2000 ms slot-free grace period.
    /// </summary>
    public const int FailsafeMs = 800;
}
