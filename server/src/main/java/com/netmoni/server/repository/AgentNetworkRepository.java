package com.netmoni.server.repository;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import com.netmoni.server.entity.AgentNetwork;
public interface AgentNetworkRepository extends JpaRepository<AgentNetwork,UUID>{
 List<AgentNetwork> findByAgent_Id(UUID agentId);
 Optional<AgentNetwork> findByIdAndAgent_Id(UUID id,UUID agentId);
 Optional<AgentNetwork> findByAgent_IdAndInterfaceNameAndCidr(UUID agentId,String interfaceName,String cidr);
 List<AgentNetwork> findByScanEligibleTrue();
}
