package com.netmoni.server.socket;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.netmoni.server.dto.agent.AgentRegistrationRequest;
import com.netmoni.server.dto.agent.AgentResponse;
import com.netmoni.server.dto.agent.AgentTcpMessage;
import com.netmoni.server.dto.agent.AgentTcpResponse;
import com.netmoni.server.service.AgentNotFoundException;
import com.netmoni.server.service.AgentRegistrationService;
import com.netmoni.server.service.MonitoringResultService;
import com.netmoni.server.dto.monitoring.MonitoringResultRequest;

@Component
public class AgentTcpServer {

	private static final Logger logger = LoggerFactory.getLogger(AgentTcpServer.class);

	private final ObjectMapper objectMapper;
	private final AgentRegistrationService agentRegistrationService;
	private final MonitoringResultService monitoringResultService;
	private final String host;
	private final int port;
	private final AtomicBoolean running = new AtomicBoolean(false);
	private final ExecutorService clientExecutor = Executors.newVirtualThreadPerTaskExecutor();
	private ServerSocket serverSocket;

	public AgentTcpServer(
			ObjectMapper objectMapper,
			AgentRegistrationService agentRegistrationService,
			MonitoringResultService monitoringResultService,
			@Value("${netmoni.agent-server.host:127.0.0.1}") String host,
			@Value("${netmoni.agent-server.port:9001}") int port) {
		this.objectMapper = objectMapper;
		this.agentRegistrationService = agentRegistrationService;
		this.monitoringResultService = monitoringResultService;
		this.host = host;
		this.port = port;
	}

	@PostConstruct
	public void start() throws IOException {
		serverSocket = new ServerSocket();
		serverSocket.bind(new InetSocketAddress(host, port));
		running.set(true);

		Thread.ofVirtual().name("agent-tcp-listener").start(this::acceptConnections);
		logger.info("Agent TCP server listening on {}:{}", host, port);
	}

	private void acceptConnections() {
		while (running.get()) {
			try {
				Socket client = serverSocket.accept();
				clientExecutor.submit(() -> handleClient(client));
			} catch (IOException exception) {
				if (running.get()) {
					logger.error("Could not accept agent TCP connection", exception);
				}
			}
		}
	}

	private void handleClient(Socket client) {
		try (client;
				BufferedReader reader = new BufferedReader(
						new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
				BufferedWriter writer = new BufferedWriter(
						new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				try {
					AgentTcpMessage message = objectMapper.readValue(line, AgentTcpMessage.class);
					writeResponse(writer, processMessage(message));
				} catch (JacksonException exception) {
					logger.warn("Received invalid JSON from agent at {}", client.getRemoteSocketAddress());
					writeResponse(writer, AgentTcpResponse.error("invalid_json"));
				} catch (IOException exception) {
					logger.debug("Agent TCP connection closed: {}", exception.getMessage());
					return;
				}
			}
		} catch (IOException exception) {
			logger.debug("Agent TCP connection closed: {}", exception.getMessage());
		}
	}

	private AgentTcpResponse processMessage(AgentTcpMessage message) {
		if (message == null || message.type() == null) {
			return AgentTcpResponse.error("message_type_is_required");
		}

		try {
			if ("agent.register".equals(message.type())) {
				AgentRegistrationRequest request = new AgentRegistrationRequest(
						message.agentId(), message.name(), message.segmentCidr(), message.version());
				AgentResponse agent = agentRegistrationService.registerAgent(request);
				logger.info("Registered agent id={} name={}", agent.agentId(), agent.name());
				return AgentTcpResponse.registered(agent);
			}
			if ("agent.heartbeat".equals(message.type())) {
				AgentResponse agent = agentRegistrationService.recordHeartbeat(message.agentId());
				return AgentTcpResponse.heartbeat(agent);
			}
			if ("monitoring.result".equals(message.type())) {
				var result=monitoringResultService.submit(new MonitoringResultRequest(message.deviceId(),message.agentId(),message.checkType(),message.responseTimeMs(),Boolean.TRUE.equals(message.available()),message.packetLossPercent(),message.portNumber(),message.portOpen(),message.snmpData(),message.checkedAt(),message.errorMessage()));
				return AgentTcpResponse.monitoringResult(message.agentId(),result.resultId());
			}
			return AgentTcpResponse.error("unsupported_message_type");
		} catch (AgentNotFoundException exception) {
			return AgentTcpResponse.error("agent_not_registered");
		} catch (IllegalArgumentException exception) {
			return AgentTcpResponse.error("invalid_agent_registration");
		} catch (RuntimeException exception) {
			logger.error("Could not process agent TCP message type={}",message.type(),exception);
			return AgentTcpResponse.error("message_processing_failed");
		}
	}

	private void writeResponse(BufferedWriter writer, AgentTcpResponse response) throws IOException {
		writer.write(objectMapper.writeValueAsString(response));
		writer.newLine();
		writer.flush();
	}

	@PreDestroy
	public void stop() {
		running.set(false);
		if (serverSocket != null) {
			try {
				serverSocket.close();
			} catch (IOException exception) {
				logger.debug("Error while closing agent TCP server", exception);
			}
		}
		clientExecutor.shutdownNow();
	}
}
