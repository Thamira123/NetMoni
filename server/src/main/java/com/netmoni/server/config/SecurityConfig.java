package com.netmoni.server.config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.http.HttpStatus;
import com.netmoni.server.security.JwtAuthenticationFilter;
@Configuration @EnableMethodSecurity
public class SecurityConfig {
	@Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
	@Bean JwtAuthenticationFilter jwtAuthenticationFilter(com.netmoni.server.service.JwtService jwt){return new JwtAuthenticationFilter(jwt);}
	@Bean SecurityFilterChain securityFilterChain(HttpSecurity http,JwtAuthenticationFilter jwtFilter)throws Exception{
		return http.csrf(csrf->csrf.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(a->a.requestMatchers("/api/health","/api/auth/**").permitAll().anyRequest().authenticated())
			.exceptionHandling(e->e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
			.addFilterBefore(jwtFilter,UsernamePasswordAuthenticationFilter.class).build();
	}
}
