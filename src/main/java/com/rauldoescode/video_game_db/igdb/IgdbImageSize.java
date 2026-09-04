package com.rauldoescode.video_game_db.igdb;

/**
 * Image sizes for IGDB images
 */
public enum IgdbImageSize {
    COVER_BIG("cover_big"), // search results cards
    COVER_BIG_2X("cover_big_2x"), // details page hero
    SCREENSHOT_HUGE("screenshot_huge"), // gallery
    THUMB("thumb"); // compact lists

    private final String token;

    /**
     * Creates a new IgdbImageSize.
     * @param token the token to use for the image size
     */
    IgdbImageSize(String token) {
        this.token = token;
    }

    /**
     * Returns the token for the image size.
     * @return the token
     */
    public String getToken() {
        return token;
    }
}
