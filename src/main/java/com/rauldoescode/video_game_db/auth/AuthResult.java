package com.rauldoescode.video_game_db.auth;

import com.rauldoescode.video_game_db.dto.response.UserResponse;

/**
 * What register and login produce. The controller copies {@code refreshToken} into cookies
 * and leaves it out of the JSON body.
 *
 * @param user         profile to return
 * @param accessToken  bearer token to return
 * @param refreshToken raw refresh token for the cookie only
 */
public record AuthResult(UserResponse user, String accessToken, IssuedRefreshToken refreshToken) {
}
