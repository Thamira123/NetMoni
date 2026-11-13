package com.netmoni.server.security;

import java.util.List;
import java.util.Map;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.netmoni.server.service.JwtService;

@Component
public final class JwtStompChannelInterceptor implements ChannelInterceptor {
	private final JwtService jwtService;

	public JwtStompChannelInterceptor(JwtService jwtService) {
		this.jwtService = jwtService;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		if (accessor == null) {
			throw new AccessDeniedException("A valid STOMP message is required");
		}
		StompCommand command = accessor.getCommand();
		if (command == StompCommand.CONNECT) {
			authenticate(accessor);
		} else if (command != StompCommand.DISCONNECT && accessor.getUser() == null) {
			throw new AccessDeniedException("A valid STOMP connection is required");
		}
		if (command == StompCommand.SEND) {
			throw new AccessDeniedException("Client event publishing is not supported");
		}
		if (command == StompCommand.SUBSCRIBE
				&& (accessor.getDestination() == null || !accessor.getDestination().startsWith("/topic/"))) {
			throw new AccessDeniedException("Only documented event topics can be subscribed to");
		}
		return message;
	}

	private void authenticate(StompHeaderAccessor accessor) {
		String authorization = accessor.getFirstNativeHeader("Authorization");
		if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
			throw new AccessDeniedException("A bearer token is required for the STOMP connection");
		}

		Map<String, Object> claims = jwtService.validate(authorization.substring(7).trim());
		if (claims == null) {
			throw new AccessDeniedException("The STOMP bearer token is invalid or expired");
		}

		String username = (String) claims.get("sub");
		String role = (String) claims.get("role");
		if (!List.of("ADMIN", "OPERATOR", "VIEWER").contains(role)) {
			throw new AccessDeniedException("The STOMP bearer token has an invalid role");
		}

		accessor.setUser(new UsernamePasswordAuthenticationToken(username, null,
				List.of(new SimpleGrantedAuthority("ROLE_" + role))));
	}
}
