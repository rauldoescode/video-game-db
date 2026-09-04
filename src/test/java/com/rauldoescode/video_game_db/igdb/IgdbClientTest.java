package com.rauldoescode.video_game_db.igdb;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class IgdbClientTest {

    @RegisterExtension
    static WireMockExtension twitch = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @RegisterExtension
    static WireMockExtension igdb = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private IgdbClient client;

    @BeforeEach
    void setUp() {
        IgdbProperties properties = new IgdbProperties(
                "test-id", "test-secret",
                URI.create("http://localhost:" + twitch.getPort() + "/oauth2/token"),
                Duration.ofSeconds(60));
        TwitchTokenProvider tokens = new TwitchTokenProvider(properties, RestClient.builder().build());

        RestClient restClient = RestClient.builder()
                .baseUrl("http://localhost:" + igdb.getPort() + "/v4")
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().set("Client-ID", properties.clientId());
                    request.getHeaders().setBearerAuth(tokens.accessToken());
                    return execution.execute(request, body);
                })
                .build();

        client = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(IgdbClient.class);
    }

    @Test
    void searchPostsFieldListWithBearerTokenAndDeserializesNestedFields() {
        stubToken("abc");
        igdb.stubFor(post(urlPathEqualTo("/v4/games")).willReturn(okJson("""
                [{
                  "id": 1020,
                  "name": "The Legend of Zelda: Breath of the Wild",
                  "slug": "the-legend-of-zelda-breath-of-the-wild",
                  "summary": "A summary",
                  "storyline": "A storyline",
                  "cover": { "image_id": "co1abc" },
                  "first_release_date": 1488499200,
                  "genres": [{ "name": "Adventure" }],
                  "platforms": [{ "name": "Nintendo Switch" }],
                  "aggregated_rating": 97.4,
                  "screenshots": [{ "image_id": "ss1xyz" }],
                  "videos": [{ "video_id": "yt123" }]
                }]
                """)));

        String query = new ApicalypseQuery().fields(IgdbFields.GAME).search("zelda").build();
        List<IgdbGame> games = client.games(query);

        assertEquals(1, games.size());
        IgdbGame game = games.getFirst();
        assertEquals(1020, game.id());
        assertEquals("The Legend of Zelda: Breath of the Wild", game.name());
        assertEquals("co1abc", game.cover().imageId());
        assertEquals(1488499200L, game.firstReleaseDate());
        assertEquals("Adventure", game.genres().getFirst().name());
        assertEquals("Nintendo Switch", game.platforms().getFirst().name());
        assertEquals(97.4, game.aggregatedRating());
        assertEquals("ss1xyz", game.screenshots().getFirst().imageId());
        assertEquals("yt123", game.videos().getFirst().videoId());

        igdb.verify(1, postRequestedFor(urlPathEqualTo("/v4/games"))
                .withHeader("Client-ID", equalTo("test-id"))
                .withHeader("Authorization", equalTo("Bearer abc"))
                .withRequestBody(equalTo(query)));
        twitch.verify(1, postRequestedFor(urlPathEqualTo("/oauth2/token")));
    }

    @Test
    void omittedOptionalFieldsAreNull() {
        stubToken("abc");
        igdb.stubFor(post(urlPathEqualTo("/v4/games")).willReturn(okJson("""
                [{ "id": 1, "name": "Untitled" }]
                """)));

        IgdbGame game = client.games(
                new ApicalypseQuery().fields(IgdbFields.GAME).search("untitled").build()
        ).getFirst();

        assertEquals(1, game.id());
        assertEquals("Untitled", game.name());
        assertNull(game.cover());
        assertNull(game.firstReleaseDate());
        assertNull(game.aggregatedRating());
        assertNull(game.summary());
        assertNull(game.genres());
    }

    private void stubToken(String accessToken) {
        twitch.stubFor(post(urlPathEqualTo("/oauth2/token"))
                .willReturn(okJson("""
                        {"access_token":"%s","expires_in":3600,"token_type":"bearer"}
                        """.formatted(accessToken))));
    }
}
