package com.netmoni.server.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "monitoring_results", indexes = {
		@Index(name = "idx_results_device_time", columnList = "device_id, checked_at"),
		@Index(name = "idx_results_agent_time", columnList = "agent_id, checked_at")
})
public class MonitoringResult {
	@Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "device_id", nullable = false) private Device device;
	@ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "agent_id", nullable = false) private Agent agent;
	@Column(name = "check_type", nullable = false, length = 40) private String checkType;
	@Column(name = "response_time_ms") private Long responseTimeMs;
	@Column(nullable = false) private boolean available;
	@Column(name = "packet_loss_percent", precision = 5, scale = 2) private BigDecimal packetLossPercent;
	@Column(name = "port_number") private Integer portNumber;
	@Column(name = "port_open") private Boolean portOpen;
	@Column(name = "snmp_data", columnDefinition = "text") private String snmpData;
	@Column(name = "checked_at", nullable = false) private Instant checkedAt;
	@Column(name = "error_message", length = 1000) private String errorMessage;
	protected MonitoringResult() {}
	public MonitoringResult(Device device, Agent agent, String checkType, Long responseTimeMs, boolean available,
			BigDecimal packetLossPercent, Integer portNumber, Boolean portOpen, String snmpData, Instant checkedAt, String errorMessage) {
		this.device=device; this.agent=agent; this.checkType=checkType; this.responseTimeMs=responseTimeMs; this.available=available;
		this.packetLossPercent=packetLossPercent; this.portNumber=portNumber; this.portOpen=portOpen; this.snmpData=snmpData;
		this.checkedAt=checkedAt == null ? Instant.now() : checkedAt; this.errorMessage=errorMessage;
	}
	public UUID getId(){return id;} public Device getDevice(){return device;} public Agent getAgent(){return agent;}
	public String getCheckType(){return checkType;} public Long getResponseTimeMs(){return responseTimeMs;} public boolean isAvailable(){return available;}
	public BigDecimal getPacketLossPercent(){return packetLossPercent;} public Integer getPortNumber(){return portNumber;} public Boolean getPortOpen(){return portOpen;}
	public String getSnmpData(){return snmpData;} public Instant getCheckedAt(){return checkedAt;} public String getErrorMessage(){return errorMessage;}
}
