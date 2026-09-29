package com.rauldoescode.video_game_db.igdb;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.rauldoescode.video_game_db.TestcontainersConfiguration;
import com.rauldoescode.video_game_db.config.RedisConfig;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The L2 cache against a real Redis and a stubbed IGDB. What matters here is that a second
 * identical lookup does not reach IGDB, that what comes back out of Redis is still a usable
 * IgdbGame, that the configured TTLs are what Redis actually holds, and that concurrent misses
 * collapse into one IGDB call. Redis being down is covered by IgdbLookupServiceRedisDownTest.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class IgdbLookupServiceTest {

    private static final String ZELDA = """
            [{
              "id": 1020,
              "name": "The Legend of Zelda: Breath of the Wild",
              "slug": "the-legend-of-zelda-breath-of-the-wild",
              "summary": "A summary",
              "cover": { "image_id": "co1abc" },
              "first_release_date": 1488499200,
              "genres": [{ "name": "Adventure" }],
              "platforms": [{ "name": "Nintendo Switch" }],
              "aggregated_rating": 97.4,
              "screenshots": [{ "image_id": "ss1xyz" }],
              "videos": [{ "video_id": "yt123" }]
            }]
            """;

    /**
     * Redis for the cache under test. {@code @ServiceConnection} lets Boot set
     * {@code spring.data.redis.*} from the container, so nothing here hard-codes a port.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class RedisTestcontainer {

        @Bean
        @ServiceConnection
        RedisContainer redisContainer() {
            return new RedisContainer(DockerImageName.parse("redis:7-alpine"));
        }
    }

    @RegisterExtension
    static WireMockExtension twitch = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @RegisterExtension
    static WireMockExtension igdb = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    /**
     * Points the app's IGDB and Twitch calls at WireMock. Credentials are set here too so the
     * test does not depend on a local .env.
     * @param properties registry Spring reads before the context starts
     */
    @DynamicPropertySource
    static void igdbProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.http.serviceclient.igdb.base-url",
                () -> "http://localhost:" + igdb.getPort() + "/v4");
        properties.add("igdb.twitch-token-uri",
                () -> "http://localhost:" + twitch.getPort() + "/oauth2/token");
        properties.add("igdb.client-id", () -> "test-id");
        properties.add("igdb.client-secret", () -> "test-secret");
    }

    @Autowired
    private IgdbLookupService lookup;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private StringRedisTemplate redis;

    /**
     * Redis outlives a single test, so entries from the previous one would look like hits here.
     */
    @BeforeEach
    void clearCaches() {
        for (String cache : List.of(RedisConfig.GAME_SEARCH, RedisConfig.GAME_DETAILS, RedisConfig.GAME_POPULAR)) {
            Objects.requireNonNull(cacheManager.getCache(cache)).clear();
        }
        stubToken();
    }

    @Test
    void secondIdenticalSearchIsServedFromRedis() {
        stubGames(ZELDA);
        IgdbSearchParams params = IgdbSearchParams.of("zelda");

        List<IgdbGame> first = lookup.search(params);
        List<IgdbGame> second = lookup.search(params);

        assertEquals(1, first.size());
        assertEquals(1, second.size());
        igdb.verify(1, postRequestedFor(urlPathEqualTo("/v4/games")));

        // Read the cached copy field by field: a JSON round trip that lost the nested records
        // would still cast to List<IgdbGame> and only fail here.
        IgdbGame game = second.getFirst();
        assertEquals(1020, game.id());
        assertEquals("The Legend of Zelda: Breath of the Wild", game.name());
        assertEquals("co1abc", game.cover().imageId());
        assertEquals(1488499200L, game.firstReleaseDate());
        assertEquals("Adventure", game.genres().getFirst().name());
        assertEquals("Nintendo Switch", game.platforms().getFirst().name());
        assertEquals(97.4, game.aggregatedRating());
        assertEquals("ss1xyz", game.screenshots().getFirst().imageId());
        assertEquals("yt123", game.videos().getFirst().videoId());
        assertNull(game.storyline());
    }

    @Test
    void searchIsStoredUnderTheHashedKeyForADay() {
        stubGames(ZELDA);
        IgdbSearchParams params = IgdbSearchParams.of("zelda");

        lookup.search(params);

        // In Redis the moment search() returns, not eventually: Spring Data Redis writes cache
        // entries asynchronously unless immediateWrites is on, which RedisConfig turns on.
        // Spring writes cacheName + "::" + key, so the key shape with a SHA-256 suffix.
        String key = RedisConfig.GAME_SEARCH + "::" + params.cacheKey();
        assertEquals(Boolean.TRUE, redis.hasKey(key));
        assertTtlIsAbout(Duration.ofHours(24), redis.getExpire(key));
    }

    @Test
    void secondDetailLookupIsServedFromRedisForAWeek() {
        stubGames(ZELDA);

        Optional<IgdbGame> first = lookup.detail(1020);
        Optional<IgdbGame> second = lookup.detail(1020);

        assertTrue(first.isPresent());
        assertTrue(second.isPresent());
        assertEquals("co1abc", second.get().cover().imageId());
        igdb.verify(1, postRequestedFor(urlPathEqualTo("/v4/games")));
        assertTtlIsAbout(Duration.ofDays(7), redis.getExpire(RedisConfig.GAME_DETAILS + "::1020"));
    }

    @Test
    void gameIgdbDoesNotHaveIsNotCached() {
        stubGames("[]");

        assertTrue(lookup.detail(999999).isEmpty());
        assertTrue(lookup.detail(999999).isEmpty());

        // A bad id must not hold a key for seven days, so the second lookup asks IGDB again.
        assertEquals(Boolean.FALSE, redis.hasKey(RedisConfig.GAME_DETAILS + "::999999"));
        igdb.verify(2, postRequestedFor(urlPathEqualTo("/v4/games")));
    }

    @Test
    void searchWithNoMatchesIsCached() {
        stubGames("[]");
        IgdbSearchParams params = IgdbSearchParams.of("no such game");

        assertTrue(lookup.search(params).isEmpty());
        assertTrue(lookup.search(params).isEmpty());

        // Unlike a missing id, "this search matches nothing" is an answer worth caching.
        igdb.verify(1, postRequestedFor(urlPathEqualTo("/v4/games")));
    }

    @Test
    void differentSearchesDoNotShareAnEntry() {
        stubGames(ZELDA);

        lookup.search(IgdbSearchParams.of("zelda"));
        lookup.search(IgdbSearchParams.of("mario"));
        lookup.search(new IgdbSearchParams("zelda", null, null, null, null, 20, 20));
        lookup.search(IgdbSearchParams.of("ZELDA"));

        // Three distinct keys; the fourth call only differs by case, so it is a hit.
        igdb.verify(3, postRequestedFor(urlPathEqualTo("/v4/games")));
    }

    @Test
    void popularRestoresRankOrderAndTheSecondCallIsACacheHit() {
        igdb.stubFor(post(urlPathEqualTo("/v4/popularity_primitives")).willReturn(okJson("""
                [{"game_id": 2, "value": 90}, {"game_id": 1, "value": 10}]
                """)));
        // /games returns the ids shuffled. Rank order has to come from the primitives list.
        igdb.stubFor(post(urlPathEqualTo("/v4/games")).willReturn(okJson("""
                [{"id": 1, "name": "First"}, {"id": 2, "name": "Second"}]
                """)));

        List<IgdbGame> first = lookup.popular(IgdbCategory.TRENDING, 20);
        List<IgdbGame> second = lookup.popular(IgdbCategory.TRENDING, 20);

        assertEquals(2L, first.get(0).id());
        assertEquals(1L, first.get(1).id());
        assertEquals(2L, second.get(0).id());
        assertEquals("Second", second.get(0).name());
        igdb.verify(1, postRequestedFor(urlPathEqualTo("/v4/popularity_primitives"))
                .withRequestBody(containing("popularity_type = 1"))
                .withRequestBody(containing("sort value desc;")));
        igdb.verify(1, postRequestedFor(urlPathEqualTo("/v4/games"))
                .withRequestBody(containing("id = (2,1)")));
        assertTtlIsAbout(Duration.ofHours(6), redis.getExpire(RedisConfig.GAME_POPULAR + "::TRENDING:20"));
    }

    @Test
    void concurrentMissesOnTheSameKeyMakeOneIgdbCall() throws Exception {
        igdb.stubFor(post(urlPathEqualTo("/v4/games"))
                .willReturn(okJson(ZELDA).withFixedDelay(300)));
        IgdbSearchParams params = IgdbSearchParams.of("zelda");

        int callers = 3;
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch go = new CountDownLatch(1);
        Callable<List<IgdbGame>> search = () -> {
            ready.countDown();
            go.await();
            return lookup.search(params);
        };

        try (ExecutorService pool = Executors.newFixedThreadPool(callers)) {
            List<Future<List<IgdbGame>>> results = List.of(
                    pool.submit(search), pool.submit(search), pool.submit(search));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            go.countDown();

            for (Future<List<IgdbGame>> result : results) {
                assertEquals("The Legend of Zelda: Breath of the Wild", result.get().getFirst().name());
            }
        }

        // Single flight: the first caller loads and the rest wait on its result.
        igdb.verify(1, postRequestedFor(urlPathEqualTo("/v4/games")));
    }

    /**
     * Asserts a TTL without waiting for it. Redis reports whole seconds and the clock has moved
     * since the write, so allow a small amount of slack under the configured value.
     * @param expected the TTL from RedisConfig
     * @param actualSeconds what Redis reports for the key
     */
    private static void assertTtlIsAbout(Duration expected, Long actualSeconds) {
        assertNotNull(actualSeconds);
        assertTrue(actualSeconds <= expected.toSeconds() && actualSeconds > expected.toSeconds() - 60,
                "expected a TTL near " + expected + " but Redis reported " + actualSeconds + "s");
    }

    private void stubGames(String json) {
        igdb.stubFor(post(urlPathEqualTo("/v4/games")).willReturn(okJson(json)));
    }

    private void stubToken() {
        twitch.stubFor(post(urlPathEqualTo("/oauth2/token")).willReturn(okJson("""
                {"access_token":"abc","expires_in":3600,"token_type":"bearer"}
                """)));
    }
}
