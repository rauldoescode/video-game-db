package com.rauldoescode.video_game_db.auth;

import com.rauldoescode.video_game_db.TestcontainersConfiguration;
import com.rauldoescode.video_game_db.user.User;
import com.rauldoescode.video_game_db.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Not rolled back with the slice transaction. Reuse detection throws after writing
 * {@code revoked_at}, and that write has to be visible to the next transaction.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
        TestcontainersConfiguration.class,
        RefreshTokenService.class,
        RefreshTokenServiceTest.ClockConfig.class
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RefreshTokenServiceTest {

    private static final Instant START = Instant.parse("2026-10-06T07:00:00Z");
    private static final Duration TTL = Duration.ofDays(30);

    @Autowired
    RefreshTokenService service;

    @Autowired
    RefreshTokenRepository tokens;

    @Autowired
    UserRepository users;

    @Autowired
    MutableClock clock;

    @BeforeEach
    void cleanTables() {
        tokens.deleteAll();
        users.deleteAll();
        clock.set(START);
    }

    @Test
    void issueStoresTheHashAndNotTheRawValue() {
        IssuedRefreshToken issued = service.issue(savedUser("raul"));

        assertTrue(tokens.findByTokenHash(issued.value()).isEmpty());
        RefreshToken row = tokens.findByTokenHash(sha256Hex(issued.value())).orElseThrow();
        assertEquals(64, row.getTokenHash().length());
        assertEquals(issued.familyId(), row.getFamilyId());
        assertEquals(issued.expiresAt(), row.getExpiresAt());
        assertEquals(START.plus(TTL), row.getExpiresAt());
        assertNull(row.getRevokedAt());
        assertNotNull(row.getCreatedAt());
        assertEquals(32, Base64.getUrlDecoder().decode(issued.value()).length);
        assertEquals(savedUserId("raul"), row.getUser().getId());
        assertEquals(row.getUser().getId(), issued.userId());
    }

    @Test
    void rotateRevokesThePresentedTokenAndKeepsTheFamily() {
        IssuedRefreshToken first = service.issue(savedUser("raul"));
        clock.advance(Duration.ofHours(1));

        IssuedRefreshToken second = service.rotate(first.value());

        assertEquals(first.familyId(), second.familyId());
        assertNotEquals(first.value(), second.value());
        assertEquals(START.plus(Duration.ofHours(1)).plus(TTL), second.expiresAt());

        RefreshToken oldRow = tokens.findByTokenHash(sha256Hex(first.value())).orElseThrow();
        RefreshToken newRow = tokens.findByTokenHash(sha256Hex(second.value())).orElseThrow();
        assertEquals(START.plus(Duration.ofHours(1)), oldRow.getRevokedAt());
        assertNull(newRow.getRevokedAt());
        assertEquals(oldRow.getUser().getId(), newRow.getUser().getId());
    }

    @Test
    void rotateStillWorksOneSecondBeforeExpiry() {
        IssuedRefreshToken issued = service.issue(savedUser("raul"));
        clock.advance(TTL.minusSeconds(1));

        IssuedRefreshToken next = service.rotate(issued.value());

        assertEquals(issued.familyId(), next.familyId());
    }

    @Test
    void rotateRejectsATokenAtItsExpiry() {
        IssuedRefreshToken issued = service.issue(savedUser("raul"));
        clock.advance(TTL);

        InvalidRefreshTokenException ex = assertThrows(
                InvalidRefreshTokenException.class, () -> service.rotate(issued.value()));

        assertEquals(InvalidRefreshTokenException.Reason.EXPIRED, ex.reason());
        RefreshToken row = tokens.findByTokenHash(sha256Hex(issued.value())).orElseThrow();
        assertNull(row.getRevokedAt());
        assertEquals(1, tokens.findByFamilyId(issued.familyId()).size());
    }

    @Test
    void reuseOfARevokedTokenRevokesTheFamily() {
        IssuedRefreshToken first = service.issue(savedUser("raul"));
        clock.advance(Duration.ofMinutes(1));
        IssuedRefreshToken second = service.rotate(first.value());
        clock.advance(Duration.ofMinutes(1));

        InvalidRefreshTokenException ex = assertThrows(
                InvalidRefreshTokenException.class, () -> service.rotate(first.value()));

        assertEquals(InvalidRefreshTokenException.Reason.REUSED, ex.reason());
        RefreshToken original = tokens.findByTokenHash(sha256Hex(first.value())).orElseThrow();
        RefreshToken successor = tokens.findByTokenHash(sha256Hex(second.value())).orElseThrow();
        assertEquals(START.plus(Duration.ofMinutes(1)), original.getRevokedAt());
        assertEquals(START.plus(Duration.ofMinutes(2)), successor.getRevokedAt());

        InvalidRefreshTokenException again = assertThrows(
                InvalidRefreshTokenException.class, () -> service.rotate(second.value()));
        assertEquals(InvalidRefreshTokenException.Reason.REUSED, again.reason());
    }

    @Test
    void revokeFamilyLeavesAnotherLoginAlone() {
        User user = savedUser("raul");
        IssuedRefreshToken loginA = service.issue(user);
        IssuedRefreshToken loginB = service.issue(user);

        service.revokeFamily(loginA.value());

        RefreshToken a = tokens.findByTokenHash(sha256Hex(loginA.value())).orElseThrow();
        RefreshToken b = tokens.findByTokenHash(sha256Hex(loginB.value())).orElseThrow();
        assertNotNull(a.getRevokedAt());
        assertNull(b.getRevokedAt());
        assertNotEquals(loginA.familyId(), loginB.familyId());
    }

    @Test
    void unknownOrBlankTokenIsRejected() {
        assertEquals(InvalidRefreshTokenException.Reason.UNKNOWN, assertThrows(
                InvalidRefreshTokenException.class, () -> service.rotate("not-a-token")).reason());
        assertEquals(InvalidRefreshTokenException.Reason.UNKNOWN, assertThrows(
                InvalidRefreshTokenException.class, () -> service.rotate("  ")).reason());
        assertEquals(InvalidRefreshTokenException.Reason.UNKNOWN, assertThrows(
                InvalidRefreshTokenException.class, () -> service.rotate(null)).reason());
        assertEquals(InvalidRefreshTokenException.Reason.UNKNOWN, assertThrows(
                InvalidRefreshTokenException.class, () -> service.revokeFamily("missing")).reason());
        assertEquals(0, tokens.count());
    }

    @Test
    void refreshTtlMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new RefreshTokenProperties(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new RefreshTokenProperties(Duration.ofDays(-1)));
        assertThrows(IllegalArgumentException.class, () -> new RefreshTokenProperties(null));
    }

    private User savedUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("not-a-real-hash");
        return users.saveAndFlush(user);
    }

    private UUID savedUserId(String username) {
        return users.findByUsername(username).orElseThrow().getId();
    }

    /**
     * Independent of {@link RefreshTokenService}: the column must be lowercase SHA-256 hex.
     */
    private static String sha256Hex(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @TestConfiguration
    static class ClockConfig {

        @Bean
        MutableClock clock() {
            return new MutableClock(START);
        }

        @Bean
        RefreshTokenProperties refreshTokenProperties() {
            return new RefreshTokenProperties(TTL);
        }
    }

    static final class MutableClock extends Clock {

        private final ZoneId zone = ZoneOffset.UTC;
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
