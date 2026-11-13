package com.netmoni.agent.communication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.netmoni.agent.config.AgentConfig;
import com.netmoni.agent.model.MonitoringResult;
import java.util.UUID;

public final class ProtocolMessages {
    private ProtocolMessages() {
    }

    public static ObjectNode registration(ObjectMapper mapper, AgentConfig config) {
        ObjectNode message = mapper.createObjectNode();
        message.put("type", "agent.register");
        message.put("agentId", config.agentId().toString());
        message.put("name", config.name());
        message.put("segmentCidr", config.subnet().toString());
        message.put("version", config.version());
        return message;
    }

    public static ObjectNode heartbeat(ObjectMapper mapper, UUID agentId) {
        ObjectNode message = mapper.createObjectNode();
        message.put("type", "agent.heartbeat");
        message.put("agentId", agentId.toString());
        return message;
    }

    public static ObjectNode monitoringResult(ObjectMapper mapper, UUID agentId, UUID deviceId,
                                               MonitoringResult result) {
        ObjectNode message = mapper.createObjectNode();
        message.put("type", "monitoring.result");
        message.put("agentId", agentId.toString());
        message.put("deviceId", deviceId.toString());
        message.put("checkType", result.checkType());
        if (result.responseTimeMs() != null) message.put("responseTimeMs", result.responseTimeMs());
        message.put("available", result.available());
        if (result.packetLossPercent() != null) message.put("packetLossPercent", result.packetLossPercent());
        if (result.portNumber() != null) message.put("portNumber", result.portNumber());
        if (result.portOpen() != null) message.put("portOpen", result.portOpen());
        if (result.snmpData() != null) message.put("snmpData", result.snmpData());
        message.put("checkedAt", result.checkedAt().toString());
        if (result.errorMessage() != null) {
            message.put("errorMessage", result.errorMessage().substring(0,
                    Math.min(result.errorMessage().length(), 1000)));
        }
        return message;
    }
}
