package com.netmoni.server.entity;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name="alerts", indexes={@Index(name="idx_alert_status_created", columnList="status, created_at"), @Index(name="idx_alert_dedupe", columnList="deduplication_key, status")})
public class Alert {
	@Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
	@Column(name="deduplication_key", nullable=false, length=200) private String deduplicationKey;
	@Column(nullable=false, length=60) private String type;
	@Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private AlertSeverity severity;
	@Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private AlertStatus status=AlertStatus.UNREAD;
	@Column(nullable=false, length=500) private String message;
	@Column(name="device_id") private UUID deviceId;
	@Column(name="agent_id") private UUID agentId;
	@Column(name="created_at", nullable=false, updatable=false) private Instant createdAt;
	@Column(name="resolved_at") private Instant resolvedAt;
	protected Alert() {}
	public Alert(String key,String type,AlertSeverity severity,String message,UUID deviceId,UUID agentId) { this.deduplicationKey=key;this.type=type;this.severity=severity;this.message=message;this.deviceId=deviceId;this.agentId=agentId;this.createdAt=Instant.now(); }
	public void markRead(){if(status==AlertStatus.UNREAD) status=AlertStatus.READ;}
	public void resolve(){status=AlertStatus.RESOLVED;resolvedAt=Instant.now();}
	public UUID getId(){return id;} public String getDeduplicationKey(){return deduplicationKey;} public String getType(){return type;}
	public AlertSeverity getSeverity(){return severity;} public AlertStatus getStatus(){return status;} public String getMessage(){return message;}
	public UUID getDeviceId(){return deviceId;} public UUID getAgentId(){return agentId;} public Instant getCreatedAt(){return createdAt;} public Instant getResolvedAt(){return resolvedAt;}
}
