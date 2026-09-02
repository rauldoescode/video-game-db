package com.rauldoescode.video_game_db.igdb;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "igdb")
public record IgdbProperties(String clientId,
                             String clientSecret,
                             URI twitchTokenUri,
                             Duration tokenRefreshBuffer
) {}
