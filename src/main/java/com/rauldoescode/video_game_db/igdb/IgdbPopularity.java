package com.rauldoescode.video_game_db.igdb;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One row from IGDB {@code /popularity_primitives}. This is a ranked game id and its score, not a
 * game. A follow-up {@code /games} query does not keep this order, so the caller reloads by
 * {@code gameId} and then sorts the games back into it.
 *
 * @param gameId the IGDB game id this score belongs to
 * @param value the popularity score; higher is more popular
 */
public record IgdbPopularity(
        @JsonProperty("game_id") long gameId,
        double value
) {}
