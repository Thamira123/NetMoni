package com.netmoni.server.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.netmoni.server.entity.*;
import com.netmoni.server.repository.*;

class DiscoveryScanServiceTest {
 @Test void queuesScanForOnlineAgentAndRejectsOverlap(){
  UUID id=UUID.randomUUID(); Agent agent=new Agent(id,"edge","10.0.0.0/24","1",Instant.now());
  AgentRepository agents=mock(AgentRepository.class); DiscoveryScanRepository scans=mock(DiscoveryScanRepository.class);
  when(agents.findById(id)).thenReturn(Optional.of(agent)); when(scans.existsByAgentIdAndStateIn(eq(id),anyCollection())).thenReturn(false);
  when(scans.save(any())).thenAnswer(call->call.getArgument(0));
  DiscoveryScanService service=new DiscoveryScanService(scans,agents,mock(NetMoniEventPublisher.class));
  var result=service.start(id);
  assertEquals("QUEUED",result.status()); assertEquals(id,result.agentId()); assertEquals(0,result.progress());
  verify(scans).save(any(DiscoveryScan.class));
  when(scans.existsByAgentIdAndStateIn(eq(id),anyCollection())).thenReturn(true);
  assertThrows(IllegalStateException.class,()->service.start(id));
 }

 @Test void refusesOfflineAgent(){
  UUID id=UUID.randomUUID(); Agent agent=new Agent(id,"edge","10.0.0.0/24","1",Instant.now()); agent.markOffline();
  AgentRepository agents=mock(AgentRepository.class); when(agents.findById(id)).thenReturn(Optional.of(agent));
  DiscoveryScanService service=new DiscoveryScanService(mock(DiscoveryScanRepository.class),agents,mock(NetMoniEventPublisher.class));
  assertThrows(IllegalStateException.class,()->service.start(id));
 }
}
