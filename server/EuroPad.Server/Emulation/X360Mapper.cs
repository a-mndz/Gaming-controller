using EuroPad.Server.Protocol;
using Nefarius.ViGEm.Client.Targets.Xbox360;

namespace EuroPad.Server.Emulation;

public static class Xbox360AxisIds
{
    public static readonly Xbox360Axis LeftThumbX = Xbox360Axis.LeftThumbX;
    public static readonly Xbox360Axis LeftThumbY = Xbox360Axis.LeftThumbY;
    public static readonly Xbox360Axis RightThumbX = Xbox360Axis.RightThumbX;
    public static readonly Xbox360Axis RightThumbY = Xbox360Axis.RightThumbY;
}

public static class Xbox360SliderIds
{
    public static readonly Xbox360Slider LeftTrigger = Xbox360Slider.LeftTrigger;
    public static readonly Xbox360Slider RightTrigger = Xbox360Slider.RightTrigger;
}

public static class XusbMask
{
    public const ushort DpadUp = 0x0001;
    public const ushort DpadDown = 0x0002;
    public const ushort DpadLeft = 0x0004;
    public const ushort DpadRight = 0x0008;
    public const ushort Start = 0x0010;
    public const ushort Back = 0x0020;
    public const ushort LeftThumb = 0x0040;
    public const ushort RightThumb = 0x0080;
    public const ushort LeftShoulder = 0x0100;
    public const ushort RightShoulder = 0x0200;
    public const ushort Guide = 0x0400;
    public const ushort A = 0x1000;
    public const ushort B = 0x2000;
    public const ushort X = 0x4000;
    public const ushort Y = 0x8000;
}

public static class X360Mapper
{
    public static ushort ButtonsLoToXusbMask(ushort lo)
    {
        ushort mask = 0;
        if ((lo & (ushort)ButtonsLo.DpadUp) != 0) mask |= XusbMask.DpadUp;
        if ((lo & (ushort)ButtonsLo.DpadRight) != 0) mask |= XusbMask.DpadRight;
        if ((lo & (ushort)ButtonsLo.DpadDown) != 0) mask |= XusbMask.DpadDown;
        if ((lo & (ushort)ButtonsLo.DpadLeft) != 0) mask |= XusbMask.DpadLeft;
        if ((lo & (ushort)ButtonsLo.Start) != 0) mask |= XusbMask.Start;
        if ((lo & (ushort)ButtonsLo.Back) != 0) mask |= XusbMask.Back;
        if ((lo & (ushort)ButtonsLo.Lb) != 0) mask |= XusbMask.LeftShoulder;
        if ((lo & (ushort)ButtonsLo.Rb) != 0) mask |= XusbMask.RightShoulder;
        if ((lo & (ushort)ButtonsLo.Guide) != 0) mask |= XusbMask.Guide;
        if ((lo & (ushort)ButtonsLo.A) != 0) mask |= XusbMask.A;
        if ((lo & (ushort)ButtonsLo.B) != 0) mask |= XusbMask.B;
        if ((lo & (ushort)ButtonsLo.X) != 0) mask |= XusbMask.X;
        if ((lo & (ushort)ButtonsLo.Y) != 0) mask |= XusbMask.Y;
        return mask;
    }

    public static byte TriggerToByte(short i16Trigger)
    {
        int clamped = Math.Clamp((int)i16Trigger, 0, (int)short.MaxValue);
        return (byte)(clamped * 255 / short.MaxValue);
    }
}
