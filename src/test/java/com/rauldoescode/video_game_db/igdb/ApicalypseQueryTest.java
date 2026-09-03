package com.rauldoescode.video_game_db.igdb;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApicalypseQueryTest {

    private static final String GAME_FIELDS =
            "id,name,slug,summary,storyline,cover.image_id,first_release_date,"
                    + "genres.name,platforms.name,aggregated_rating,screenshots.image_id,videos.video_id";

    @Test
    void fieldsMatchV2List() {
        assertEquals(
                "fields " + GAME_FIELDS + ";",
                new ApicalypseQuery().fields(IgdbFields.GAME).build()
        );
    }

    @Test
    void searchDefaultsToLimit20() {
        assertEquals(
                "fields " + GAME_FIELDS + "; search \"zelda\"; limit 20;",
                new ApicalypseQuery().fields(IgdbFields.GAME).search("zelda").build()
        );
    }

    @Test
    void wildcardFieldsAreRejected() {
        ApicalypseQuery query = new ApicalypseQuery();
        assertThrows(IllegalArgumentException.class, () -> query.fields("*"));
        assertThrows(IllegalArgumentException.class, () -> query.fields("id", "*"));
    }

    @Test
    void limitAboveMaxClampsTo500() {
        assertEquals(
                "fields " + GAME_FIELDS + "; limit 500;",
                new ApicalypseQuery().fields(IgdbFields.GAME).limit(501).build()
        );
    }

    @Test
    void whereClausesJoinWithAmpersand() {
        assertEquals(
                "fields " + GAME_FIELDS + "; search \"mario\"; where platforms = 6 & first_release_date >= 1577836800; limit 20;",
                new ApicalypseQuery()
                        .fields(IgdbFields.GAME)
                        .where("platforms = 6")
                        .where("first_release_date >= 1577836800")
                        .search("mario")
                        .build()
        );
    }

    @Test
    void fieldsOnlyOmitsLimit() {
        assertEquals(
                "fields id,name;",
                new ApicalypseQuery().fields("id", "name").build()
        );
    }

    @Test
    void explicitLimitWinsOverSearchDefault() {
        assertEquals(
                "fields " + GAME_FIELDS + "; search \"zelda\"; limit 50;",
                new ApicalypseQuery().fields(IgdbFields.GAME).search("zelda").limit(50).build()
        );
        assertEquals(
                "fields " + GAME_FIELDS + "; search \"zelda\"; limit 50;",
                new ApicalypseQuery().fields(IgdbFields.GAME).limit(50).search("zelda").build()
        );
    }

    @Test
    void searchQuotesAreEscaped() {
        assertEquals(
                "fields " + GAME_FIELDS + "; search \"say \\\"hi\\\"\"; limit 20;",
                new ApicalypseQuery().fields(IgdbFields.GAME).search("say \"hi\"").build()
        );
    }

    @Test
    void buildWithoutFieldsThrows() {
        assertThrows(IllegalStateException.class, () -> new ApicalypseQuery().search("zelda").build());
    }
}
