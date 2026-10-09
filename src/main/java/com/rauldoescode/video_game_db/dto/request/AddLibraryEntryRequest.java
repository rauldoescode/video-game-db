package com.rauldoescode.video_game_db.dto.request;

import com.rauldoescode.video_game_db.library.GameStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Adds a game to the signed-in user's library.
 *
 * @param igdbId the IGDB game id
 * @param status starting status, or null for {@code PLAN_TO_PLAY}
 */
public record AddLibraryEntryRequest(
        @NotNull @Positive Long igdbId,
        GameStatus status
) {}
