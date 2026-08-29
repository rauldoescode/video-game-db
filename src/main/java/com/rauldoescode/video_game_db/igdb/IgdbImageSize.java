package com.rauldoescode.video_game_db.igdb;

public enum IgdbImageSize {
    COVER_BIG("cover_big"), // search results cards
    COVER_BIG_2X("cover_big_2x"), // details page hero
    SCREENSHOT_HUGE("screenshot_huge"), // gallery
    THUMB("thumb"); // compact lists

    private final String token;

    IgdbImageSize(String token) {
        this.token = token;
    }

    public String getToken() {
        return token;
    }
}
