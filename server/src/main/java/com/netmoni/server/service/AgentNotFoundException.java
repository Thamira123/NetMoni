package com.netmoni.server.service;

import java.util.UUID;

public class AgentNotFoundException extends RuntimeException {
	public AgentNotFoundException(UUID agentId) {
		super("Agent not found: " + agentId);
	}
}
