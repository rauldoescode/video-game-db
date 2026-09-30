package com.rauldoescode.video_game_db.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.rauldoescode.video_game_db.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccessTokenServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void issueWritesAccessClaimsAndFifteenMinuteExpiry() {
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        AccessTokenService service = new AccessTokenService(
                new NimbusJwtEncoder(new ImmutableSecret<>(key)),
                new JwtProperties(SECRET, Duration.ofMinutes(15), "video-game-db"),
                Clock.fixed(issuedAt, ZoneOffset.UTC));

        User user = new User();
        user.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        user.setUsername("raul");

        String token = service.issue(user);
        Jwt jwt = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build().decode(token);

        assertEquals(user.getId().toString(), jwt.getSubject());
        assertEquals("raul", jwt.getClaimAsString("username"));
        assertEquals("access", jwt.getClaimAsString("token_use"));
        assertEquals("video-game-db", jwt.getClaimAsString(JwtClaimNames.ISS));
        assertEquals(issuedAt, jwt.getIssuedAt());
        assertEquals(issuedAt.plus(Duration.ofMinutes(15)), jwt.getExpiresAt());
    }
}
