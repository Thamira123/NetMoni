package com.netmoni.server.repository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.netmoni.server.entity.UserAccount;
public interface UserAccountRepository extends JpaRepository<UserAccount,UUID>{Optional<UserAccount> findByUsername(String username);boolean existsByUsername(String username);}
