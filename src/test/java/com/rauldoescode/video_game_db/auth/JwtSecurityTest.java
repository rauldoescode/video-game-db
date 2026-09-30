package com.rauldoescode.video_game_db.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.rauldoescode.video_game_db.TestcontainersConfiguration;
import com.rauldoescode.video_game_db.dto.response.PageResponse;
import com.rauldoescode.video_game_db.game.GameService;
import com.rauldoescode.video_game_db.user.User;
import com.rauldoescode.video_game_db.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class JwtSecurityTest {

    private static final String WRONG_SECRET = "wrong-secret-wrong-secret-wrong-sec";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository users;

    @Autowired
    AccessTokenService accessTokens;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    JwtProperties jwtProperties;

    @MockitoBean
    GameService gameService;

    private User user;

    @BeforeEach
    void saveUser() {
        users.deleteAll();
        User row = new User();
        row.setUsername("raul");
        row.setEmail("raul@example.com");
        row.setPasswordHash("not-a-real-hash");
        row.setBio("hello");
        user = users.save(row);
    }

    @Test
    void mintedTokenReturnsTheProfile() throws Exception {
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(accessTokens.issue(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.username").value("raul"))
                .andExpect(jsonPath("$.email").value("raul@example.com"))
                .andExpect(jsonPath("$.bio").value("hello"))
                .andExpect(jsonPath("$.isProfilePublic").value(true))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void missingTokenIs401() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIs401() throws Exception {
        // The decoder allows 60 seconds of clock skew, so the token is issued far enough
        // in the past that exp is already behind that window.
        AccessTokenService shifted = new AccessTokenService(
                jwtEncoder, jwtProperties, Clock.offset(Clock.systemUTC(), Duration.ofMinutes(-20)));

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(shifted.issue(user))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongSecretIs401() throws Exception {
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(signedWith(WRONG_SECRET, "access"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshTokenUseIs401() throws Exception {
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(signedWith(jwtProperties.secret(), "refresh"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void gameSearchStaysPublic() throws Exception {
        when(gameService.search(any(), isNull(), isNull(), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, -1, -1));

        mockMvc.perform(get("/api/games/search").param("q", "zelda"))
                .andExpect(status().isOk());
    }

    private String signedWith(String secret, String tokenUse) {
        SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .issuedAt(now)
                .expiresAt(now.plus(jwtProperties.accessTtl()))
                .subject(user.getId().toString())
                .claim("username", user.getUsername())
                .claim("token_use", tokenUse)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return new NimbusJwtEncoder(new ImmutableSecret<>(key))
                .encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
