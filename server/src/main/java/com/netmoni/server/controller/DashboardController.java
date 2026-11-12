package com.netmoni.server.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.netmoni.server.dto.dashboard.DashboardSummary;
import com.netmoni.server.service.DashboardService;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
	private final DashboardService dashboard;
	public DashboardController(DashboardService dashboard) { this.dashboard=dashboard; }
	@GetMapping("/summary") public DashboardSummary summary() { return dashboard.summary(); }
}
