package com.rauldoescode.video_game_db.igdb;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

@Configuration
@EnableConfigurationProperties(IgdbProperties.class)
@ImportHttpServices(group="igdb", types=IgdbClient.class) // creates proxy bean for IgdbClient
public class IgdbConfig {

    /**
     * Creates a RestClient for making HTTP requests to the Twitch API.
     * @return a RestClient
     */
    @Bean
    public RestClient twitchTokenRestClient() {
        return RestClient.builder().build();
    }

    /**
     * Configures the RestClient to add the Client-ID and Bearer token to all requests.
     * @param properties the IgdbProperties object containing the Twitch API credentials
     * @param tokenProvider the TwitchTokenProvider object for retrieving the access token
     * @return a RestClientHttpServiceGroupConfigurer
     */
    @Bean
    public RestClientHttpServiceGroupConfigurer groupConfigurer(IgdbProperties properties, TwitchTokenProvider tokenProvider) {
        return groups -> groups
                .filterByName("igdb")
                .forEachClient((group, builder) -> builder
                        .requestInterceptor((request, body, execution) -> {
                            request.getHeaders().set("Client-ID", properties.clientId());
                            request.getHeaders().setBearerAuth(tokenProvider.accessToken());
                            return execution.execute(request, body);
                        })
                );
    }
}
