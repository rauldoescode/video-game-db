package com.rauldoescode.video_game_db.igdb;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(IgdbProperties.class)
public class IgdbConfig {

    @Bean
    public RestClient twitchTokenRestClient() {
        return RestClient.builder().build();
    }
}
