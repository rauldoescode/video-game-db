package com.rauldoescode.video_game_db.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * The search card for a game
 * @param id IGDB game ID
 * @param name game name
 * @param slug game slug
 * @param coverUrl game cover URL
 * @param firstReleaseDate game release date
 * @param genres game genres
 * @param platforms game platforms
 * @param aggregatedRating game aggregated rating
 */
public record GameSummaryResponse(
        long id,
        String name,
        String slug,
        String coverUrl,
        LocalDate firstReleaseDate,
        List<String> genres,
        List<String> platforms,
        Double aggregatedRating
) {}
