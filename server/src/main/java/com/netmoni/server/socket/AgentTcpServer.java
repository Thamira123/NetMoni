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
import com.netmoni.server.service.DeviceService;
import com.netmoni.server.service.DiscoveryScanService;
import com.netmoni.server.service.AgentNetworkService;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class AgentTcpServer {

	private static final Logger logger = LoggerFactory.getLogger(AgentTcpServer.class);

	private final ObjectMapper objectMapper;
	private final AgentRegistrationService agentRegistrationService;
	private final MonitoringResultService monitoringResultService;
	private final DeviceService deviceService;
	private final DiscoveryScanService discoveryScanService;
	private final AgentNetworkService agentNetworkService;
	private final String host;
	private final int port;
	private final AtomicBoolean running = new AtomicBoolean(false);
	private final ExecutorService clientExecutor = Executors.newVirtualThreadPerTaskExecutor();
	private ServerSocket serverSocket;

	@Autowired
	public AgentTcpServer(
			ObjectMapper objectMapper,
			AgentRegistrationService agentRegistrationService,
			MonitoringResultService monitoringResultService,
			DeviceService deviceService,
			DiscoveryScanService discoveryScanService,
			AgentNetworkService agentNetworkService,
			@Value("${netmoni.agent-server.host:127.0.0.1}") String host,
			@Value("${netmoni.agent-server.port:9001}") int port) {
		this.objectMapper = objectMapper;
		this.agentRegistrationService = agentRegistrationService;
		this.monitoringResultService = monitoringResultService;
		this.deviceService = deviceService;
		this.discoveryScanService = discoveryScanService;
		this.agentNetworkService = agentNetworkService;
		this.host = host;
		this.port = port;
	}

	AgentTcpServer(ObjectMapper mapper, AgentRegistrationService registrations, MonitoringResultService monitoring, DeviceService devices, String host, int port) {
		this(mapper, registrations, monitoring, devices, null, null, host, port);
	}
	AgentTcpServer(ObjectMapper mapper,AgentRegistrationService registrations,MonitoringResultService monitoring,DeviceService devices,DiscoveryScanService scans,String host,int port){this(mapper,registrations,monitoring,devices,scans,null,host,port);}

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

	AgentTcpResponse processMessage(AgentTcpMessage message) {
		if (message == null || message.type() == null) {
			return AgentTcpResponse.error("message_type_is_required");
		}

		try {
			if ("agent.register".equals(message.type())) {
				AgentRegistrationRequest request = new AgentRegistrationRequest(
						message.agentId(), message.name(), message.segmentCidr(), message.version(),message.authorizedCidrs());
				AgentResponse agent = agentRegistrationService.registerAgent(request,true);
				if(agentNetworkService!=null)agentNetworkService.synchronize(message.agentId(),message.networks());
				logger.info("Registered agent id={} name={}", agent.agentId(), agent.name());
				return AgentTcpResponse.registered(agent);
			}
			if("agent.networks".equals(message.type())){agentRegistrationService.getAgent(message.agentId());agentNetworkService.synchronize(message.agentId(),message.networks());return AgentTcpResponse.networksAccepted(message.agentId());}
			if ("agent.heartbeat".equals(message.type())) {
				AgentResponse agent = agentRegistrationService.recordHeartbeat(message.agentId());
				return AgentTcpResponse.heartbeat(agent);
			}
			if ("monitoring.result".equals(message.type())) {
				var result=monitoringResultService.submit(new MonitoringResultRequest(message.deviceId(),message.agentId(),message.checkType(),message.responseTimeMs(),Boolean.TRUE.equals(message.available()),message.packetLossPercent(),message.portNumber(),message.portOpen(),message.snmpData(),message.checkedAt(),message.errorMessage()));
				return AgentTcpResponse.monitoringResult(message.agentId(),result.resultId());
			}
			if ("device.discovered".equals(message.type())) {
				var devices = deviceService.registerDiscovered(message.agentId(), message.devices());
				if (message.scanId() != null) discoveryScanService.progress(message.agentId(), message.scanId(), message.progress() == null ? 0 : message.progress(), value(message.checked()), value(message.discovered()), value(message.failed()), value(message.newDevices()), value(message.existingDevices()));
				logger.info("Registered {} discovered device(s) for agent id={}", devices.size(), message.agentId());
				return AgentTcpResponse.devicesRegistered(message.agentId(), devices);
			}
			if ("discovery.poll".equals(message.type())) {
				agentRegistrationService.getAgent(message.agentId());
				var scan = discoveryScanService.poll(message.agentId());
				return scan == null ? AgentTcpResponse.scanIdle(message.agentId()) : AgentTcpResponse.scanCommand(message.agentId(), scan);
			}
			if ("discovery.started".equals(message.type())) { discoveryScanService.started(message.agentId(), message.scanId()); return AgentTcpResponse.scanIdle(message.agentId()); }
			if ("discovery.progress".equals(message.type())) { discoveryScanService.progress(message.agentId(), message.scanId(), value(message.progress()), value(message.checked()), value(message.discovered()), value(message.failed()), value(message.newDevices()), value(message.existingDevices())); return AgentTcpResponse.scanIdle(message.agentId()); }
			if ("discovery.completed".equals(message.type())) { discoveryScanService.complete(message.agentId(), message.scanId(), value(message.checked()), value(message.discovered()), value(message.failed()), value(message.newDevices()), value(message.existingDevices()), message.durationMs() == null ? 0 : message.durationMs()); return AgentTcpResponse.scanIdle(message.agentId()); }
			if ("discovery.failed".equals(message.type())) { discoveryScanService.fail(message.agentId(), message.scanId(), message.errorMessage()); return AgentTcpResponse.scanIdle(message.agentId()); }
			return AgentTcpResponse.error("unsupported_message_type");
		} catch (AgentNotFoundException exception) {
			return AgentTcpResponse.error("agent_not_registered");
		} catch (IllegalArgumentException exception) {
			String error = "agent.register".equals(message.type()) ? "invalid_agent_registration"
					: "device.discovered".equals(message.type()) ? "invalid_device_discovery"
					: "invalid_monitoring_result";
			return AgentTcpResponse.error(error);
		} catch (RuntimeException exception) {
			logger.error("Could not process agent TCP message type={}",message.type(),exception);
			return AgentTcpResponse.error("message_processing_failed");
		}
	}
	private static int value(Integer value) { return value == null ? 0 : value; }

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
