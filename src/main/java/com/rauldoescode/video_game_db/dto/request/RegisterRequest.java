package com.rauldoescode.video_game_db.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;

/**
 * A new account. Username and email are trimmed before validation; email is stored lowercase.
 *
 * @param username 3–50 characters, the column width
 * @param email    address used at login
 * @param password 8–72 characters; BCrypt only uses the first 72 bytes
 */
public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 50) String username,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password
) {

    public RegisterRequest {
        if (username != null) {
            username = username.trim();
        }
        if (email != null) {
            email = email.trim().toLowerCase(Locale.ROOT);
        }
    }
}
