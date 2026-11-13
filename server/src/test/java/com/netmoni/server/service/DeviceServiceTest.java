package com.netmoni.server.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.netmoni.server.dto.device.DeviceRequest;
import com.netmoni.server.dto.agent.DiscoveredDeviceRequest;
import com.netmoni.server.entity.Agent;
import com.netmoni.server.entity.Device;
import com.netmoni.server.entity.DeviceStatus;
import com.netmoni.server.repository.AgentRepository;
import com.netmoni.server.repository.DeviceRepository;
import com.netmoni.server.repository.AgentNetworkRepository;
@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {
	@Mock DeviceRepository devices; @Mock AgentRepository agents; @Mock NetMoniEventPublisher events;
	@Mock AgentNetworkRepository agentNetworks;
	@InjectMocks DeviceService service;
	@Test void createsDeviceLinkedToAgent(){UUID agentId=UUID.randomUUID();Agent agent=new Agent(agentId,"agent","10.0.0.0/24","1",Instant.now());
		when(agents.findById(agentId)).thenReturn(Optional.of(agent));when(devices.findByAgent_IdAndIpAddress(agentId,"10.0.0.1")).thenReturn(Optional.empty());when(devices.save(any(Device.class))).thenAnswer(i->i.getArgument(0));
		var response=service.create(new DeviceRequest("router","10.0.0.1","gw","router","lab",agentId));
		assertEquals(agentId,response.agentId());assertEquals("10.0.0.1",response.ipAddress());assertEquals(DeviceStatus.UNKNOWN,response.status());}
	@Test void rejectsInvalidIpv4(){assertThrows(IllegalArgumentException.class,()->service.create(new DeviceRequest("bad","300.1.1.1",null,"router",null,UUID.randomUUID())));verifyNoInteractions(agents,devices);}
	@Test void registersDiscoveredDeviceAndReturnsStableIdForMonitoring(){UUID agentId=UUID.randomUUID();Agent agent=new Agent(agentId,"agent","10.0.0.0/24","1",Instant.now());
		when(agents.findById(agentId)).thenReturn(Optional.of(agent));when(devices.findByAgent_IdAndIpAddress(agentId,"10.0.0.20")).thenReturn(Optional.empty());when(devices.save(any(Device.class))).thenAnswer(i->i.getArgument(0));
		var response=service.registerDiscovered(agentId,List.of(new DiscoveredDeviceRequest("10.0.0.20","printer",Instant.now())));
		assertEquals(1,response.size());assertEquals("10.0.0.20",response.getFirst().ipAddress());assertNotNull(response.getFirst().deviceId());
		verify(devices).save(argThat(device->device.getAgent().getId().equals(agentId)&&device.getStatus()==DeviceStatus.UNKNOWN));
		verify(events).publish(eq("/topic/devices"),eq("device.discovered"),any());}
	@Test void repeatedDiscoveryReturnsExistingDeviceWithoutCreatingADuplicate(){UUID agentId=UUID.randomUUID();Agent agent=new Agent(agentId,"agent","10.0.0.0/24","1",Instant.now());UUID deviceId=UUID.randomUUID();Device device=new Device(deviceId,"printer","10.0.0.20","printer", "UNKNOWN",null,agent);
		when(agents.findById(agentId)).thenReturn(Optional.of(agent));when(devices.findByAgent_IdAndIpAddress(agentId,"10.0.0.20")).thenReturn(Optional.of(device));when(devices.save(any(Device.class))).thenAnswer(i->i.getArgument(0));
		var response=service.registerDiscovered(agentId,List.of(new DiscoveredDeviceRequest("10.0.0.20","printer",Instant.now()),new DiscoveredDeviceRequest("10.0.0.20","printer",Instant.now())));
		assertEquals(1,response.size());assertEquals(deviceId,response.getFirst().deviceId());assertFalse(response.getFirst().created());verify(devices).save(device);verify(events).publish(eq("/topic/devices"),eq("device.discovered"),any());}
	@Test void rejectsDiscoveredAddressOutsideAgentSubnet(){UUID agentId=UUID.randomUUID();Agent agent=new Agent(agentId,"agent","10.0.0.0/24","1",Instant.now());
		when(agents.findById(agentId)).thenReturn(Optional.of(agent));
		assertThrows(IllegalArgumentException.class,()->service.registerDiscovered(agentId,List.of(new DiscoveredDeviceRequest("10.0.1.20",null,Instant.now()))));
		verify(devices,never()).save(any());}
}
