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
            using var mgr = new ProfileManager(dir);
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
            DeleteDir(dir);
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
            using var mgr = new ProfileManager(dir);
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
            DeleteDir(dir);
        }
    }

    [Fact]
    public void ShipsSampleProfilesOnFirstRun()
    {
        var dir = Path.Combine(Path.GetTempPath(), "europad-test-" + Guid.NewGuid().ToString("N"));
        try
        {
            using var mgr = new ProfileManager(dir);
            Assert.True(File.Exists(Path.Combine(dir, "default.json")));
            Assert.True(File.Exists(Path.Combine(dir, "ets2.json")));
            Assert.Contains("ets2", mgr.Names);

            Assert.True(mgr.SetActive("ets2"));
            Assert.Equal(900, mgr.Active.SteerRange);
            Assert.Equal("[", mgr.Active.Keys["IND_L"]);
            Assert.Equal(PadAxis.Lx, mgr.ActiveRouting.Steer);
        }
        finally
        {
            DeleteDir(dir);
        }
    }

    [Fact]
    public void ActiveStaysResolvableWhenOnlyForeignProfilesExist()
    {
        // Regression: a profiles dir holding just ets2.json used to throw KeyNotFoundException on
        // every hi-button edge, so ETS2 keys silently never fired.
        var dir = Path.Combine(Path.GetTempPath(), "europad-test-" + Guid.NewGuid().ToString("N"));
        try
        {
            Directory.CreateDirectory(dir);
            File.WriteAllText(Path.Combine(dir, "ets2.json"), """
                {"name":"ets2","keys":{"HORN":"H"},"axisMap":{"steer":"RX"}}
                """);
            using var mgr = new ProfileManager(dir);

            Assert.Equal("default", mgr.ActiveName);
            Assert.Equal(16, mgr.ActiveKeysByBit.Length);
            Assert.Equal(PadAxis.Lx, mgr.ActiveRouting.Steer);

            Assert.True(mgr.SetActive("ets2"));
            Assert.Equal(PadAxis.Rx, mgr.ActiveRouting.Steer);
            Assert.False(mgr.SetActive("nope"));
            Assert.Equal("ets2", mgr.ActiveName);
        }
        finally
        {
            DeleteDir(dir);
        }
    }

    [Fact]
    public void SetBitKey_UpdatesActiveProfileAndPersists()
    {
        var dir = Path.Combine(Path.GetTempPath(), "europad-test-" + Guid.NewGuid().ToString("N"));
        try
        {
            Directory.CreateDirectory(dir);
            File.WriteAllText(Path.Combine(dir, "default.json"), """
                {"name":"default","keys":{"HORN":"H","IND_L":"["}}
                """);
            using var mgr = new ProfileManager(dir);
            Assert.Equal("default", mgr.ActiveName);

            int bit = Array.IndexOf(ProfileManager.HiBitNames, "HORN");
            Assert.True(bit >= 0);

            Assert.True(mgr.SetBitKey(bit, "X"));
            Assert.Equal("X", mgr.Active.Keys["HORN"]);
            Assert.Equal((byte)'X', mgr.ActiveKeysByBit[bit]);

            var text = File.ReadAllText(Path.Combine(dir, "default.json"));
            Assert.Contains("\"HORN\"", text);
            Assert.Contains("\"X\"", text);

            Assert.False(mgr.SetBitKey(99, "X"));      // out of range
            Assert.False(mgr.SetBitKey(bit, "NotAKey")); // unknown key name
            Assert.Equal("X", mgr.Active.Keys["HORN"]); // unchanged after failures
        }
        finally
        {
            DeleteDir(dir);
        }
    }

    /// <summary>
    /// Windows can still be holding a handle on a just-closed file (indexer, antivirus, or a
    /// FileSystemWatcher callback that was dispatched moments before Dispose). A bare
    /// Directory.Delete then throws IOException and fails an otherwise green test, so retry briefly
    /// and never let cleanup be the thing that reports a failure.
    /// </summary>
    private static void DeleteDir(string dir)
    {
        for (int attempt = 0; attempt < 10; attempt++)
        {
            try
            {
                if (Directory.Exists(dir)) Directory.Delete(dir, true);
                return;
            }
            catch (IOException) { Thread.Sleep(50); }
            catch (UnauthorizedAccessException) { Thread.Sleep(50); }
        }
    }
}
