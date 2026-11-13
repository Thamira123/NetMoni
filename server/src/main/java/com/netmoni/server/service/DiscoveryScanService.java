package com.netmoni.server.service;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.netmoni.server.entity.*;
import com.netmoni.server.repository.*;
import com.netmoni.server.dto.discovery.DiscoveryScanResponse;

@Service
public class DiscoveryScanService {
 private final DiscoveryScanRepository scans; private final AgentRepository agents; private final NetMoniEventPublisher events;
 public DiscoveryScanService(DiscoveryScanRepository scans,AgentRepository agents,NetMoniEventPublisher events){this.scans=scans;this.agents=agents;this.events=events;}
 @Transactional
 public synchronized DiscoveryScanResponse start(UUID agentId){
  Agent a=agents.findById(agentId).orElseThrow(()->new AgentNotFoundException(agentId));
  if(a.getStatus()!=AgentStatus.ONLINE)throw new IllegalStateException("Agent is offline; scan was not queued");
  if(scans.existsByAgentIdAndStateIn(agentId,List.of(DiscoveryScan.State.QUEUED,DiscoveryScan.State.RUNNING)))throw new IllegalStateException("A scan is already queued or running for this agent");
  DiscoveryScan scan=scans.save(new DiscoveryScan(agentId)); var dto=DiscoveryScanResponse.from(scan); events.publish("/topic/discovery","discovery.started",dto); return dto;
 }
 @Transactional
 public DiscoveryScanResponse latest(UUID agentId){if(!agents.existsById(agentId))throw new AgentNotFoundException(agentId);return scans.findFirstByAgentIdOrderByCreatedAtDesc(agentId).map(DiscoveryScanResponse::from).orElse(null);}
 @Transactional
 public List<DiscoveryScanResponse> history(UUID agentId){if(!agents.existsById(agentId))throw new AgentNotFoundException(agentId);return scans.findAll().stream().filter(s->s.getAgentId().equals(agentId)).sorted(Comparator.comparing(DiscoveryScan::getCreatedAt).reversed()).limit(20).map(DiscoveryScanResponse::from).toList();}
 @Transactional
 public UUID poll(UUID agentId){return scans.findFirstByAgentIdAndStateOrderByCreatedAtAsc(agentId,DiscoveryScan.State.QUEUED).map(DiscoveryScan::getId).orElse(null);}
 @Transactional
 public void started(UUID agentId,UUID scanId){mutate(agentId,scanId,"discovery.started",DiscoveryScan::start);}
 @Transactional
 public void progress(UUID agentId,UUID scanId,int percent,int checked,int discovered,int failed,int newDevices,int existingDevices){mutate(agentId,scanId,"discovery.progress",s->s.progress(percent,checked,discovered,failed,newDevices,existingDevices));}
 @Transactional
 public void complete(UUID agentId,UUID scanId,int checked,int discovered,int failed,int newDevices,int existingDevices,long duration){mutate(agentId,scanId,"discovery.completed",s->s.complete(checked,discovered,failed,newDevices,existingDevices,duration));}
 @Transactional
 public void fail(UUID agentId,UUID scanId,String reason){mutate(agentId,scanId,"discovery.failed",s->s.fail(reason));}
 private void mutate(UUID agentId,UUID scanId,String event,java.util.function.Consumer<DiscoveryScan> action){
  if(agentId==null||scanId==null)throw new IllegalArgumentException("agentId and scanId are required");
  DiscoveryScan scan=scans.findByIdAndAgentId(scanId,agentId).orElseThrow(()->new IllegalArgumentException("Unknown scan id for agent"));
  action.accept(scan); var saved=scans.save(scan); events.publish("/topic/discovery",event,DiscoveryScanResponse.from(saved));
 }
}
