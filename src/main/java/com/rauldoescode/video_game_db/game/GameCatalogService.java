package com.rauldoescode.video_game_db.game;

import com.rauldoescode.video_game_db.exception.GameNotFoundException;
import com.rauldoescode.video_game_db.igdb.IgdbGame;
import com.rauldoescode.video_game_db.igdb.IgdbLookupService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Writes {@code games} rows. A row exists only for a game someone has put in a library, so the
 * library still renders when IGDB is down without copying the IGDB catalog.
 */
@Service
public class GameCatalogService {

    private final GameRepository games;
    private final IgdbLookupService igdbLookupService;

    /**
     * @param games stored games
     * @param igdbLookupService cached, throttled IGDB reads
     */
    public GameCatalogService(GameRepository games, IgdbLookupService igdbLookupService) {
        this.games = games;
        this.igdbLookupService = igdbLookupService;
    }

    /**
     * Returns the stored row, or loads the game from IGDB and stores it first.
     * <p>
     * Not {@code @Transactional}: the IGDB call should not hold a database connection, and a
     * lost insert race has to leave a usable transaction for the re-read.
     *
     * @param igdbId the IGDB game id
     * @return the stored game
     * @throws GameNotFoundException IGDB has no game with that id
     */
    public Game findOrStore(long igdbId) {
        Optional<Game> stored = games.findById(igdbId);
        if (stored.isPresent()) {
            return stored.get();
        }
        IgdbGame igdbGame = igdbLookupService.detail(igdbId)
                .orElseThrow(() -> new GameNotFoundException(igdbId));
        try {
            return games.saveAndFlush(toEntity(igdbGame));
        } catch (DataIntegrityViolationException ex) {
            // Another request stored the same game between the lookup and the insert.
            return games.findById(igdbId).orElseThrow(() -> ex);
        }
    }

    private static Game toEntity(IgdbGame igdbGame) {
        Game game = new Game();
        game.setId(igdbGame.id());
        game.setTitle(igdbGame.name());
        game.setSlug(igdbGame.slug());
        game.setCoverImageId(igdbGame.cover() == null ? null : igdbGame.cover().imageId());
        game.setReleaseDate(GameService.releaseDate(igdbGame.firstReleaseDate()));
        game.setAggregatedRating(igdbGame.aggregatedRating() == null
                ? null
                : BigDecimal.valueOf(igdbGame.aggregatedRating()).setScale(2, RoundingMode.HALF_UP));
        return game;
    }
}
