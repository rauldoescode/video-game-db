package com.rauldoescode.video_game_db.auth;

/**
 * A presented refresh token cannot be used. The handler turns every reason into the same 401.
 */
public class InvalidRefreshTokenException extends RuntimeException {

    /**
     * Why the token was rejected.
     * {@code UNKNOWN} means nothing is stored for it.
     * {@code EXPIRED} means it is the current token and its lifetime has ended.
     * {@code REUSED} means it was already rotated or revoked, so the whole family was revoked.
     */
    public enum Reason {
        UNKNOWN,
        EXPIRED,
        REUSED
    }

    private final Reason reason;

    /**
     * @param reason why the token was rejected
     */
    public InvalidRefreshTokenException(Reason reason) {
        super("Refresh token was rejected: " + reason);
        this.reason = reason;
    }

    /**
     * @return why the token was rejected
     */
    public Reason reason() {
        return reason;
    }
}
