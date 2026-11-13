package com.netmoni.server.repository;

import java.util.List;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import com.netmoni.server.entity.Device;
import com.netmoni.server.entity.DeviceStatus;

public interface DeviceRepository extends JpaRepository<Device, UUID>,JpaSpecificationExecutor<Device> {
	List<Device> findByAgent_Id(UUID agentId);
	Optional<Device> findByAgent_IdAndIpAddress(UUID agentId, String ipAddress);
	long countByStatus(DeviceStatus status);
	long countByMonitoringEnabledTrue();
	List<Device> findByAgent_IdAndMonitoringEnabledTrue(UUID agentId);
	long countByAgent_Id(java.util.UUID agentId);
	long countByAgent_IdAndNetworkCidr(java.util.UUID agentId,String networkCidr);
	java.util.List<Device> findByNetworkCidr(String networkCidr);
	java.util.List<Device> findByAgent_IdAndNetworkCidr(java.util.UUID agentId,String networkCidr);
}
