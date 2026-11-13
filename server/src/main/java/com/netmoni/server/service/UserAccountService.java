package com.netmoni.server.service;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.netmoni.server.dto.auth.AuthResponse;
import com.netmoni.server.dto.auth.LoginRequest;
import com.netmoni.server.dto.auth.RegisterRequest;
import com.netmoni.server.entity.AccountRole;
import com.netmoni.server.entity.UserAccount;
import com.netmoni.server.repository.UserAccountRepository;
@Service
public class UserAccountService implements UserDetailsService {
	private final UserAccountRepository users;private final PasswordEncoder passwords;private final JwtService jwt;
	public UserAccountService(UserAccountRepository users,PasswordEncoder passwords,JwtService jwt){this.users=users;this.passwords=passwords;this.jwt=jwt;}
	@Override @Transactional(readOnly=true) public UserDetails loadUserByUsername(String username){UserAccount a=users.findByUsername(username).orElseThrow(()->new UsernameNotFoundException("Invalid username or password"));return User.withUsername(a.getUsername()).password(a.getPasswordHash()).roles(a.getRole().name()).build();}
	@Transactional public AuthResponse register(RegisterRequest request){String username=request.username().trim().toLowerCase();if(users.existsByUsername(username))throw new IllegalArgumentException("Username is already registered");UserAccount account=users.save(new UserAccount(username,passwords.encode(request.password()),AccountRole.VIEWER));return token(account);}
	@Transactional(readOnly=true) public AuthResponse login(LoginRequest request){UserAccount account=users.findByUsername(request.username().trim().toLowerCase()).orElseThrow(()->new IllegalArgumentException("Invalid username or password"));if(!passwords.matches(request.password(),account.getPasswordHash()))throw new IllegalArgumentException("Invalid username or password");return token(account);}
	private AuthResponse token(UserAccount account){return new AuthResponse(jwt.issue(account.getUsername(),account.getRole()),"Bearer",jwt.expiresAt(),account.getUsername(),account.getRole());}
}
