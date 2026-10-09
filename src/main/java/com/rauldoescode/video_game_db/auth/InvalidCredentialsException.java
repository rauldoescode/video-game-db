package com.rauldoescode.video_game_db.auth;

/**
 * Login failed. The message is the same for an unknown email and a wrong password.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
