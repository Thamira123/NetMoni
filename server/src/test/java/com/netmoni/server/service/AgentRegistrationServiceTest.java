package com.netmoni.server.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.netmoni.server.dto.agent.AgentRegistrationRequest;
import com.netmoni.server.dto.agent.AgentResponse;
import com.netmoni.server.entity.Agent;
import com.netmoni.server.entity.AgentStatus;
import com.netmoni.server.repository.AgentRepository;

@ExtendWith(MockitoExtension.class)
class AgentRegistrationServiceTest {

	@Mock
	private AgentRepository agentRepository;

	@InjectMocks
	private AgentRegistrationService service;

	@Test
	void registersANewAgentAndMarksItOnline() {
		UUID agentId = UUID.randomUUID();
		AgentRegistrationRequest request = new AgentRegistrationRequest(
				agentId, "segment-a", "192.168.1.0/24", "1.0.0");
		when(agentRepository.findById(agentId)).thenReturn(Optional.empty());
		when(agentRepository.save(any(Agent.class))).thenAnswer(invocation -> invocation.getArgument(0));

		AgentResponse response = service.registerAgent(request);

		assertEquals(agentId, response.agentId());
		assertEquals("segment-a", response.name());
		assertEquals("192.168.1.0/24", response.segmentCidr());
		assertEquals(AgentStatus.ONLINE, response.status());
		assertNotNull(response.registeredAt());
		assertNotNull(response.lastHeartbeat());
		verify(agentRepository).save(any(Agent.class));
	}

	@Test
	void repeatedRegistrationUpdatesExistingAgentInsteadOfCreatingAnother() {
		UUID agentId = UUID.randomUUID();
		Instant registeredAt = Instant.parse("2026-01-01T00:00:00Z");
		Agent existing = new Agent(agentId, "old-name", "192.168.1.0/24", "0.9.0", registeredAt);
		when(agentRepository.findById(agentId)).thenReturn(Optional.of(existing));
		when(agentRepository.save(existing)).thenReturn(existing);

		AgentResponse response = service.registerAgent(new AgentRegistrationRequest(
				agentId, "new-name", "192.168.2.0/24", "1.0.0"));

		assertEquals("new-name", response.name());
		assertEquals("192.168.2.0/24", response.segmentCidr());
		assertEquals(registeredAt, response.registeredAt());
		assertEquals(AgentStatus.ONLINE, response.status());
		verify(agentRepository).save(existing);
	}

	@Test void agentHeartbeatRegistrationCannotExpandAdminAllowedRanges(){UUID id=UUID.randomUUID();Agent existing=new Agent(id,"edge","10.0.0.0/24","1",Instant.now());existing.updateAuthorizedCidrs(java.util.List.of("10.0.0.0/24"));when(agentRepository.findById(id)).thenReturn(Optional.of(existing));when(agentRepository.save(existing)).thenReturn(existing);
		service.registerAgent(new AgentRegistrationRequest(id,"edge","192.168.1.0/24","1",java.util.List.of("10.0.0.0/24","192.168.1.0/24")),true);
		assertEquals(java.util.List.of("10.0.0.0/24"),existing.getAuthorizedCidrs());assertEquals("10.0.0.0/24",existing.getSegmentCidr());}

	@Test
	void heartbeatForUnknownAgentFailsClearly() {
		UUID agentId = UUID.randomUUID();
		when(agentRepository.findById(agentId)).thenReturn(Optional.empty());

		assertThrows(AgentNotFoundException.class, () -> service.recordHeartbeat(agentId));
	}

	@Test
	void registrationRejectsMissingName() {
		AgentRegistrationRequest request = new AgentRegistrationRequest(
				UUID.randomUUID(), " ", "192.168.1.0/24", "1.0.0");

		assertThrows(IllegalArgumentException.class, () -> service.registerAgent(request));
	}
}
