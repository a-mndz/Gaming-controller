using QRCoder;

namespace EuroPad.Server.Net;

/// <summary>
/// Phase-3 pairing surface (T3.6, FR-1.3): prints a scannable QR to the server console encoding
/// <c>ip=…&amp;port=…[&amp;pin=…]</c>. The phone reads the QR once to fill in its connect fields;
/// the slot is still assigned dynamically at HELLO time, never baked into the code (ARCHITECTURE §7).
/// The tray app's "Copy pairing QR" takes over this display surface in Phase 4 (T4.6).
/// </summary>
public static class PairingQr
{
    public static string PayloadFor(string ip, int port, int? pin) =>
        pin is > 0 and <= 9999 ? $"ip={ip}&port={port}&pin={pin:D4}" : $"ip={ip}&port={port}";

    /// <summary>
    /// Console rendering with half-block characters: two module rows per character row, dark =
    /// ▄/▀/█, light = space. QRCoder 1.6 removed its own ASCII renderer, so we read the matrix.
    /// </summary>
    public static string RenderAscii(QRCodeData data)
    {
        var m = data.ModuleMatrix;
        int n = m.Count;
        var sb = new System.Text.StringBuilder((n / 2 + 1) * (n * 2 + 3));
        for (int y = 0; y < n; y += 2)
        {
            for (int x = 0; x < n; x++)
            {
                bool top = m[y][x];
                bool bottom = y + 1 < n && m[y + 1][x];
                sb.Append((top, bottom) switch
                {
                    (true, true) => '█',
                    (true, false) => '▀',
                    (false, true) => '▄',
                    _ => ' ',
                });
            }
            sb.AppendLine();
        }
        return sb.ToString();
    }
}
