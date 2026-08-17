using EuroPad.Server.Profiles;
using EuroPad.Server.Protocol;
using Xunit;

namespace EuroPad.Server.Tests;

public class AxisRoutingTests
{
    private static short[] Axes(short lx = 0, short ly = 0, short rx = 0, short ry = 0,
                                short lt = 0, short rt = 0, short steer = 0)
    {
        var a = new short[Proto.AxesCount];
        a[AxisIndex.Lx] = lx;
        a[AxisIndex.Ly] = ly;
        a[AxisIndex.Rx] = rx;
        a[AxisIndex.Ry] = ry;
        a[AxisIndex.Lt] = lt;
        a[AxisIndex.Rt] = rt;
        a[AxisIndex.Steer] = steer;
        return a;
    }

    [Fact]
    public void SteerLandsOnLeftThumbXByDefault()
    {
        var routed = AxisRouter.Route(Axes(steer: 12000), AxisRouting.Default);
        Assert.Equal(12000, routed.Lx);
        Assert.Equal(0, routed.Ly);
        Assert.Equal(0, routed.Rx);
    }

    [Fact]
    public void SticksPassThroughUntouched()
    {
        var routed = AxisRouter.Route(Axes(lx: -3000, ly: 500, rx: 700, ry: -900), AxisRouting.Default);
        Assert.Equal(-3000, routed.Lx);
        Assert.Equal(500, routed.Ly);
        Assert.Equal(700, routed.Rx);
        Assert.Equal(-900, routed.Ry);
    }

    [Fact]
    public void PedalsScaleToTriggerBytes()
    {
        var routed = AxisRouter.Route(Axes(lt: short.MaxValue, rt: 20000), AxisRouting.Default);
        Assert.Equal(255, routed.LeftTrigger);
        Assert.Equal(155, routed.RightTrigger);
    }

    [Fact]
    public void ProfileCanMoveSteeringToRightStick()
    {
        var routing = AxisRouting.FromMap(new Dictionary<string, string> { ["steer"] = "RX" });
        var routed = AxisRouter.Route(Axes(steer: -8000), routing);
        Assert.Equal(-8000, routed.Rx);
        Assert.Equal(0, routed.Lx);
    }

    [Fact]
    public void ProfileCanSwapPedals()
    {
        var routing = AxisRouting.FromMap(new Dictionary<string, string>
        {
            ["throttle"] = "LT",
            ["brake"] = "RT",
        });
        var routed = AxisRouter.Route(Axes(lt: short.MaxValue, rt: short.MaxValue), routing);
        Assert.Equal(255, routed.LeftTrigger);
        Assert.Equal(255, routed.RightTrigger);

        var throttleOnly = AxisRouter.Route(Axes(rt: short.MaxValue), routing);
        Assert.Equal(255, throttleOnly.LeftTrigger);
        Assert.Equal(0, throttleOnly.RightTrigger);
    }

    [Fact]
    public void SteerClampsInsteadOfWrapping()
    {
        var routed = AxisRouter.Route(Axes(lx: 30000, steer: 30000), AxisRouting.Default);
        Assert.Equal(short.MaxValue, routed.Lx);
    }

    [Fact]
    public void UnknownOrMissingMapEntriesFallBackToDefaults()
    {
        var routing = AxisRouting.FromMap(new Dictionary<string, string> { ["steer"] = "banana" });
        Assert.Equal(PadAxis.Lx, routing.Steer);
        Assert.Equal(PadSlider.RightTrigger, routing.Throttle);
        Assert.Equal(PadSlider.LeftTrigger, routing.Brake);
    }

    [Fact]
    public void SteerCanBeDisabled()
    {
        var routing = AxisRouting.FromMap(new Dictionary<string, string> { ["steer"] = "none" });
        var routed = AxisRouter.Route(Axes(steer: 20000), routing);
        Assert.Equal(0, routed.Lx);
    }

    [Fact]
    public void NegativeTriggerValuesClampToZero()
    {
        var routed = AxisRouter.Route(Axes(lt: -5000), AxisRouting.Default);
        Assert.Equal(0, routed.LeftTrigger);
    }
}
