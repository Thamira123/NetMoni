package com.netmoni.server.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Autowired;

import com.netmoni.server.dto.agent.AgentRegistrationRequest;
import com.netmoni.server.dto.agent.AgentResponse;
import com.netmoni.server.entity.Agent;
import com.netmoni.server.repository.AgentRepository;

@Service
public class AgentRegistrationService {

	private final AgentRepository agentRepository;
	private final NetMoniEventPublisher events;
	@Autowired(required = false)
	private AlertService alertService;

	@Autowired
	public AgentRegistrationService(AgentRepository agentRepository, NetMoniEventPublisher events) {
		this.agentRepository = agentRepository;
		this.events = events;
	}

	public AgentRegistrationService(AgentRepository agentRepository) {
		this.agentRepository = agentRepository;
		this.events = null;
	}

	@Transactional
	public AgentResponse registerAgent(AgentRegistrationRequest request) {
		validate(request);
		Instant now = Instant.now();
		Agent agent = agentRepository.findById(request.agentId())
				.map(existing -> {
					existing.updateRegistration(request.name().trim(), request.segmentCidr().trim(),
							request.version().trim(), now);
					return existing;
				})
				.orElseGet(() -> new Agent(request.agentId(), request.name().trim(),
						request.segmentCidr().trim(), request.version().trim(), now));
		AgentResponse response=toResponse(agentRepository.save(agent));
		if(events!=null) events.publish("/topic/agents","agent.online",response);
		if(events!=null) events.publish("/topic/dashboard","dashboard.statistics.updated",java.util.Map.of("generatedAt",Instant.now()));
		if(agent.getStatus()==com.netmoni.server.entity.AgentStatus.ONLINE && alertService!=null) alertService.resolveByKey("agent:"+agent.getId()+":disconnected");
		return response;
	}

	@Transactional
	public AgentResponse recordHeartbeat(UUID agentId) {
		if (agentId == null) {
			throw new IllegalArgumentException("agentId is required");
		}
		Agent agent = agentRepository.findById(agentId)
				.orElseThrow(() -> new AgentNotFoundException(agentId));
		boolean wasOffline=agent.getStatus()!=com.netmoni.server.entity.AgentStatus.ONLINE;
		agent.recordHeartbeat(Instant.now());
		AgentResponse response=toResponse(agentRepository.save(agent));
		if(wasOffline&&events!=null) events.publish("/topic/agents","agent.online",response);
		if(wasOffline&&events!=null) events.publish("/topic/dashboard","dashboard.statistics.updated",java.util.Map.of("generatedAt",Instant.now()));
		if(wasOffline&&alertService!=null) alertService.resolveByKey("agent:"+agentId+":disconnected");
		return response;
	}

	@Transactional(readOnly = true)
	public AgentResponse getAgent(UUID agentId) {
		return agentRepository.findById(agentId)
				.map(AgentRegistrationService::toResponse)
				.orElseThrow(() -> new AgentNotFoundException(agentId));
	}

	@Transactional(readOnly = true)
	public List<AgentResponse> getAgents() {
		return agentRepository.findAll().stream()
				.map(AgentRegistrationService::toResponse)
				.toList();
	}

	@Transactional(readOnly = true)
	public org.springframework.data.domain.Page<AgentResponse> getAgents(int page,int size) {
		checkPage(page,size);
		return agentRepository.findAll(PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"registeredAt"))).map(AgentRegistrationService::toResponse);
	}
	private static void checkPage(int page,int size){if(page<0||size<1||size>100)throw new IllegalArgumentException("page must be >= 0 and size must be between 1 and 100");}

	private static void validate(AgentRegistrationRequest request) {
		if (request == null || request.agentId() == null) {
			throw new IllegalArgumentException("agentId is required");
		}
		requireText(request.name(), "name");
		requireText(request.segmentCidr(), "segmentCidr");
		requireText(request.version(), "version");
		if (request.name().length() > 100 || request.segmentCidr().length() > 50
				|| request.version().length() > 50) {
			throw new IllegalArgumentException("Agent registration fields exceed their maximum lengths");
		}
	}

	private static void requireText(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " is required");
		}
	}

	private static AgentResponse toResponse(Agent agent) {
		return new AgentResponse(agent.getId(), agent.getName(), agent.getSegmentCidr(), agent.getVersion(),
				agent.getStatus(), agent.getRegisteredAt(), agent.getLastHeartbeat());
	}
}
