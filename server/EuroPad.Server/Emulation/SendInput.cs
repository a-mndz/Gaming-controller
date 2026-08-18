using System.Collections.Concurrent;
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

/// <summary>One resolved physical keystroke: which VK, which set-1 scancode, and whether it needs the E0 prefix.</summary>
public readonly record struct KeyStroke(ushort Vk, ushort Scan, bool Extended)
{
    /// <summary>Media/browser keys have no set-1 scancode; those must fall back to VK-only injection.</summary>
    public bool HasScanCode => Scan != 0;
}

/// <summary>
/// VK -> scancode resolution, kept free of P/Invoke so it can be unit-tested.
///
/// Why this exists: the emulator used to call SendInput with <c>wVk</c> set and <c>wScan = 0</c>.
/// That is fine for anything reading WM_KEYDOWN, but ETS2 (like most SDL/DirectInput/RawInput games)
/// keys off the *scancode*, and a scancode of 0 matches no physical key — so held keys such as
/// GEAR_UP (Shift) and GEAR_DN (Ctrl) simply never registered in game.
/// </summary>
public static class KeyScan
{
    public const ushort VkShift = 0x10;
    public const ushort VkControl = 0x11;
    public const ushort VkMenu = 0x12;
    public const ushort VkLShift = 0xA0;
    public const ushort VkLControl = 0xA2;
    public const ushort VkLMenu = 0xA4;

    /// <summary>
    /// VK_SHIFT / VK_CONTROL / VK_MENU are the "either side" aliases; they have no single physical
    /// key. Games that bind a specific side (ETS2 binds left by default) never see the alias, so
    /// resolve to the left-hand physical key.
    /// </summary>
    public static ushort Canonicalize(ushort vk) => vk switch
    {
        VkShift => VkLShift,
        VkControl => VkLControl,
        VkMenu => VkLMenu,
        _ => vk,
    };

    /// <summary>
    /// Keys that live behind the 0xE0 prefix on a set-1 keyboard. MAPVK_VK_TO_VSC_EX usually reports
    /// this itself, but not on every driver/layout, and getting it wrong turns an arrow key into the
    /// numeric keypad equivalent.
    /// </summary>
    public static bool IsExtended(ushort vk) => vk switch
    {
        0x21 or 0x22 or 0x23 or 0x24 => true, // PgUp PgDn End Home
        0x25 or 0x26 or 0x27 or 0x28 => true, // Left Up Right Down
        0x2D or 0x2E => true,                 // Insert Delete
        0x2C => true,                         // PrintScreen
        0x90 => true,                         // NumLock
        0xA1 => false,                        // RShift is *not* extended (quirk of set 1)
        0xA3 or 0xA5 => true,                 // RControl RAlt
        0x6F => true,                         // numpad divide
        _ => false,
    };

    public static KeyStroke Resolve(ushort vk, Func<ushort, uint> vkToScanEx)
    {
        ushort canonical = Canonicalize(vk);
        uint raw = vkToScanEx(canonical);
        ushort scan = (ushort)(raw & 0xFF);
        byte prefix = (byte)((raw >> 8) & 0xFF);
        bool extended = prefix is 0xE0 or 0xE1 || IsExtended(canonical);
        return new KeyStroke(canonical, scan, extended);
    }
}

public static class NativeKeySender
{
    private const uint InputKeyboard = 1;
    private const uint KeyEventKeyUp = 0x0002;
    private const uint KeyEventScanCode = 0x0008;
    private const uint KeyEventExtendedKey = 0x0001;
    private const uint MapVkToVscEx = 4;

    private static readonly int InputStructSize = Marshal.SizeOf(typeof(Input));
    private static readonly ConcurrentDictionary<ushort, KeyStroke> Cache = new();

    [DllImport("user32.dll", SetLastError = true)]
    private static extern uint SendInput(uint nInputs, Input[] pInputs, int cbSize);

    [DllImport("user32.dll")]
    private static extern uint MapVirtualKey(uint uCode, uint uMapType);

    public static void Key(ushort vk, bool down)
    {
        var stroke = Cache.GetOrAdd(vk, v => KeyScan.Resolve(v, code => MapVirtualKey(code, MapVkToVscEx)));

        uint flags = down ? 0u : KeyEventKeyUp;
        ushort wVk = stroke.Vk;
        ushort wScan = stroke.Scan;

        if (stroke.HasScanCode)
        {
            // KEYEVENTF_SCANCODE: the OS derives the VK from the scancode, so message-loop consumers
            // and raw-scancode consumers (the game) both see a real key. wVk must be 0 with this flag.
            flags |= KeyEventScanCode;
            if (stroke.Extended) flags |= KeyEventExtendedKey;
            wVk = 0;
        }

        var input = new Input
        {
            type = InputKeyboard,
            u = new InputUnion
            {
                ki = new KeybdInput
                {
                    wVk = wVk,
                    wScan = wScan,
                    dwFlags = flags,
                    time = 0,
                    dwExtraInfo = UIntPtr.Zero,
                },
            },
        };
        SendInput(1, new[] { input }, InputStructSize);
    }
}
