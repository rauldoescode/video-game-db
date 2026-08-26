package com.rauldoescode.video_game_db.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests(auth -> auth
			.requestMatchers(
					HttpMethod.GET,
					"/actuator/health",
					"/actuator/health/liveness",
					"/actuator/health/readiness"
			).permitAll()
			.anyRequest().authenticated());

		// Unauthenticated /api/** should be 401, not 403
		http.httpBasic(Customizer.withDefaults());
		return http.build();
	}

}
