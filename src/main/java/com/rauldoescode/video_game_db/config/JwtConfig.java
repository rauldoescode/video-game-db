package com.rauldoescode.video_game_db.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.rauldoescode.video_game_db.auth.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

	/**
	 * Clock for access-token {@code iat} and {@code exp}. Tests can replace this bean.
	 */
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	/**
	 * Signs access tokens with the shared HMAC secret.
	 */
	@Bean
	JwtEncoder jwtEncoder(JwtProperties properties) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(properties)));
	}

	/**
	 * Verifies access tokens. A {@code JwtDecoder} bean replaces the one Boot would
	 * auto-configure from an issuer URI, which this app does not have.
	 */
	@Bean
	JwtDecoder jwtDecoder(JwtProperties properties) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey(properties))
				.macAlgorithm(MacAlgorithm.HS256)
				.build();
		decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(
				new JwtIssuerValidator(properties.issuer()),
				tokenUseValidator()));
		return decoder;
	}

	/**
	 * Rejects a token unless its {@code token_use} claim is {@code access}.
	 */
	static OAuth2TokenValidator<Jwt> tokenUseValidator() {
        return jwt -> {
            // Return success if the token_use is "access"
            if ("access".equals(jwt.getClaimAsString("token_use"))) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    OAuth2ErrorCodes.INVALID_TOKEN, "token_use is not access", null));
        };
	}

	private static SecretKey secretKey(JwtProperties properties) {
		return new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}
}
