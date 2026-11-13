package com.netmoni.agent.config;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentConfigTest {
    @TempDir
    java.nio.file.Path tempDirectory;

    @Test
    void loadsSettingsAndKeepsGeneratedAgentIdAcrossRestarts() throws Exception {
        var dataDirectory = tempDirectory.resolve("agent data");
        var configFile = tempDirectory.resolve("agent.properties");
        Files.writeString(configFile, """
                agent.name=test-agent
                agent.local-ip=127.0.0.1
                agent.segment-cidr=127.0.0.1/32
                agent.data-directory=%s
                """.formatted(dataDirectory.toString().replace("\\", "\\\\")));

        AgentConfig first = AgentConfig.load(configFile);
        AgentConfig second = AgentConfig.load(configFile);

        assertEquals(first.agentId(), second.agentId());
        assertEquals("127.0.0.1", first.localIp());
        assertFalse(first.networkInterfaceName().isBlank());
        assertTrue(Files.exists(dataDirectory.resolve("agent-id")));
    }

    @Test
    void requiresTheConfiguredAgentAddressToBelongToAnActiveInterface() throws Exception {
        var configFile = tempDirectory.resolve("not-local.properties");
        Files.writeString(configFile, """
                agent.local-ip=192.0.2.25
                agent.segment-cidr=192.0.2.0/24
                agent.data-directory=%s
                """.formatted(tempDirectory.resolve("data").toString().replace("\\", "\\\\")));

        assertThrows(IllegalArgumentException.class, () -> AgentConfig.load(configFile));
    }

    @Test
    void acceptsConfiguredSnmpV3AuthPrivWithoutPrintingCredentials() throws Exception {
        var configFile = tempDirectory.resolve("snmpv3.properties");
        Files.writeString(configFile, """
                agent.local-ip=127.0.0.1
                agent.segment-cidr=127.0.0.1/32
                agent.data-directory=%s
                agent.snmp.enabled=true
                agent.snmp.version=3
                agent.snmp.security-name=monitor
                agent.snmp.auth-passphrase=auth-secret-123
                agent.snmp.privacy-passphrase=privacy-secret-123
                """.formatted(tempDirectory.resolve("data-v3").toString().replace("\\", "\\\\")));

        AgentConfig config = AgentConfig.load(configFile);

        assertEquals("3", config.snmpVersion());
        assertFalse(config.toString().contains("auth-secret-123"));
        assertFalse(config.toString().contains("privacy-secret-123"));
    }

    @Test void loadsExplicitAuthorizedCidrsForMoreThanOneInterfaceNetwork() throws Exception {
        var configFile=tempDirectory.resolve("multi-network.properties");
        Files.writeString(configFile,"""
                agent.local-ip=127.0.0.1
                agent.segment-cidr=127.0.0.1/32
                agent.allowed-cidrs=127.0.0.1/32,10.20.0.0/24,192.168.40.0/24
                agent.data-directory=%s
                """.formatted(tempDirectory.resolve("multi").toString().replace("\\","\\\\")));
        AgentConfig config=AgentConfig.load(configFile);
        assertEquals(3,config.allowedCidrs().size());
        assertTrue(config.allowedCidrs().contains(Ipv4Cidr.parse("10.20.0.0/24")));
    }

    @Test
    void rejectsMissingExplicitConfigurationFile() {
        assertThrows(java.io.IOException.class,
                () -> AgentConfig.load(tempDirectory.resolve("missing.properties")));
    }
}
