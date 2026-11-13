package com.netmoni.server.controller;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import com.netmoni.server.dto.alert.AlertRequest;
import com.netmoni.server.dto.alert.AlertResponse;
import com.netmoni.server.dto.common.PageResponse;
import com.netmoni.server.entity.AlertSeverity;
import com.netmoni.server.entity.AlertStatus;
import com.netmoni.server.service.AlertService;
import org.springframework.security.access.prepost.PreAuthorize;
@RestController @RequestMapping("/api/alerts")
public class AlertController {
	private final AlertService service;
	public AlertController(AlertService service){this.service=service;}
	@PostMapping @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')") @ResponseStatus(HttpStatus.CREATED) public AlertResponse create(@Valid @RequestBody AlertRequest request){return service.create(request);}
	@GetMapping public PageResponse<AlertResponse> list(@RequestParam(required=false) AlertStatus status,@RequestParam(required=false) AlertSeverity severity,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size){return PageResponse.from(service.find(status,severity,page,size));}
	@PatchMapping("/{id}/read") public AlertResponse read(@PathVariable UUID id){return service.markRead(id);}
	@PatchMapping("/{id}/resolve") @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')") public AlertResponse resolve(@PathVariable UUID id){return service.resolve(id);}
}
