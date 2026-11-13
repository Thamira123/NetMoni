package com.netmoni.agent.communication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class BackendClient {
    private final String host;
    private final int port;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final ObjectMapper objectMapper;

    public BackendClient(String host, int port, int connectTimeoutMs, int readTimeoutMs, ObjectMapper objectMapper) {
        this.host = host;
        this.port = port;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.objectMapper = objectMapper;
    }

    public JsonNode send(JsonNode message, String expectedType) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
            socket.setSoTimeout(readTimeoutMs);
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                writer.write(objectMapper.writeValueAsString(message));
                writer.newLine();
                writer.flush();
                String line = reader.readLine();
                if (line == null) {
                    throw new IOException("Backend closed the connection without an acknowledgement");
                }
                JsonNode response = objectMapper.readTree(line);
                if (response == null || !response.isObject()) {
                    throw new IOException("Backend returned an empty or invalid acknowledgement");
                }
                if (response.hasNonNull("message") || "error".equals(response.path("type").asText())) {
                    throw new IOException("Backend rejected message: " + response.path("message").asText());
                }
                if (!expectedType.equals(response.path("type").asText())) {
                    throw new IOException("Unexpected backend acknowledgement type: " + response.path("type").asText());
                }
                String agentId = message.path("agentId").asText(null);
                if (agentId == null || agentId.isBlank()) {
                    throw new IOException("Agent message is missing agentId");
                }
                if (!response.hasNonNull("agentId") || !agentId.equals(response.path("agentId").asText())) {
                    throw new IOException("Backend acknowledgement has a different agentId");
                }
                if ("monitoring.result.accepted".equals(expectedType) && !response.hasNonNull("resultId")) {
                    throw new IOException("Backend monitoring acknowledgement is missing resultId");
                }
                return response;
            }
        }
    }
}
