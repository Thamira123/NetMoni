package com.netmoni.server.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

class CorsConfigurationTest {
	@Test
	void allowsOnlyConfiguredFrontendOriginsAndBearerRequests() {
		SecurityConfig config = new SecurityConfig();
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/devices");
		CorsConfiguration cors = config.corsConfigurationSource(
				"http://localhost:5173, https://netmoni.example").getCorsConfiguration(request);

		assertNotNull(cors);
		assertEquals("https://netmoni.example", cors.checkOrigin("https://netmoni.example"));
		assertNull(cors.checkOrigin("https://untrusted.example"));
		assertTrue(cors.getAllowedMethods().contains(HttpMethod.OPTIONS.name()));
		assertEquals("Authorization", cors.checkHeaders(java.util.List.of("Authorization")).getFirst());
	}
}
