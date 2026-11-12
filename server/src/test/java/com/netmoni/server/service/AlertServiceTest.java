package com.netmoni.server.service;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import com.netmoni.server.dto.alert.AlertRequest;
import com.netmoni.server.entity.*;
import com.netmoni.server.repository.AlertRepository;
@ExtendWith(MockitoExtension.class)
class AlertServiceTest {
	@Mock AlertRepository alerts; @Mock NetMoniEventPublisher events; @Mock ObjectProvider<AlertEmailNotifier> email;
	@InjectMocks AlertService service;
	@Test void returnsExistingUnresolvedAlertInsteadOfDuplicating(){Alert existing=new Alert("device:1:down","DEVICE_UNAVAILABLE",AlertSeverity.CRITICAL,"down",UUID.randomUUID(),UUID.randomUUID());
		when(alerts.findFirstByDeduplicationKeyAndStatusNot("device:1:down",AlertStatus.RESOLVED)).thenReturn(Optional.of(existing));
		assertEquals(AlertStatus.UNREAD,service.create(new AlertRequest("device:1:down","DEVICE_UNAVAILABLE",AlertSeverity.CRITICAL,"down",null,null)).status());verify(alerts,never()).save(any());}
}
