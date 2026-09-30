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
					"/actuator/health/readiness",
					"/api/games/**"
			).permitAll()
			.anyRequest().authenticated());

		// BearerTokenAuthenticationEntryPoint turns a missing or rejected token into 401.
		http.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
		return http.build();
	}

}
