package com.rauldoescode.video_game_db.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private static final String AUTH_PATH_PREFIX = "/api/auth/";

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests(auth -> auth
			.requestMatchers(
					HttpMethod.GET,
					"/actuator/health",
					"/actuator/health/liveness",
					"/actuator/health/readiness",
					"/api/games/**"
			).permitAll()
			.requestMatchers(
					HttpMethod.POST,
					"/api/auth/register",
					"/api/auth/login",
					"/api/auth/refresh",
					"/api/auth/logout"
			).permitAll()
			.anyRequest().authenticated());

		// Browser auth is a bearer token plus a SameSite=Lax refresh cookie.
		// This API does not use a session or a CSRF token.
		http.csrf(csrf -> csrf.disable());
		http.sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
		// BearerTokenAuthenticationEntryPoint turns a missing or rejected token into 401.
		http.oauth2ResourceServer(oauth2 -> oauth2
				.bearerTokenResolver(ignoringAuthEndpoints())
				.jwt(Customizer.withDefaults()));
		return http.build();
	}

	/**
	 * BCrypt hashes for {@code users.password_hash}.
	 */
	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	/**
	 * The SPA calls refresh because its access token expired, and may still attach that token.
	 * The bearer filter rejects an expired token with 401 even on a permitted path, so
	 * {@code /api/auth/**} does not read the header at all.
	 */
	private static BearerTokenResolver ignoringAuthEndpoints() {
		DefaultBearerTokenResolver header = new DefaultBearerTokenResolver();
		return request -> request.getRequestURI().startsWith(AUTH_PATH_PREFIX) ? null : header.resolve(request);
	}

}
