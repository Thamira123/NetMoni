package com.netmoni.agent.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

public record AgentConfig(
        UUID agentId,
        String name,
        String localIp,
        String version,
        Ipv4Cidr subnet,
        String backendHost,
        int backendPort,
        int connectTimeoutMs,
        int readTimeoutMs,
        int monitoringIntervalSeconds,
        int discoveryIntervalSeconds,
        int heartbeatIntervalSeconds,
        int retryIntervalSeconds,
        int outboxFlushIntervalSeconds,
        int icmpTimeoutMs,
        int icmpRetries,
        int workerThreads,
        int[] tcpPorts,
        Map<String, UUID> deviceIds,
        Path dataDirectory,
        boolean snmpEnabled,
        String snmpVersion,
        int snmpPort,
        String snmpCommunity,
        int snmpTimeoutMs) {

    public AgentConfig {
        tcpPorts = Arrays.copyOf(tcpPorts, tcpPorts.length);
        deviceIds = Map.copyOf(deviceIds);
    }

    @Override
    public int[] tcpPorts() {
        return Arrays.copyOf(tcpPorts, tcpPorts.length);
    }

    public static AgentConfig load(Path externalConfig) throws IOException {
        Properties properties = new Properties();
        try (InputStream defaults = AgentConfig.class.getResourceAsStream("/application.properties")) {
            if (defaults == null) {
                throw new IOException("Missing default application.properties");
            }
            properties.load(defaults);
        }
        if (externalConfig != null && !Files.exists(externalConfig)) {
            throw new IOException("Configuration file does not exist: " + externalConfig);
        }
        if (externalConfig != null) {
            try (InputStream input = Files.newInputStream(externalConfig)) {
                properties.load(input);
            }
        }
        applyEnvironment(properties);

        Path dataDirectory = Path.of(properties.getProperty("agent.data-directory")).toAbsolutePath().normalize();
        Files.createDirectories(dataDirectory);
        UUID agentId = loadAgentId(properties.getProperty("agent.id"), dataDirectory.resolve("agent-id"));
        String name = properties.getProperty("agent.name", "").trim();
        if (name.isEmpty()) {
            name = java.net.InetAddress.getLocalHost().getHostName();
        }
        if (name.length() > 100) {
            throw new IllegalArgumentException("agent.name must not exceed 100 characters");
        }

        String version = required(properties, "agent.version");
        if (version.length() > 50) {
            throw new IllegalArgumentException("agent.version must not exceed 50 characters");
        }
        Ipv4Cidr subnet = Ipv4Cidr.parse(required(properties, "agent.segment-cidr"));
        if (subnet.toString().length() > 50) {
            throw new IllegalArgumentException("agent.segment-cidr must not exceed 50 characters");
        }
        String localIp = resolveLocalIp(properties.getProperty("agent.local-ip", ""), subnet);
        String snmpVersion = properties.getProperty("agent.snmp.version", "2c").trim().toLowerCase();
        if (!snmpVersion.equals("1") && !snmpVersion.equals("2c")) {
            throw new IllegalArgumentException("SNMP version must be 1 or 2c; SNMPv3 is not supported by this agent");
        }

        return new AgentConfig(
                agentId, name, localIp, version, subnet,
                required(properties, "agent.backend.host"),
                integer(properties, "agent.backend.port", 1, 65535),
                integer(properties, "agent.backend.connect-timeout-ms", 1, 120_000),
                integer(properties, "agent.backend.read-timeout-ms", 1, 120_000),
                integer(properties, "agent.monitoring.interval-seconds", 1, 86_400),
                integer(properties, "agent.discovery.interval-seconds", 1, 86_400),
                integer(properties, "agent.heartbeat.interval-seconds", 1, 86_400),
                integer(properties, "agent.retry.interval-seconds", 1, 86_400),
                integer(properties, "agent.outbox.flush-interval-seconds", 1, 3600),
                integer(properties, "agent.icmp.timeout-ms", 1, 120_000),
                integer(properties, "agent.icmp.retries", 1, 10),
                integer(properties, "agent.worker-threads", 1, 64),
                parsePorts(properties.getProperty("agent.tcp.ports", "")),
                parseDeviceIds(properties.getProperty("agent.device-ids", "")),
                dataDirectory,
                Boolean.parseBoolean(properties.getProperty("agent.snmp.enabled", "false")),
                snmpVersion,
                integer(properties, "agent.snmp.port", 1, 65535),
                properties.getProperty("agent.snmp.community", ""),
                integer(properties, "agent.snmp.timeout-ms", 1, 120_000));
    }

    private static void applyEnvironment(Properties properties) {
        Map<String, String> environment = new HashMap<>();
        environment.put("NETMONI_AGENT_ID", "agent.id");
        environment.put("NETMONI_AGENT_NAME", "agent.name");
        environment.put("NETMONI_AGENT_LOCAL_IP", "agent.local-ip");
        environment.put("NETMONI_AGENT_VERSION", "agent.version");
        environment.put("NETMONI_AGENT_SEGMENT_CIDR", "agent.segment-cidr");
        environment.put("NETMONI_BACKEND_HOST", "agent.backend.host");
        environment.put("NETMONI_BACKEND_PORT", "agent.backend.port");
        environment.put("NETMONI_DATA_DIRECTORY", "agent.data-directory");
        environment.put("NETMONI_SNMP_COMMUNITY", "agent.snmp.community");
        environment.forEach((variable, key) -> {
            String value = System.getenv(variable);
            if (value != null && !value.isBlank()) {
                properties.setProperty(key, value);
            }
        });
    }

    private static String resolveLocalIp(String configuredIp, Ipv4Cidr subnet) throws IOException {
        if (!configuredIp.isBlank()) {
            String ip = configuredIp.trim();
            if (!subnet.contains(ip)) {
                throw new IllegalArgumentException("agent.local-ip must be inside agent.segment-cidr");
            }
            return ip;
        }
        try {
            var interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                var addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    String hostAddress = address.getHostAddress();
                    if (address instanceof Inet4Address && subnet.contains(hostAddress)) {
                        return hostAddress;
                    }
                }
            }
        } catch (SocketException exception) {
            throw new IOException("Could not inspect local network interfaces", exception);
        }
        throw new IllegalArgumentException("No local IPv4 interface is inside " + subnet
                + "; configure agent.local-ip explicitly");
    }

    private static UUID loadAgentId(String configuredId, Path idFile) throws IOException {
        if (!configuredId.isBlank()) {
            return UUID.fromString(configuredId.trim());
        }
        if (Files.exists(idFile)) {
            return UUID.fromString(Files.readString(idFile).trim());
        }
        UUID id = UUID.randomUUID();
        Files.writeString(idFile, id.toString());
        return id;
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Configuration property is required: " + key);
        }
        return value.trim();
    }

    private static int integer(Properties properties, String key, int min, int max) {
        int value;
        try {
            value = Integer.parseInt(required(properties, key));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Configuration property must be an integer: " + key, exception);
        }
        if (value < min || value > max) {
            throw new IllegalArgumentException(key + " must be between " + min + " and " + max);
        }
        return value;
    }

    private static int[] parsePorts(String value) {
        if (value.isBlank()) {
            return new int[0];
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .mapToInt(port -> {
                    int number = Integer.parseInt(port);
                    if (number < 1 || number > 65535) {
                        throw new IllegalArgumentException("TCP ports must be between 1 and 65535");
                    }
                    return number;
                })
                .distinct()
                .toArray();
    }

    private static Map<String, UUID> parseDeviceIds(String value) {
        Map<String, UUID> result = new HashMap<>();
        if (value.isBlank()) {
            return result;
        }
        for (String mapping : value.split(",")) {
            String[] parts = mapping.trim().split("=", 2);
            if (parts.length != 2 || !parts[0].matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
                throw new IllegalArgumentException("Device mapping must be IPv4=UUID: " + mapping);
            }
            Ipv4Cidr.parseAddress(parts[0]);
            if (result.put(parts[0], UUID.fromString(parts[1].trim())) != null) {
                throw new IllegalArgumentException("Duplicate device IP mapping: " + parts[0]);
            }
        }
        if (result.size() > Ipv4Cidr.MAX_SCAN_HOSTS) {
            throw new IllegalArgumentException("agent.device-ids cannot contain more than "
                    + Ipv4Cidr.MAX_SCAN_HOSTS + " devices");
        }
        return result;
    }
}
