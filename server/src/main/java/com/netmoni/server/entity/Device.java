package com.netmoni.server.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "devices", indexes = {
		@Index(name = "idx_devices_agent_id", columnList = "agent_id"),
		@Index(name = "idx_devices_status", columnList = "status")
}, uniqueConstraints = @UniqueConstraint(name = "uk_devices_agent_ip_address", columnNames = {"agent_id", "ip_address"}))
public class Device {
	@Id
	private UUID id;
	@Column(nullable = false, length = 120)
	private String name;
	@Column(name = "ip_address", nullable = false, length = 45)
	private String ipAddress;
	@Column(length = 253)
	private String hostname;
	@Column(name = "device_type", nullable = false, length = 40)
	private String deviceType;
	@Column(length = 160)
	private String location;
	@Column(name="network_cidr",length=50) private String networkCidr;
	@Column(name="interface_name",length=120) private String interfaceName;
	@Column(name="mac_address",length=24) private String macAddress;
	@Column(name="last_seen_at") private Instant lastSeenAt;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "agent_id", nullable = false)
	private Agent agent;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private DeviceStatus status = DeviceStatus.UNKNOWN;
	@Column(name = "monitoring_enabled", nullable = false)
	private boolean monitoringEnabled = true;
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Device() {}

	public Device(UUID id, String name, String ipAddress, String hostname, String deviceType,
			String location, Agent agent) {
		this.id = id == null ? UUID.randomUUID() : id;
		this.name = name;
		this.ipAddress = ipAddress;
		this.hostname = hostname;
		this.deviceType = deviceType;
		this.location = location;
		this.agent = agent;
	}

	@PrePersist
	void onCreate() { Instant now = Instant.now(); createdAt = now; updatedAt = now; }
	@PreUpdate
	void onUpdate() { updatedAt = Instant.now(); }
	public void update(String name, String ipAddress, String hostname, String deviceType, String location, Agent agent) {
		this.name = name; this.ipAddress = ipAddress; this.hostname = hostname;
		this.deviceType = deviceType; this.location = location; this.agent = agent;
	}
	public void updateDiscoveredHostname(String hostname) {
		if (hostname != null && !hostname.isBlank()) this.hostname = hostname;
	}
	public void updateDiscoveryMetadata(String hostname,String networkCidr,String interfaceName,String macAddress,String deviceType,Instant seenAt){if(hostname!=null&&!hostname.isBlank())this.hostname=hostname;if(networkCidr!=null&&!networkCidr.isBlank())this.networkCidr=networkCidr;if(interfaceName!=null&&!interfaceName.isBlank())this.interfaceName=interfaceName;if(macAddress!=null&&!macAddress.isBlank())this.macAddress=macAddress;if(deviceType!=null&&!deviceType.isBlank()&&"UNKNOWN".equals(this.deviceType))this.deviceType=deviceType;this.lastSeenAt=seenAt==null?Instant.now():seenAt;}
	public void updateStatus(DeviceStatus status) { this.status = status; }
	public void recordSeen(Instant at){this.lastSeenAt=at==null?Instant.now():at;}
	public UUID getId() { return id; }
	public String getName() { return name; }
	public String getIpAddress() { return ipAddress; }
	public String getHostname() { return hostname; }
	public String getDeviceType() { return deviceType; }
	public String getLocation() { return location; }
	public Agent getAgent() { return agent; }
	public DeviceStatus getStatus() { return status; }
	public boolean isMonitoringEnabled() { return monitoringEnabled; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
	public String getNetworkCidr(){return networkCidr;} public String getInterfaceName(){return interfaceName;} public String getMacAddress(){return macAddress;} public Instant getLastSeenAt(){return lastSeenAt;}
}
