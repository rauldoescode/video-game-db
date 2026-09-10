package com.rauldoescode.video_game_db.igdb;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

/**
 * Properties/credentials for the IGDB API
 * @param clientId IGDB client ID
 * @param clientSecret IGDB client secret
 * @param twitchTokenUri URI for requesting a Twitch access token
 * @param tokenRefreshBuffer buffer time before the token expires to refresh it
 * @param requestsPerSecond outbound requests per second IGDB allows
 * @param maxConcurrent outbound requests IGDB allows in flight at once
 * @param throttleTimeout how long a caller waits for throttle capacity before giving up
 */
@ConfigurationProperties(prefix = "igdb")
public record IgdbProperties(String clientId,
                             String clientSecret,
                             URI twitchTokenUri,
                             Duration tokenRefreshBuffer,
                             int requestsPerSecond,
                             int maxConcurrent,
                             Duration throttleTimeout
) {}
