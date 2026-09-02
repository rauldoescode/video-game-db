package com.rauldoescode.video_game_db.igdb;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TwitchTokenProviderTest {

    @RegisterExtension
    static WireMockExtension twitch = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private TwitchTokenProvider provider;

    @BeforeEach
    void setUp() {
        URI tokenUri = URI.create("http://localhost:" + twitch.getPort() + "/oauth2/token");
        IgdbProperties properties = new IgdbProperties(
                "test-id", "test-secret", tokenUri, Duration.ofSeconds(60));
        provider = new TwitchTokenProvider(properties, RestClient.builder().build());
    }

    @Test
    void firstCallFetchesToken() {
        stubToken("abc", 3600);

        assertEquals("abc", provider.accessToken());
        twitch.verify(1, postRequestedFor(urlPathEqualTo("/oauth2/token")));
    }

    @Test
    void secondCallReusesCachedToken() {
        stubToken("abc", 3600);

        assertEquals("abc", provider.accessToken());
        assertEquals("abc", provider.accessToken()); // cache hit
        twitch.verify(1, postRequestedFor(urlPathEqualTo("/oauth2/token")));
    }

    @Test
    void refetchesWhenExpiresInIsInsideRefreshBuffer() {
        stubToken("abc", 30);

        assertEquals("abc", provider.accessToken()); // fetches first but expires immediately
        assertEquals("abc", provider.accessToken()); // fetches again because the first one expired immediately
        twitch.verify(2, postRequestedFor(urlPathEqualTo("/oauth2/token")));
    }

    /**
     * Stubs the Twitch token endpoint to return the given access token and expiration time.
     * @param accessToken the access token to return
     * @param expiresInSeconds the number of seconds until the token expires
     */
    private void stubToken(String accessToken, long expiresInSeconds) {
        twitch.stubFor(post(urlPathEqualTo("/oauth2/token"))
                .willReturn(okJson("""
                        {"access_token":"%s","expires_in":%d,"token_type":"bearer"}
                        """.formatted(accessToken, expiresInSeconds))));
    }

}
