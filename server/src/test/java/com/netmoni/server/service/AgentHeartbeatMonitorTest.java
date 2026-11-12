package com.netmoni.server.service;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.netmoni.server.entity.*;
import com.netmoni.server.repository.AgentRepository;
class AgentHeartbeatMonitorTest {
	@Test void marksExpiredAgentOfflineAndCreatesAlert(){AgentRepository repo=mock(AgentRepository.class);NetMoniEventPublisher events=mock(NetMoniEventPublisher.class);AlertService alerts=mock(AlertService.class);Agent agent=new Agent(UUID.randomUUID(),"agent-a","10.0.0.0/24","1",Instant.now());when(repo.findByStatusAndLastHeartbeatBefore(eq(AgentStatus.ONLINE),any())).thenReturn(List.of(agent));
		new AgentHeartbeatMonitor(repo,events,alerts,90).markExpiredAgentsOffline();assertEquals(AgentStatus.OFFLINE,agent.getStatus());verify(events).publish(eq("/topic/agents"),eq("agent.offline"),any());verify(alerts).create(any());}
}
