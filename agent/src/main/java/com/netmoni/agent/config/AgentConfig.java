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
        String networkInterfaceName,
        String macAddress,
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
        String snmpSecurityName,
        String snmpAuthProtocol,
        String snmpAuthPassphrase,
        String snmpPrivacyProtocol,
        String snmpPrivacyPassphrase,
        int snmpTimeoutMs,
        java.util.List<Ipv4Cidr> allowedCidrs) {

    public AgentConfig {
        tcpPorts = Arrays.copyOf(tcpPorts, tcpPorts.length);
        deviceIds = Map.copyOf(deviceIds);
        allowedCidrs = java.util.List.copyOf(allowedCidrs);
    }

    public AgentConfig(UUID agentId,String name,String localIp,String networkInterfaceName,String macAddress,String version,
            Ipv4Cidr subnet,String backendHost,int backendPort,int connectTimeoutMs,int readTimeoutMs,
            int monitoringIntervalSeconds,int discoveryIntervalSeconds,int heartbeatIntervalSeconds,int retryIntervalSeconds,
            int outboxFlushIntervalSeconds,int icmpTimeoutMs,int icmpRetries,int workerThreads,int[] tcpPorts,
            Map<String,UUID> deviceIds,Path dataDirectory,boolean snmpEnabled,String snmpVersion,int snmpPort,
            String snmpCommunity,String snmpSecurityName,String snmpAuthProtocol,String snmpAuthPassphrase,
            String snmpPrivacyProtocol,String snmpPrivacyPassphrase,int snmpTimeoutMs) {
        this(agentId,name,localIp,networkInterfaceName,macAddress,version,subnet,backendHost,backendPort,connectTimeoutMs,
                readTimeoutMs,monitoringIntervalSeconds,discoveryIntervalSeconds,heartbeatIntervalSeconds,retryIntervalSeconds,
                outboxFlushIntervalSeconds,icmpTimeoutMs,icmpRetries,workerThreads,tcpPorts,deviceIds,dataDirectory,snmpEnabled,
                snmpVersion,snmpPort,snmpCommunity,snmpSecurityName,snmpAuthProtocol,snmpAuthPassphrase,snmpPrivacyProtocol,
                snmpPrivacyPassphrase,snmpTimeoutMs,java.util.List.of(subnet));
    }

    @Override
    public int[] tcpPorts() {
        return Arrays.copyOf(tcpPorts, tcpPorts.length);
    }

    @Override
    public String toString() {
        return "AgentConfig[agentId=" + agentId + ", name=" + name + ", localIp=" + localIp
                + ", networkInterfaceName=" + networkInterfaceName + ", version=" + version
                + ", subnet=" + subnet + ", backendHost=" + backendHost + ", backendPort=" + backendPort
                + ", monitoringIntervalSeconds=" + monitoringIntervalSeconds + ", discoveryIntervalSeconds="
                + discoveryIntervalSeconds + ", heartbeatIntervalSeconds=" + heartbeatIntervalSeconds
                + ", allowedCidrs=" + allowedCidrs + ", tcpPorts=" + Arrays.toString(tcpPorts) + ", snmpEnabled=" + snmpEnabled
                + ", snmpVersion=" + snmpVersion + ", snmpPort=" + snmpPort + ", credentials=[REDACTED]]";
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
        LocalIdentity identity = resolveLocalIdentity(properties.getProperty("agent.local-ip", ""),
                properties.getProperty("agent.network-interface", ""), subnet);
        java.util.List<Ipv4Cidr> allowedCidrs = parseAllowedCidrs(properties.getProperty("agent.allowed-cidrs", subnet.toString()), subnet);
        String snmpVersion = properties.getProperty("agent.snmp.version", "2c").trim().toLowerCase();
        if (!snmpVersion.equals("1") && !snmpVersion.equals("2c") && !snmpVersion.equals("3")) {
            throw new IllegalArgumentException("SNMP version must be 1, 2c, or 3");
        }
        String snmpSecurityName = properties.getProperty("agent.snmp.security-name", "").trim();
        String snmpAuthProtocol = properties.getProperty("agent.snmp.auth-protocol", "SHA256").trim().toUpperCase();
        String snmpAuthPassphrase = properties.getProperty("agent.snmp.auth-passphrase", "");
        String snmpPrivacyProtocol = properties.getProperty("agent.snmp.privacy-protocol", "AES128").trim().toUpperCase();
        String snmpPrivacyPassphrase = properties.getProperty("agent.snmp.privacy-passphrase", "");
        if ("3".equals(snmpVersion) && Boolean.parseBoolean(properties.getProperty("agent.snmp.enabled", "false"))) {
            if (snmpSecurityName.isBlank() || snmpAuthPassphrase.length() < 8 || snmpPrivacyPassphrase.length() < 8) {
                throw new IllegalArgumentException("SNMPv3 authPriv requires a security name and auth/privacy passphrases of at least 8 characters");
            }
            if (!snmpAuthProtocol.equals("SHA") && !snmpAuthProtocol.equals("SHA256")) {
                throw new IllegalArgumentException("SNMPv3 auth protocol must be SHA or SHA256");
            }
            if (!snmpPrivacyProtocol.equals("AES128")) {
                throw new IllegalArgumentException("SNMPv3 privacy protocol must be AES128");
            }
        }

        return new AgentConfig(
                agentId, name, identity.localIp(), identity.interfaceName(), identity.macAddress(), version, subnet,
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
                snmpSecurityName, snmpAuthProtocol, snmpAuthPassphrase,
                snmpPrivacyProtocol, snmpPrivacyPassphrase,
                integer(properties, "agent.snmp.timeout-ms", 1, 120_000), allowedCidrs);
    }

    private static void applyEnvironment(Properties properties) {
        Map<String, String> environment = new HashMap<>();
        environment.put("NETMONI_AGENT_ID", "agent.id");
        environment.put("NETMONI_AGENT_NAME", "agent.name");
        environment.put("NETMONI_AGENT_LOCAL_IP", "agent.local-ip");
        environment.put("NETMONI_AGENT_NETWORK_INTERFACE", "agent.network-interface");
        environment.put("NETMONI_AGENT_VERSION", "agent.version");
        environment.put("NETMONI_AGENT_SEGMENT_CIDR", "agent.segment-cidr");
        environment.put("NETMONI_AGENT_ALLOWED_CIDRS", "agent.allowed-cidrs");
        environment.put("NETMONI_BACKEND_HOST", "agent.backend.host");
        environment.put("NETMONI_BACKEND_PORT", "agent.backend.port");
        environment.put("NETMONI_BACKEND_CONNECT_TIMEOUT_MS", "agent.backend.connect-timeout-ms");
        environment.put("NETMONI_BACKEND_READ_TIMEOUT_MS", "agent.backend.read-timeout-ms");
        environment.put("NETMONI_MONITORING_INTERVAL_SECONDS", "agent.monitoring.interval-seconds");
        environment.put("NETMONI_DISCOVERY_INTERVAL_SECONDS", "agent.discovery.interval-seconds");
        environment.put("NETMONI_HEARTBEAT_INTERVAL_SECONDS", "agent.heartbeat.interval-seconds");
        environment.put("NETMONI_RETRY_INTERVAL_SECONDS", "agent.retry.interval-seconds");
        environment.put("NETMONI_OUTBOX_FLUSH_INTERVAL_SECONDS", "agent.outbox.flush-interval-seconds");
        environment.put("NETMONI_ICMP_TIMEOUT_MS", "agent.icmp.timeout-ms");
        environment.put("NETMONI_ICMP_RETRIES", "agent.icmp.retries");
        environment.put("NETMONI_WORKER_THREADS", "agent.worker-threads");
        environment.put("NETMONI_TCP_PORTS", "agent.tcp.ports");
        environment.put("NETMONI_DEVICE_IDS", "agent.device-ids");
        environment.put("NETMONI_DATA_DIRECTORY", "agent.data-directory");
        environment.put("NETMONI_SNMP_ENABLED", "agent.snmp.enabled");
        environment.put("NETMONI_SNMP_VERSION", "agent.snmp.version");
        environment.put("NETMONI_SNMP_PORT", "agent.snmp.port");
        environment.put("NETMONI_SNMP_COMMUNITY", "agent.snmp.community");
        environment.put("NETMONI_SNMP_SECURITY_NAME", "agent.snmp.security-name");
        environment.put("NETMONI_SNMP_AUTH_PROTOCOL", "agent.snmp.auth-protocol");
        environment.put("NETMONI_SNMP_AUTH_PASSPHRASE", "agent.snmp.auth-passphrase");
        environment.put("NETMONI_SNMP_PRIVACY_PROTOCOL", "agent.snmp.privacy-protocol");
        environment.put("NETMONI_SNMP_PRIVACY_PASSPHRASE", "agent.snmp.privacy-passphrase");
        environment.put("NETMONI_SNMP_TIMEOUT_MS", "agent.snmp.timeout-ms");
        environment.forEach((variable, key) -> {
            String value = System.getenv(variable);
            if (value != null && !value.isBlank()) {
                properties.setProperty(key, value);
            }
        });
    }

    private static LocalIdentity resolveLocalIdentity(String configuredIp, String configuredInterface,
                                                       Ipv4Cidr subnet) throws IOException {
        String requestedIp = configuredIp.isBlank() ? null : configuredIp.trim();
        String requestedInterface = configuredInterface.isBlank() ? null : configuredInterface.trim();
        if (requestedIp != null && !subnet.contains(requestedIp)) {
            throw new IllegalArgumentException("agent.local-ip must be inside agent.segment-cidr");
        }
        try {
            var interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp()) {
                    continue;
                }
                if (requestedInterface != null && !requestedInterface.equals(networkInterface.getName())) {
                    continue;
                }
                var addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    String hostAddress = address.getHostAddress();
                    if (address instanceof Inet4Address && subnet.contains(hostAddress)
                            && (requestedIp == null || requestedIp.equals(hostAddress))) {
                        byte[] hardwareAddress = networkInterface.getHardwareAddress();
                        String macAddress = hardwareAddress == null ? null : java.util.HexFormat.ofDelimiter(":")
                                .withUpperCase().formatHex(hardwareAddress);
                        return new LocalIdentity(hostAddress, networkInterface.getName(), macAddress);
                    }
                }
            }
        } catch (SocketException exception) {
            throw new IOException("Could not inspect local network interfaces", exception);
        }
        if (requestedInterface != null) {
            throw new IllegalArgumentException("No active IPv4 address in " + subnet
                    + " was found on agent.network-interface " + requestedInterface);
        }
        throw new IllegalArgumentException(requestedIp == null
                ? "No active local IPv4 interface is inside " + subnet
                : "agent.local-ip is not assigned to an active local interface: " + requestedIp);
    }

    private record LocalIdentity(String localIp, String interfaceName, String macAddress) { }

    private static java.util.List<Ipv4Cidr> parseAllowedCidrs(String configured, Ipv4Cidr fallback) {
        if (configured == null || configured.isBlank()) return java.util.List.of(fallback);
        java.util.LinkedHashMap<String,Ipv4Cidr> cidrs=new java.util.LinkedHashMap<>();
        for(String value:configured.split(",")) {
            Ipv4Cidr cidr=Ipv4Cidr.parse(value.trim());
            cidrs.put(cidr.toString(),cidr);
            if(cidrs.size()>32)throw new IllegalArgumentException("agent.allowed-cidrs supports at most 32 networks");
        }
        cidrs.putIfAbsent(fallback.toString(),fallback);
        return java.util.List.copyOf(cidrs.values());
    }

    private static UUID loadAgentId(String configuredId, Path idFile) throws IOException {
        if (configuredId != null && !configuredId.isBlank()) {
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
