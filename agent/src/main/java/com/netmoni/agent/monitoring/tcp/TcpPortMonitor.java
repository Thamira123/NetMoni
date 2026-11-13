package com.netmoni.agent.monitoring.tcp;

import com.netmoni.agent.model.MonitoringResult;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;

public final class TcpPortMonitor {
    private final int timeoutMs;

    public TcpPortMonitor(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public MonitoringResult check(String ipAddress, int port) {
        long started = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ipAddress, port), timeoutMs);
            long elapsed = (System.nanoTime() - started) / 1_000_000;
            return new MonitoringResult("TCP", elapsed, true, null, port, true, null,
                    Instant.now(), null);
        } catch (Exception exception) {
            long elapsed = (System.nanoTime() - started) / 1_000_000;
            return new MonitoringResult("TCP", elapsed, false, null, port, false, null,
                    Instant.now(), exception.getMessage());
        }
    }
}
