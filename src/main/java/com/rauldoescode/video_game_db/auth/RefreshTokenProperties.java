package com.rauldoescode.video_game_db.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Lifetime of an opaque refresh token. Each rotation starts a new lifetime from that moment.
 *
 * @param ttl how long a newly issued refresh token stays valid
 */
@ConfigurationProperties(prefix = "app.refresh-token")
public record RefreshTokenProperties(Duration ttl) {

    public RefreshTokenProperties {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("app.refresh-token.ttl must be positive");
        }
    }
}
