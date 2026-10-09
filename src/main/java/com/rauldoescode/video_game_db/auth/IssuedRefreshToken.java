package com.rauldoescode.video_game_db.auth;

import java.time.Instant;
import java.util.UUID;

/**
 * A refresh token just written. {@code value} is the secret for the cookie; only its hash is stored.
 *
 * @param value     raw token, 32 bytes encoded as base64url without padding
 * @param familyId  login this token belongs to
 * @param expiresAt when this token stops being accepted
 * @param userId    account the token represents
 */
public record IssuedRefreshToken(String value, UUID familyId, Instant expiresAt, UUID userId) {
}
