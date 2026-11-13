package com.netmoni.server.dto.discovery;
import java.time.Instant;
import java.util.UUID;
import com.netmoni.server.entity.DiscoveryScan;
public record DiscoveryScanResponse(UUID scanId, UUID agentId, String status, int progress, int checked,
 int discovered, int failed, int newDevices, int existingDevices, Instant createdAt, Instant startedAt, Instant finishedAt, Long durationMs, String errorMessage) {
 public static DiscoveryScanResponse from(DiscoveryScan s){return new DiscoveryScanResponse(s.getId(),s.getAgentId(),s.getState().name(),s.getProgress(),s.getChecked(),s.getDiscovered(),s.getFailed(),s.getNewDevices(),s.getExistingDevices(),s.getCreatedAt(),s.getStartedAt(),s.getFinishedAt(),s.getDurationMs(),s.getErrorMessage());}
}
