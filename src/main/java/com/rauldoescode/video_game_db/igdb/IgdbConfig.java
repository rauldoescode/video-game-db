package com.rauldoescode.video_game_db.igdb;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
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
     * Configures the RestClient to add the Client-ID and Bearer token to all requests, and to hold
     * each request until the outbound IGDB throttle allows it.
     * @param properties the IgdbProperties object containing the Twitch API credentials
     * @param tokenProvider the TwitchTokenProvider object for retrieving the access token
     * @param throttle the outbound rate limiter for IGDB
     * @return a RestClientHttpServiceGroupConfigurer
     */
    @Bean
    public RestClientHttpServiceGroupConfigurer groupConfigurer(IgdbProperties properties,
                                                                TwitchTokenProvider tokenProvider,
                                                                IgdbThrottle throttle) {
        return groups -> groups
                .filterByName("igdb")
                .forEachClient((group, builder) -> builder
                        .requestInterceptor(igdbInterceptor(properties, tokenProvider, throttle))
                );
    }

    /**
     * Builds the interceptor applied to every IGDB request. Package-private and static so tests can
     * apply the same interceptor to their own RestClient instead of reimplementing it.
     * <p>
     * Headers are set before the throttle so a Twitch token refresh does not spend an IGDB request
     * slot. The throttle releases its concurrency permit when execute() returns, which bounds
     * requests in flight rather than response bodies still being read further up the stack.
     * @param properties the IgdbProperties object containing the Twitch API credentials
     * @param tokenProvider the TwitchTokenProvider object for retrieving the access token
     * @param throttle the outbound rate limiter for IGDB
     * @return an interceptor that authenticates and throttles one IGDB request
     */
    static ClientHttpRequestInterceptor igdbInterceptor(IgdbProperties properties,
                                                       TwitchTokenProvider tokenProvider,
                                                       IgdbThrottle throttle) {
        return (request, body, execution) -> {
            request.getHeaders().set("Client-ID", properties.clientId());
            request.getHeaders().setBearerAuth(tokenProvider.accessToken());
            return throttle.execute(() -> execution.execute(request, body));
        };
    }
}
