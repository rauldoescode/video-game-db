package com.rauldoescode.video_game_db.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;

/**
 * An existing account. The email is trimmed and lowercased so it matches registration.
 *
 * @param email    address stored on the account
 * @param password the raw password; a wrong value is rejected as invalid credentials, not as a field error
 */
public record LoginRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank String password
) {

    public LoginRequest {
        if (email != null) {
            email = email.trim().toLowerCase(Locale.ROOT);
        }
    }
}
