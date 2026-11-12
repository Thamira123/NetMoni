package com.netmoni.server.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import com.netmoni.server.entity.AgentStatus;

import com.netmoni.server.entity.Agent;

public interface AgentRepository extends JpaRepository<Agent, UUID> {
	List<Agent> findByStatusAndLastHeartbeatBefore(AgentStatus status, Instant cutoff);
	long countByStatus(AgentStatus status);
}
