package com.rauldoescode.video_game_db.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * The full game details when you open from search card
 * @param id IGDB game ID
 * @param name game name
 * @param summary game summary
 * @param storyline game storyline
 * @param coverUrl game cover URL
 * @param firstReleaseDate game release date
 * @param genres game genres
 * @param platforms game platforms
 * @param aggregatedRating game aggregated rating
 * @param screenshotUrls game screenshots
 * @param videoUrls game videos
 */
public record GameDetailResponse(
        long id,
        String name,
        String summary,
        String storyline,
        String coverUrl,
        LocalDate firstReleaseDate,
        List<String> genres,
        List<String> platforms,
        Double aggregatedRating,
        List<String> screenshotUrls,
        List<String> videoUrls
) {}
