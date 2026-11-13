package com.netmoni.agent.communication;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netmoni.agent.config.AgentConfig;
import com.netmoni.agent.config.Ipv4Cidr;
import com.netmoni.agent.model.MonitoringResult;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProtocolMessagesTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final UUID agentId = UUID.fromString("1e36fb24-0a57-4cdf-87b0-90f3ba3e9f17");

    @Test
    void buildsBackendRegistrationHeartbeatAndResultShapes() {
        AgentConfig config = new AgentConfig(agentId, "segment-a", "192.168.1.2", "eth0", null, "1.0.0",
                Ipv4Cidr.parse("192.168.1.0/24"), "127.0.0.1", 9001,
                1000, 1000, 60, 300, 30, 15, 2, 500, 2, 4, new int[0], Map.of(),
                Path.of("data"), false, "2c", 161, "", "", "SHA256", "", "AES128", "", 1000);
        assertEquals("agent.register", ProtocolMessages.registration(mapper, config).path("type").asText());
        assertEquals(agentId.toString(), ProtocolMessages.heartbeat(mapper, agentId).path("agentId").asText());

        MonitoringResult result = new MonitoringResult("ICMP", 12L, true, new BigDecimal("0.00"),
                null, null, null, Instant.parse("2026-11-12T12:00:00Z"), null);
        var json = ProtocolMessages.monitoringResult(mapper, agentId,
                UUID.fromString("b861d57f-42e0-4834-808a-3bd51a56293a"), result);
        assertEquals("monitoring.result", json.path("type").asText());
        assertEquals(12L, json.path("responseTimeMs").asLong());
        assertEquals("2026-11-12T12:00:00Z", json.path("checkedAt").asText());
    }
}
