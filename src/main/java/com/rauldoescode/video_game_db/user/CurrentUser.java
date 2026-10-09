package com.rauldoescode.video_game_db.user;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Reads the signed-in user's id from the access token.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /**
     * {@code sub} is the user UUID. A value this app did not issue is an auth failure,
     * because {@link com.rauldoescode.video_game_db.exception.GlobalExceptionHandler} maps a
     * raw {@link IllegalArgumentException} onto a validation error.
     *
     * @param jwt the verified access token
     * @return the user id
     */
    public static UUID id(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token subject is not a user id");
        }
    }
}
