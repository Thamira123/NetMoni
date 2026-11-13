package com.netmoni.agent.config;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class Ipv4CidrTest {
    @Test
    void parsesRangeAndEnumeratesUsableHosts() {
        Ipv4Cidr subnet = Ipv4Cidr.parse("192.168.4.17/30");
        assertEquals("192.168.4.16/30", subnet.toString());
        assertTrue(subnet.contains("192.168.4.18"));
        assertFalse(subnet.contains("192.168.5.1"));
        assertEquals(java.util.List.of("192.168.4.17", "192.168.4.18"), subnet.hosts(10));
    }

    @Test
    void rejectsUnboundedSubnetScans() {
        assertThrows(IllegalArgumentException.class, () -> Ipv4Cidr.parse("10.0.0.0/8").hosts(10_000));
        assertThrows(IllegalArgumentException.class, () -> Ipv4Cidr.parse("192.168.1.256/24"));
    }
}
