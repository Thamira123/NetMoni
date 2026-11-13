package com.netmoni.server.service;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.netmoni.server.dto.agent.AgentNetworkRequest;
import com.netmoni.server.entity.Agent;
import com.netmoni.server.repository.*;
class AgentNetworkServiceTest {
 @Test void onlyAllowsReportedNetworksInsideServerConfiguredAgentRanges(){
  UUID id=UUID.randomUUID();Agent agent=new Agent(id,"edge","10.0.0.0/24","1",Instant.now());
  AgentNetworkRepository nets=mock(AgentNetworkRepository.class);AgentRepository agents=mock(AgentRepository.class);
  when(agents.findById(id)).thenReturn(Optional.of(agent));when(nets.findByAgent_IdAndInterfaceNameAndCidr(eq(id),anyString(),anyString())).thenReturn(Optional.empty());when(nets.save(any())).thenAnswer(i->i.getArgument(0));
  AgentNetworkService service=new AgentNetworkService(nets,agents,mock(DeviceRepository.class),mock(DiscoveryScanRepository.class),mock(NetMoniEventPublisher.class));
  service.synchronize(id,List.of(new AgentNetworkRequest("eth0","Ethernet","ETHERNET","UP","10.0.0.4","10.0.0.0/24","255.255.255.0","10.0.0.255",null,"00:11:22:33:44:55",null,null,true,true,Instant.now()),new AgentNetworkRequest("wlan0","Wi-Fi","WIFI","UP","192.168.2.4","192.168.2.0/24","255.255.255.0","192.168.2.255",null,null,null,null,true,true,Instant.now())));
  verify(nets,times(2)).save(argThat(n->n.isAuthorized()==n.getCidr().startsWith("10.")&&n.isScanEligible()==n.isAuthorized()));
 }
}
