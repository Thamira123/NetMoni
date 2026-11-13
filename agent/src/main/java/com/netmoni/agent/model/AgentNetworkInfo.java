package com.netmoni.agent.model;

import java.time.Instant;

public record AgentNetworkInfo(String interfaceName, String displayName, String interfaceType,
        String interfaceStatus, String address, String cidr, String subnetMask, String broadcastAddress,
        String gateway, String macAddress, String ipv6Address, Integer ipv6PrefixLength,
        boolean authorized, boolean scanEligible, Instant lastDetectedAt) { }
