package com.rauldoescode.video_game_db.library;

/**
 * Where a game sits in a user's library. The same strings are in the {@code chk_status}
 * constraint, the JSON, and the UI.
 */
public enum GameStatus {
    PLAN_TO_PLAY,
    PLAYING,
    COMPLETED,
    ON_HOLD,
    DROPPED
}
