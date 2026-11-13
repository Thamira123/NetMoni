package com.netmoni.agent.discovery;

import com.netmoni.agent.config.AgentConfig;
import com.netmoni.agent.model.DiscoveredDevice;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NetworkDiscovery {
    private static final Logger logger = LoggerFactory.getLogger(NetworkDiscovery.class);
    private final AgentConfig config;
    private final ExecutorService workers;

    public NetworkDiscovery(AgentConfig config, ExecutorService workers) {
        this.config = config;
        this.workers = workers;
    }

    public List<DiscoveredDevice> scan() {
        return scan(config.subnet(),(checked,total)->{});
    }
    public List<DiscoveredDevice> scan(BiConsumer<Integer,Integer> progress) {
        return scan(config.subnet(),progress);
    }
    public List<DiscoveredDevice> scan(com.netmoni.agent.config.Ipv4Cidr subnet,BiConsumer<Integer,Integer> progress) {
        return scan(subnet,null,progress);
    }
    public List<DiscoveredDevice> scan(com.netmoni.agent.config.Ipv4Cidr subnet,String interfaceName,BiConsumer<Integer,Integer> progress) {
        List<Future<DiscoveredDevice>> futures = new ArrayList<>();
        for (String ip : subnet.hosts(com.netmoni.agent.config.Ipv4Cidr.MAX_SCAN_HOSTS)) {
            futures.add(workers.submit(() -> probe(ip,subnet)));
        }
        List<DiscoveredDevice> devices = new ArrayList<>();
        AtomicInteger checked = new AtomicInteger();
        for (Future<DiscoveredDevice> future : futures) {
            try {
                DiscoveredDevice device = future.get();
                if (device != null) {
                    devices.add(device);
                }
                progress.accept(checked.incrementAndGet(), futures.size());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Network discovery was interrupted", exception);
            } catch (ExecutionException exception) {
                logger.warn("Discovery probe failed: {}", exception.getCause().getMessage());
                progress.accept(checked.incrementAndGet(), futures.size());
            }
        }
        Map<String,String> macs=ArpTable.read(subnet);
        return devices.stream().map(d->new DiscoveredDevice(d.ipAddress(),d.hostname(),d.discoveredAt(),subnet.toString(),interfaceName,macs.get(d.ipAddress()),d.deviceType())).toList();
    }

    private DiscoveredDevice probe(String ip,com.netmoni.agent.config.Ipv4Cidr subnet) {
        try {
            InetAddress address = InetAddress.getByName(ip);
            if (!address.isReachable(config.icmpTimeoutMs()) && !hasOpenConfiguredPort(ip)) {
                return null;
            }
            String hostname = address.getCanonicalHostName();
            if (hostname.equals(ip)) {
                hostname = null;
            }
            return new DiscoveredDevice(ip, hostname, Instant.now(), subnet.toString(), null, null, "UNKNOWN");
        } catch (IOException exception) {
            logger.debug("Could not discover {}: {}", ip, exception.getMessage());
            return null;
        }
    }

    private boolean hasOpenConfiguredPort(String ip) {
        int probeTimeoutMs = Math.min(config.connectTimeoutMs(), 300);
        for (int port : config.tcpPorts()) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(ip, port), probeTimeoutMs);
                return true;
            } catch (IOException ignored) {
                // ICMP may be blocked; a configured reachable TCP service still identifies the host.
            }
        }
        return false;
    }
}
