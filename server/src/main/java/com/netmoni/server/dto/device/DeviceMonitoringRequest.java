package com.netmoni.server.dto.device;

import jakarta.validation.constraints.NotNull;

public record DeviceMonitoringRequest(@NotNull Boolean enabled) { }
