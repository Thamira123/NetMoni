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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import java.util.Arrays;
import java.util.List;
import com.netmoni.server.security.JwtAuthenticationFilter;
@Configuration @EnableMethodSecurity
public class SecurityConfig {
	@Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
	@Bean JwtAuthenticationFilter jwtAuthenticationFilter(com.netmoni.server.service.JwtService jwt){return new JwtAuthenticationFilter(jwt);}
	@Bean SecurityFilterChain securityFilterChain(HttpSecurity http,JwtAuthenticationFilter jwtFilter,
			CorsConfigurationSource corsConfigurationSource)throws Exception{
		return http.csrf(csrf->csrf.disable()).cors(cors->cors.configurationSource(corsConfigurationSource))
			.sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(a->a.requestMatchers("/api/health","/api/auth/**","/ws","/ws/**").permitAll().anyRequest().authenticated())
			.exceptionHandling(e->e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
			.addFilterBefore(jwtFilter,UsernamePasswordAuthenticationFilter.class).build();
	}
	@Bean CorsConfigurationSource corsConfigurationSource(
			@Value("${netmoni.cors.allowed-origins:http://localhost:5173}") String allowedOrigins){
		CorsConfiguration configuration=new CorsConfiguration();
		configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(origin->!origin.isEmpty()).toList());
		configuration.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
		configuration.setAllowedHeaders(List.of("Authorization","Content-Type"));
		configuration.setAllowCredentials(false);
		configuration.setMaxAge(3600L);
		UrlBasedCorsConfigurationSource source=new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**",configuration);
		return source;
	}
}
