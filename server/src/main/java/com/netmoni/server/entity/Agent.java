package com.netmoni.server.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "agents")
public class Agent {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(name = "segment_cidr", nullable = false, length = 50)
	private String segmentCidr;

	@Column(nullable = false, length = 50)
	private String version;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AgentStatus status;

	@Column(name = "registered_at", nullable = false, updatable = false)
	private Instant registeredAt;

	@Column(name = "last_heartbeat")
	private Instant lastHeartbeat;

	protected Agent() {
	}

	public Agent(UUID id, String name, String segmentCidr, String version, Instant registeredAt) {
		this.id = id;
		this.name = name;
		this.segmentCidr = segmentCidr;
		this.version = version;
		this.registeredAt = registeredAt;
		this.status = AgentStatus.ONLINE;
		this.lastHeartbeat = registeredAt;
	}

	public void updateRegistration(String name, String segmentCidr, String version, Instant now) {
		this.name = name;
		this.segmentCidr = segmentCidr;
		this.version = version;
		this.status = AgentStatus.ONLINE;
		this.lastHeartbeat = now;
	}

	public void recordHeartbeat(Instant now) {
		this.status = AgentStatus.ONLINE;
		this.lastHeartbeat = now;
	}

	public void markOffline() { this.status = AgentStatus.OFFLINE; }

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getSegmentCidr() {
		return segmentCidr;
	}

	public String getVersion() {
		return version;
	}

	public AgentStatus getStatus() {
		return status;
	}

	public Instant getRegisteredAt() {
		return registeredAt;
	}

	public Instant getLastHeartbeat() {
		return lastHeartbeat;
	}
}
