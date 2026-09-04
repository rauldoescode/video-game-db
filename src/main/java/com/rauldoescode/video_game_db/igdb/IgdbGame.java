package com.rauldoescode.video_game_db.igdb;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Represents a game returned by the IGDB /games endpoint
 * @param id IGDB game ID
 * @param name game name
 * @param slug game slug
 * @param summary game summary
 * @param storyline game storyline
 * @param cover game cover
 * @param firstReleaseDate game release date
 * @param genres game genres
 * @param platforms game platforms
 * @param aggregatedRating game aggregated rating
 * @param screenshots game screenshots
 * @param videos game videos
 */
public record IgdbGame(
        long id,
        String name,
        String slug,
        String summary,
        String storyline,
        Cover cover,
        @JsonProperty("first_release_date") Long firstReleaseDate,
        List<Named> genres,
        List<Named> platforms,
        @JsonProperty("aggregated_rating") Double aggregatedRating,
        List<Cover> screenshots,
        List<Video> videos
) {
    // Helps with deserialization
    public record Cover(@JsonProperty("image_id") String imageId) {}
    public record Named(String name) {}
    public record Video(@JsonProperty("video_id") String videoId) {}
}
