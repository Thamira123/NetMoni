package com.netmoni.server.service;

import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import com.netmoni.server.dto.device.DeviceRequest;
import com.netmoni.server.dto.device.DeviceResponse;
import com.netmoni.server.dto.agent.DiscoveredDeviceRequest;
import com.netmoni.server.dto.agent.DiscoveredDeviceResponse;
import com.netmoni.server.entity.Agent;
import com.netmoni.server.entity.Device;
import com.netmoni.server.entity.DeviceStatus;
import com.netmoni.server.repository.AgentRepository;
import com.netmoni.server.repository.DeviceRepository;

@Service
public class DeviceService {
	private static final int MAX_DISCOVERED_DEVICES = 4096;
	private final DeviceRepository devices;
	private final AgentRepository agents;
	private final NetMoniEventPublisher events;
	public DeviceService(DeviceRepository devices, AgentRepository agents, NetMoniEventPublisher events) { this.devices = devices; this.agents = agents; this.events=events; }

	@Transactional
	public DeviceResponse create(DeviceRequest request) {
		validateIp(request.ipAddress());
		Agent agent = agents.findById(request.agentId()).orElseThrow(() -> new AgentNotFoundException(request.agentId()));
		Optional<Device> existing = devices.findByAgent_IdAndIpAddress(agent.getId(), request.ipAddress().trim());
		if (existing.isPresent()) return toResponse(existing.get());
		return toResponse(devices.save(new Device(null, request.name().trim(), request.ipAddress().trim(),
				clean(request.hostname()), request.deviceType().trim(), clean(request.location()), agent)));
	}

	@Transactional
	public List<DiscoveredDeviceResponse> registerDiscovered(UUID agentId, List<DiscoveredDeviceRequest> discovered) {
		if (agentId == null) throw new IllegalArgumentException("agentId is required");
		if (discovered == null || discovered.size() > MAX_DISCOVERED_DEVICES) {
			throw new IllegalArgumentException("devices must contain at most " + MAX_DISCOVERED_DEVICES + " entries");
		}
		Agent agent = agents.findById(agentId).orElseThrow(() -> new AgentNotFoundException(agentId));
		Map<String, DiscoveredDeviceRequest> uniqueByIp = new LinkedHashMap<>();
		for (DiscoveredDeviceRequest item : discovered) {
			if (item == null) throw new IllegalArgumentException("discovered device cannot be null");
			String ip = item.ipAddress() == null ? "" : item.ipAddress().trim();
			validateIpv4(ip);
			if (!belongsToCidr(ip, agent.getSegmentCidr())) {
				throw new IllegalArgumentException("Discovered address is outside the registered agent subnet");
			}
			uniqueByIp.put(ip, item);
		}

		List<DiscoveredDeviceResponse> registered = new ArrayList<>(uniqueByIp.size());
		boolean changed = false;
		for (Map.Entry<String, DiscoveredDeviceRequest> entry : uniqueByIp.entrySet()) {
			String ip = entry.getKey();
			String hostname = clean(entry.getValue().hostname());
			if (hostname != null && hostname.length() > 253) hostname = hostname.substring(0, 253);
			Device device = devices.findByAgent_IdAndIpAddress(agentId, ip).orElse(null);
			boolean created = device == null;
			if (device == null) {
				String name = hostname == null || hostname.length() > 120 ? ip : hostname;
				device = new Device(null, name, ip, hostname, "UNKNOWN", null, agent);
				device = devices.save(device);
				changed = true;
			} else if (hostname != null && !hostname.equals(device.getHostname())) {
				device.updateDiscoveredHostname(hostname);
				device = devices.save(device);
				changed = true;
			}
			registered.add(new DiscoveredDeviceResponse(ip, device.getId(), created));
		}
		if (changed) {
			events.publish("/topic/devices", "device.discovered", registered);
			events.publish("/topic/dashboard", "dashboard.statistics.updated",
					java.util.Map.of("generatedAt", java.time.Instant.now()));
		}
		return List.copyOf(registered);
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
	private static void validateIpv4(String ip) {
		if (ip == null || !(ip.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")))
			throw new IllegalArgumentException("Discovered ipAddress must be IPv4");
		for (String part : ip.split("\\.")) {
			if (Integer.parseInt(part) > 255) throw new IllegalArgumentException("Invalid IPv4 address");
		}
	}
	private static boolean belongsToCidr(String ip, String cidr) {
		try {
			String[] networkParts = cidr.split("/", 2);
			if (networkParts.length != 2) return false;
			int prefix = Integer.parseInt(networkParts[1]);
			if (prefix < 0 || prefix > 32) return false;
			long address = ipv4Number(ip);
			long network = ipv4Number(networkParts[0]);
			long mask = prefix == 0 ? 0 : (0xffff_ffffL << (32 - prefix)) & 0xffff_ffffL;
			return (address & mask) == (network & mask);
		} catch (RuntimeException exception) {
			return false;
		}
	}
	private static long ipv4Number(String ip) {
		validateIpv4(ip);
		long result = 0;
		for (String part : ip.split("\\.")) result = (result << 8) | Integer.parseInt(part);
		return result;
	}
	private static DeviceResponse toResponse(Device d) { return new DeviceResponse(d.getId(), d.getName(), d.getIpAddress(), d.getHostname(), d.getDeviceType(), d.getLocation(), d.getAgent().getId(), d.getStatus(), d.isMonitoringEnabled(), d.getCreatedAt(), d.getUpdatedAt()); }
}
