package com.netmoni.server.repository;

import java.util.List;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.netmoni.server.entity.Device;
import com.netmoni.server.entity.DeviceStatus;

public interface DeviceRepository extends JpaRepository<Device, UUID> {
	List<Device> findByAgent_Id(UUID agentId);
	Optional<Device> findByAgent_IdAndIpAddress(UUID agentId, String ipAddress);
	long countByStatus(DeviceStatus status);
	long countByMonitoringEnabledTrue();
}
