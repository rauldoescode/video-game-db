package com.rauldoescode.video_game_db.library;

import java.util.UUID;

/**
 * No entry with this id in the signed-in user's library. Another user's entry is reported the
 * same way, so an id never confirms that someone else's entry exists. The handler turns this into a 404.
 */
public class LibraryEntryNotFoundException extends RuntimeException {

    private final UUID entryId;

    /**
     * @param entryId the library entry id that was not found
     */
    public LibraryEntryNotFoundException(UUID entryId) {
        super("Library entry " + entryId + " was not found");
        this.entryId = entryId;
    }

    /**
     * @return the library entry id that was not found
     */
    public UUID entryId() {
        return entryId;
    }
}
