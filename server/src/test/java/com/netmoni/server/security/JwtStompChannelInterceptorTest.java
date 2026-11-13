package com.netmoni.server.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.netmoni.server.entity.AccountRole;
import com.netmoni.server.service.JwtService;

import tools.jackson.databind.ObjectMapper;

class JwtStompChannelInterceptorTest {
	private static final String SECRET = "test-secret-that-is-at-least-32-bytes-long";

	@Test
	void authenticatesConnectFromNativeBearerHeader() {
		JwtService jwt = new JwtService(SECRET, 3600, new ObjectMapper());
		JwtStompChannelInterceptor interceptor = new JwtStompChannelInterceptor(jwt);
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
		accessor.addNativeHeader("Authorization", "Bearer " + jwt.issue("viewer@example.com", AccountRole.VIEWER));
		Message<byte[]> message = message(accessor);

		Message<?> result = interceptor.preSend(message, null);

		assertNotNull(StompHeaderAccessor.wrap(result).getUser());
		assertEquals("viewer@example.com", StompHeaderAccessor.wrap(result).getUser().getName());
	}

	@Test
	void rejectsConnectWithoutBearerToken() {
		JwtStompChannelInterceptor interceptor = new JwtStompChannelInterceptor(
				new JwtService(SECRET, 3600, new ObjectMapper()));
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);

		assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message(accessor), null));
	}

	@Test
	void rejectsUnauthenticatedSubscription() {
		JwtStompChannelInterceptor interceptor = new JwtStompChannelInterceptor(
				new JwtService(SECRET, 3600, new ObjectMapper()));
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);

		assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message(accessor), null));
	}

	@Test
	void rejectsClientPublishingAndNonTopicSubscriptions() {
		JwtStompChannelInterceptor interceptor = new JwtStompChannelInterceptor(
				new JwtService(SECRET, 3600, new ObjectMapper()));
		StompHeaderAccessor send = StompHeaderAccessor.create(StompCommand.SEND);
		send.setDestination("/topic/dashboard");
		send.setUser(UsernamePasswordAuthenticationToken.authenticated("viewer", null, java.util.List.of()));
		StompHeaderAccessor subscribe = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
		subscribe.setDestination("/queue/private");
		subscribe.setUser(UsernamePasswordAuthenticationToken.authenticated("viewer", null, java.util.List.of()));

		assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message(send), null));
		assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message(subscribe), null));
	}

	private Message<byte[]> message(StompHeaderAccessor accessor) {
		accessor.setLeaveMutable(true);
		return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
	}
}
