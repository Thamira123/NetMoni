package com.netmoni.server.repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.netmoni.server.entity.Alert;
import com.netmoni.server.entity.AlertSeverity;
import com.netmoni.server.entity.AlertStatus;
public interface AlertRepository extends JpaRepository<Alert,UUID> {
	Optional<Alert> findFirstByDeduplicationKeyAndStatusNot(String key,AlertStatus status);
	Page<Alert> findBySeverityAndStatusOrderByCreatedAtDesc(AlertSeverity severity,AlertStatus status,Pageable pageable);
	Page<Alert> findByStatusOrderByCreatedAtDesc(AlertStatus status,Pageable pageable);
	Page<Alert> findBySeverityOrderByCreatedAtDesc(AlertSeverity severity,Pageable pageable);
	Page<Alert> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
