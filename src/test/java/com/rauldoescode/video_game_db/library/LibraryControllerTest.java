package com.rauldoescode.video_game_db.library;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.rauldoescode.video_game_db.TestcontainersConfiguration;
import com.rauldoescode.video_game_db.auth.AccessTokenService;
import com.rauldoescode.video_game_db.config.RedisConfig;
import com.rauldoescode.video_game_db.game.Game;
import com.rauldoescode.video_game_db.game.GameRepository;
import com.rauldoescode.video_game_db.user.User;
import com.rauldoescode.video_game_db.user.UserRepository;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The library routes against Postgres, Redis, and a stubbed IGDB. IGDB is only reached the first
 * time anyone adds a game; after that the {@code games} row is the source.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, LibraryControllerTest.RedisTestcontainer.class})
class LibraryControllerTest {

    private static final long ZELDA = 1020L;
    private static final long WITCHER = 1942L;
    private static final long HADES = 113112L;

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
    MockMvc mockMvc;

    @Autowired
    UserRepository users;

    @Autowired
    GameRepository games;

    @Autowired
    UserGameEntryRepository entries;

    @Autowired
    AccessTokenService accessTokens;

    @Autowired
    CacheManager cacheManager;

    @BeforeEach
    void reset() {
        entries.deleteAll();
        games.deleteAll();
        users.deleteAll();
        clearGameDetailsCache();
        twitch.stubFor(WireMock.post(urlPathEqualTo("/oauth2/token"))
                .willReturn(okJson("""
                        {"access_token":"abc","expires_in":3600,"token_type":"bearer"}
                        """)));
        stubGame(ZELDA, """
                [{
                  "id": 1020,
                  "name": "The Legend of Zelda: Breath of the Wild",
                  "slug": "the-legend-of-zelda-breath-of-the-wild",
                  "cover": { "image_id": "co1abc" },
                  "first_release_date": 1488499200,
                  "aggregated_rating": 97.4
                }]
                """);
        stubGame(WITCHER, """
                [{ "id": 1942, "name": "The Witcher 3: Wild Hunt" }]
                """);
        stubGame(HADES, """
                [{ "id": 113112, "name": "Hades" }]
                """);
    }

    @Test
    void anonymousCallsAre401() throws Exception {
        mockMvc.perform(get("/api/library"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/library")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"igdbId\":1020}"))
                .andExpect(status().isUnauthorized());

        assertEquals(0, games.count());
    }

    @Test
    void addStoresTheGameAndReturnsTheEntry() throws Exception {
        String token = tokenFor(savedUser("raul"));

        add(token, ZELDA, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("PLAN_TO_PLAY"))
                .andExpect(jsonPath("$.hoursPlayed").value(0))
                .andExpect(jsonPath("$.rating").doesNotExist())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.game.id").value(ZELDA))
                .andExpect(jsonPath("$.game.name").value("The Legend of Zelda: Breath of the Wild"))
                .andExpect(jsonPath("$.game.coverUrl")
                        .value("https://images.igdb.com/igdb/image/upload/t_cover_big/co1abc.jpg"))
                .andExpect(jsonPath("$.game.firstReleaseDate").value("2017-03-03"))
                .andExpect(jsonPath("$.game.aggregatedRating").value(97.4));

        Game stored = games.findById(ZELDA).orElseThrow();
        assertEquals("the-legend-of-zelda-breath-of-the-wild", stored.getSlug());
        assertEquals("co1abc", stored.getCoverImageId());
        assertEquals(LocalDate.of(2017, 3, 3), stored.getReleaseDate());
        assertEquals(0, stored.getAggregatedRating().compareTo(new BigDecimal("97.40")));
    }

    @Test
    void addAcceptsAStartingStatus() throws Exception {
        add(tokenFor(savedUser("raul")), ZELDA, "PLAYING")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLAYING"));
    }

    @Test
    void aStoredGameIsNotFetchedFromIgdbAgain() throws Exception {
        add(tokenFor(savedUser("raul")), ZELDA, null).andExpect(status().isCreated());
        // Without the Redis copy, only the games row can save the second add an IGDB call.
        clearGameDetailsCache();

        add(tokenFor(savedUser("other")), ZELDA, null).andExpect(status().isCreated());

        igdb.verify(1, postRequestedFor(urlPathEqualTo("/v4/games")));
        assertEquals(1, games.count());
        assertEquals(2, entries.count());
    }

    @Test
    void addingTheSameGameTwiceIs409() throws Exception {
        String token = tokenFor(savedUser("raul"));
        add(token, ZELDA, null).andExpect(status().isCreated());

        add(token, ZELDA, "PLAYING")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Game 1020 is already in your library"))
                .andExpect(jsonPath("$.errors[0].field").value("igdbId"));

        assertEquals(1, entries.count());
        assertEquals(GameStatus.PLAN_TO_PLAY, entries.findAll().getFirst().getStatus());
    }

    @Test
    void unknownIgdbIdIs404AndStoresNothing() throws Exception {
        stubGame(999999L, "[]");

        add(tokenFor(savedUser("raul")), 999999L, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Game 999999 was not found"));

        assertEquals(0, games.count());
        assertEquals(0, entries.count());
    }

    @Test
    void invalidBodyIs400() throws Exception {
        String token = tokenFor(savedUser("raul"));

        mockMvc.perform(post("/api/library")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("igdbId"));

        mockMvc.perform(post("/api/library")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"igdbId\":1020,\"status\":\"BACKLOG\"}"))
                .andExpect(status().isBadRequest());

        assertEquals(0, entries.count());
    }

    @Test
    void listIsNewestFirstAndFiltersByStatus() throws Exception {
        String token = tokenFor(savedUser("raul"));
        add(token, ZELDA, "PLAYING").andExpect(status().isCreated());
        add(token, WITCHER, null).andExpect(status().isCreated());
        add(token, HADES, "PLAYING").andExpect(status().isCreated());

        mockMvc.perform(get("/api/library").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].game.id").value(contains((int) HADES, (int) WITCHER, (int) ZELDA)));

        mockMvc.perform(get("/api/library")
                        .param("status", "PLAYING")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].game.id").value(contains((int) HADES, (int) ZELDA)));
    }

    @Test
    void unknownStatusFilterIs400() throws Exception {
        mockMvc.perform(get("/api/library")
                        .param("status", "BACKLOG")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(savedUser("raul"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("status"));
    }

    @Test
    void oneUserNeverSeesAnotherUsersLibrary() throws Exception {
        add(tokenFor(savedUser("raul")), ZELDA, null).andExpect(status().isCreated());
        String other = tokenFor(savedUser("other"));

        mockMvc.perform(get("/api/library").header(HttpHeaders.AUTHORIZATION, "Bearer " + other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    private ResultActions add(String token, long igdbId, String status) throws Exception {
        String body = status == null
                ? "{\"igdbId\":%d}".formatted(igdbId)
                : "{\"igdbId\":%d,\"status\":\"%s\"}".formatted(igdbId, status);
        return mockMvc.perform(post("/api/library")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private User savedUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("not-a-real-hash");
        return users.save(user);
    }

    private String tokenFor(User user) {
        return accessTokens.issue(user);
    }

    private void stubGame(long igdbId, String json) {
        igdb.stubFor(WireMock.post(urlPathEqualTo("/v4/games"))
                .withRequestBody(containing("id = " + igdbId + ";"))
                .willReturn(okJson(json)));
    }

    private void clearGameDetailsCache() {
        Objects.requireNonNull(cacheManager.getCache(RedisConfig.GAME_DETAILS)).clear();
    }
}
