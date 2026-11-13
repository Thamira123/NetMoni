package com.netmoni.agent.monitoring.snmp;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class SnmpMonitorTest {
    @Test
    void reportsMissingCommunityWithoutOpeningANetworkSession() {
        var monitor = new SnmpMonitor("2c", 161, "", 100, new ObjectMapper());

        var result = monitor.check("127.0.0.1");

        assertEquals("SNMP", result.checkType());
        assertFalse(result.available());
        assertEquals("SNMP community is not configured", result.errorMessage());
    }
}
