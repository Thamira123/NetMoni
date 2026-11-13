package com.netmoni.server.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import com.netmoni.server.dto.monitoring.MonitoringResultRequest;
import com.netmoni.server.dto.monitoring.MonitoringResultResponse;
import com.netmoni.server.service.MonitoringResultService;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/monitoring-results")
public class MonitoringResultController {
	private final MonitoringResultService service;
	public MonitoringResultController(MonitoringResultService service) { this.service=service; }
	@PostMapping @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')") @ResponseStatus(HttpStatus.CREATED) public MonitoringResultResponse submit(@Valid @RequestBody MonitoringResultRequest request) { return service.submit(request); }
	@GetMapping("/device/{deviceId}") public List<MonitoringResultResponse> device(@PathVariable UUID deviceId, @RequestParam Instant from, @RequestParam Instant to) { return service.forDevice(deviceId,from,to); }
	@GetMapping("/agent/{agentId}") public List<MonitoringResultResponse> agent(@PathVariable UUID agentId, @RequestParam Instant from, @RequestParam Instant to) { return service.forAgent(agentId,from,to); }
	@GetMapping("/recent") public List<MonitoringResultResponse> recent(@RequestParam(defaultValue="50") int limit) { return service.recent(limit); }
	@GetMapping("/device/{deviceId}/availability") public double availability(@PathVariable UUID deviceId, @RequestParam Instant from, @RequestParam Instant to) { return service.availability(deviceId,from,to); }
}
