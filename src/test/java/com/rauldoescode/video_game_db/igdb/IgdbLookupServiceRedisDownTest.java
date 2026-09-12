package com.rauldoescode.video_game_db.igdb;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.rauldoescode.video_game_db.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Redis down is not a request failure. There is no Redis container here: the app is
 * pointed at a port nothing is listening on, which is the harshest version of the requirement,
 * since every read and every write fails rather than just some. Lookups must still answer from
 * IGDB, which they reach through the usual throttle.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class IgdbLookupServiceRedisDownTest {

    private static final String ZELDA = """
            [{ "id": 1020, "name": "The Legend of Zelda: Breath of the Wild",
               "cover": { "image_id": "co1abc" } }]
            """;

    @RegisterExtension
    static WireMockExtension twitch = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @RegisterExtension
    static WireMockExtension igdb = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    /**
     * Points Redis at a closed port. Taking a port from the OS and releasing it is more reliable
     * than picking a number and hoping it is free.
     * @param properties registry Spring reads before the context starts
     */
    @DynamicPropertySource
    static void deadRedis(DynamicPropertyRegistry properties) {
        properties.add("spring.data.redis.host", () -> "localhost");
        properties.add("spring.data.redis.port", IgdbLookupServiceRedisDownTest::closedPort);
        properties.add("spring.http.serviceclient.igdb.base-url",
                () -> "http://localhost:" + igdb.getPort() + "/v4");
        properties.add("igdb.twitch-token-uri",
                () -> "http://localhost:" + twitch.getPort() + "/oauth2/token");
        properties.add("igdb.client-id", () -> "test-id");
        properties.add("igdb.client-secret", () -> "test-secret");
    }

    @Autowired
    private IgdbLookupService lookup;

    @BeforeEach
    void stubTwitchAndIgdb() {
        twitch.stubFor(post(urlPathEqualTo("/oauth2/token")).willReturn(okJson("""
                {"access_token":"abc","expires_in":3600,"token_type":"bearer"}
                """)));
        igdb.stubFor(post(urlPathEqualTo("/v4/games")).willReturn(okJson(ZELDA)));
    }

    @Test
    void searchStillWorksAndEveryCallReachesIgdb() {
        IgdbSearchParams params = IgdbSearchParams.of("zelda");

        List<IgdbGame> first = lookup.search(params);
        List<IgdbGame> second = lookup.search(params);

        assertEquals("The Legend of Zelda: Breath of the Wild", first.getFirst().name());
        assertEquals("The Legend of Zelda: Breath of the Wild", second.getFirst().name());
        // No L2, so there is nothing to hit: both reads fail and both calls go out.
        igdb.verify(2, postRequestedFor(urlPathEqualTo("/v4/games")));
    }

    @Test
    void detailStillWorks() {
        assertEquals(1020, lookup.detail(1020).orElseThrow().id());
        igdb.verify(1, postRequestedFor(urlPathEqualTo("/v4/games")));
    }

    /**
     * @return a port that was free a moment ago and has nothing listening on it
     */
    private static int closedPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Could not reserve a closed port", e);
        }
    }
}
