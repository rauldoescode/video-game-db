package com.rauldoescode.video_game_db.auth;

/**
 * Register found a username or email that is already stored. The handler turns this into a 409.
 */
public class AccountConflictException extends RuntimeException {

    private final String field;

    /**
     * @param field   {@code username} or {@code email}
     * @param message text returned as the ProblemDetail detail and the field error
     */
    public AccountConflictException(String field, String message) {
        super(message);
        this.field = field;
    }

    /**
     * @return the request field that collided
     */
    public String field() {
        return field;
    }
}
