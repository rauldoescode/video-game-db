package com.rauldoescode.video_game_db.auth;

import com.jayway.jsonpath.JsonPath;
import com.rauldoescode.video_game_db.TestcontainersConfiguration;
import com.rauldoescode.video_game_db.user.User;
import com.rauldoescode.video_game_db.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.servlet.http.Cookie;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    private static final String PASSWORD = "correct-horse";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository users;

    @Autowired
    RefreshTokenRepository tokens;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JwtDecoder jwtDecoder;

    @BeforeEach
    void cleanTables() {
        tokens.deleteAll();
        users.deleteAll();
    }

    @Test
    void registerReturnsProfileAndAccessTokenAndRefreshCookies() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"  raul  ","email":"Raul@Example.com","password":"%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.username").value("raul"))
                .andExpect(jsonPath("$.user.email").value("raul@example.com"))
                .andExpect(jsonPath("$.user.isProfilePublic").value(true))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        List<String> cookies = refreshCookies(result);
        String rawRefresh = cookieValue(cookies.get(0));
        assertEquals(rawRefresh, cookieValue(cookies.get(1)));
        assertFalse(body.contains(rawRefresh));

        String accessToken = JsonPath.read(body, "$.accessToken");
        Jwt jwt = jwtDecoder.decode(accessToken);
        String userId = JsonPath.read(body, "$.user.id");
        assertEquals(userId, jwt.getSubject());
        assertEquals("raul", jwt.getClaimAsString("username"));
        assertEquals("access", jwt.getClaimAsString("token_use"));

        User user = users.findByUsername("raul").orElseThrow();
        assertEquals("raul@example.com", user.getEmail());
        assertNotEquals(PASSWORD, user.getPasswordHash());
        assertTrue(user.getPasswordHash().startsWith("$2a$"));
        assertTrue(passwordEncoder.matches(PASSWORD, user.getPasswordHash()));

        RefreshToken stored = tokens.findAll().get(0);
        assertEquals(sha256Hex(rawRefresh), stored.getTokenHash());
        assertEquals(user.getId(), stored.getUser().getId());

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("raul"));
    }

    @Test
    void loginAcceptsTheRegisteredEmailIgnoringCase() throws Exception {
        register("raul", "raul@example.com");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"  RAUL@example.com  ","password":"%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("raul"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        assertEquals(2, refreshCookies(result).size());
        assertEquals(2, tokens.count());
    }

    @Test
    void duplicateUsernameOrEmailIs409() throws Exception {
        register("raul", "raul@example.com");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"raul","email":"other@example.com","password":"%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].field").value("username"))
                .andExpect(jsonPath("$.errors[0].message").value("Username is already registered"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"other","email":"RAUL@example.com","password":"%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].message").value("Email is already registered"));

        assertEquals(1, users.count());
    }

    @Test
    void unknownEmailAndWrongPasswordLookTheSame() throws Exception {
        register("raul", "raul@example.com");

        MvcResult unknown = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nobody@example.com","password":"%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"))
                .andReturn();

        MvcResult wrong = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"raul@example.com","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"))
                .andReturn();

        assertEquals(unknown.getResponse().getContentAsString(), wrong.getResponse().getContentAsString());
        assertEquals(1, tokens.count());
    }

    @Test
    void invalidRegisterBodyIs400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"ab","email":"not-an-email","password":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors[?(@.field == 'username')].message",
                        hasItem("size must be between 3 and 50")))
                .andExpect(jsonPath("$.errors[?(@.field == 'email')].message",
                        hasItem("must be a well-formed email address")))
                .andExpect(jsonPath("$.errors[?(@.field == 'password')].message",
                        hasItem("size must be between 8 and 72")));

        assertEquals(0, users.count());
    }

    @Test
    void refreshRotatesTheCookieAndReturnsANewAccessToken() throws Exception {
        String first = cookieValue(refreshCookies(register("raul", "raul@example.com")).get(0));

        MvcResult result = mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("raul"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        String second = cookieValue(refreshCookies(result).get(0));
        assertNotEquals(first, second);

        RefreshToken oldRow = tokens.findByTokenHash(sha256Hex(first)).orElseThrow();
        RefreshToken newRow = tokens.findByTokenHash(sha256Hex(second)).orElseThrow();
        assertNotNull(oldRow.getRevokedAt());
        assertNull(newRow.getRevokedAt());
        assertEquals(oldRow.getFamilyId(), newRow.getFamilyId());

        String accessToken = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("raul"));
    }

    @Test
    void reusingARotatedCookieIs401AndRevokesTheFamily() throws Exception {
        String first = cookieValue(refreshCookies(register("raul", "raul@example.com")).get(0));
        MvcResult rotated = mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie(first)))
                .andExpect(status().isOk())
                .andReturn();
        String second = cookieValue(refreshCookies(rotated).get(0));

        MvcResult reuse = mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie(first)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Refresh token is invalid"))
                .andExpect(jsonPath("$.instance").value("/api/auth/refresh"))
                .andReturn();
        assertCleared(reuse);

        // The revocation committed even though the request failed, so the successor is dead too.
        assertTrue(tokens.findAll().stream().allMatch(token -> token.getRevokedAt() != null));
        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie(second)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithoutOrWithAnUnknownCookieIs401() throws Exception {
        assertCleared(mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Refresh token is invalid"))
                .andReturn());

        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie("not-a-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Refresh token is invalid"));
    }

    @Test
    void refreshIgnoresAStaleBearerHeader() throws Exception {
        String raw = cookieValue(refreshCookies(register("raul", "raul@example.com")).get(0));

        mockMvc.perform(post("/api/auth/refresh")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer expired.or.garbage")
                        .cookie(refreshCookie(raw)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void logoutRevokesTheFamilyAndClearsBothCookies() throws Exception {
        String raw = cookieValue(refreshCookies(register("raul", "raul@example.com")).get(0));
        String otherLogin = cookieValue(refreshCookies(login("raul@example.com")).get(0));

        MvcResult result = mockMvc.perform(post("/api/auth/logout").cookie(refreshCookie(raw)))
                .andExpect(status().isNoContent())
                .andReturn();
        assertCleared(result);

        assertNotNull(tokens.findByTokenHash(sha256Hex(raw)).orElseThrow().getRevokedAt());
        assertNull(tokens.findByTokenHash(sha256Hex(otherLogin)).orElseThrow().getRevokedAt());
        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie(raw)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie(otherLogin)))
                .andExpect(status().isOk());
    }

    @Test
    void logoutWithoutOrWithAnUnknownCookieIsStill204() throws Exception {
        assertCleared(mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent())
                .andReturn());

        assertCleared(mockMvc.perform(post("/api/auth/logout").cookie(refreshCookie("not-a-token")))
                .andExpect(status().isNoContent())
                .andReturn());
    }

    private MvcResult login(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private static Cookie refreshCookie(String value) {
        return new Cookie(RefreshTokenCookies.NAME, value);
    }

    private static void assertCleared(MvcResult result) {
        List<String> cookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertEquals(2, cookies.size());
        assertTrue(cookies.stream().anyMatch(cookie -> cookie.contains("Path=/api/auth/refresh")));
        assertTrue(cookies.stream().anyMatch(cookie -> cookie.contains("Path=/api/auth/logout")));
        for (String cookie : cookies) {
            assertTrue(cookie.startsWith(RefreshTokenCookies.NAME + "=;"));
            assertTrue(cookie.contains("Max-Age=0"));
            assertTrue(cookie.contains("HttpOnly"));
            assertTrue(cookie.contains("Secure"));
            assertTrue(cookie.contains("SameSite=Lax"));
        }
    }

    private MvcResult register(String username, String email) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s","password":"%s"}
                                """.formatted(username, email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private static List<String> refreshCookies(MvcResult result) {
        List<String> cookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertEquals(2, cookies.size());
        assertTrue(cookies.stream().anyMatch(cookie -> cookie.contains("Path=/api/auth/refresh")));
        assertTrue(cookies.stream().anyMatch(cookie -> cookie.contains("Path=/api/auth/logout")));
        for (String cookie : cookies) {
            assertTrue(cookie.startsWith(RefreshTokenCookies.NAME + "="));
            assertTrue(cookie.contains("HttpOnly"));
            assertTrue(cookie.contains("Secure"));
            assertTrue(cookie.contains("SameSite=Lax"));
        }
        return cookies;
    }

    private static String cookieValue(String setCookie) {
        int start = (RefreshTokenCookies.NAME + "=").length();
        return setCookie.substring(start, setCookie.indexOf(';'));
    }

    private static String sha256Hex(String rawToken) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(rawToken.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
