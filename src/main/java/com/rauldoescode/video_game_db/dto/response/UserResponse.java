package com.rauldoescode.video_game_db.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * The signed-in user's profile.
 *
 * @param id              user id, the same value as the access token's {@code sub}
 * @param username        unique username
 * @param email           unique email
 * @param bio             profile text, or null
 * @param avatarUrl       avatar URL, or null
 * @param isProfilePublic whether other users may see this profile
 * @param createdAt       when the account was created
 */
public record UserResponse(
        UUID id,
        String username,
        String email,
        String bio,
        String avatarUrl,
        @JsonProperty("isProfilePublic") boolean isProfilePublic,
        Instant createdAt
) {}
