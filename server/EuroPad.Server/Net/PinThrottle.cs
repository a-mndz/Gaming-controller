using System.Net;

namespace EuroPad.Server.Net;

/// <summary>
/// Three wrong PINs from one address earns that address a 60 s lockout.
///
/// The bookkeeping is keyed on the source address, which any host on the LAN can forge, so it is
/// capped: past <see cref="MaxTrackedIps"/> tracked addresses a fail is not recorded at all. That is
/// not a hole — the PIN comparison never consults this, so the HELLO is still rejected. It only means
/// a flood of HELLOs from forged sources cannot also be a memory-growth attack.
/// </summary>
public sealed class PinThrottle
{
    public const int FailsBeforeLockout = 3;
    public const long LockoutMs = 60_000;
    public const int MaxTrackedIps = 1024;

    private readonly object _gate = new();
    private readonly Dictionary<string, long> _lockedUntil = new();
    private readonly Dictionary<string, int> _fails = new();

    public bool IsLocked(EndPoint remote, long now)
    {
        var key = Key(remote);
        lock (_gate)
        {
            if (!_lockedUntil.TryGetValue(key, out var until)) return false;
            if (now < until) return true;
            // Expired: drop it on the way past, because nothing else ever did — an address that once
            // tripped a lockout used to stay in the dictionary for the life of the process.
            _lockedUntil.Remove(key);
            return false;
        }
    }

    public void RecordFail(EndPoint remote, long now)
    {
        var key = Key(remote);
        lock (_gate)
        {
            if (_fails.Count + _lockedUntil.Count >= MaxTrackedIps) Prune(now);
            if (_fails.Count + _lockedUntil.Count >= MaxTrackedIps) return;

            var count = _fails.GetValueOrDefault(key) + 1;
            if (count >= FailsBeforeLockout)
            {
                _fails.Remove(key);
                _lockedUntil[key] = now + LockoutMs;
            }
            else
            {
                _fails[key] = count;
            }
        }
    }

    /// <summary>Called once a HELLO from this address is accepted.</summary>
    public void Clear(EndPoint remote)
    {
        lock (_gate) { _fails.Remove(Key(remote)); }
    }

    /// <summary>Addresses currently held in either dictionary. Bounded by <see cref="MaxTrackedIps"/>.</summary>
    public int TrackedCount
    {
        get { lock (_gate) { return _fails.Count + _lockedUntil.Count; } }
    }

    /// <summary>
    /// Elapsed lockouts, plus every partial fail count. Forgiving one or two fails is harmless —
    /// three in a row still locks the address out — and it keeps the prune to a single pass.
    /// </summary>
    private void Prune(long now)
    {
        List<string>? expired = null;
        foreach (var kv in _lockedUntil)
        {
            if (now < kv.Value) continue;
            (expired ??= new()).Add(kv.Key);
        }
        if (expired is not null)
            foreach (var key in expired)
                _lockedUntil.Remove(key);
        _fails.Clear();
    }

    /// <summary>Address only, no port: a phone that retries binds a fresh source port each attempt.</summary>
    private static string Key(EndPoint remote) =>
        remote is IPEndPoint ip ? ip.Address.ToString() : remote.ToString()!;
}
