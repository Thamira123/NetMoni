package com.netmoni.server.entity;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name="discovery_scans", indexes=@Index(name="idx_discovery_scan_agent_created", columnList="agent_id,created_at"))
public class DiscoveryScan {
    public enum State { QUEUED, RUNNING, COMPLETED, FAILED }
    @Id private UUID id;
    @Column(name="agent_id", nullable=false) private UUID agentId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private State state;
    @Column(nullable=false) private int progress;
    @Column(nullable=false) private int checked;
    @Column(nullable=false) private int discovered;
    @Column(nullable=false) private int failed;
    @Column(name="new_devices",nullable=false) private int newDevices;
    @Column(name="existing_devices",nullable=false) private int existingDevices;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="started_at") private Instant startedAt;
    @Column(name="finished_at") private Instant finishedAt;
    @Column(name="duration_ms") private Long durationMs;
    @Column(name="error_message",length=500) private String errorMessage;
    protected DiscoveryScan() {}
    public DiscoveryScan(UUID agentId) { this.id=UUID.randomUUID(); this.agentId=agentId; this.state=State.QUEUED; this.createdAt=Instant.now(); }
    public void start(){ if(state==State.QUEUED){state=State.RUNNING; startedAt=Instant.now();} }
    public void progress(int progress,int checked,int discovered,int failed,int newDevices,int existingDevices){ if(state==State.QUEUED)start(); this.progress=Math.max(0,Math.min(100,progress)); this.checked=Math.max(0,checked); this.discovered=Math.max(0,discovered); this.failed=Math.max(0,failed); this.newDevices=Math.max(0,newDevices); this.existingDevices=Math.max(0,existingDevices); }
    public void complete(int checked,int discovered,int failed,int newDevices,int existingDevices,long durationMs){ progress(100,checked,discovered,failed,newDevices,existingDevices); state=State.COMPLETED; finishedAt=Instant.now(); this.durationMs=Math.max(0,durationMs); }
    public void fail(String reason){state=State.FAILED; finishedAt=Instant.now(); errorMessage=reason==null?"Scan failed":reason.substring(0,Math.min(500,reason.length()));}
    public UUID getId(){return id;} public UUID getAgentId(){return agentId;} public State getState(){return state;} public int getProgress(){return progress;} public int getChecked(){return checked;} public int getDiscovered(){return discovered;} public int getFailed(){return failed;} public int getNewDevices(){return newDevices;} public int getExistingDevices(){return existingDevices;} public Instant getCreatedAt(){return createdAt;} public Instant getStartedAt(){return startedAt;} public Instant getFinishedAt(){return finishedAt;} public Long getDurationMs(){return durationMs;} public String getErrorMessage(){return errorMessage;}
}
