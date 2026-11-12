package com.netmoni.server.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.netmoni.server.entity.MonitoringResult;

public interface MonitoringResultRepository extends JpaRepository<MonitoringResult, UUID> {
	List<MonitoringResult> findByDevice_IdAndCheckedAtBetweenOrderByCheckedAtDesc(UUID deviceId, Instant from, Instant to, Pageable pageable);
	List<MonitoringResult> findByAgent_IdAndCheckedAtBetweenOrderByCheckedAtDesc(UUID agentId, Instant from, Instant to, Pageable pageable);
	List<MonitoringResult> findByDevice_IdOrderByCheckedAtDesc(UUID deviceId, Pageable pageable);
	List<MonitoringResult> findByAgent_IdOrderByCheckedAtDesc(UUID agentId, Pageable pageable);
	List<MonitoringResult> findAllByOrderByCheckedAtDesc(Pageable pageable);
	long countByDevice_IdAndCheckedAtBetween(UUID deviceId, Instant from, Instant to);
	long countByDevice_IdAndAvailableTrueAndCheckedAtBetween(UUID deviceId, Instant from, Instant to);
	long countByCheckedAtAfter(Instant from);
	long countByAvailableTrueAndCheckedAtAfter(Instant from);
}
