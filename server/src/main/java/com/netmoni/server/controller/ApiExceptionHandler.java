package com.netmoni.server.controller;

import java.util.Map;

import com.netmoni.server.service.AgentNotFoundException;
import com.netmoni.server.service.DeviceNotFoundException;
import com.netmoni.server.service.AlertNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.security.authentication.BadCredentialsException;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(AgentNotFoundException.class)
	public ResponseEntity<Map<String, String>> handleAgentNotFound(AgentNotFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(Map.of("error", "agent_not_found", "message", exception.getMessage()));
	}

	@ExceptionHandler({DeviceNotFoundException.class, AlertNotFoundException.class})
	public ResponseEntity<Map<String, String>> handleNotFound(RuntimeException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "resource_not_found", "message", exception.getMessage()));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, String>> handleInvalidRequest(IllegalArgumentException exception) {
		return ResponseEntity.badRequest()
				.body(Map.of("error", "invalid_request", "message", exception.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException exception) {
		String message = exception.getBindingResult().getFieldErrors().stream().findFirst()
				.map(error -> error.getField() + " " + error.getDefaultMessage()).orElse("Request validation failed");
		return ResponseEntity.badRequest().body(Map.of("error", "validation_failed", "message", message));
	}

	@ExceptionHandler(BadCredentialsException.class)
	public ResponseEntity<Map<String, String>> handleBadCredentials(BadCredentialsException exception) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "unauthorized", "message", "Invalid username or password"));
	}
}
