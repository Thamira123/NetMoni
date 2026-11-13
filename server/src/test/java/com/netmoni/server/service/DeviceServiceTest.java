package com.netmoni.server.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.netmoni.server.dto.device.DeviceRequest;
import com.netmoni.server.entity.Agent;
import com.netmoni.server.entity.Device;
import com.netmoni.server.entity.DeviceStatus;
import com.netmoni.server.repository.AgentRepository;
import com.netmoni.server.repository.DeviceRepository;
@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {
	@Mock DeviceRepository devices; @Mock AgentRepository agents; @Mock NetMoniEventPublisher events;
	@InjectMocks DeviceService service;
	@Test void createsDeviceLinkedToAgent(){UUID agentId=UUID.randomUUID();Agent agent=new Agent(agentId,"agent","10.0.0.0/24","1",Instant.now());
		when(agents.findById(agentId)).thenReturn(Optional.of(agent));when(devices.save(any(Device.class))).thenAnswer(i->i.getArgument(0));
		var response=service.create(new DeviceRequest("router","10.0.0.1","gw","router","lab",agentId));
		assertEquals(agentId,response.agentId());assertEquals("10.0.0.1",response.ipAddress());assertEquals(DeviceStatus.UNKNOWN,response.status());}
	@Test void rejectsInvalidIpv4(){assertThrows(IllegalArgumentException.class,()->service.create(new DeviceRequest("bad","300.1.1.1",null,"router",null,UUID.randomUUID())));verifyNoInteractions(agents,devices);}
}
