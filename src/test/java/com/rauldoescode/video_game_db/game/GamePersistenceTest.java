package com.rauldoescode.video_game_db.game;

import com.rauldoescode.video_game_db.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class GamePersistenceTest {

    private static final long BREATH_OF_THE_WILD = 1020L;

    @Autowired
    TestEntityManager entityManager;

    @Test
    void persistKeepsTheIgdbIdAndSetsCachedAt() {
        Game game = new Game();
        game.setId(BREATH_OF_THE_WILD);
        game.setTitle("The Legend of Zelda: Breath of the Wild");

        Game saved = entityManager.persistFlushFind(game);

        assertEquals(BREATH_OF_THE_WILD, saved.getId());
        assertNotNull(saved.getCachedAt());
        assertNull(saved.getSlug());
        assertNull(saved.getReleaseDate());
        assertNull(saved.getAggregatedRating());
    }

    @Test
    void optionalColumnsSurviveTheDatabaseRoundTrip() {
        Game game = new Game();
        game.setId(BREATH_OF_THE_WILD);
        game.setTitle("The Legend of Zelda: Breath of the Wild");
        game.setSlug("the-legend-of-zelda-breath-of-the-wild");
        game.setCoverImageId("co1abc");
        game.setReleaseDate(LocalDate.of(2017, 3, 3));
        game.setAggregatedRating(new BigDecimal("97.4"));

        entityManager.persistAndFlush(game);
        entityManager.clear();

        Game reloaded = entityManager.find(Game.class, BREATH_OF_THE_WILD);

        assertEquals("the-legend-of-zelda-breath-of-the-wild", reloaded.getSlug());
        assertEquals("co1abc", reloaded.getCoverImageId());
        assertEquals(LocalDate.of(2017, 3, 3), reloaded.getReleaseDate());
        // DECIMAL(5, 2) hands the value back at the column's scale, not the scale we wrote
        assertEquals(0, reloaded.getAggregatedRating().compareTo(new BigDecimal("97.4")));
        assertEquals(2, reloaded.getAggregatedRating().scale());
    }

    @Test
    void reCachingARowMovesCachedAtForward() {
        Instant stale = Instant.parse("2020-01-01T00:00:00Z");
        entityManager.getEntityManager()
                .createNativeQuery("""
                        insert into games (id, title, cached_at)
                        values (1020, 'Breath of the Wild', timestamptz '2020-01-01T00:00:00Z')
                        """)
                .executeUpdate();
        entityManager.clear();

        Game game = entityManager.find(Game.class, BREATH_OF_THE_WILD);
        assertEquals(stale, game.getCachedAt());

        game.setTitle("The Legend of Zelda: Breath of the Wild");
        entityManager.flush();

        assertTrue(game.getCachedAt().isAfter(stale));
    }
}
