package com.rauldoescode.video_game_db.game;

import com.rauldoescode.video_game_db.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class GameRepositoryTest {

    private static final long BREATH_OF_THE_WILD = 1020L;

    @Autowired
    GameRepository games;

    @Test
    void saveThenFindByIdUsesTheIgdbId() {
        Game game = new Game();
        game.setId(BREATH_OF_THE_WILD);
        game.setTitle("The Legend of Zelda: Breath of the Wild");
        games.saveAndFlush(game);

        Game found = games.findById(BREATH_OF_THE_WILD).orElseThrow();
        assertEquals("The Legend of Zelda: Breath of the Wild", found.getTitle());
        assertTrue(games.existsById(BREATH_OF_THE_WILD));
        assertTrue(games.findById(999999L).isEmpty());
    }
}
