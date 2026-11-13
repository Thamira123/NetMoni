package com.netmoni.agent.communication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public final class MessageOutbox {
    private static final int MAX_MESSAGES = 10_000;
    private final Path file;
    private final ObjectMapper objectMapper;

    public MessageOutbox(Path file, ObjectMapper objectMapper) throws IOException {
        this.file = file;
        this.objectMapper = objectMapper;
        Files.createDirectories(file.toAbsolutePath().getParent());
        if (!Files.exists(file)) {
            Files.createFile(file);
        }
        readAll();
    }

    public synchronized void add(JsonNode message) throws IOException {
        List<String> lines = Files.readAllLines(file);
        if (lines.size() >= MAX_MESSAGES) {
            throw new IOException("Monitoring outbox is full; refusing to discard unsent results");
        }
        Files.writeString(file, objectMapper.writeValueAsString(message) + System.lineSeparator(),
                java.nio.file.StandardOpenOption.APPEND);
    }

    public synchronized JsonNode peek() throws IOException {
        List<JsonNode> messages = readAll();
        return messages.isEmpty() ? null : messages.get(0);
    }

    public synchronized void removeFirst() throws IOException {
        List<JsonNode> messages = readAll();
        if (!messages.isEmpty()) {
            writeAll(messages.subList(1, messages.size()));
        }
    }

    private List<JsonNode> readAll() throws IOException {
        List<JsonNode> messages = new ArrayList<>();
        int lineNumber = 0;
        for (String line : Files.readAllLines(file)) {
            lineNumber++;
            if (line.isBlank()) continue;
            try {
                messages.add(objectMapper.readTree(line));
            } catch (IOException exception) {
                throw new IOException("Invalid outbox JSON at line " + lineNumber, exception);
            }
        }
        if (messages.size() > MAX_MESSAGES) {
            throw new IOException("Outbox contains more than " + MAX_MESSAGES + " messages");
        }
        return messages;
    }

    private void writeAll(List<JsonNode> messages) throws IOException {
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        List<String> lines = new ArrayList<>(messages.size());
        for (JsonNode message : messages) {
            lines.add(objectMapper.writeValueAsString(message));
        }
        Files.write(temporary, lines);
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
