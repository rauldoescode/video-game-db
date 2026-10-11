package com.rauldoescode.video_game_db.library;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.jayway.jsonpath.JsonPath;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
        mockMvc.perform(put("/api/library/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PLAYING\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/library/" + UUID.randomUUID()))
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

    @Test
    void updateReplacesEveryFieldAndMovesTheEntryToTheTop() throws Exception {
        String token = tokenFor(savedUser("raul"));
        UUID zelda = entryId(add(token, ZELDA, null).andExpect(status().isCreated()));
        add(token, WITCHER, null).andExpect(status().isCreated());
        Instant before = entries.findById(zelda).orElseThrow().getUpdatedAt();

        update(token, zelda, """
                {
                  "status": "COMPLETED",
                  "rating": 10,
                  "review": "  Best open world.  ",
                  "hoursPlayed": 120,
                  "platformPlayed": "Nintendo Switch",
                  "startedAt": "2017-03-03",
                  "completedAt": "2017-05-01"
                }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(zelda.toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.rating").value(10))
                .andExpect(jsonPath("$.review").value("Best open world."))
                .andExpect(jsonPath("$.hoursPlayed").value(120))
                .andExpect(jsonPath("$.platformPlayed").value("Nintendo Switch"))
                .andExpect(jsonPath("$.startedAt").value("2017-03-03"))
                .andExpect(jsonPath("$.completedAt").value("2017-05-01"))
                .andExpect(jsonPath("$.game.id").value(ZELDA))
                .andExpect(jsonPath("$.game.name").value("The Legend of Zelda: Breath of the Wild"));

        UserGameEntry stored = entries.findById(zelda).orElseThrow();
        assertEquals(GameStatus.COMPLETED, stored.getStatus());
        assertTrue(stored.getUpdatedAt().isAfter(before));

        mockMvc.perform(get("/api/library").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$[*].game.id").value(contains((int) ZELDA, (int) WITCHER)));
    }

    @Test
    void updateClearsOmittedAndBlankFields() throws Exception {
        String token = tokenFor(savedUser("raul"));
        UUID zelda = entryId(add(token, ZELDA, null).andExpect(status().isCreated()));
        update(token, zelda, """
                {"status":"PLAYING","rating":8,"review":"Great","hoursPlayed":10,
                 "platformPlayed":"Switch","startedAt":"2024-01-01"}
                """)
                .andExpect(status().isOk());

        update(token, zelda, "{\"status\":\"ON_HOLD\",\"review\":\"   \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ON_HOLD"))
                .andExpect(jsonPath("$.rating").doesNotExist())
                .andExpect(jsonPath("$.review").doesNotExist())
                .andExpect(jsonPath("$.hoursPlayed").value(0))
                .andExpect(jsonPath("$.platformPlayed").doesNotExist())
                .andExpect(jsonPath("$.startedAt").doesNotExist());

        UserGameEntry stored = entries.findById(zelda).orElseThrow();
        assertNull(stored.getRating());
        assertNull(stored.getReview());
        assertEquals(0, stored.getHoursPlayed());
    }

    @Test
    void updatingAnotherUsersEntryIs404LikeAMissingOne() throws Exception {
        UUID raulsEntry = entryId(add(tokenFor(savedUser("raul")), ZELDA, null).andExpect(status().isCreated()));
        String other = tokenFor(savedUser("other"));

        update(other, raulsEntry, "{\"status\":\"DROPPED\",\"rating\":1}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Library entry " + raulsEntry + " was not found"));

        UUID missing = UUID.randomUUID();
        update(other, missing, "{\"status\":\"DROPPED\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Library entry " + missing + " was not found"));

        UserGameEntry untouched = entries.findById(raulsEntry).orElseThrow();
        assertEquals(GameStatus.PLAN_TO_PLAY, untouched.getStatus());
        assertNull(untouched.getRating());
    }

    @Test
    void invalidUpdateBodyIs400() throws Exception {
        String token = tokenFor(savedUser("raul"));
        UUID zelda = entryId(add(token, ZELDA, null).andExpect(status().isCreated()));

        update(token, zelda, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("status"));
        update(token, zelda, "{\"status\":\"PLAYING\",\"rating\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("rating"));
        update(token, zelda, "{\"status\":\"PLAYING\",\"rating\":11}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("rating"));
        update(token, zelda, "{\"status\":\"PLAYING\",\"hoursPlayed\":-1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("hoursPlayed"));
        update(token, zelda, "{\"status\":\"PLAYING\",\"platformPlayed\":\"%s\"}".formatted("x".repeat(51)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("platformPlayed"));
        update(token, zelda, "{\"status\":\"COMPLETED\",\"startedAt\":\"2024-05-01\",\"completedAt\":\"2024-04-30\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("completedAt"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be before startedAt"));
        update(token, zelda, "{\"status\":\"BACKLOG\"}")
                .andExpect(status().isBadRequest());

        assertEquals(GameStatus.PLAN_TO_PLAY, entries.findById(zelda).orElseThrow().getStatus());
    }

    @Test
    void sameDayStartAndCompletionIsAccepted() throws Exception {
        String token = tokenFor(savedUser("raul"));
        UUID zelda = entryId(add(token, ZELDA, null).andExpect(status().isCreated()));

        update(token, zelda, "{\"status\":\"COMPLETED\",\"startedAt\":\"2024-05-01\",\"completedAt\":\"2024-05-01\"}")
                .andExpect(status().isOk());
    }

    @Test
    void malformedEntryIdIs400() throws Exception {
        mockMvc.perform(delete("/api/library/not-a-uuid")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(savedUser("raul"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("entryId"));
    }

    @Test
    void deleteRemovesTheEntryButKeepsTheGame() throws Exception {
        String token = tokenFor(savedUser("raul"));
        UUID zelda = entryId(add(token, ZELDA, null).andExpect(status().isCreated()));

        mockMvc.perform(delete("/api/library/" + zelda).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        assertFalse(entries.existsById(zelda));
        assertTrue(games.existsById(ZELDA));
        mockMvc.perform(get("/api/library").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$", hasSize(0)));

        // The unique (user, game) slot is free again.
        add(token, ZELDA, null).andExpect(status().isCreated());
    }

    @Test
    void deletingAnotherUsersEntryIs404AndKeepsIt() throws Exception {
        UUID raulsEntry = entryId(add(tokenFor(savedUser("raul")), ZELDA, null).andExpect(status().isCreated()));
        String other = tokenFor(savedUser("other"));

        mockMvc.perform(delete("/api/library/" + raulsEntry).header(HttpHeaders.AUTHORIZATION, "Bearer " + other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Library entry " + raulsEntry + " was not found"));
        mockMvc.perform(delete("/api/library/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, "Bearer " + other))
                .andExpect(status().isNotFound());

        assertTrue(entries.existsById(raulsEntry));
    }

    private ResultActions update(String token, UUID entryId, String body) throws Exception {
        return mockMvc.perform(put("/api/library/" + entryId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static UUID entryId(ResultActions created) throws Exception {
        String body = created.andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
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
