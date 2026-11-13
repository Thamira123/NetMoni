package com.netmoni.server.service;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.netmoni.server.dto.monitoring.MonitoringResultRequest;
import com.netmoni.server.entity.*;
import com.netmoni.server.repository.*;
@ExtendWith(MockitoExtension.class)
class MonitoringResultServiceTest {
	@Mock MonitoringResultRepository results; @Mock DeviceRepository devices; @Mock AgentRepository agents; @Mock NetMoniEventPublisher events;
	@Mock AlertService alertService;
	MonitoringResultService service;
	@BeforeEach void setUp(){service=new MonitoringResultService(results,devices,agents,events,alertService,1000);}
	@Test void submittedUnavailableCheckMarksDeviceOffline(){UUID aid=UUID.randomUUID(),did=UUID.randomUUID();Agent agent=new Agent(aid,"agent","10.0.0.0/24","1",Instant.now());Device device=new Device(did,"router","10.0.0.1",null,"router",null,agent);
		when(devices.findById(did)).thenReturn(Optional.of(device));when(agents.findById(aid)).thenReturn(Optional.of(agent));when(results.save(any())).thenAnswer(i->i.getArgument(0));
		var response=service.submit(new MonitoringResultRequest(did,aid,"ICMP",15L,false,null,null,null,null,Instant.now(),"timeout"));
		assertFalse(response.available());assertEquals(DeviceStatus.OFFLINE,device.getStatus());verify(events).publish(eq("/topic/monitoring"),eq("monitoring.result.created"),any());}
	@Test void rejectsResultFromAgentNotAssignedToDevice(){UUID aid=UUID.randomUUID(),other=UUID.randomUUID(),did=UUID.randomUUID();Agent owner=new Agent(aid,"agent","10.0.0.0/24","1",Instant.now());Device device=new Device(did,"router","10.0.0.1",null,"router",null,owner);Agent wrong=new Agent(other,"other","10.0.1.0/24","1",Instant.now());
		when(devices.findById(did)).thenReturn(Optional.of(device));when(agents.findById(other)).thenReturn(Optional.of(wrong));
		assertThrows(IllegalArgumentException.class,()->service.submit(new MonitoringResultRequest(did,other,"ICMP",1L,true,null,null,null,null,null,null)));verifyNoInteractions(results);}
}
