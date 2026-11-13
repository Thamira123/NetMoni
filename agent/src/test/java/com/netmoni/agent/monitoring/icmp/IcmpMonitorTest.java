package com.netmoni.agent.monitoring.icmp;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class IcmpMonitorTest {
    @Test
    void calculatesPacketLossAndAveragesSuccessfulProbeLatency() {
        AtomicInteger probes = new AtomicInteger();
        IcmpMonitor monitor = new IcmpMonitor(250, 4, (ip, timeout) -> probes.incrementAndGet() <= 3);

        var result = monitor.check("192.0.2.10");

        assertEquals(4, probes.get());
        assertTrue(result.available());
        assertEquals(new BigDecimal("25.00"), result.packetLossPercent());
        assertNotNull(result.responseTimeMs());
        assertNull(result.errorMessage());
    }

    @Test
    void reportsAllLostProbesAndRetainsProbeFailure() {
        IcmpMonitor monitor = new IcmpMonitor(100, 2, (ip, timeout) -> false);

        var result = monitor.check("192.0.2.10");

        assertFalse(result.available());
        assertEquals(new BigDecimal("100.00"), result.packetLossPercent());
        assertNull(result.responseTimeMs());
        assertNotNull(result.errorMessage());
    }

    @Test
    void isolatesProbeExceptionsAsUnavailableResults() {
        IcmpMonitor monitor = new IcmpMonitor(100, 1, (ip, timeout) -> {
            throw new java.io.IOException("permission denied");
        });

        var result = monitor.check("192.0.2.10");

        assertFalse(result.available());
        assertEquals("permission denied", result.errorMessage());
    }
}
