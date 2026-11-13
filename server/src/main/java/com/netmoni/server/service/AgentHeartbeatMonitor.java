package com.netmoni.server.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.netmoni.server.entity.Agent;
import com.netmoni.server.entity.AgentStatus;
import com.netmoni.server.repository.AgentRepository;

@Component
public class AgentHeartbeatMonitor {
	private static final Logger log = LoggerFactory.getLogger(AgentHeartbeatMonitor.class);
	private final AgentRepository agents;
	private final long timeoutSeconds;
	private final NetMoniEventPublisher events;
	private final AlertService alerts;
	private final DiscoveryScanService scans;
	@org.springframework.beans.factory.annotation.Autowired
	public AgentHeartbeatMonitor(AgentRepository agents, NetMoniEventPublisher events, AlertService alerts, DiscoveryScanService scans, @Value("${netmoni.agent.heartbeat-timeout-seconds:90}") long timeoutSeconds) {
		if (timeoutSeconds < 1) throw new IllegalArgumentException("heartbeat timeout must be positive");
		this.agents = agents; this.events=events; this.alerts=alerts; this.scans=scans; this.timeoutSeconds = timeoutSeconds;
	}
	public AgentHeartbeatMonitor(AgentRepository agents,NetMoniEventPublisher events,AlertService alerts,long timeoutSeconds){this(agents,events,alerts,null,timeoutSeconds);}
	@Scheduled(fixedDelayString = "${netmoni.agent.status-check-interval-ms:15000}")
	@Transactional
	public void markExpiredAgentsOffline() {
		var expired = agents.findByStatusAndLastHeartbeatBefore(AgentStatus.ONLINE, Instant.now().minus(timeoutSeconds, ChronoUnit.SECONDS));
		expired.forEach(agent -> { agent.markOffline(); if(scans!=null)scans.failActiveForAgent(agent.getId(),"Agent went offline before the scan completed"); events.publish("/topic/agents", "agent.offline", java.util.Map.of("agentId",agent.getId())); events.publish("/topic/dashboard","dashboard.statistics.updated",java.util.Map.of("generatedAt",Instant.now())); alerts.create(new com.netmoni.server.dto.alert.AlertRequest("agent:"+agent.getId()+":disconnected","AGENT_DISCONNECTED",com.netmoni.server.entity.AlertSeverity.WARNING,"Agent "+agent.getName()+" missed its heartbeat",null,agent.getId())); });
		if (!expired.isEmpty()) log.info("Marked {} agents offline after heartbeat timeout", expired.size());
	}
}
