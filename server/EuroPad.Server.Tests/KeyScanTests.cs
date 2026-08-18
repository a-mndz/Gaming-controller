using EuroPad.Server.Emulation;
using Xunit;

namespace EuroPad.Server.Tests;

/// <summary>
/// The keyboard side of the 2026-08-18 bug report ("gear selectors are not working").
///
/// GEAR_UP/GEAR_DN are Shift/Ctrl, injected via SendInput. The old sender set only <c>wVk</c> and
/// left <c>wScan = 0</c>; ETS2 reads scancodes, and scancode 0 is no key at all. These tests pin the
/// resolution rules: generic modifier aliases become the left physical key, extended keys keep their
/// 0xE0 prefix, and keys with no scancode still fall back to VK injection.
/// </summary>
public class KeyScanTests
{
    /// <summary>Stand-in for MapVirtualKey(vk, MAPVK_VK_TO_VSC_EX) using the real set-1 table.</summary>
    private static uint FakeMap(ushort vk) => vk switch
    {
        0xA0 => 0x2A,   // LShift
        0xA1 => 0x36,   // RShift
        0xA2 => 0x1D,   // LControl
        0xA3 => 0xE01D, // RControl
        0xA4 => 0x38,   // LAlt
        0xA5 => 0xE038, // RAlt
        0x20 => 0x39,   // Space
        0x50 => 0x19,   // P
        0x25 => 0xE04B, // Left arrow
        0xAF => 0x00,   // VolumeUp — no set-1 scancode
        _ => 0x01,
    };

    [Theory]
    [InlineData(KeyScan.VkShift, KeyScan.VkLShift)]
    [InlineData(KeyScan.VkControl, KeyScan.VkLControl)]
    [InlineData(KeyScan.VkMenu, KeyScan.VkLMenu)]
    public void GenericModifiers_ResolveToLeftPhysicalKey(ushort input, ushort expected)
        => Assert.Equal(expected, KeyScan.Canonicalize(input));

    [Fact]
    public void OrdinaryKeys_PassThroughCanonicalize()
    {
        Assert.Equal(0x50, KeyScan.Canonicalize(0x50)); // P
        Assert.Equal(0x20, KeyScan.Canonicalize(0x20)); // Space
    }

    [Fact]
    public void GearUp_Shift_GetsRealScanCode()
    {
        // The profile default for GEAR_UP is "Shift" -> VK 0x10.
        var stroke = KeyScan.Resolve(KeyScan.VkShift, FakeMap);
        Assert.Equal(KeyScan.VkLShift, stroke.Vk);
        Assert.Equal(0x2A, stroke.Scan);
        Assert.False(stroke.Extended);
        Assert.True(stroke.HasScanCode);
    }

    [Fact]
    public void GearDown_Ctrl_GetsRealScanCode()
    {
        var stroke = KeyScan.Resolve(KeyScan.VkControl, FakeMap);
        Assert.Equal(KeyScan.VkLControl, stroke.Vk);
        Assert.Equal(0x1D, stroke.Scan);
        Assert.False(stroke.Extended);
    }

    [Fact]
    public void RightControl_KeepsExtendedPrefix()
    {
        var stroke = KeyScan.Resolve(0xA3, FakeMap);
        Assert.Equal(0x1D, stroke.Scan); // low byte only — the prefix travels as the flag
        Assert.True(stroke.Extended);
    }

    [Fact]
    public void RightShift_IsNotExtended()
    {
        // Set-1 quirk: RShift is 0x36 with no E0 prefix. Flagging it extended would inject garbage.
        var stroke = KeyScan.Resolve(0xA1, FakeMap);
        Assert.Equal(0x36, stroke.Scan);
        Assert.False(stroke.Extended);
    }

    [Fact]
    public void ArrowKey_IsExtended()
    {
        var stroke = KeyScan.Resolve(0x25, FakeMap);
        Assert.Equal(0x4B, stroke.Scan);
        Assert.True(stroke.Extended);
    }

    [Fact]
    public void ArrowKey_ExtendedEvenIfMapperOmitsPrefix()
    {
        // Some drivers/layouts report the bare scancode; the static table has to cover for them or
        // Left arrow silently becomes numpad-4.
        var stroke = KeyScan.Resolve(0x25, _ => 0x4B);
        Assert.True(stroke.Extended);
    }

    [Fact]
    public void MediaKey_FallsBackToVkOnly()
    {
        var stroke = KeyScan.Resolve(0xAF, FakeMap);
        Assert.Equal(0xAF, stroke.Vk);
        Assert.False(stroke.HasScanCode); // sender keeps wVk and drops KEYEVENTF_SCANCODE
    }

    [Fact]
    public void EveryProfileDefaultKey_ResolvesToSomething()
    {
        // Walk the built-in default map: no action may end up with neither a scancode nor a VK.
        string[] defaults =
        {
            "[", "]", "F", "H", "N", "Space", "L", "K", "O", "P", "B", "V", "U", "E", "Shift", "Ctrl",
        };
        foreach (var key in defaults)
        {
            Assert.True(EuroPad.Server.Profiles.VkLookup.TryGet(key, out var vk), $"'{key}' has no VK");
            var stroke = KeyScan.Resolve(vk, FakeMap);
            Assert.True(stroke.HasScanCode || stroke.Vk != 0, $"'{key}' resolves to nothing injectable");
        }
    }
}
