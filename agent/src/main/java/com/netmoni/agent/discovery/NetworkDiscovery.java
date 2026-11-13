package com.netmoni.agent.discovery;

import com.netmoni.agent.config.AgentConfig;
import com.netmoni.agent.model.DiscoveredDevice;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
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
        List<Future<DiscoveredDevice>> futures = new ArrayList<>();
        for (String ip : config.subnet().hosts(com.netmoni.agent.config.Ipv4Cidr.MAX_SCAN_HOSTS)) {
            futures.add(workers.submit(() -> probe(ip)));
        }
        List<DiscoveredDevice> devices = new ArrayList<>();
        for (Future<DiscoveredDevice> future : futures) {
            try {
                DiscoveredDevice device = future.get();
                if (device != null) {
                    devices.add(device);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Network discovery was interrupted", exception);
            } catch (ExecutionException exception) {
                logger.warn("Discovery probe failed: {}", exception.getCause().getMessage());
            }
        }
        return List.copyOf(devices);
    }

    private DiscoveredDevice probe(String ip) {
        try {
            InetAddress address = InetAddress.getByName(ip);
            if (!address.isReachable(config.icmpTimeoutMs())) {
                return null;
            }
            String hostname = address.getCanonicalHostName();
            if (hostname.equals(ip)) {
                hostname = null;
            }
            return new DiscoveredDevice(ip, hostname, Instant.now());
        } catch (IOException exception) {
            logger.debug("Could not discover {}: {}", ip, exception.getMessage());
            return null;
        }
    }
}
