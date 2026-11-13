package com.netmoni.server.dto.alert;
import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.netmoni.server.entity.AlertSeverity;
public record AlertRequest(@NotBlank @Size(max=200) String deduplicationKey,@NotBlank @Size(max=60) String type,
	@NotNull AlertSeverity severity,@NotBlank @Size(max=500) String message,UUID deviceId,UUID agentId) {}
