package com.netmoni.server.service;
import java.time.Instant;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import com.netmoni.server.dto.event.NetMoniEvent;
@Component
public class NetMoniEventPublisher {
	private final SimpMessagingTemplate messages;
	public NetMoniEventPublisher(SimpMessagingTemplate messages){this.messages=messages;}
	public void publish(String destination,String event,Object data){messages.convertAndSend(destination,new NetMoniEvent(event,Instant.now(),data));}
}
