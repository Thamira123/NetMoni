package com.netmoni.server.service;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import com.netmoni.server.dto.device.DeviceRequest;
import com.netmoni.server.dto.device.DeviceResponse;
import com.netmoni.server.entity.Agent;
import com.netmoni.server.entity.Device;
import com.netmoni.server.entity.DeviceStatus;
import com.netmoni.server.repository.AgentRepository;
import com.netmoni.server.repository.DeviceRepository;

@Service
public class DeviceService {
	private final DeviceRepository devices;
	private final AgentRepository agents;
	private final NetMoniEventPublisher events;
	public DeviceService(DeviceRepository devices, AgentRepository agents, NetMoniEventPublisher events) { this.devices = devices; this.agents = agents; this.events=events; }

	@Transactional
	public DeviceResponse create(DeviceRequest request) {
		validateIp(request.ipAddress());
		Agent agent = agents.findById(request.agentId()).orElseThrow(() -> new AgentNotFoundException(request.agentId()));
		return toResponse(devices.save(new Device(null, request.name().trim(), request.ipAddress().trim(),
				clean(request.hostname()), request.deviceType().trim(), clean(request.location()), agent)));
	}
	@Transactional(readOnly = true)
	public List<DeviceResponse> findAll() { return devices.findAll().stream().map(DeviceService::toResponse).toList(); }
	@Transactional(readOnly = true)
	public org.springframework.data.domain.Page<DeviceResponse> findAll(int page,int size) { if(page<0||size<1||size>100)throw new IllegalArgumentException("page must be >= 0 and size must be between 1 and 100"); return devices.findAll(PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"createdAt"))).map(DeviceService::toResponse); }
	@Transactional(readOnly = true)
	public DeviceResponse find(UUID id) { return toResponse(requireDevice(id)); }
	@Transactional(readOnly = true)
	public List<DeviceResponse> findByAgent(UUID agentId) {
		if (!agents.existsById(agentId)) throw new AgentNotFoundException(agentId);
		return devices.findByAgent_Id(agentId).stream().map(DeviceService::toResponse).toList();
	}
	@Transactional
	public DeviceResponse update(UUID id, DeviceRequest request) {
		validateIp(request.ipAddress());
		Device device = requireDevice(id);
		Agent agent = agents.findById(request.agentId()).orElseThrow(() -> new AgentNotFoundException(request.agentId()));
		device.update(request.name().trim(), request.ipAddress().trim(), clean(request.hostname()), request.deviceType().trim(), clean(request.location()), agent);
		return toResponse(devices.save(device));
	}
	@Transactional
	public DeviceResponse updateStatus(UUID id, DeviceStatus status) {
		if (status == null) throw new IllegalArgumentException("status is required");
		Device device = requireDevice(id); DeviceStatus old=device.getStatus(); device.updateStatus(status); DeviceResponse response=toResponse(devices.save(device));
		if(old!=status) events.publish("/topic/devices", "device.status.changed", response);
		return response;
	}
	@Transactional
	public void delete(UUID id) { devices.delete(requireDevice(id)); }
	private Device requireDevice(UUID id) { return devices.findById(id).orElseThrow(() -> new DeviceNotFoundException(id)); }
	private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
	private static void validateIp(String ip) {
		if (ip == null || !(ip.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}") || ip.matches("[0-9a-fA-F:]+")))
			throw new IllegalArgumentException("ipAddress must be an IPv4 or IPv6 address");
		if (ip.contains(".")) for (String part : ip.split("\\.")) if (Integer.parseInt(part) > 255) throw new IllegalArgumentException("Invalid IPv4 address");
	}
	private static DeviceResponse toResponse(Device d) { return new DeviceResponse(d.getId(), d.getName(), d.getIpAddress(), d.getHostname(), d.getDeviceType(), d.getLocation(), d.getAgent().getId(), d.getStatus(), d.isMonitoringEnabled(), d.getCreatedAt(), d.getUpdatedAt()); }
}
