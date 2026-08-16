using Makaretu.Dns;

namespace EuroPad.Server.Net;

public sealed class MdnsAnnouncer : IAsyncDisposable
{
    private MulticastService? _mdns;
    private ServiceDiscovery? _discovery;

    public const string ServiceName = "_europad._udp";
    public const string InstanceName = "EuroPad Server";

    public Task StartAsync(int port)
    {
        try
        {
            _mdns = new MulticastService();
            _discovery = new ServiceDiscovery(_mdns);
            _discovery.Advertise(new ServiceProfile(InstanceName, ServiceName, (ushort)port));
            _mdns.Start();
            Console.WriteLine($"mDNS: announcing '{InstanceName}' as {ServiceName} on port {port}");
        }
        catch (Exception e)
        {
            Console.Error.WriteLine($"mDNS announce failed: {e.Message} - manual IP remains available (FR-1.2)");
            _mdns = null;
            _discovery = null;
        }
        return Task.CompletedTask;
    }

    public ValueTask DisposeAsync()
    {
        _discovery?.Dispose();
        _mdns?.Stop();
        return ValueTask.CompletedTask;
    }
}
