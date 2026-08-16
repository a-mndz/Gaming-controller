namespace EuroPad.Server.Emulation;

public delegate void KeySender(ushort vk, bool down);

public sealed class KeyboardEmulator
{
    private readonly object _gate = new();
    private readonly KeySender _send;

    public KeyboardEmulator() : this(NativeKeySender.Key) { }

    public KeyboardEmulator(KeySender send) => _send = send;

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

    public void ReleaseAll(byte[] keysByBit)
    {
        lock (_gate)
        {
            foreach (var vk in keysByBit)
                if (vk != 0) _send(vk, false);
        }
    }
}
