package com.netmoni.server.repository;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import com.netmoni.server.entity.DiscoveryScan;
public interface DiscoveryScanRepository extends JpaRepository<DiscoveryScan,UUID> {
 Optional<DiscoveryScan> findFirstByAgentIdOrderByCreatedAtDesc(UUID agentId);
 boolean existsByAgentIdAndStateIn(UUID agentId, Collection<DiscoveryScan.State> states);
 Optional<DiscoveryScan> findFirstByAgentIdAndStateOrderByCreatedAtAsc(UUID agentId, DiscoveryScan.State state);
 Optional<DiscoveryScan> findFirstByAgentIdAndNetworkIdAndStateOrderByCreatedAtAsc(UUID agentId,UUID networkId,DiscoveryScan.State state);
 boolean existsByAgentIdAndNetworkIdAndStateIn(UUID agentId,UUID networkId,Collection<DiscoveryScan.State> states);
 Optional<DiscoveryScan> findByIdAndAgentId(UUID id,UUID agentId);
 Optional<DiscoveryScan> findFirstByNetworkIdOrderByCreatedAtDesc(UUID networkId);
}
