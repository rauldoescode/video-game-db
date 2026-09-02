package com.rauldoescode.video_game_db.igdb;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Component
public class TwitchTokenProvider {

    private static final String CACHE_KEY = "twitch";

    // The token we store in the cache
    private record TwitchToken(String accessToken, Duration ttl) {}

    // The response we get from Twitch (token_type not listed here, but it's always "bearer" when authorized)
    private record TwitchTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") long expiresIn
    ) {}

    private final Cache<String, TwitchToken> cache;
    private final IgdbProperties igdbProperties;
    private final RestClient restClient;

    /**
     * Creates a new TwitchTokenProvider. Initializes caffeine cache with a maximum size of 1 and expires
     * after the given duration 'ttl'.
     * @param igdbProperties an object containing the Twitch API credentials
     * @param restClient a RestClient for making HTTP requests
     */
    public TwitchTokenProvider(IgdbProperties igdbProperties, RestClient restClient) {
        this.igdbProperties = igdbProperties;
        this.restClient = restClient;
        this.cache = Caffeine.newBuilder()
                .maximumSize(1)
                .expireAfter(Expiry.<String, TwitchToken>creating((key, token) -> token.ttl()))
                .build();
    }

    /**
     * Retrieves the access token from the cache or fetches a new one if it's expired.
     * @return Access token for Twitch API
     */
    public String accessToken() {
        return cache.get(CACHE_KEY, this::fetch).accessToken();
    }

    /**
     * Fetches a new access token from Twitch.
     * @param key key to use for caching the token
     * @return Access token for Twitch API
     */
    private TwitchToken fetch(String key) {
        TwitchTokenResponse response = restClient.post()
                .uri(igdbProperties.twitchTokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(tokenRequestForm())
                .retrieve()
                .body(TwitchTokenResponse.class);
        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            throw new IllegalStateException("Twitch token response did not include access_token");
        }
        return new TwitchToken(response.accessToken(), cacheTtl(response.expiresIn()));
    }

    /**
     * Calculates the TTL for the token based on the expiration time from the Twitch response minus
     * a buffer. Helps prevent Twitch from rejecting our requests if we're too close to expiration.
     * @param expiresInSeconds the number of seconds until the token expires
     * @return TTL for the token
     */
    private Duration cacheTtl(long expiresInSeconds) {
        Duration ttl = Duration.ofSeconds(expiresInSeconds).minus(igdbProperties.tokenRefreshBuffer());
        return ttl.isNegative() ? Duration.ZERO : ttl;
    }

    /**
     * Creates a form for requesting a new access token from Twitch.
     * @return Form for requesting a new access token
     */
    private MultiValueMap<String, String> tokenRequestForm() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", igdbProperties.clientId());
        form.add("client_secret", igdbProperties.clientSecret());
        form.add("grant_type", "client_credentials");
        return form;
    }

}
