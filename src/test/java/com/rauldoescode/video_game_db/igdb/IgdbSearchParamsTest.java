package com.rauldoescode.video_game_db.igdb;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IgdbSearchParamsTest {

    @Test
    void canonicalOmitsAbsentFiltersAndKeepsFixedOrder() {
        assertEquals("q=zelda|limit=20|offset=0", IgdbSearchParams.of("zelda").canonical());
        assertEquals(
                "q=mario|genre=platform|platform=nintendo switch|yearFrom=2017|yearTo=2020|limit=20|offset=0",
                new IgdbSearchParams("mario", "Platform", "Nintendo Switch", 2017, 2020, 20, 0).canonical()
        );
    }

    @Test
    void searchTermCaseAndPaddingDoNotChangeTheKey() {
        assertEquals(
                IgdbSearchParams.of("zelda").cacheKey(),
                IgdbSearchParams.of("  ZeLdA  ").cacheKey()
        );
    }

    @Test
    void blankFilterIsTheSameAsNoFilter() {
        assertEquals(
                IgdbSearchParams.of("zelda").cacheKey(),
                new IgdbSearchParams("zelda", "   ", null, null, null, 20, 0).cacheKey()
        );
    }

    @Test
    void differentSearchesGetDifferentKeys() {
        String zelda = IgdbSearchParams.of("zelda").cacheKey();

        assertNotEquals(zelda, IgdbSearchParams.of("mario").cacheKey());
        assertNotEquals(zelda, new IgdbSearchParams("zelda", null, null, null, null, 20, 20).cacheKey());
        assertNotEquals(zelda, new IgdbSearchParams("zelda", null, null, null, null, 50, 0).cacheKey());
        assertNotEquals(zelda, new IgdbSearchParams("zelda", "rpg", null, null, null, 20, 0).cacheKey());
    }

    @Test
    void cacheKeyIsSha256Hex() {
        // printf '%s' 'q=zelda|limit=20|offset=0' | shasum -a 256
        assertEquals(
                "40aefe8fa4b5575d4598e5943fc305f242cc19b5b6534b99e44e5cddb289a2b8",
                IgdbSearchParams.of("zelda").cacheKey()
        );
    }

    @Test
    void blankSearchTermIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> IgdbSearchParams.of(null));
        assertThrows(IllegalArgumentException.class, () -> IgdbSearchParams.of("   "));
    }

    @Test
    void limitAboveMaxClampsTo500() {
        assertEquals(500, new IgdbSearchParams("zelda", null, null, null, null, 501, 0).limit());
        assertEquals(
                new IgdbSearchParams("zelda", null, null, null, null, 500, 0).cacheKey(),
                new IgdbSearchParams("zelda", null, null, null, null, 501, 0).cacheKey()
        );
    }

    @Test
    void impossiblePagingAndYearRangesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new IgdbSearchParams("zelda", null, null, null, null, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new IgdbSearchParams("zelda", null, null, null, null, 20, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new IgdbSearchParams("zelda", null, null, 2020, 2017, 20, 0));
    }
}
