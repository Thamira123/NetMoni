package com.netmoni.server.dto.discovery;
import java.util.List;
import java.util.UUID;
public record ScanRequest(List<UUID> networkIds) { }
