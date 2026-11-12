package com.netmoni.server.dto.auth;
import java.time.Instant;
import com.netmoni.server.entity.AccountRole;
public record AuthResponse(String accessToken,String tokenType,Instant expiresAt,String username,AccountRole role){}
