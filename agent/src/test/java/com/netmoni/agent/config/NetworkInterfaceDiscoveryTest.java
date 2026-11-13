package com.netmoni.agent.config;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
class NetworkInterfaceDiscoveryTest {
 @Test void neverMarksAnInterfaceAsScannableWithoutAnExplicitAllowedCidr(){
  var networks=NetworkInterfaceDiscovery.discover(List.of());
  assertTrue(networks.stream().noneMatch(n->n.scanEligible()));
 }
 @Test void loopbackIsNotExposedAsAnAvailableScanNetwork(){
  var networks=NetworkInterfaceDiscovery.discover(List.of(Ipv4Cidr.parse("127.0.0.1/32")));
  assertTrue(networks.stream().noneMatch(n->n.address()!=null&&n.address().startsWith("127.")));
 }
}
