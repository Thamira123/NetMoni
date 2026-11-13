package com.netmoni.agent.communication;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.FutureTask;
import org.junit.jupiter.api.Test;

class BackendClientTest {
    @Test
    void sendsOneJsonLineAndValidatesAcknowledgement() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String agentId = UUID.randomUUID().toString();
        try (ServerSocket server = new ServerSocket(0)) {
            FutureTask<String> receivedMessage = new FutureTask<>(() -> {
                try (var socket = server.accept();
                     var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                     var writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)) {
                    String line = reader.readLine();
                    writer.write("{\"type\":\"agent.registered\",\"agentId\":\"" + agentId + "\"}\n");
                    writer.flush();
                    return line;
                }
            });
            Thread serverThread = new Thread(receivedMessage);
            serverThread.start();

            var client = new BackendClient("127.0.0.1", server.getLocalPort(), 1000, 1000, mapper);
            var response = client.send(mapper.readTree("{\"type\":\"agent.register\",\"agentId\":\""
                    + agentId + "\"}"), "agent.registered");

            assertEquals("agent.registered", response.path("type").asText());
            assertEquals(agentId, response.path("agentId").asText());
            assertEquals("agent.register", mapper.readTree(receivedMessage.get()).path("type").asText());
        }
    }

    @Test
    void rejectsAcknowledgementForDifferentAgent() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (ServerSocket server = new ServerSocket(0)) {
            Thread serverThread = new Thread(() -> {
                try (var socket = server.accept();
                     var writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)) {
                    writer.write("{\"type\":\"agent.registered\",\"agentId\":\"different\"}\n");
                    writer.flush();
                } catch (java.io.IOException ignored) { }
            });
            serverThread.start();

            var client = new BackendClient("127.0.0.1", server.getLocalPort(), 1000, 1000, mapper);
            var message = mapper.readTree("{\"type\":\"agent.register\",\"agentId\":\"expected\"}");
            assertThrows(java.io.IOException.class, () -> client.send(message, "agent.registered"));
            serverThread.join();
        }
    }
}
