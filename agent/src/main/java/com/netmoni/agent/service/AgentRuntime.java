package com.netmoni.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netmoni.agent.communication.BackendClient;
import com.netmoni.agent.communication.MessageOutbox;
import com.netmoni.agent.communication.ProtocolMessages;
import com.netmoni.agent.config.AgentConfig;
import com.netmoni.agent.discovery.NetworkDiscovery;
import com.netmoni.agent.model.DiscoveredDevice;
import com.netmoni.agent.model.MonitoringResult;
import com.netmoni.agent.monitoring.icmp.IcmpMonitor;
import com.netmoni.agent.monitoring.snmp.SnmpMonitor;
import com.netmoni.agent.monitoring.tcp.TcpPortMonitor;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
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

    public AgentRuntime(AgentConfig config) throws IOException {
        this.config = config;
        workers = Executors.newScheduledThreadPool(config.workerThreads());
        backend = new BackendClient(config.backendHost(), config.backendPort(),
                config.connectTimeoutMs(), config.readTimeoutMs(), mapper);
        outbox = new MessageOutbox(config.dataDirectory().resolve("outbox.jsonl"), mapper);
        discovery = new NetworkDiscovery(config, workers);
        icmp = new IcmpMonitor(config.icmpTimeoutMs(), config.icmpRetries());
        tcp = new TcpPortMonitor(config.connectTimeoutMs());
        snmp = new SnmpMonitor(config.snmpVersion(), config.snmpPort(), config.snmpCommunity(),
                config.snmpTimeoutMs(), mapper);
    }

    public void start() {
        logger.info("Starting NetMoni agent {} ({}) at {} on subnet {}",
                config.agentId(), config.name(), config.localIp(), config.subnet());
        scheduler.scheduleWithFixedDelay(this::register, 0, config.retryIntervalSeconds(), TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(this::heartbeat, config.heartbeatIntervalSeconds(),
                config.heartbeatIntervalSeconds(), TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(this::runDiscovery, 0, config.discoveryIntervalSeconds(),
                TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(this::monitorDevices, 5, config.monitoringIntervalSeconds(),
                TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(this::flushOutbox, 1,
                config.outboxFlushIntervalSeconds(), TimeUnit.SECONDS);
    }

    private void register() {
        if (registered.get()) return;
        try {
            backend.send(ProtocolMessages.registration(mapper, config), "agent.registered");
            registered.set(true);
            logger.info("Agent registration acknowledged by backend");
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
        if (!discoveryRunning.compareAndSet(false, true)) return;
        workers.execute(() -> {
            try {
                List<DiscoveredDevice> found = discovery.scan();
                logger.info("Discovery found {} reachable device(s) in configured subnet", found.size());
            } catch (RuntimeException exception) {
                logger.error("Network discovery failed", exception);
            } finally {
                discoveryRunning.set(false);
            }
        });
    }

    private void monitorDevices() {
        if (!registered.get() || !monitoringRunning.compareAndSet(false, true)) return;
        if (config.deviceIds().isEmpty()) {
            monitoringRunning.set(false);
            return;
        }
        AtomicInteger remaining = new AtomicInteger(config.deviceIds().size());
        config.deviceIds().forEach((ip, deviceId) -> workers.execute(() -> {
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
        if (!config.subnet().contains(ip)) {
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
        try {
            if (!workers.awaitTermination(5, TimeUnit.SECONDS)) {
                workers.shutdownNow();
            }
        } catch (InterruptedException exception) {
            workers.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("NetMoni agent stopped; pending outbox messages were retained");
    }
}
