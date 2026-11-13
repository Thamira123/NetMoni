package com.netmoni.server.controller;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;
import com.netmoni.server.dto.auth.AuthResponse;
import com.netmoni.server.dto.auth.LoginRequest;
import com.netmoni.server.dto.auth.RegisterRequest;
import com.netmoni.server.service.UserAccountService;
@RestController @RequestMapping("/api/auth")
public class AuthController {
	private final UserAccountService users;
	public AuthController(UserAccountService users){this.users=users;}
	@PostMapping("/register") @ResponseStatus(HttpStatus.CREATED) public AuthResponse register(@Valid @RequestBody RegisterRequest request){return users.register(request);}
	@PostMapping("/login") public AuthResponse login(@Valid @RequestBody LoginRequest request){try{return users.login(request);}catch(IllegalArgumentException e){throw new BadCredentialsException("Invalid username or password");}}
}
