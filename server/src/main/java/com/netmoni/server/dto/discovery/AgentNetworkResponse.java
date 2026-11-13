package com.netmoni.server.dto.discovery;
import java.time.Instant;
import java.util.UUID;
import com.netmoni.server.entity.AgentNetwork;
import com.netmoni.server.entity.AgentStatus;
public record AgentNetworkResponse(UUID networkId,UUID agentId,String agentName,AgentStatus agentStatus,
 String interfaceName,String displayName,String interfaceType,String interfaceStatus,String address,String cidr,
 String subnetMask,String broadcastAddress,String gateway,String macAddress,String ipv6Address,Integer ipv6PrefixLength,
 boolean authorized,boolean scanEligible,Instant lastDetectedAt,int deviceCount,Instant lastScanAt) {
 public static AgentNetworkResponse from(AgentNetwork n,int count,Instant lastScan){var a=n.getAgent();return new AgentNetworkResponse(n.getId(),a.getId(),a.getName(),a.getStatus(),n.getInterfaceName(),n.getDisplayName(),n.getInterfaceType(),n.getInterfaceStatus(),n.getAddress(),n.getCidr(),n.getSubnetMask(),n.getBroadcastAddress(),n.getGateway(),n.getMacAddress(),n.getIpv6Address(),n.getIpv6PrefixLength(),n.isAuthorized(),n.isScanEligible(),n.getLastDetectedAt(),count,lastScan);}
}
