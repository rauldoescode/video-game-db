package com.rauldoescode.video_game_db.library;

/**
 * The game is already in this user's library. The handler turns this into a 409.
 */
public class LibraryEntryConflictException extends RuntimeException {

    private final long igdbId;

    /**
     * @param igdbId the IGDB game id that is already in the library
     */
    public LibraryEntryConflictException(long igdbId) {
        super("Game " + igdbId + " is already in your library");
        this.igdbId = igdbId;
    }

    /**
     * @return the IGDB game id that is already in the library
     */
    public long igdbId() {
        return igdbId;
    }
}
