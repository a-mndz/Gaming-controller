using EuroPad.Server.Emulation;
using EuroPad.Server.Protocol;

namespace EuroPad.Server.Profiles;

public enum PadAxis { None, Lx, Ly, Rx, Ry }

public enum PadSlider { None, LeftTrigger, RightTrigger }

/// <summary>
/// Where a deck's semantic axes land on the virtual X360 pad. Driven by the profile's
/// <c>axisMap</c> block so a user can move steering to the right stick without a rebuild.
/// </summary>
public sealed class AxisRouting
{
    public PadAxis Steer { get; init; } = PadAxis.Lx;
    public PadSlider Throttle { get; init; } = PadSlider.RightTrigger;
    public PadSlider Brake { get; init; } = PadSlider.LeftTrigger;

    public static readonly AxisRouting Default = new();

    public static AxisRouting FromMap(IDictionary<string, string>? map)
    {
        if (map is null || map.Count == 0) return Default;
        return new AxisRouting
        {
            Steer = ParseAxis(Get(map, "steer"), PadAxis.Lx),
            Throttle = ParseSlider(Get(map, "throttle"), PadSlider.RightTrigger),
            Brake = ParseSlider(Get(map, "brake"), PadSlider.LeftTrigger),
        };
    }

    private static string? Get(IDictionary<string, string> map, string key)
    {
        foreach (var kv in map)
            if (string.Equals(kv.Key, key, StringComparison.OrdinalIgnoreCase)) return kv.Value;
        return null;
    }

    private static PadAxis ParseAxis(string? v, PadAxis fallback) => v?.Trim().ToUpperInvariant() switch
    {
        "LX" => PadAxis.Lx,
        "LY" => PadAxis.Ly,
        "RX" => PadAxis.Rx,
        "RY" => PadAxis.Ry,
        "NONE" or "OFF" => PadAxis.None,
        _ => fallback,
    };

    private static PadSlider ParseSlider(string? v, PadSlider fallback) => v?.Trim().ToUpperInvariant() switch
    {
        "LT" or "LEFTTRIGGER" => PadSlider.LeftTrigger,
        "RT" or "RIGHTTRIGGER" => PadSlider.RightTrigger,
        "NONE" or "OFF" => PadSlider.None,
        _ => fallback,
    };
}

/// <summary>Pad-ready axis values resolved from one wire frame.</summary>
public struct PadAxes
{
    public short Lx, Ly, Rx, Ry;
    public byte LeftTrigger, RightTrigger;
}

public static class AxisRouter
{
    /// <summary>
    /// Sticks pass through; the deck-neutral STEER axis is added into its routed stick axis
    /// (decks drive either the stick or STEER, never both, so adding is order-independent);
    /// LT/RT frame axes go to their routed sliders.
    /// </summary>
    public static PadAxes Route(short[] axes, AxisRouting routing)
    {
        var padAxes = new PadAxes
        {
            Lx = At(axes, AxisIndex.Lx),
            Ly = At(axes, AxisIndex.Ly),
            Rx = At(axes, AxisIndex.Rx),
            Ry = At(axes, AxisIndex.Ry),
        };

        short steer = At(axes, AxisIndex.Steer);
        if (steer != 0)
        {
            switch (routing.Steer)
            {
                case PadAxis.Lx: padAxes.Lx = AddClamp(padAxes.Lx, steer); break;
                case PadAxis.Ly: padAxes.Ly = AddClamp(padAxes.Ly, steer); break;
                case PadAxis.Rx: padAxes.Rx = AddClamp(padAxes.Rx, steer); break;
                case PadAxis.Ry: padAxes.Ry = AddClamp(padAxes.Ry, steer); break;
            }
        }

        AddSlider(ref padAxes, routing.Brake, X360Mapper.TriggerToByte(At(axes, AxisIndex.Lt)));
        AddSlider(ref padAxes, routing.Throttle, X360Mapper.TriggerToByte(At(axes, AxisIndex.Rt)));
        return padAxes;
    }

    private static short At(short[] axes, int i) => (uint)i < (uint)axes.Length ? axes[i] : (short)0;

    private static short AddClamp(short a, short b) =>
        (short)Math.Clamp(a + b, short.MinValue, short.MaxValue);

    private static void AddSlider(ref PadAxes p, PadSlider target, byte value)
    {
        if (value == 0) return;
        switch (target)
        {
            case PadSlider.LeftTrigger: p.LeftTrigger = (byte)Math.Min(255, p.LeftTrigger + value); break;
            case PadSlider.RightTrigger: p.RightTrigger = (byte)Math.Min(255, p.RightTrigger + value); break;
        }
    }
}
