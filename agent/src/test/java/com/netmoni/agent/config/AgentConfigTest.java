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
        assertTrue(Files.exists(dataDirectory.resolve("agent-id")));
    }

    @Test
    void rejectsMissingExplicitConfigurationFile() {
        assertThrows(java.io.IOException.class,
                () -> AgentConfig.load(tempDirectory.resolve("missing.properties")));
    }
}
