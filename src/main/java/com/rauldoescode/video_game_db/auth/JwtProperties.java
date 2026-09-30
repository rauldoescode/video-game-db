package com.rauldoescode.video_game_db.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * HMAC signing settings for access tokens issued by this app.
 *
 * @param secret    shared HMAC-SHA256 key; at least 32 bytes
 * @param accessTtl lifetime of an access token
 * @param issuer    {@code iss} claim written into every access token
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, Duration accessTtl, String issuer) {

    private static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "app.jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes");
        }
    }
}
