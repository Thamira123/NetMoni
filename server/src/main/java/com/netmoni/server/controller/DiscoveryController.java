package com.netmoni.server.controller;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.netmoni.server.dto.discovery.DiscoveryScanResponse;
import com.netmoni.server.dto.device.DeviceResponse;
import com.netmoni.server.service.*;
@RestController @RequestMapping("/api/discovery/agents/{agentId}")
public class DiscoveryController {
 private final DiscoveryScanService scans; private final DeviceService devices;
 public DiscoveryController(DiscoveryScanService scans,DeviceService devices){this.scans=scans;this.devices=devices;}
 @PostMapping("/scan") @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
 public ResponseEntity<DiscoveryScanResponse> start(@PathVariable UUID agentId){return ResponseEntity.accepted().body(scans.start(agentId));}
 @GetMapping("/scan") public ResponseEntity<DiscoveryScanResponse> latest(@PathVariable UUID agentId){var result=scans.latest(agentId);return result==null?ResponseEntity.noContent().build():ResponseEntity.ok(result);}
 @GetMapping("/scans") public List<DiscoveryScanResponse> history(@PathVariable UUID agentId){return scans.history(agentId);}
 @GetMapping("/devices") public List<DeviceResponse> devices(@PathVariable UUID agentId){return devices.findByAgent(agentId);}
}
