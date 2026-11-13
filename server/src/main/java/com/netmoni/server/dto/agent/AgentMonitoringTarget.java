package com.netmoni.server.dto.agent;

import java.util.UUID;

public record AgentMonitoringTarget(String ipAddress, UUID deviceId) { }
