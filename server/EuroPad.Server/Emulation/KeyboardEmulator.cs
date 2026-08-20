namespace EuroPad.Server.Emulation;

public delegate void KeySender(ushort vk, bool down);

public sealed class KeyboardEmulator
{
    private readonly object _gate = new();
    private readonly KeySender _send;

    public KeyboardEmulator() : this(NativeKeySender.Key) { }

    public KeyboardEmulator(KeySender send) => _send = send;

    /// <summary>
    /// Presses/releases only the bits that changed between the two hi-button words. Releasing one
    /// slot's held keys is <c>Apply(keys, held, 0)</c> — there is deliberately no ReleaseAll, because
    /// a blind release of every mapped key let one phone's failsafe drop the horn another phone was
    /// still holding.
    /// </summary>
    public void Apply(byte[] keysByBit, ushort prevHi, ushort curHi)
    {
        ushort changed = (ushort)(prevHi ^ curHi);
        if (changed == 0) return;

        lock (_gate)
        {
            for (int bit = 0; bit < keysByBit.Length && bit < 16; bit++)
            {
                if ((changed & (1 << bit)) == 0) continue;
                var vk = keysByBit[bit];
                if (vk == 0) continue;
                _send(vk, (curHi & (1 << bit)) != 0);
            }
        }
    }
}
