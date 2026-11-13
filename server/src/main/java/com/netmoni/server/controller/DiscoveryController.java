package com.netmoni.server.controller;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.netmoni.server.dto.discovery.*;
import com.netmoni.server.dto.device.DeviceResponse;
import com.netmoni.server.service.*;
@RestController @RequestMapping("/api/discovery")
public class DiscoveryController {
 private final DiscoveryScanService scans;private final DeviceService devices;private final AgentNetworkService networks;
 public DiscoveryController(DiscoveryScanService scans,DeviceService devices,AgentNetworkService networks){this.scans=scans;this.devices=devices;this.networks=networks;}
 @GetMapping("/networks") public List<AgentNetworkResponse> networks(){return networks.all();}
 @GetMapping("/agents/{agentId}/networks") public List<AgentNetworkResponse> agentNetworks(@PathVariable UUID agentId){return networks.byAgent(agentId);}
 @PostMapping("/agents/{agentId}/scan") @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')") public ResponseEntity<DiscoveryScanResponse> startLegacy(@PathVariable UUID agentId){return ResponseEntity.accepted().body(scans.start(agentId));}
 @PostMapping("/agents/{agentId}/networks/{networkId}/scan") @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')") public ResponseEntity<DiscoveryScanResponse> start(@PathVariable UUID agentId,@PathVariable UUID networkId){return ResponseEntity.accepted().body(scans.start(agentId,networkId));}
 @PostMapping("/scan") @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')") public ResponseEntity<List<DiscoveryScanResponse>> startMany(@RequestBody ScanRequest request){return ResponseEntity.accepted().body(scans.startMany(request.networkIds()));}
 @GetMapping("/agents/{agentId}/scan") public ResponseEntity<DiscoveryScanResponse> latest(@PathVariable UUID agentId){var result=scans.latest(agentId);return result==null?ResponseEntity.noContent().build():ResponseEntity.ok(result);}
 @GetMapping("/agents/{agentId}/scans") public List<DiscoveryScanResponse> history(@PathVariable UUID agentId){return scans.history(agentId);}
 @GetMapping("/agents/{agentId}/devices") public List<DeviceResponse> devices(@PathVariable UUID agentId){return devices.findByAgent(agentId);}
 @GetMapping("/scans/{scanId}") public DiscoveryScanResponse byId(@PathVariable UUID scanId){return scans.byId(scanId);}
 @GetMapping("/scans/{scanId}/results") public List<DeviceResponse> results(@PathVariable UUID scanId){var scan=scans.byId(scanId);return devices.findByAgentAndNetwork(scan.agentId(),scan.networkCidr());}
}
