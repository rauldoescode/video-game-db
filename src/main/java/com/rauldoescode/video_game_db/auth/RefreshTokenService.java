package com.rauldoescode.video_game_db.auth;

import com.rauldoescode.video_game_db.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Issues and rotates opaque refresh tokens. A family is the chain of tokens that
 * started at one login: rotation revokes the presented token and appends a new one
 * with the same {@code familyId}. Presenting a token that was already revoked
 * revokes every token still live in that family.
 */
@Service
public class RefreshTokenService {

    private static final int RAW_TOKEN_BYTES = 32;

    private final RefreshTokenRepository tokens;
    private final RefreshTokenProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    /**
     * @param tokens     stored hashes
     * @param properties lifetime applied to each newly issued token
     * @param clock      source of {@code expiresAt} and {@code revokedAt}, so tests can move time
     */
    public RefreshTokenService(RefreshTokenRepository tokens, RefreshTokenProperties properties, Clock clock) {
        this.tokens = tokens;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Starts a family for a new login.
     *
     * @param user account the cookie will represent
     * @return the raw token to set as the cookie
     */
    @Transactional
    public IssuedRefreshToken issue(User user) {
        return persistNew(user, UUID.randomUUID(), Instant.now(clock));
    }

    /**
     * Revokes {@code rawToken} and issues the next one in its family.
     * <p>
     * {@code noRollbackFor} is what makes reuse detection stick. This method throws
     * after revoking the family, and that revocation has to commit anyway: the request
     * fails, but the stolen chain must already be dead.
     *
     * @param rawToken the cookie value
     * @return the replacement raw token
     * @throws InvalidRefreshTokenException the cookie is unknown, expired, or already revoked
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public IssuedRefreshToken rotate(String rawToken) {
        Instant now = Instant.now(clock);
        RefreshToken current = locked(rawToken);
        if (current.getRevokedAt() != null) {
            tokens.revokeUnrevokedInFamily(current.getFamilyId(), now);
            throw new InvalidRefreshTokenException(InvalidRefreshTokenException.Reason.REUSED);
        }
        if (!current.getExpiresAt().isAfter(now)) {
            throw new InvalidRefreshTokenException(InvalidRefreshTokenException.Reason.EXPIRED);
        }
        current.setRevokedAt(now);
        return persistNew(current.getUser(), current.getFamilyId(), now);
    }

    /**
     * Revokes every still-live token in the presented token's family. Logout calls this.
     * An already-revoked cookie still resolves the family, so logout of an old cookie
     * kills the tokens that replaced it.
     *
     * @param rawToken the cookie value
     * @throws InvalidRefreshTokenException nothing is stored for this cookie
     */
    @Transactional
    public void revokeFamily(String rawToken) {
        RefreshToken current = locked(rawToken);
        tokens.revokeUnrevokedInFamily(current.getFamilyId(), Instant.now(clock));
    }

    private RefreshToken locked(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRefreshTokenException(InvalidRefreshTokenException.Reason.UNKNOWN);
        }
        return tokens.findByTokenHashForUpdate(sha256Hex(rawToken))
                .orElseThrow(() -> new InvalidRefreshTokenException(InvalidRefreshTokenException.Reason.UNKNOWN));
    }

    private IssuedRefreshToken persistNew(User user, UUID familyId, Instant now) {
        String raw = newRawToken();
        RefreshToken row = new RefreshToken();
        row.setUser(user);
        row.setTokenHash(sha256Hex(raw));
        row.setFamilyId(familyId);
        row.setExpiresAt(now.plus(properties.ttl()));
        tokens.saveAndFlush(row);
        return new IssuedRefreshToken(raw, familyId, row.getExpiresAt(), user.getId());
    }

    private String newRawToken() {
        byte[] bytes = new byte[RAW_TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
