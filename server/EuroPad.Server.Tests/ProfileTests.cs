using EuroPad.Server.Profiles;
using Xunit;

namespace EuroPad.Server.Tests;

public class ProfileTests
{
    [Fact]
    public void BuiltInDefault_HasEts2Keys()
    {
        var dir = Path.Combine(Path.GetTempPath(), "europad-test-" + Guid.NewGuid().ToString("N"));
        try
        {
            var mgr = new ProfileManager(dir);
            var active = mgr.Active;
            Assert.Equal("default", active.Name);
            Assert.Equal("[", active.Keys["IND_L"]);
            Assert.Equal("]", active.Keys["IND_R"]);
            Assert.Equal("F", active.Keys["HAZARD"]);
            Assert.Equal("H", active.Keys["HORN"]);
            Assert.Equal("Shift", active.Keys["GEAR_UP"]);
            Assert.Equal("Ctrl", active.Keys["GEAR_DN"]);

            var bits = mgr.ActiveKeysByBit;
            Assert.Equal((byte)'[', bits[0]);
            Assert.Equal(0x20, bits[5]);
            Assert.Equal(0x10, bits[14]);
            Assert.Equal(0x11, bits[15]);
        }
        finally
        {
            Directory.Delete(dir, true);
        }
    }

    [Fact]
    public void VkLookup_HandlesLettersDigitsSpecials()
    {
        Assert.True(VkLookup.TryGet("Space", out var space));
        Assert.Equal(0x20, space);
        Assert.True(VkLookup.TryGet("a", out var a));
        Assert.Equal(0x41, a);
        Assert.True(VkLookup.TryGet("5", out var five));
        Assert.Equal(0x35, five);
        Assert.True(VkLookup.TryGet("F12", out var f12));
        Assert.Equal(0x7B, f12);
        Assert.False(VkLookup.TryGet("NotAKey", out _));
    }

    [Fact]
    public void HotReload_PicksUpNewFile()
    {
        var dir = Path.Combine(Path.GetTempPath(), "europad-test-" + Guid.NewGuid().ToString("N"));
        try
        {
            var mgr = new ProfileManager(dir);
            var path = Path.Combine(dir, "ets2.json");
            File.WriteAllText(path, """
                {"name":"ets2","game":"ETS2","keys":{"IND_L":"[","IND_R":"]","HAZARD":"F","HORN":"H"},"steerRange":270}
                """);

            var deadline = Environment.TickCount64 + 5000;
            while (Environment.TickCount64 < deadline)
            {
                mgr.SetActive("ets2");
                if (mgr.Active.Name == "ets2") break;
                Thread.Sleep(50);
            }
            Assert.Equal("ets2", mgr.Active.Name);
            Assert.Equal("[", mgr.Active.Keys["IND_L"]);
        }
        finally
        {
            Directory.Delete(dir, true);
        }
    }
}
