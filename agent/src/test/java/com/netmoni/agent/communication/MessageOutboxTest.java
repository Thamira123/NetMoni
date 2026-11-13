package com.netmoni.agent.communication;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MessageOutboxTest {
    @TempDir
    java.nio.file.Path tempDirectory;

    @Test
    void retainsMessagesAcrossRestartAndRemovesOnlyAcknowledgedHead() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var file = tempDirectory.resolve("outbox.jsonl");
        MessageOutbox outbox = new MessageOutbox(file, mapper);
        outbox.add(mapper.readTree("{\"type\":\"one\"}"));
        outbox.add(mapper.readTree("{\"type\":\"two\"}"));

        MessageOutbox restarted = new MessageOutbox(file, mapper);
        assertEquals("one", restarted.peek().path("type").asText());
        restarted.removeFirst();
        assertEquals("two", restarted.peek().path("type").asText());
        restarted.removeFirst();
        assertNull(restarted.peek());
        assertTrue(Files.exists(file));
    }
}
