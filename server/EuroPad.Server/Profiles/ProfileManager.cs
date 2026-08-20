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

public sealed class ProfileManager : IDisposable
{
    private readonly string _dir;
    private readonly FileSystemWatcher _watcher;
    private readonly object _gate = new();
    private readonly Dictionary<string, ProfileData> _byName = new(StringComparer.OrdinalIgnoreCase);
    private readonly Dictionary<string, byte[]> _keysByBit = new(StringComparer.OrdinalIgnoreCase);
    private readonly Dictionary<string, AxisRouting> _routing = new(StringComparer.OrdinalIgnoreCase);
    private readonly Dictionary<string, string> _fileByName = new(StringComparer.OrdinalIgnoreCase);
    private string _activeName = DefaultName;
    private volatile bool _disposed;

    public const string DefaultName = "default";

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
        // "default" must always exist: it is the fallback active profile, and a dir that
        // contains only e.g. ets2.json must not leave Active/ActiveKeysByBit unresolvable.
        LoadBuiltInDefault();
        CopySamples();
        LoadAll();

        _watcher = new FileSystemWatcher(dir, "*.json") { EnableRaisingEvents = true };
        _watcher.Changed += (_, _) => LoadAll();
        _watcher.Created += (_, _) => LoadAll();
        _watcher.Deleted += (_, _) => LoadAll();
    }

    /// <summary>
    /// Stops watching and, crucially, waits for a reload that is already running on a
    /// FileSystemWatcher threadpool callback. Disposing the watcher alone does not cancel a callback
    /// that has already been dispatched, so without the drain below a reload could still hold a
    /// profile file open after Dispose returned — which broke a caller that deleted the profiles
    /// directory next (the hot-reload unit test, intermittently).
    /// </summary>
    public void Dispose()
    {
        // Set first: any callback that reaches the gate after this point bails out (see LoadAll).
        _disposed = true;
        _watcher.EnableRaisingEvents = false;
        _watcher.Dispose();
        lock (_gate) { }   // drains a reload already inside the lock
    }

    public ProfileData Active
    {
        get { lock (_gate) return _byName.TryGetValue(_activeName, out var p) ? p : _byName[DefaultName]; }
    }

    public byte[] ActiveKeysByBit
    {
        get { lock (_gate) return _keysByBit.TryGetValue(_activeName, out var k) ? k : _keysByBit[DefaultName]; }
    }

    public AxisRouting ActiveRouting
    {
        get { lock (_gate) return _routing.TryGetValue(_activeName, out var r) ? r : _routing[DefaultName]; }
    }

    public string ActiveName
    {
        get { lock (_gate) return _byName.ContainsKey(_activeName) ? _activeName : DefaultName; }
    }

    public bool SetActive(string name)
    {
        lock (_gate)
        {
            if (!_byName.ContainsKey(name)) return false;
            _activeName = name;
            return true;
        }
    }

    public bool SetBitKey(int bit, string key)
    {
        if (bit < 0 || bit >= HiBitNames.Length) return false;
        if (!VkLookup.TryGet(key, out _)) return false;

        lock (_gate)
        {
            var name = _byName.ContainsKey(_activeName) ? _activeName : DefaultName;
            var data = _byName[name];
            data.Keys[HiBitNames[bit]] = key;
            _keysByBit[name] = BuildKeysByBit(data);
            WriteProfile(data, name);
        }
        return true;
    }

    /// <summary>
    /// Persists a profile to its own file (the phone's remap panel writes through here). Built-in
    /// "default" with no file yet lands in default.json so the edit survives restarts.
    /// </summary>
    private void WriteProfile(ProfileData data, string name)
    {
        try
        {
            var path = _fileByName.TryGetValue(name, out var p) ? p : Path.Combine(_dir, name + ".json");
            _fileByName[name] = path;
            File.WriteAllText(path, JsonSerializer.Serialize(data, WriteOpts), Encoding.UTF8);
        }
        catch (Exception e)
        {
            Console.Error.WriteLine($"profile write failed ({name}): {e.Message}");
        }
    }

    private static readonly JsonSerializerOptions WriteOpts = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        WriteIndented = true,
    };

    public IReadOnlyCollection<string> Names
    {
        get { lock (_gate) return _byName.Keys.ToArray(); }
    }

    private const string SamplePrefix = "EuroPad.Server.Profiles.Samples.";

    private void CopySamples()
    {
        try
        {
            var asm = typeof(ProfileManager).Assembly;
            foreach (var res in asm.GetManifestResourceNames())
            {
                if (!res.StartsWith(SamplePrefix, StringComparison.Ordinal)) continue;
                using var stream = asm.GetManifestResourceStream(res);
                if (stream is null) continue;
                var dest = Path.Combine(_dir, res[SamplePrefix.Length..]);
                if (File.Exists(dest)) continue;
                using var outStream = File.Create(dest);
                stream.CopyTo(outStream);
            }
        }
        catch { }
    }

    private static readonly JsonSerializerOptions JsonOpts = new() { PropertyNameCaseInsensitive = true };

    private void LoadAll()
    {
        // Runs on FileSystemWatcher threadpool callbacks: an unhandled throw here (e.g. the dir
        // was deleted between the event and the read) would kill the process, so swallow at the top.
        try
        {
            if (_disposed || !Directory.Exists(_dir)) return;
            lock (_gate)
            {
                // Re-check under the gate: Dispose sets the flag and then waits on this same lock,
                // so a callback that got here late must not open any file.
                if (_disposed) return;
                foreach (var file in Directory.GetFiles(_dir, "*.json"))
                {
                    try
                    {
                        var data = JsonSerializer.Deserialize<ProfileData>(ReadShared(file), JsonOpts);
                        if (data?.Name is not null)
                        {
                            _byName[data.Name] = data;
                            _keysByBit[data.Name] = BuildKeysByBit(data);
                            _routing[data.Name] = AxisRouting.FromMap(data.AxisMap);
                            _fileByName[data.Name] = file;
                        }
                    }
                    catch { }
                }
            }
        }
        catch { }
    }

    /// <summary>
    /// Reads a profile without locking other processes out. File.ReadAllText opens with
    /// FileShare.Read, so a reload landing on the same millisecond as an editor's save (or a
    /// directory delete) makes the *other* side fail. Profiles are a few hundred bytes; sharing
    /// write+delete access costs nothing and a torn read just fails to deserialize and is ignored.
    /// </summary>
    private static string ReadShared(string path)
    {
        using var fs = new FileStream(path, FileMode.Open, FileAccess.Read,
                                      FileShare.ReadWrite | FileShare.Delete);
        using var reader = new StreamReader(fs, Encoding.UTF8);
        return reader.ReadToEnd();
    }

    private void LoadBuiltInDefault()
    {
        var p = new ProfileData
        {
            Name = DefaultName,
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
        _routing[p.Name] = AxisRouting.FromMap(p.AxisMap);
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
        // US-layout VK_OEM_*. Punctuation has no ASCII/VK identity: (byte)'[' is 0x5B = VK_LWIN and
        // (byte)']' is 0x5D = VK_APPS, so the stock ETS2 indicators used to open the Start menu and
        // the context menu instead of signalling. Same trap for every other symbol below.
        ["-"] = 0xBD, ["="] = 0xBB, ["["] = 0xDB, ["]"] = 0xDD, ["\\"] = 0xDC,
        [";"] = 0xBA, ["'"] = 0xDE, [","] = 0xBC, ["."] = 0xBE, ["/"] = 0xBF, ["`"] = 0xC0,
    };

    /// <summary>
    /// Only letters, digits and the names above resolve. Anything else is rejected rather than
    /// coerced: an unknown name that silently became some unrelated VK is how a keymap ends up
    /// pressing Windows keys mid-drive, and SetBitKey uses this as its validation gate.
    /// </summary>
    public static bool TryGet(string key, out byte vk)
    {
        if (Map.TryGetValue(key, out vk)) return true;
        if (key.Length == 1)
        {
            char c = char.ToUpperInvariant(key[0]);
            if (c is >= 'A' and <= 'Z' or >= '0' and <= '9') { vk = (byte)c; return true; }
        }
        vk = 0;
        return false;
    }
}
