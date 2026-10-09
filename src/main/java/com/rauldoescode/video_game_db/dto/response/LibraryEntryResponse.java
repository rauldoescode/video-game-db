package com.rauldoescode.video_game_db.dto.response;

import com.rauldoescode.video_game_db.library.GameStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One game in the signed-in user's library.
 *
 * @param id             entry id, used by the update and delete routes
 * @param game           the stored game
 * @param status         where the game sits in the library
 * @param rating         1–10, or null
 * @param review         review text, or null
 * @param hoursPlayed    hours played, 0 by default
 * @param platformPlayed platform name, or null
 * @param startedAt      date started, or null
 * @param completedAt    date completed, or null
 * @param createdAt      when the game was added
 * @param updatedAt      last change, the default sort
 */
public record LibraryEntryResponse(
        UUID id,
        Game game,
        GameStatus status,
        Integer rating,
        String review,
        int hoursPlayed,
        String platformPlayed,
        LocalDate startedAt,
        LocalDate completedAt,
        Instant createdAt,
        Instant updatedAt
) {

    /**
     * The game as stored in {@code games}. Genres and platforms are not stored, so they are not here.
     *
     * @param id               IGDB game id
     * @param name             game name
     * @param slug             game slug
     * @param coverUrl         https cover URL at the card size, or null
     * @param firstReleaseDate release date, or null
     * @param aggregatedRating IGDB critic rating, or null
     */
    public record Game(
            long id,
            String name,
            String slug,
            String coverUrl,
            LocalDate firstReleaseDate,
            Double aggregatedRating
    ) {}
}
