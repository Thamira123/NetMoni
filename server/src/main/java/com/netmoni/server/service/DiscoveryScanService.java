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
 private final AgentNetworkService agentNetworks; private final AgentNetworkRepository networkRepository;
 @org.springframework.beans.factory.annotation.Autowired
 public DiscoveryScanService(DiscoveryScanRepository scans,AgentRepository agents,NetMoniEventPublisher events,AgentNetworkService agentNetworks,AgentNetworkRepository networkRepository){this.scans=scans;this.agents=agents;this.events=events;this.agentNetworks=agentNetworks;this.networkRepository=networkRepository;}
 public DiscoveryScanService(DiscoveryScanRepository scans,AgentRepository agents,NetMoniEventPublisher events){this(scans,agents,events,null,null);}
 @Transactional
 public synchronized DiscoveryScanResponse start(UUID agentId){
  Agent a=agents.findById(agentId).orElseThrow(()->new AgentNotFoundException(agentId));
  if(a.getStatus()!=AgentStatus.ONLINE)throw new IllegalStateException("Agent is offline; scan was not queued");
  if(agentNetworks!=null&&!agentNetworks.isAuthorizedRange(a,a.getSegmentCidr()))throw new IllegalStateException("Agent segment is not in its administrator-authorized CIDRs");
  if(scans.existsByAgentIdAndStateIn(agentId,List.of(DiscoveryScan.State.QUEUED,DiscoveryScan.State.RUNNING)))throw new IllegalStateException("A scan is already queued or running for this agent");
  DiscoveryScan scan=scans.save(new DiscoveryScan(agentId,null,a.getSegmentCidr())); var dto=DiscoveryScanResponse.from(scan); events.publish("/topic/discovery","discovery.started",dto); return dto;
 }
 @Transactional public synchronized DiscoveryScanResponse start(UUID agentId,UUID networkId){AgentNetwork n=agentNetworks.requireEligible(agentId,networkId);if(scans.existsByAgentIdAndNetworkIdAndStateIn(agentId,networkId,List.of(DiscoveryScan.State.QUEUED,DiscoveryScan.State.RUNNING)))throw new IllegalStateException("A scan is already queued or running for this network");DiscoveryScan scan=scans.save(new DiscoveryScan(agentId,networkId,n.getCidr()));var dto=DiscoveryScanResponse.from(scan);events.publish("/topic/discovery","discovery.started",dto);return dto;}
 @Transactional public synchronized List<DiscoveryScanResponse> startMany(List<UUID> networkIds){if(networkIds==null||networkIds.isEmpty()||networkIds.size()>64)throw new IllegalArgumentException("Select between 1 and 64 networks");List<AgentNetwork> selected=new ArrayList<>();for(UUID id:new LinkedHashSet<>(networkIds)){AgentNetwork n=networkRepository.findById(id).orElseThrow(()->new IllegalArgumentException("Network not found"));agentNetworks.requireEligible(n.getAgent().getId(),id);if(scans.existsByAgentIdAndNetworkIdAndStateIn(n.getAgent().getId(),id,List.of(DiscoveryScan.State.QUEUED,DiscoveryScan.State.RUNNING)))throw new IllegalStateException("A scan is already queued or running for network "+n.getCidr());selected.add(n);}List<DiscoveryScanResponse> results=new ArrayList<>();for(AgentNetwork n:selected){DiscoveryScan scan=scans.save(new DiscoveryScan(n.getAgent().getId(),n.getId(),n.getCidr()));var dto=DiscoveryScanResponse.from(scan);events.publish("/topic/discovery","discovery.started",dto);results.add(dto);}return List.copyOf(results);}
 @Transactional
 public DiscoveryScanResponse latest(UUID agentId){if(!agents.existsById(agentId))throw new AgentNotFoundException(agentId);return scans.findFirstByAgentIdOrderByCreatedAtDesc(agentId).map(DiscoveryScanResponse::from).orElse(null);}
 @Transactional public void failActiveForAgent(UUID agentId,String reason){for(DiscoveryScan scan:scans.findByAgentIdAndStateIn(agentId,List.of(DiscoveryScan.State.QUEUED,DiscoveryScan.State.RUNNING))){scan.fail(reason);var saved=scans.save(scan);events.publish("/topic/discovery","discovery.failed",DiscoveryScanResponse.from(saved));}}
 @Transactional
 public List<DiscoveryScanResponse> history(UUID agentId){if(!agents.existsById(agentId))throw new AgentNotFoundException(agentId);return scans.findAll().stream().filter(s->s.getAgentId().equals(agentId)).sorted(Comparator.comparing(DiscoveryScan::getCreatedAt).reversed()).limit(20).map(DiscoveryScanResponse::from).toList();}
 @Transactional
 public com.netmoni.server.dto.discovery.DiscoveryScanCommand poll(UUID agentId){return scans.findFirstByAgentIdAndStateOrderByCreatedAtAsc(agentId,DiscoveryScan.State.QUEUED).map(s->new com.netmoni.server.dto.discovery.DiscoveryScanCommand(s.getId(),s.getNetworkId(),s.getNetworkCidr())).orElse(null);}
 @Transactional(readOnly=true) public DiscoveryScanResponse byId(UUID id){return scans.findById(id).map(DiscoveryScanResponse::from).orElseThrow(()->new IllegalArgumentException("Scan not found"));}
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
