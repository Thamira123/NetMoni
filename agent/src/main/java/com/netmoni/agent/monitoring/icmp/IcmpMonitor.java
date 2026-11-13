package com.netmoni.agent.monitoring.icmp;

import com.netmoni.agent.model.MonitoringResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetAddress;
import java.time.Instant;

public final class IcmpMonitor {
    @FunctionalInterface
    public interface ReachabilityProbe {
        boolean isReachable(String ipAddress, int timeoutMs) throws Exception;
    }

    private final int timeoutMs;
    private final int attempts;
    private final ReachabilityProbe probe;

    public IcmpMonitor(int timeoutMs, int attempts) {
        this(timeoutMs, attempts, (ipAddress, timeout) -> InetAddress.getByName(ipAddress).isReachable(timeout));
    }

    public IcmpMonitor(int timeoutMs, int attempts, ReachabilityProbe probe) {
        if (timeoutMs < 1 || attempts < 1) {
            throw new IllegalArgumentException("ICMP timeout and attempts must be positive");
        }
        this.timeoutMs = timeoutMs;
        this.attempts = attempts;
        this.probe = probe;
    }

    public MonitoringResult check(String ipAddress) {
        int successful = 0;
        long elapsedTotal = 0;
        String error = null;
        try {
            for (int attempt = 0; attempt < attempts; attempt++) {
                long started = System.nanoTime();
                if (probe.isReachable(ipAddress, timeoutMs)) {
                    successful++;
                    elapsedTotal += (System.nanoTime() - started) / 1_000_000;
                } else {
                    error = "No response within " + timeoutMs + " ms";
                }
            }
        } catch (Exception exception) {
            error = exception.getMessage();
        }
        BigDecimal packetLoss = BigDecimal.valueOf((attempts - successful) * 100.0 / attempts)
                .setScale(2, RoundingMode.HALF_UP);
        Long responseTime = successful == 0 ? null : elapsedTotal / successful;
        return new MonitoringResult("ICMP", responseTime, successful > 0, packetLoss, null, null, null,
                Instant.now(), successful == 0 ? error : null);
    }
}
