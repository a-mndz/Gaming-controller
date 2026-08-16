using System.Text;
using System.Text.Json;

namespace EuroPad.Server.Profiles;

public sealed class ProfileData
{
    public string Name { get; set; } = "default";
    public string Game { get; set; } = "";
    public Dictionary<string, string> Keys { get; set; } = new();
    public Dictionary<string, string> AxisMap { get; set; } = new();
    public int SteerRange { get; set; } = 270;
}

public sealed class ProfileManager
{
    private readonly string _dir;
    private readonly FileSystemWatcher _watcher;
    private readonly object _gate = new();
    private readonly Dictionary<string, ProfileData> _byName = new(StringComparer.OrdinalIgnoreCase);
    private readonly Dictionary<string, byte[]> _keysByBit = new(StringComparer.OrdinalIgnoreCase);
    private string _activeName = "default";

    public static readonly string[] HiBitNames =
    {
        "IND_L", "IND_R", "HAZARD", "HORN", "AIR_HORN", "HANDBRAKE",
        "LIGHTS", "BEAM", "WARNING", "WIPERS", "EXH_BRAKE", "DIFF_LOCK",
        "AXLE_RAISE", "ENGINE", "GEAR_UP", "GEAR_DN",
    };

    public ProfileManager(string dir)
    {
        _dir = dir;
        Directory.CreateDirectory(dir);
        LoadAll();
        if (_byName.Count == 0) LoadBuiltInDefault();

        _watcher = new FileSystemWatcher(dir, "*.json") { EnableRaisingEvents = true };
        _watcher.Changed += (_, _) => LoadAll();
        _watcher.Created += (_, _) => LoadAll();
        _watcher.Deleted += (_, _) => LoadAll();
    }

    public ProfileData Active
    {
        get { lock (_gate) return _byName[_activeName]; }
    }

    public byte[] ActiveKeysByBit
    {
        get { lock (_gate) return _keysByBit[_activeName]; }
    }

    public void SetActive(string name) { lock (_gate) if (_byName.ContainsKey(name)) _activeName = name; }

    private static readonly JsonSerializerOptions JsonOpts = new() { PropertyNameCaseInsensitive = true };

    private void LoadAll()
    {
        lock (_gate)
        {
            foreach (var file in Directory.GetFiles(_dir, "*.json"))
            {
                try
                {
                    var data = JsonSerializer.Deserialize<ProfileData>(File.ReadAllText(file, Encoding.UTF8), JsonOpts);
                    if (data?.Name is not null)
                    {
                        _byName[data.Name] = data;
                        _keysByBit[data.Name] = BuildKeysByBit(data);
                    }
                }
                catch { }
            }
        }
    }

    private void LoadBuiltInDefault()
    {
        var p = new ProfileData
        {
            Name = "default",
            Game = "Generic",
            Keys = new Dictionary<string, string>
            {
                ["IND_L"] = "[", ["IND_R"] = "]", ["HAZARD"] = "F", ["HORN"] = "H",
                ["AIR_HORN"] = "N", ["HANDBRAKE"] = "Space", ["LIGHTS"] = "L",
                ["BEAM"] = "K", ["WARNING"] = "O", ["WIPERS"] = "P",
                ["EXH_BRAKE"] = "B", ["DIFF_LOCK"] = "V", ["AXLE_RAISE"] = "U",
                ["ENGINE"] = "E", ["GEAR_UP"] = "Shift", ["GEAR_DN"] = "Ctrl",
            },
        };
        _byName[p.Name] = p;
        _keysByBit[p.Name] = BuildKeysByBit(p);
    }

    private static byte[] BuildKeysByBit(ProfileData data)
    {
        var arr = new byte[16];
        for (int i = 0; i < HiBitNames.Length; i++)
        {
            if (data.Keys.TryGetValue(HiBitNames[i], out var key) && VkLookup.TryGet(key, out var vk))
                arr[i] = vk;
        }
        return arr;
    }
}

public static class VkLookup
{
    private static readonly Dictionary<string, byte> Map = new(StringComparer.OrdinalIgnoreCase)
    {
        ["Space"] = 0x20, ["Backspace"] = 0x08, ["Tab"] = 0x09, ["Enter"] = 0x0D,
        ["Shift"] = 0x10, ["Ctrl"] = 0x11, ["Alt"] = 0x12, ["Esc"] = 0x1B,
        ["CapsLock"] = 0x14, ["Left"] = 0x25, ["Up"] = 0x26, ["Right"] = 0x27, ["Down"] = 0x28,
        ["F1"] = 0x70, ["F2"] = 0x71, ["F3"] = 0x72, ["F4"] = 0x73, ["F5"] = 0x74,
        ["F6"] = 0x75, ["F7"] = 0x76, ["F8"] = 0x77, ["F9"] = 0x78, ["F10"] = 0x79,
        ["F11"] = 0x7A, ["F12"] = 0x7B, ["MediaPlayPause"] = 0xB3, ["MediaStop"] = 0xB2,
        ["MediaNext"] = 0xB0, ["MediaPrev"] = 0xB1, ["VolumeUp"] = 0xAF, ["VolumeDown"] = 0xAE, ["Mute"] = 0xAD,
    };

    public static bool TryGet(string key, out byte vk)
    {
        if (Map.TryGetValue(key, out vk)) return true;
        if (key.Length == 1)
        {
            char c = char.ToUpperInvariant(key[0]);
            if (c is >= 'A' and <= 'Z' or >= '0' and <= '9') { vk = (byte)c; return true; }
            vk = (byte)key[0];
            return vk >= 0x20;
        }
        vk = 0;
        return false;
    }
}
