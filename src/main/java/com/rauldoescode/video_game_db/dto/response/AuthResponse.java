package com.rauldoescode.video_game_db.dto.response;

/**
 * Register and login body. The refresh token is a cookie, not a field on this record.
 *
 * @param user        the new or existing profile
 * @param accessToken bearer token for {@code Authorization}
 */
public record AuthResponse(UserResponse user, String accessToken) {
}
