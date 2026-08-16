namespace EuroPad.Server.Protocol;

[Flags]
public enum ButtonsLo : ushort
{
    None = 0,
    DpadUp = 1 << 0,
    DpadRight = 1 << 1,
    DpadDown = 1 << 2,
    DpadLeft = 1 << 3,
    Start = 1 << 4,
    Back = 1 << 5,
    Lb = 1 << 6,
    Rb = 1 << 7,
    A = 1 << 8,
    B = 1 << 9,
    X = 1 << 10,
    Y = 1 << 11,
    Guide = 1 << 12,
}

[Flags]
public enum ButtonsHi : ushort
{
    None = 0,
    IndL = 1 << 0,
    IndR = 1 << 1,
    Hazard = 1 << 2,
    Horn = 1 << 3,
    AirHorn = 1 << 4,
    Handbrake = 1 << 5,
    Lights = 1 << 6,
    Beam = 1 << 7,
    Warning = 1 << 8,
    Wipers = 1 << 9,
    ExhBrake = 1 << 10,
    DiffLock = 1 << 11,
    AxleRaise = 1 << 12,
    Engine = 1 << 13,
    GearUp = 1 << 14,
    GearDn = 1 << 15,
}

public static class AxisIndex
{
    public const int Lx = 0;
    public const int Ly = 1;
    public const int Rx = 2;
    public const int Ry = 3;
    public const int Lt = 4;
    public const int Rt = 5;
    public const int Steer = 6;
    public const int Aux0 = 7;
}

public readonly struct InputFrame
{
    public readonly byte Flags;
    public readonly ushort Seq;
    public readonly uint TimestampMs;
    public readonly ushort ButtonsLoRaw;
    public readonly ushort ButtonsHiRaw;
    public readonly short[] Axes;

    public InputFrame(byte flags, ushort seq, uint timestampMs, ushort buttonsLo, ushort buttonsHi, short[] axes)
    {
        Flags = flags;
        Seq = seq;
        TimestampMs = timestampMs;
        ButtonsLoRaw = buttonsLo;
        ButtonsHiRaw = buttonsHi;
        Axes = axes;
    }

    public bool IsPingRequest => (Flags & Proto.FlagPingRequest) != 0;

    public bool IsHello => (Flags & Proto.FlagHello) != 0;

    public int Pin => ButtonsLoRaw;

    public int Axis(int i) => (uint)i < (uint)Axes.Length ? Axes[i] : 0;
}
