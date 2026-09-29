package com.rauldoescode.video_game_db.igdb;

/**
 * Which IGDB popularity primitive backs {@code GET /api/games/popular}. The number is IGDB's
 * {@code popularity_type} id, used in the Apicalypse {@code where}. The enum name is the cache-key
 * segment, so {@code TRENDING} at size 20 is stored as {@code TRENDING:20}.
 */
public enum IgdbCategory {
    /** IGDB visits. */
    TRENDING(1),
    /** IGDB want-to-play count. */
    WANT_TO_PLAY(2);

    private final int popularityType;

    /**
     * @param popularityType IGDB's {@code popularity_type} id for this category
     */
    IgdbCategory(int popularityType) {
        this.popularityType = popularityType;
    }

    /**
     * @return the IGDB {@code popularity_type} id
     */
    public int popularityType() {
        return popularityType;
    }
}
