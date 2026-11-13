package com.netmoni.server.config;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.config.ChannelRegistration;
import com.netmoni.server.security.JwtStompChannelInterceptor;
@Configuration @EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
	@Value("${netmoni.websocket.allowed-origin:http://localhost:5173}") private String allowedOrigin;
	private final JwtStompChannelInterceptor jwtInterceptor;
	public WebSocketConfig(JwtStompChannelInterceptor jwtInterceptor){this.jwtInterceptor=jwtInterceptor;}
	@Override public void configureMessageBroker(MessageBrokerRegistry registry){registry.enableSimpleBroker("/topic");registry.setApplicationDestinationPrefixes("/app");}
	@Override public void registerStompEndpoints(StompEndpointRegistry registry){registry.addEndpoint("/ws").setAllowedOriginPatterns(allowedOrigin);}
	@Override public void configureClientInboundChannel(ChannelRegistration registration){registration.interceptors(jwtInterceptor);}
}
