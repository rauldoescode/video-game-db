package com.rauldoescode.video_game_db.igdb;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IgdbCategoryTest {

    @Test
    void trendingIsIgdbVisits() {
        assertEquals(1, IgdbCategory.TRENDING.popularityType());
    }

    @Test
    void wantToPlayIsIgdbWantToPlay() {
        assertEquals(2, IgdbCategory.WANT_TO_PLAY.popularityType());
    }
}
