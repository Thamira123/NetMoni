package com.netmoni.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netmoni.agent.communication.BackendClient;
import com.netmoni.agent.communication.MessageOutbox;
import com.netmoni.agent.communication.ProtocolMessages;
import com.netmoni.agent.config.AgentConfig;
import com.netmoni.agent.config.NetworkInterfaceDiscovery;
import com.netmoni.agent.config.Ipv4Cidr;
import com.netmoni.agent.discovery.NetworkDiscovery;
import com.netmoni.agent.model.DiscoveredDevice;
import com.netmoni.agent.model.MonitoringResult;
import com.netmoni.agent.monitoring.icmp.IcmpMonitor;
import com.netmoni.agent.monitoring.snmp.SnmpMonitor;
import com.netmoni.agent.monitoring.tcp.TcpPortMonitor;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AgentRuntime implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(AgentRuntime.class);
    private final AgentConfig config;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final ScheduledExecutorService workers;
    private final ScheduledExecutorService discoveryWorkers;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final BackendClient backend;
    private final MessageOutbox outbox;
    private final NetworkDiscovery discovery;
    private final IcmpMonitor icmp;
    private final TcpPortMonitor tcp;
    private final SnmpMonitor snmp;
    private final AtomicBoolean registered = new AtomicBoolean();
    private final AtomicBoolean discoveryRunning = new AtomicBoolean();
    private final AtomicBoolean monitoringRunning = new AtomicBoolean();
    private final AtomicBoolean outboxRunning = new AtomicBoolean();
    private final Map<String, UUID> deviceIds = new ConcurrentHashMap<>();

    public AgentRuntime(AgentConfig config) throws IOException {
        this.config = config;
        deviceIds.putAll(config.deviceIds());
        workers = Executors.newScheduledThreadPool(config.workerThreads());
        discoveryWorkers = Executors.newScheduledThreadPool(config.workerThreads());
        backend = new BackendClient(config.backendHost(), config.backendPort(),
                config.connectTimeoutMs(), config.readTimeoutMs(), mapper);
        outbox = new MessageOutbox(config.dataDirectory().resolve("outbox.jsonl"), mapper);
        discovery = new NetworkDiscovery(config, discoveryWorkers);
        icmp = new IcmpMonitor(config.icmpTimeoutMs(), config.icmpRetries());
        tcp = new TcpPortMonitor(config.connectTimeoutMs());
        snmp = new SnmpMonitor(config.snmpVersion(), config.snmpPort(), config.snmpCommunity(),
                config.snmpSecurityName(), config.snmpAuthProtocol(), config.snmpAuthPassphrase(),
                config.snmpPrivacyProtocol(), config.snmpPrivacyPassphrase(), config.snmpTimeoutMs(), mapper);
    }

    public void start() {
        logger.info("Starting NetMoni agent {} ({}) at {} via interface {} on subnet {}",
                config.agentId(), config.name(), config.localIp(), config.networkInterfaceName(), config.subnet());
        scheduler.scheduleWithFixedDelay(this::register, 0, config.retryIntervalSeconds(), TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(this::heartbeat, config.heartbeatIntervalSeconds(),
                config.heartbeatIntervalSeconds(), TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(this::runDiscovery, 0, config.discoveryIntervalSeconds(),
                TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(this::monitorDevices, 5, config.monitoringIntervalSeconds(),
                TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(this::flushOutbox, 1,
                config.outboxFlushIntervalSeconds(), TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(this::pollDiscoveryCommand, 2, 2, TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(this::reportNetworks, 30, 60, TimeUnit.SECONDS);
    }

    private void register() {
        if (registered.get()) return;
        try {
            backend.send(ProtocolMessages.registration(mapper, config), "agent.registered");
            registered.set(true);
            logger.info("Agent registration acknowledged by backend");
            runDiscovery();
        } catch (IOException exception) {
            logger.warn("Agent registration failed; will retry: {}", exception.getMessage());
        }
    }

    private void heartbeat() {
        if (!registered.get()) return;
        try {
            backend.send(ProtocolMessages.heartbeat(mapper, config.agentId()), "agent.heartbeat.accepted");
        } catch (IOException exception) {
            registered.set(false);
            logger.warn("Agent heartbeat failed; registration will be retried: {}", exception.getMessage());
        }
    }

    private void runDiscovery() {
        if (!registered.get()) return;
        if (!discoveryRunning.compareAndSet(false, true)) return;
        workers.execute(() -> {
            try {
                int totalFound=0,registeredCount=0;
                for(var network:NetworkInterfaceDiscovery.discover(config.allowedCidrs()).stream().filter(n->n.scanEligible()).toList()) {
                    Ipv4Cidr cidr=Ipv4Cidr.parse(network.cidr());
                    List<DiscoveredDevice> found=discovery.scan(cidr,network.interfaceName(),(checked,total)->{});
                    if(found.isEmpty())continue;
                    JsonNode ack=backend.send(ProtocolMessages.discoveredDevices(mapper,config.agentId(),found),"device.discovery.accepted");
                    registeredCount+=mergeDeviceMappings(cidr,deviceIds,ack.path("devices")); totalFound+=found.size();
                }
                logger.info("Discovery found {} reachable device(s); {} registered across authorized interfaces",totalFound,registeredCount);
            } catch (IOException exception) {
                logger.warn("Could not register discovered devices; discovery will retry: {}", exception.getMessage());
            } catch (RuntimeException exception) {
                logger.error("Network discovery failed", exception);
            } finally {
                discoveryRunning.set(false);
            }
        });
    }

    private void reportNetworks(){if(!registered.get())return;try{backend.send(ProtocolMessages.networks(mapper,config),"agent.networks.accepted");}catch(IOException e){logger.debug("Could not refresh network inventory: {}",e.getMessage());}}

    private void pollDiscoveryCommand() {
        if (!registered.get()) return;
        try {
            JsonNode response = backend.send(ProtocolMessages.discoveryCommand(mapper, config.agentId()), null);
            replaceMonitoringTargets(deviceIds, response.path("monitoringTargets"), config.allowedCidrs());
            if ("discovery.command".equals(response.path("type").asText()) && response.hasNonNull("scanId")) {
                UUID scanId = UUID.fromString(response.path("scanId").asText());
                String cidr=response.path("networkCidr").asText("");
                if (discoveryRunning.compareAndSet(false, true)) workers.execute(() -> runRequestedScan(scanId,cidr));
            }
        } catch (IOException | IllegalArgumentException exception) {
            logger.debug("Could not poll queued discovery command: {}", exception.getMessage());
        }
    }

    private void runRequestedScan(UUID scanId,String networkCidr) {
        long startedAt = System.nanoTime();
        AtomicInteger lastReported = new AtomicInteger(-1);
        AtomicInteger newDevices = new AtomicInteger();
        AtomicInteger existingDevices = new AtomicInteger();
        try {
            Ipv4Cidr subnet=Ipv4Cidr.parse(networkCidr);
            var network=NetworkInterfaceDiscovery.discover(config.allowedCidrs()).stream().filter(n->n.scanEligible()&&n.cidr().equals(networkCidr)).findFirst().orElseThrow(()->new IllegalArgumentException("Requested network is no longer an active authorized interface"));
            backend.send(ProtocolMessages.discoveryEvent(mapper, config.agentId(), scanId, "discovery.started", 0, 0, 0, 0, 0, null), "discovery.idle");
            List<DiscoveredDevice> found = discovery.scan(subnet,network.interfaceName(),(checked, total) -> {
                int percent = total == 0 ? 100 : checked * 100 / total;
                if (percent == 100 || percent / 5 > lastReported.get()) {
                    lastReported.set(percent / 5);
                    try { backend.send(ProtocolMessages.discoveryEvent(mapper, config.agentId(), scanId, "discovery.progress", percent, checked, 0, 0, 0, null), "discovery.idle"); }
                    catch (IOException exception) { throw new IllegalStateException("Could not report scan progress", exception); }
                }
            });
            if (!found.isEmpty()) {
                int total = subnet.hosts(Ipv4Cidr.MAX_SCAN_HOSTS).size();
                var ack = backend.send(ProtocolMessages.discoveredDevices(mapper, config.agentId(), found, scanId, 100, total, Math.max(0,total-found.size())), "device.discovery.accepted");
                mergeDeviceMappings(subnet, deviceIds, ack.path("devices"));
                for (JsonNode mapping : ack.path("devices")) {
                    if (mapping.path("created").asBoolean(false)) newDevices.incrementAndGet(); else existingDevices.incrementAndGet();
                }
                backend.send(ProtocolMessages.discoveryEvent(mapper, config.agentId(), scanId, "discovery.progress", 100, total, found.size(), Math.max(0,total-found.size()), newDevices.get(), existingDevices.get(), 0, null), "discovery.idle");
            }
            long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            int checked=subnet.hosts(Ipv4Cidr.MAX_SCAN_HOSTS).size(); int failed = Math.max(0, checked - found.size());
            backend.send(ProtocolMessages.discoveryEvent(mapper, config.agentId(), scanId, "discovery.completed", 100,
                    checked, found.size(), failed,
                    newDevices.get(), existingDevices.get(), duration, null), "discovery.idle");
            logger.info("Dashboard scan {} completed: {} reachable device(s)", scanId, found.size());
        } catch (Exception exception) {
            try { backend.send(ProtocolMessages.discoveryEvent(mapper, config.agentId(), scanId, "discovery.failed", 0, 0, 0, 0, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt), exception.getMessage()), "discovery.idle"); }
            catch (IOException reportError) { logger.warn("Could not report failed scan {}: {}", scanId, reportError.getMessage()); }
            logger.warn("Dashboard scan {} failed: {}", scanId, exception.getMessage());
        } finally { discoveryRunning.set(false); }
    }

    static int mergeDeviceMappings(com.netmoni.agent.config.Ipv4Cidr subnet, Map<String, UUID> deviceIds,
                                   JsonNode returnedDevices) throws IOException {
        if (returnedDevices == null || !returnedDevices.isArray()) {
            throw new IOException("Backend discovery acknowledgement is missing its device mappings");
        }
        int registeredCount = 0;
        for (JsonNode device : returnedDevices) {
            String ip = device.path("ipAddress").asText("");
            String id = device.path("deviceId").asText("");
            if (subnet.contains(ip) && !id.isBlank()) {
                deviceIds.put(ip, UUID.fromString(id));
                registeredCount++;
            }
        }
        return registeredCount;
    }

    static void replaceMonitoringTargets(Map<String, UUID> mappings, JsonNode targets, List<Ipv4Cidr> allowedCidrs) {
        if (targets == null || !targets.isArray()) return;
        Map<String, UUID> updated = new ConcurrentHashMap<>();
        for (JsonNode target : targets) {
            String ip = target.path("ipAddress").asText("");
            String id = target.path("deviceId").asText("");
            if (ip.isBlank() || id.isBlank() || allowedCidrs.stream().noneMatch(c -> c.contains(ip))) continue;
            try { updated.put(ip, UUID.fromString(id)); }
            catch (IllegalArgumentException ignored) { /* Ignore malformed backend target rows. */ }
        }
        mappings.clear();
        mappings.putAll(updated);
    }

    static boolean isMonitoringTarget(Map<String, UUID> mappings, String deviceId) {
        try { return mappings.containsValue(UUID.fromString(deviceId)); }
        catch (IllegalArgumentException ignored) { return false; }
    }

    private void monitorDevices() {
        if (!registered.get() || !monitoringRunning.compareAndSet(false, true)) return;
        if (deviceIds.isEmpty()) {
            monitoringRunning.set(false);
            return;
        }
        AtomicInteger remaining = new AtomicInteger(deviceIds.size());
        deviceIds.forEach((ip, deviceId) -> workers.execute(() -> {
            try {
                monitorDevice(ip, deviceId);
            } catch (Exception exception) {
                logger.error("Monitoring {} failed; unsent results remain in the outbox", ip, exception);
            } finally {
                if (remaining.decrementAndGet() == 0) {
                    monitoringRunning.set(false);
                }
            }
        }));
    }

    private void monitorDevice(String ip, UUID deviceId) throws IOException {
        if (config.allowedCidrs().stream().noneMatch(c->c.contains(ip))) {
            logger.error("Skipping configured device outside authorized subnet: {}", ip);
            return;
        }
        enqueue(deviceId, icmp.check(ip));
        for (int port : config.tcpPorts()) {
            enqueue(deviceId, tcp.check(ip, port));
        }
        if (config.snmpEnabled()) {
            enqueue(deviceId, snmp.check(ip));
        }
    }

    private void enqueue(UUID deviceId, MonitoringResult result) throws IOException {
        outbox.add(ProtocolMessages.monitoringResult(mapper, config.agentId(), deviceId, result));
    }

    private void flushOutbox() {
        if (!registered.get() || !outboxRunning.compareAndSet(false, true)) return;
        try {
            while (true) {
                JsonNode message = outbox.peek();
                if (message == null) return;
                String queuedDevice = message.path("deviceId").asText("");
                if (!queuedDevice.isBlank() && !isMonitoringTarget(deviceIds, queuedDevice)) {
                    outbox.removeFirst();
                    continue;
                }
                backend.send(message, "monitoring.result.accepted");
                outbox.removeFirst();
            }
        } catch (IOException exception) {
            logger.warn("Could not deliver monitoring result; it remains queued: {}", exception.getMessage());
        } finally {
            outboxRunning.set(false);
        }
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        workers.shutdown();
        discoveryWorkers.shutdown();
        try {
            if (!workers.awaitTermination(5, TimeUnit.SECONDS)) {
                workers.shutdownNow();
            }
            if (!discoveryWorkers.awaitTermination(5, TimeUnit.SECONDS)) {
                discoveryWorkers.shutdownNow();
            }
        } catch (InterruptedException exception) {
            workers.shutdownNow();
            discoveryWorkers.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("NetMoni agent stopped; pending outbox messages were retained");
    }
}
