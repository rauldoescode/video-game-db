package com.rauldoescode.video_game_db.exception;

/**
 * IGDB has no game for the id that was requested. The handler turns this into a 404 ProblemDetail.
 */
public class GameNotFoundException extends RuntimeException {

    private final long igdbId;

    /**
     * @param igdbId the IGDB game id that was not found
     */
    public GameNotFoundException(long igdbId) {
        super("Game " + igdbId + " was not found");
        this.igdbId = igdbId;
    }

    /**
     * @return the IGDB game id that was not found
     */
    public long igdbId() {
        return igdbId;
    }
}
