package com.netmoni.server.controller;

import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import com.netmoni.server.dto.device.DeviceRequest;
import com.netmoni.server.dto.device.DeviceResponse;
import com.netmoni.server.dto.common.PageResponse;
import com.netmoni.server.entity.DeviceStatus;
import com.netmoni.server.service.DeviceService;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {
	private final DeviceService service;
	public DeviceController(DeviceService service) { this.service = service; }
	@PostMapping @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')") @ResponseStatus(HttpStatus.CREATED) public DeviceResponse create(@Valid @RequestBody DeviceRequest request) { return service.create(request); }
	@GetMapping public PageResponse<DeviceResponse> findAll(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) { return PageResponse.from(service.findAll(page,size)); }
	@GetMapping("/{id}") public DeviceResponse find(@PathVariable UUID id) { return service.find(id); }
	@GetMapping("/by-agent/{agentId}") public List<DeviceResponse> byAgent(@PathVariable UUID agentId) { return service.findByAgent(agentId); }
	@PutMapping("/{id}") @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')") public DeviceResponse update(@PathVariable UUID id, @Valid @RequestBody DeviceRequest request) { return service.update(id, request); }
	@PatchMapping("/{id}/status") @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')") public DeviceResponse status(@PathVariable UUID id, @RequestParam DeviceStatus status) { return service.updateStatus(id, status); }
	@DeleteMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID id) { service.delete(id); }
}
