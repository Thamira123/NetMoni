package com.netmoni.agent.monitoring.tcp;

import static org.junit.jupiter.api.Assertions.*;
import java.net.ServerSocket;
import org.junit.jupiter.api.Test;

class TcpPortMonitorTest {
    @Test
    void detectsListeningAndClosedLocalPorts() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            var monitor = new TcpPortMonitor(500);
            var open = monitor.check("127.0.0.1", server.getLocalPort());
            assertTrue(open.available());
            assertEquals(Boolean.TRUE, open.portOpen());
        }
        try (ServerSocket reserved = new ServerSocket(0)) {
            int closedPort = reserved.getLocalPort();
            reserved.close();
            var closed = new TcpPortMonitor(500).check("127.0.0.1", closedPort);
            assertFalse(closed.available());
            assertEquals(Boolean.FALSE, closed.portOpen());
        }
    }
}
