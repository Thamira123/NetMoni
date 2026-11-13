package com.netmoni.server.dto.discovery;
import java.util.UUID;
public record DiscoveryScanCommand(UUID scanId,UUID networkId,String networkCidr) { }
