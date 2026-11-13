package com.netmoni.server.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.netmoni.server.dto.monitoring.MonitoringResultRequest;
import com.netmoni.server.dto.monitoring.MonitoringResultResponse;
import com.netmoni.server.entity.Agent;
import com.netmoni.server.entity.Device;
import com.netmoni.server.entity.MonitoringResult;
import com.netmoni.server.entity.DeviceStatus;
import com.netmoni.server.repository.AgentRepository;
import com.netmoni.server.repository.DeviceRepository;
import com.netmoni.server.repository.MonitoringResultRepository;

@Service
public class MonitoringResultService {
	private static final int MAX_RESULTS = 500;
	private final MonitoringResultRepository results;
	private final DeviceRepository devices;
	private final AgentRepository agents;
	private final NetMoniEventPublisher events;
	private final AlertService alertService;
	private final long highResponseThresholdMs;
	public MonitoringResultService(MonitoringResultRepository results, DeviceRepository devices, AgentRepository agents,
			NetMoniEventPublisher events, AlertService alertService,
			@org.springframework.beans.factory.annotation.Value("${netmoni.monitoring.high-response-time-ms:1000}") long highResponseThresholdMs) {
		this.results=results; this.devices=devices; this.agents=agents; this.events=events; this.alertService=alertService; this.highResponseThresholdMs=highResponseThresholdMs;
	}
	@Transactional
	public MonitoringResultResponse submit(MonitoringResultRequest request) {
		Device device = devices.findById(request.deviceId()).orElseThrow(() -> new DeviceNotFoundException(request.deviceId()));
		Agent agent = agents.findById(request.agentId()).orElseThrow(() -> new AgentNotFoundException(request.agentId()));
		if (!device.getAgent().getId().equals(agent.getId())) throw new IllegalArgumentException("Device is not assigned to this agent");
		if (request.available() && request.errorMessage() != null && !request.errorMessage().isBlank()) throw new IllegalArgumentException("Available result cannot contain an error message");
		MonitoringResult saved = results.save(new MonitoringResult(device, agent, request.checkType().trim(), request.responseTimeMs(), request.available(), request.packetLossPercent(), request.portNumber(), request.portOpen(), request.snmpData(), request.checkedAt(), clean(request.errorMessage())));
		MonitoringResultResponse response=toResponse(saved);
		String unavailableKey="device:"+device.getId()+":unavailable";
		if(!request.available()){
			String type= request.checkType().toUpperCase().contains("PORT")?"TCP_PORT_FAILURE":("SNMP".equalsIgnoreCase(request.checkType())?"SNMP_CHECK_FAILURE":"DEVICE_UNAVAILABLE");
			alertService.create(new com.netmoni.server.dto.alert.AlertRequest(unavailableKey,type,com.netmoni.server.entity.AlertSeverity.CRITICAL,
				request.errorMessage()==null?device.getName()+" is unavailable":request.errorMessage(),device.getId(),agent.getId()));
		}else alertService.resolveByKey(unavailableKey);
		String latencyKey="device:"+device.getId()+":high-response-time";
		if(request.responseTimeMs()!=null&&request.responseTimeMs()>highResponseThresholdMs) alertService.create(new com.netmoni.server.dto.alert.AlertRequest(latencyKey,"HIGH_RESPONSE_TIME",com.netmoni.server.entity.AlertSeverity.WARNING,"High response time: "+request.responseTimeMs()+" ms",device.getId(),agent.getId()));
		else alertService.resolveByKey(latencyKey);
		String packetLossKey="device:"+device.getId()+":packet-loss";
		if(request.packetLossPercent()!=null&&request.packetLossPercent().compareTo(new java.math.BigDecimal("10.0"))>=0) alertService.create(new com.netmoni.server.dto.alert.AlertRequest(packetLossKey,"HIGH_PACKET_LOSS",com.netmoni.server.entity.AlertSeverity.WARNING,"Packet loss: "+request.packetLossPercent()+"%",device.getId(),agent.getId()));
		else alertService.resolveByKey(packetLossKey);
		DeviceStatus newStatus=request.available()?DeviceStatus.ONLINE:DeviceStatus.OFFLINE;
		if(device.getStatus()!=newStatus){device.updateStatus(newStatus);devices.save(device);events.publish("/topic/devices","device.status.changed",java.util.Map.of("deviceId",device.getId(),"status",device.getStatus()));}
		events.publish("/topic/monitoring", "monitoring.result.created", response);
		events.publish("/topic/dashboard","dashboard.statistics.updated",java.util.Map.of("generatedAt",Instant.now()));
		return response;
	}
	@Transactional(readOnly=true)
	public List<MonitoringResultResponse> forDevice(UUID id, Instant from, Instant to) {
		checkRange(from,to);
		if(!devices.existsById(id)) throw new DeviceNotFoundException(id);
		return results.findByDevice_IdAndCheckedAtBetweenOrderByCheckedAtDesc(id,from,to,PageRequest.of(0,MAX_RESULTS)).stream().map(MonitoringResultService::toResponse).toList();
	}
	@Transactional(readOnly=true)
	public List<MonitoringResultResponse> forAgent(UUID id, Instant from, Instant to) {
		checkRange(from,to);
		if(!agents.existsById(id)) throw new AgentNotFoundException(id);
		return results.findByAgent_IdAndCheckedAtBetweenOrderByCheckedAtDesc(id,from,to,PageRequest.of(0,MAX_RESULTS)).stream().map(MonitoringResultService::toResponse).toList();
	}
	@Transactional(readOnly=true)
	public List<MonitoringResultResponse> recent(int limit) {
		if (limit < 1 || limit > MAX_RESULTS) throw new IllegalArgumentException("limit must be between 1 and 500");
		return results.findAllByOrderByCheckedAtDesc(PageRequest.of(0,limit)).stream().map(MonitoringResultService::toResponse).toList();
	}
	@Transactional(readOnly=true)
	public List<MonitoringResultResponse> recentActivities(int limit) { return recent(limit); }
	@Transactional(readOnly=true)
	public double availability(UUID deviceId, Instant from, Instant to) {
		checkRange(from,to);
		if(!devices.existsById(deviceId)) throw new DeviceNotFoundException(deviceId);
		long total = results.countByDevice_IdAndCheckedAtBetween(deviceId,from,to);
		return total == 0 ? 0 : 100.0 * results.countByDevice_IdAndAvailableTrueAndCheckedAtBetween(deviceId,from,to) / total;
	}
	private static void checkRange(Instant from, Instant to) { if(from==null||to==null||from.isAfter(to)) throw new IllegalArgumentException("Valid from and to timestamps are required"); }
	private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
	private static MonitoringResultResponse toResponse(MonitoringResult r) { return new MonitoringResultResponse(r.getId(),r.getDevice().getId(),r.getAgent().getId(),r.getCheckType(),r.getResponseTimeMs(),r.isAvailable(),r.getPacketLossPercent(),r.getPortNumber(),r.getPortOpen(),r.getSnmpData(),r.getCheckedAt(),r.getErrorMessage()); }
}
