package com.netmoni.server.controller;

import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import com.netmoni.server.dto.agent.AgentRegistrationRequest;
import com.netmoni.server.dto.agent.AgentResponse;
import com.netmoni.server.dto.common.PageResponse;
import com.netmoni.server.service.AgentRegistrationService;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

	private final AgentRegistrationService agentRegistrationService;

	public AgentController(AgentRegistrationService agentRegistrationService) {
		this.agentRegistrationService = agentRegistrationService;
	}

	@PostMapping("/register")
	@PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
	public ResponseEntity<AgentResponse> register(@Valid @RequestBody AgentRegistrationRequest request) {
		return ResponseEntity.ok(agentRegistrationService.registerAgent(request));
	}

	@GetMapping
	public PageResponse<AgentResponse> getAgents(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) {
		return PageResponse.from(agentRegistrationService.getAgents(page,size));
	}

	@GetMapping("/{agentId}")
	public AgentResponse getAgent(@PathVariable UUID agentId) {
		return agentRegistrationService.getAgent(agentId);
	}
}
