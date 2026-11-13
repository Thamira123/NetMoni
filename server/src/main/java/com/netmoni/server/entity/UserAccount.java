package com.netmoni.server.entity;
import java.util.UUID;
import jakarta.persistence.*;
@Entity @Table(name="app_users",indexes=@Index(name="idx_app_users_username",columnList="username",unique=true))
public class UserAccount {
	@Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
	@Column(nullable=false,unique=true,length=254) private String username;
	@Column(name="password_hash",nullable=false,length=100) private String passwordHash;
	@Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private AccountRole role;
	protected UserAccount(){}
	public UserAccount(String username,String passwordHash,AccountRole role){this.username=username;this.passwordHash=passwordHash;this.role=role;}
	public UUID getId(){return id;} public String getUsername(){return username;} public String getPasswordHash(){return passwordHash;} public AccountRole getRole(){return role;}
}
