package com.netmoni.server.entity;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;
@Entity @Table(name="agent_networks",uniqueConstraints=@UniqueConstraint(name="uk_agent_network_interface_cidr",columnNames={"agent_id","interface_name","cidr"}),indexes=@Index(name="idx_agent_network_agent",columnList="agent_id"))
public class AgentNetwork {
 @Id private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="agent_id",nullable=false) private Agent agent;
 @Column(name="interface_name",nullable=false,length=120) private String interfaceName;
 @Column(name="display_name",length=200) private String displayName;
 @Column(name="interface_type",nullable=false,length=30) private String interfaceType;
 @Column(name="interface_status",nullable=false,length=20) private String interfaceStatus;
 @Column(length=45) private String address;
 @Column(nullable=false,length=50) private String cidr;
 @Column(name="subnet_mask",length=45) private String subnetMask;
 @Column(name="broadcast_address",length=45) private String broadcastAddress;
 @Column(length=45) private String gateway;
 @Column(name="mac_address",length=24) private String macAddress;
 @Column(name="ipv6_address",length=80) private String ipv6Address;
 @Column(name="ipv6_prefix_length") private Integer ipv6PrefixLength;
 @Column(nullable=false) private boolean authorized;
 @Column(name="scan_eligible",nullable=false) private boolean scanEligible;
 @Column(name="last_detected_at",nullable=false) private Instant lastDetectedAt;
 protected AgentNetwork(){}
 public AgentNetwork(UUID id,Agent agent,String interfaceName,String displayName,String interfaceType,String interfaceStatus,String address,String cidr,String subnetMask,String broadcastAddress,String gateway,String macAddress,String ipv6Address,Integer ipv6PrefixLength,boolean authorized,boolean scanEligible,Instant lastDetectedAt){this.id=id==null?UUID.randomUUID():id;this.agent=agent;update(interfaceName,displayName,interfaceType,interfaceStatus,address,cidr,subnetMask,broadcastAddress,gateway,macAddress,ipv6Address,ipv6PrefixLength,authorized,scanEligible,lastDetectedAt);}
 public void update(String interfaceName,String displayName,String interfaceType,String interfaceStatus,String address,String cidr,String subnetMask,String broadcastAddress,String gateway,String macAddress,String ipv6Address,Integer ipv6PrefixLength,boolean authorized,boolean scanEligible,Instant at){this.interfaceName=interfaceName;this.displayName=displayName;this.interfaceType=interfaceType;this.interfaceStatus=interfaceStatus;this.address=address;this.cidr=cidr;this.subnetMask=subnetMask;this.broadcastAddress=broadcastAddress;this.gateway=gateway;this.macAddress=macAddress;this.ipv6Address=ipv6Address;this.ipv6PrefixLength=ipv6PrefixLength;this.authorized=authorized;this.scanEligible=scanEligible;this.lastDetectedAt=at==null?Instant.now():at;}
 public UUID getId(){return id;} public Agent getAgent(){return agent;} public String getInterfaceName(){return interfaceName;} public String getDisplayName(){return displayName;} public String getInterfaceType(){return interfaceType;} public String getInterfaceStatus(){return interfaceStatus;} public String getAddress(){return address;} public String getCidr(){return cidr;} public String getSubnetMask(){return subnetMask;} public String getBroadcastAddress(){return broadcastAddress;} public String getGateway(){return gateway;} public String getMacAddress(){return macAddress;} public String getIpv6Address(){return ipv6Address;} public Integer getIpv6PrefixLength(){return ipv6PrefixLength;} public boolean isAuthorized(){return authorized;} public boolean isScanEligible(){return scanEligible;} public Instant getLastDetectedAt(){return lastDetectedAt;}
}
