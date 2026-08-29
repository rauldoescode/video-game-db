package com.rauldoescode.video_game_db.igdb;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IgdbImageUrlsTest {

    @Test
    void coverBigBuildsHttpsUrl() {
        assertEquals(
                "https://images.igdb.com/igdb/image/upload/t_cover_big/co1r7f.jpg",
                IgdbImageUrls.url("co1r7f", IgdbImageSize.COVER_BIG)
        );
    }

    @Test
    void coverBig2xBuildsHttpsUrl() {
        assertEquals(
                "https://images.igdb.com/igdb/image/upload/t_cover_big_2x/co1r7f.jpg",
                IgdbImageUrls.url("co1r7f", IgdbImageSize.COVER_BIG_2X)
        );
    }

    @Test
    void screenshotHugeBuildsHttpsUrl() {
        assertEquals(
                "https://images.igdb.com/igdb/image/upload/t_screenshot_huge/co1r7f.jpg",
                IgdbImageUrls.url("co1r7f", IgdbImageSize.SCREENSHOT_HUGE)
        );
    }

    @Test
    void thumbBuildsHttpsUrl() {
        assertEquals(
                "https://images.igdb.com/igdb/image/upload/t_thumb/co1r7f.jpg",
                IgdbImageUrls.url("co1r7f", IgdbImageSize.THUMB)
        );
    }

    // Handles empty and whitespace-only image ids
    @Test
    void blankImageIdReturnsNull() {
        assertNull(IgdbImageUrls.url("  ", IgdbImageSize.THUMB));
    }

    @Test
    void nullImageIdReturnsNull() {
        assertNull(IgdbImageUrls.url(null, IgdbImageSize.THUMB));
    }
}
