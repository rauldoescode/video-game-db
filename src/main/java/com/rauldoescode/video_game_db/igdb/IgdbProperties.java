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
 */
@ConfigurationProperties(prefix = "igdb")
public record IgdbProperties(String clientId,
                             String clientSecret,
                             URI twitchTokenUri,
                             Duration tokenRefreshBuffer
) {}
