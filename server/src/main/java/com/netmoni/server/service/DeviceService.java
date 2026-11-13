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
	@org.springframework.beans.factory.annotation.Autowired(required=false) private com.netmoni.server.repository.AgentNetworkRepository networkRepository;
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
			String cidr=clean(item.networkCidr());
			if(cidr==null){if(agent.getAuthorizedCidrs().stream().noneMatch(allowed->belongsToCidr(ip,allowed)))throw new IllegalArgumentException("Discovered address is outside the registered agent networks");}
			else if(!validCidr(cidr)||!belongsToCidr(ip,cidr)||agent.getAuthorizedCidrs().stream().noneMatch(allowed->containsCidr(allowed,cidr)))throw new IllegalArgumentException("Discovered address is outside its authorized network");
			if(cidr!=null&&item.interfaceName()!=null&&networkRepository!=null){var network=networkRepository.findByAgent_IdAndInterfaceNameAndCidr(agentId,item.interfaceName(),cidr).orElseThrow(()->new IllegalArgumentException("Discovered device refers to an unknown agent interface network"));if(!network.isAuthorized()||!network.isScanEligible())throw new IllegalArgumentException("Discovered device network is not eligible");}
			uniqueByIp.put(ip, item);
		}

		List<DiscoveredDeviceResponse> registered = new ArrayList<>(uniqueByIp.size());
		boolean changed = false;
		for (Map.Entry<String, DiscoveredDeviceRequest> entry : uniqueByIp.entrySet()) {
			String ip = entry.getKey();
			String cidr=clean(entry.getValue().networkCidr());
			String hostname = clean(entry.getValue().hostname());
			if (hostname != null && hostname.length() > 253) hostname = hostname.substring(0, 253);
			Device device = devices.findByAgent_IdAndIpAddress(agentId, ip).orElse(null);
			boolean created = device == null;
			if (device == null) {
				String name = hostname == null || hostname.length() > 120 ? ip : hostname;
				device = new Device(null, name, ip, hostname, safeDeviceType(entry.getValue().deviceType()), null, agent);
				changed = true;
			}
			device.updateDiscoveryMetadata(hostname,cidr,clean(entry.getValue().interfaceName()),validMac(entry.getValue().macAddress())?entry.getValue().macAddress():null,safeDeviceType(entry.getValue().deviceType()),entry.getValue().discoveredAt());
			device = devices.save(device); changed=true;
			registered.add(new DiscoveredDeviceResponse(ip, device.getId(), created));
		}
		if (changed) {
			events.publish("/topic/devices", "device.discovered", registered);
			events.publish("/topic/devices", registered.stream().anyMatch(DiscoveredDeviceResponse::created)?"device.registered":"device.updated",registered);
			events.publish("/topic/dashboard", "dashboard.statistics.updated",
					java.util.Map.of("generatedAt", java.time.Instant.now()));
		}
		return List.copyOf(registered);
	}
	@Transactional(readOnly = true)
	public List<DeviceResponse> findAll() { return devices.findAll().stream().map(DeviceService::toResponse).toList(); }
	@Transactional(readOnly = true)
	public org.springframework.data.domain.Page<DeviceResponse> findAll(int page,int size) { if(page<0||size<1||size>100)throw new IllegalArgumentException("page must be >= 0 and size must be between 1 and 100"); return devices.findAll(PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"createdAt"))).map(DeviceService::toResponse); }
	@Transactional(readOnly=true) public org.springframework.data.domain.Page<DeviceResponse> findAll(int page,int size,String search,String networkCidr,UUID agentId,String deviceType,DeviceStatus status,String sortBy,String direction){
		if(page<0||size<1||size>100)throw new IllegalArgumentException("page must be >= 0 and size must be between 1 and 100");String property=java.util.Set.of("name","ipAddress","lastSeenAt","createdAt","deviceType","status").contains(sortBy)?sortBy:"createdAt";Sort sort=Sort.by("asc".equalsIgnoreCase(direction)?Sort.Direction.ASC:Sort.Direction.DESC,property);
		org.springframework.data.jpa.domain.Specification<Device> spec=(root,query,cb)->{java.util.List<jakarta.persistence.criteria.Predicate> p=new java.util.ArrayList<>();if(search!=null&&!search.isBlank()){String pattern="%"+search.trim().toLowerCase(java.util.Locale.ROOT)+"%";p.add(cb.or(cb.like(cb.lower(root.get("name")),pattern),cb.like(cb.lower(root.get("ipAddress")),pattern),cb.like(cb.lower(root.get("hostname")),pattern),cb.like(cb.lower(root.get("macAddress")),pattern)));}if(networkCidr!=null&&!networkCidr.isBlank())p.add(cb.equal(root.get("networkCidr"),networkCidr));if(agentId!=null)p.add(cb.equal(root.get("agent").get("id"),agentId));if(deviceType!=null&&!deviceType.isBlank())p.add(cb.equal(cb.upper(root.get("deviceType")),deviceType.trim().toUpperCase(java.util.Locale.ROOT)));if(status!=null)p.add(cb.equal(root.get("status"),status));return cb.and(p.toArray(jakarta.persistence.criteria.Predicate[]::new));};return devices.findAll(spec,PageRequest.of(page,size,sort)).map(DeviceService::toResponse);
	}
	@Transactional(readOnly = true)
	public DeviceResponse find(UUID id) { return toResponse(requireDevice(id)); }
	@Transactional(readOnly = true)
	public List<DeviceResponse> findByAgent(UUID agentId) {
		if (!agents.existsById(agentId)) throw new AgentNotFoundException(agentId);
		return devices.findByAgent_Id(agentId).stream().map(DeviceService::toResponse).toList();
	}
	@Transactional(readOnly=true) public List<DeviceResponse> findByNetworkCidr(String cidr){return devices.findByNetworkCidr(cidr).stream().map(DeviceService::toResponse).toList();}
	@Transactional(readOnly=true) public List<DeviceResponse> findByAgentAndNetwork(UUID agentId,String cidr){return devices.findByAgent_IdAndNetworkCidr(agentId,cidr).stream().map(DeviceService::toResponse).toList();}
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
	public DeviceResponse updateMonitoringEnabled(UUID id, boolean enabled) {
		Device device = requireDevice(id);
		if (device.isMonitoringEnabled() == enabled) return toResponse(device);
		device.updateMonitoringEnabled(enabled);
		DeviceResponse response = toResponse(devices.save(device));
		events.publish("/topic/devices", "device.monitoring.changed", response);
		events.publish("/topic/dashboard", "dashboard.statistics.updated", java.util.Map.of("generatedAt", java.time.Instant.now()));
		return response;
	}
	@Transactional(readOnly = true)
	public List<com.netmoni.server.dto.agent.AgentMonitoringTarget> monitoringTargets(UUID agentId) {
		if (!agents.existsById(agentId)) throw new AgentNotFoundException(agentId);
		return devices.findByAgent_IdAndMonitoringEnabledTrue(agentId).stream()
				.map(device -> new com.netmoni.server.dto.agent.AgentMonitoringTarget(device.getIpAddress(), device.getId()))
				.toList();
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
	private static boolean validCidr(String cidr){try{String[] p=cidr.split("/",-1);return p.length==2&&Integer.parseInt(p[1])>=0&&Integer.parseInt(p[1])<=32&&ipv4Number(p[0])>=0;}catch(RuntimeException e){return false;}}
	private static boolean containsCidr(String outer,String inner){try{String[] a=outer.split("/"),b=inner.split("/");int ap=Integer.parseInt(a[1]),bp=Integer.parseInt(b[1]);return ap<=bp&&belongsToCidr(b[0],outer);}catch(RuntimeException e){return false;}}
	private static boolean validMac(String mac){return mac!=null&&mac.matches("(?i)(?:[0-9a-f]{2}:){5}[0-9a-f]{2}");}
	private static String safeDeviceType(String type){if(type==null)return "UNKNOWN";return java.util.Set.of("COMPUTER","ROUTER","SWITCH","PRINTER","SERVER","MOBILE","UNKNOWN").contains(type.toUpperCase(java.util.Locale.ROOT))?type.toUpperCase(java.util.Locale.ROOT):"UNKNOWN";}
	private static long ipv4Number(String ip) {
		validateIpv4(ip);
		long result = 0;
		for (String part : ip.split("\\.")) result = (result << 8) | Integer.parseInt(part);
		return result;
	}
	private static DeviceResponse toResponse(Device d) { return new DeviceResponse(d.getId(), d.getName(), d.getIpAddress(), d.getHostname(), d.getDeviceType(), d.getLocation(), d.getAgent().getId(), d.getStatus(), d.isMonitoringEnabled(), d.getCreatedAt(), d.getUpdatedAt(),d.getNetworkCidr(),d.getInterfaceName(),d.getMacAddress(),d.getLastSeenAt()); }
}
