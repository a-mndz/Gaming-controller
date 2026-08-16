using EuroPad.Server.Emulation;
using EuroPad.Server.Protocol;
using Xunit;

namespace EuroPad.Server.Tests;

public class MapperTests
{
    [Theory]
    [InlineData(0, 0)]
    [InlineData(short.MaxValue, 255)]
    public void TriggerToByte_RangeMaps(short input, byte expected)
    {
        Assert.Equal(expected, X360Mapper.TriggerToByte(input));
    }

    [Theory]
    [InlineData(short.MinValue)]
    [InlineData(-100)]
    [InlineData(-1)]
    public void TriggerToByte_NegativeClampedToZero(short input)
    {
        Assert.Equal((byte)0, X360Mapper.TriggerToByte(input));
    }

    [Fact]
    public void ButtonsLo_MapsToXusbMask()
    {
        ushort lo = (ushort)(ButtonsLo.A | ButtonsLo.Lb | ButtonsLo.DpadUp);
        var mapped = X360Mapper.ButtonsLoToXusbMask(lo);
        Assert.True((mapped & XusbMask.A) != 0);
        Assert.True((mapped & XusbMask.LeftShoulder) != 0);
        Assert.True((mapped & XusbMask.DpadUp) != 0);
        Assert.False((mapped & XusbMask.B) != 0);
    }

    [Fact]
    public void ButtonsLo_FullSurfaceMatchesXusbReportBits()
    {
        ushort lo = 0xFFFF;
        var mapped = X360Mapper.ButtonsLoToXusbMask(lo);

        Assert.Equal(XusbMask.DpadUp | XusbMask.DpadRight | XusbMask.DpadDown | XusbMask.DpadLeft |
                     XusbMask.Start | XusbMask.Back | XusbMask.LeftShoulder | XusbMask.RightShoulder |
                     XusbMask.Guide | XusbMask.A | XusbMask.B | XusbMask.X | XusbMask.Y, mapped);
    }

    [Fact]
    public void ButtonsLo_TriggerBitsNotMappedToPad()
    {
        ushort lo = (ushort)((1 << 13) | (1 << 14) | (1 << 15));
        Assert.Equal((ushort)0, X360Mapper.ButtonsLoToXusbMask(lo));
    }
}

public class KeyboardEmulatorTests
{
    private readonly List<(ushort vk, bool down)> _events = new();
    private KeyboardEmulator Make() => new((vk, down) => _events.Add((vk, down)));

    [Fact]
    public void Apply_FiresOnlyOnChangedBits()
    {
        var kb = Make();
        var keys = new byte[16];
        keys[3] = (byte)'H';
        keys[5] = 0x20;

        kb.Apply(keys, 0, (ushort)(1 << 3));
        Assert.Equal(new List<(ushort, bool)> { ((ushort)'H', true) }, _events);

        kb.Apply(keys, (ushort)(1 << 3), (ushort)((1 << 3) | (1 << 5)));
        Assert.Equal(new List<(ushort, bool)> { ((ushort)'H', true), (0x20, true) }, _events);
    }

    [Fact]
    public void Apply_ReleaseOnBitClear()
    {
        var kb = Make();
        var keys = new byte[16];
        keys[0] = (byte)'[';

        kb.Apply(keys, 0, 1);
        kb.Apply(keys, 1, 0);
        Assert.Equal(new List<(ushort, bool)> { ((ushort)'[', true), ((ushort)'[', false) }, _events);
    }

    [Fact]
    public void Apply_NoEventsOnUnchangedInput()
    {
        var kb = Make();
        var keys = new byte[16];
        keys[3] = (byte)'H';

        kb.Apply(keys, 0, (ushort)(1 << 3));
        kb.Apply(keys, (ushort)(1 << 3), (ushort)(1 << 3));
        Assert.Single(_events);
    }

    [Fact]
    public void Apply_SkipsUnmappedBits()
    {
        var kb = Make();
        var keys = new byte[16];

        kb.Apply(keys, 0, 0xFFFF);
        Assert.Empty(_events);
    }

    [Fact]
    public void ReleaseAll_ReleasesMappedKeysOnly()
    {
        var kb = Make();
        var keys = new byte[16];
        keys[0] = (byte)'[';
        keys[14] = 0x10;

        kb.ReleaseAll(keys);
        Assert.Equal(new List<(ushort, bool)> { ((ushort)'[', false), (0x10, false) }, _events);
    }
}
