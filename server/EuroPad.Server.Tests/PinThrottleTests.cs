using System.Net;
using EuroPad.Server.Net;
using Xunit;

namespace EuroPad.Server.Tests;

/// <summary>
/// The lockout bookkeeping used to live in <c>SlotManager</c>, whose constructor needs a real
/// <c>ViGEmClient</c> — so the one security-relevant path on the server had no test at all. Two things
/// it has to get right: three fails really do lock the address out, and the dictionaries do not grow
/// without bound when the source address is forged (which, on a LAN, it trivially can be).
/// </summary>
public class PinThrottleTests
{
    private static IPEndPoint At(string ip, int port = 5000) => new(IPAddress.Parse(ip), port);

    [Fact]
    public void ThirdFailLocksTheAddressOut()
    {
        var pins = new PinThrottle();
        var phone = At("192.168.1.9");

        pins.RecordFail(phone, 0);
        Assert.False(pins.IsLocked(phone, 0));
        pins.RecordFail(phone, 10);
        Assert.False(pins.IsLocked(phone, 10));
        pins.RecordFail(phone, 20);
        Assert.True(pins.IsLocked(phone, 20));
    }

    [Fact]
    public void RetryFromAFreshSourcePortStillCountsAgainstTheSameAddress()
    {
        var pins = new PinThrottle();
        // A phone that reconnects binds a new ephemeral port each attempt; keying on the full
        // endpoint would hand it an unlimited supply of fresh guesses.
        pins.RecordFail(At("192.168.1.9", 41001), 0);
        pins.RecordFail(At("192.168.1.9", 41002), 1);
        pins.RecordFail(At("192.168.1.9", 41003), 2);

        Assert.True(pins.IsLocked(At("192.168.1.9", 41004), 3));
    }

    [Fact]
    public void OneAddressLockoutDoesNotAffectAnother()
    {
        var pins = new PinThrottle();
        for (var i = 0; i < PinThrottle.FailsBeforeLockout; i++) pins.RecordFail(At("192.168.1.9"), i);

        Assert.True(pins.IsLocked(At("192.168.1.9"), 0));
        Assert.False(pins.IsLocked(At("192.168.1.10"), 0));
    }

    [Fact]
    public void AcceptedHelloForgetsThePartialFails()
    {
        var pins = new PinThrottle();
        pins.RecordFail(At("192.168.1.9"), 0);
        pins.RecordFail(At("192.168.1.9"), 1);
        pins.Clear(At("192.168.1.9"));

        pins.RecordFail(At("192.168.1.9"), 2);
        Assert.False(pins.IsLocked(At("192.168.1.9"), 2));
        Assert.Equal(1, pins.TrackedCount);
    }

    [Fact]
    public void ExpiredLockoutIsForgottenAsItIsRead()
    {
        var pins = new PinThrottle();
        for (var i = 0; i < PinThrottle.FailsBeforeLockout; i++) pins.RecordFail(At("192.168.1.9"), i);
        Assert.Equal(1, pins.TrackedCount);

        // The clock starts at the third fail, not at zero.
        Assert.False(pins.IsLocked(At("192.168.1.9"), PinThrottle.FailsBeforeLockout + PinThrottle.LockoutMs));
        // Not merely "no longer locked": the entry is gone, so a process that ran for a month with a
        // PIN set does not carry every address that ever mistyped it.
        Assert.Equal(0, pins.TrackedCount);
    }

    [Fact]
    public void FloodOfForgedAddressesStaysBounded()
    {
        var pins = new PinThrottle();
        for (var i = 0; i < PinThrottle.MaxTrackedIps * 3; i++)
            pins.RecordFail(At($"10.{i / 65536 % 256}.{i / 256 % 256}.{i % 256}"), i);

        Assert.True(
            pins.TrackedCount <= PinThrottle.MaxTrackedIps,
            $"tracked {pins.TrackedCount} addresses, cap is {PinThrottle.MaxTrackedIps}");
    }

    [Fact]
    public void PruningUnderFloodStillLocksOutARepeatOffender()
    {
        var pins = new PinThrottle();
        var attacker = At("192.168.1.9");
        // The prune drops partial counts, so an attacker must not be able to reset their own count by
        // spraying HELLOs from forged addresses in between guesses: three *consecutive* fails lock.
        for (var i = 0; i < PinThrottle.MaxTrackedIps * 2; i++)
            pins.RecordFail(At($"10.{i / 65536 % 256}.{i / 256 % 256}.{i % 256}"), i);

        for (var i = 0; i < PinThrottle.FailsBeforeLockout; i++) pins.RecordFail(attacker, i);
        Assert.True(pins.IsLocked(attacker, 0));
    }
}
