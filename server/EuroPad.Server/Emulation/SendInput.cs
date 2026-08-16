using System.Runtime.InteropServices;

namespace EuroPad.Server.Emulation;

[StructLayout(LayoutKind.Sequential)]
public struct MouseInput
{
    public int dx;
    public int dy;
    public uint mouseData;
    public uint dwFlags;
    public uint time;
    public UIntPtr dwExtraInfo;
}

[StructLayout(LayoutKind.Sequential)]
public struct KeybdInput
{
    public ushort wVk;
    public ushort wScan;
    public uint dwFlags;
    public uint time;
    public UIntPtr dwExtraInfo;
}

[StructLayout(LayoutKind.Sequential)]
public struct HardwareInput
{
    public uint uMsg;
    public ushort wParamL;
    public ushort wParamH;
}

[StructLayout(LayoutKind.Explicit)]
public struct InputUnion
{
    [FieldOffset(0)] public MouseInput mi;
    [FieldOffset(0)] public KeybdInput ki;
    [FieldOffset(0)] public HardwareInput hi;
}

[StructLayout(LayoutKind.Sequential)]
public struct Input
{
    public uint type;
    public InputUnion u;
}

public static class NativeKeySender
{
    private const uint InputKeyboard = 1;
    private const uint KeyUp = 0x0002;
    private static readonly int InputStructSize = Marshal.SizeOf(typeof(Input));

    [DllImport("user32.dll", SetLastError = true)]
    private static extern uint SendInput(uint nInputs, Input[] pInputs, int cbSize);

    public static void Key(ushort vk, bool down)
    {
        var input = new Input
        {
            type = InputKeyboard,
            u = new InputUnion
            {
                ki = new KeybdInput
                {
                    wVk = vk,
                    wScan = 0,
                    dwFlags = down ? 0 : KeyUp,
                    time = 0,
                    dwExtraInfo = UIntPtr.Zero,
                },
            },
        };
        SendInput(1, new[] { input }, InputStructSize);
    }
}
