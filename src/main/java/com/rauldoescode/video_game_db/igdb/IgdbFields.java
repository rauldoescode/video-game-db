package com.rauldoescode.video_game_db.igdb;

public final class IgdbFields {

    public static final String[] GAME = {
            "id",
            "name",
            "slug",
            "summary",
            "storyline",
            "cover.image_id",
            "first_release_date",
            "genres.name",
            "platforms.name",
            "aggregated_rating",
            "screenshots.image_id",
            "videos.video_id"
    };

    private IgdbFields() {}
}
