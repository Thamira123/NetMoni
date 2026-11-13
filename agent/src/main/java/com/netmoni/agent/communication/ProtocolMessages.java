package com.netmoni.agent.communication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.netmoni.agent.config.AgentConfig;
import com.netmoni.agent.model.MonitoringResult;
import com.netmoni.agent.model.DiscoveredDevice;
import com.netmoni.agent.config.NetworkInterfaceDiscovery;
import java.util.List;
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
        addNetworks(message,config);
        return message;
    }
    public static ObjectNode networks(ObjectMapper mapper,AgentConfig config){ObjectNode m=mapper.createObjectNode();m.put("type","agent.networks");m.put("agentId",config.agentId().toString());addNetworks(m,config);return m;}
    private static void addNetworks(ObjectNode message,AgentConfig config) {
        var allowed=message.putArray("authorizedCidrs"); config.allowedCidrs().forEach(c->allowed.add(c.toString()));
        var networks=message.putArray("networks");
        for(var network:NetworkInterfaceDiscovery.discover(config.allowedCidrs())) {
            var n=networks.addObject(); n.put("interfaceName",network.interfaceName()); n.put("displayName",network.displayName());
            n.put("interfaceType",network.interfaceType()); n.put("interfaceStatus",network.interfaceStatus());
            n.put("address",network.address()); n.put("cidr",network.cidr()); n.put("subnetMask",network.subnetMask());
            if(network.broadcastAddress()!=null)n.put("broadcastAddress",network.broadcastAddress()); if(network.gateway()!=null)n.put("gateway",network.gateway());
            if(network.macAddress()!=null)n.put("macAddress",network.macAddress()); if(network.ipv6Address()!=null)n.put("ipv6Address",network.ipv6Address());
            if(network.ipv6PrefixLength()!=null)n.put("ipv6PrefixLength",network.ipv6PrefixLength()); n.put("authorized",network.authorized());
            n.put("scanEligible",network.scanEligible()); n.put("lastDetectedAt",network.lastDetectedAt().toString());
        }
    }

    public static ObjectNode heartbeat(ObjectMapper mapper, UUID agentId) {
        ObjectNode message = mapper.createObjectNode();
        message.put("type", "agent.heartbeat");
        message.put("agentId", agentId.toString());
        return message;
    }

    public static ObjectNode discoveredDevices(ObjectMapper mapper, UUID agentId, List<DiscoveredDevice> devices) {
        return discoveredDevices(mapper, agentId, devices, null);
    }
    public static ObjectNode discoveredDevices(ObjectMapper mapper, UUID agentId, List<DiscoveredDevice> devices, UUID scanId) {
        ObjectNode message = mapper.createObjectNode();
        message.put("type", "device.discovered");
        message.put("agentId", agentId.toString());
        if (scanId != null) message.put("scanId", scanId.toString());
        var entries = message.putArray("devices");
        for (DiscoveredDevice device : devices) {
            var entry = entries.addObject();
            entry.put("ipAddress", device.ipAddress());
            if (device.hostname() != null) entry.put("hostname", device.hostname());
            if (device.discoveredAt() != null) entry.put("discoveredAt", device.discoveredAt().toString());
            if (device.networkCidr()!=null) entry.put("networkCidr",device.networkCidr()); if(device.interfaceName()!=null)entry.put("interfaceName",device.interfaceName());
            if(device.macAddress()!=null)entry.put("macAddress",device.macAddress()); if(device.deviceType()!=null)entry.put("deviceType",device.deviceType());
        }
        return message;
    }
    public static ObjectNode discoveredDevices(ObjectMapper mapper, UUID agentId, List<DiscoveredDevice> devices,
                                                UUID scanId, int progress, int checked, int failed) {
        ObjectNode message=discoveredDevices(mapper,agentId,devices,scanId);
        if(scanId!=null){message.put("progress",progress);message.put("checked",checked);message.put("discovered",devices.size());message.put("failed",failed);}
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

    public static ObjectNode discoveryCommand(ObjectMapper mapper, UUID agentId) { ObjectNode m=mapper.createObjectNode(); m.put("type","discovery.poll"); m.put("agentId",agentId.toString()); return m; }
    public static ObjectNode discoveryEvent(ObjectMapper mapper, UUID agentId, UUID scanId, String type, int progress, int checked, int discovered, int failed, long durationMs, String error) {
        return discoveryEvent(mapper,agentId,scanId,type,progress,checked,discovered,failed,0,0,durationMs,error);
    }
    public static ObjectNode discoveryEvent(ObjectMapper mapper, UUID agentId, UUID scanId, String type, int progress, int checked, int discovered, int failed, int newDevices, int existingDevices, long durationMs, String error) {
        ObjectNode m=mapper.createObjectNode(); m.put("type",type); m.put("agentId",agentId.toString()); m.put("scanId",scanId.toString());
        m.put("progress",progress); m.put("checked",checked); m.put("discovered",discovered); m.put("failed",failed); m.put("newDevices",newDevices); m.put("existingDevices",existingDevices); m.put("durationMs",durationMs);
        if(error!=null)m.put("errorMessage",error); return m;
    }
}
