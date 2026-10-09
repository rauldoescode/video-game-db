package com.rauldoescode.video_game_db.library;

import com.rauldoescode.video_game_db.dto.response.LibraryEntryResponse;
import com.rauldoescode.video_game_db.game.Game;
import com.rauldoescode.video_game_db.game.GameCatalogService;
import com.rauldoescode.video_game_db.igdb.IgdbImageSize;
import com.rauldoescode.video_game_db.igdb.IgdbImageUrls;
import com.rauldoescode.video_game_db.user.User;
import com.rauldoescode.video_game_db.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * The signed-in user's library. Every method takes the user id from the access token, so one
 * user never reads or writes another user's entries.
 */
@Service
public class LibraryService {

    private static final String UNIQUE_USER_GAME = "unique_user_game";

    private final UserGameEntryRepository entries;
    private final UserRepository users;
    private final GameCatalogService gameCatalog;

    /**
     * @param entries     library rows
     * @param users       accounts
     * @param gameCatalog stores the {@code games} row on first add
     */
    public LibraryService(UserGameEntryRepository entries, UserRepository users, GameCatalogService gameCatalog) {
        this.entries = entries;
        this.users = users;
        this.gameCatalog = gameCatalog;
    }

    /**
     * Stores the game if needed, then adds it to the user's library.
     * <p>
     * Not {@code @Transactional}, for the same reason as {@link GameCatalogService#findOrStore}:
     * the IGDB call on a first add should not hold a database connection.
     *
     * @param userId the signed-in user
     * @param igdbId the IGDB game id
     * @param status starting status, or null for {@code PLAN_TO_PLAY}
     * @return the new entry
     * @throws com.rauldoescode.video_game_db.exception.GameNotFoundException IGDB has no game with that id
     * @throws LibraryEntryConflictException the game is already in this library
     */
    public LibraryEntryResponse add(UUID userId, long igdbId, GameStatus status) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User " + userId + " was not found"));
        Game game = gameCatalog.findOrStore(igdbId);

        UserGameEntry entry = new UserGameEntry();
        entry.setUser(user);
        entry.setGame(game);
        if (status != null) {
            entry.setStatus(status);
        }
        try {
            return toResponse(entries.saveAndFlush(entry));
        } catch (DataIntegrityViolationException ex) {
            // The unique constraint is the duplicate check, so two simultaneous adds cannot both pass it.
            String message = ex.getMostSpecificCause().getMessage();
            if (message != null && message.contains(UNIQUE_USER_GAME)) {
                throw new LibraryEntryConflictException(igdbId);
            }
            throw ex;
        }
    }

    /**
     * The user's library, newest change first.
     *
     * @param userId the signed-in user
     * @param status only this status, or null for every entry
     * @return the entries, empty when the library is
     */
    public List<LibraryEntryResponse> list(UUID userId, GameStatus status) {
        List<UserGameEntry> found = status == null
                ? entries.findLibrary(userId)
                : entries.findLibraryByStatus(userId, status);
        return found.stream()
                .map(LibraryService::toResponse)
                .toList();
    }

    private static LibraryEntryResponse toResponse(UserGameEntry entry) {
        Game game = entry.getGame();
        return new LibraryEntryResponse(
                entry.getId(),
                new LibraryEntryResponse.Game(
                        game.getId(),
                        game.getTitle(),
                        game.getSlug(),
                        IgdbImageUrls.url(game.getCoverImageId(), IgdbImageSize.COVER_BIG),
                        game.getReleaseDate(),
                        game.getAggregatedRating() == null ? null : game.getAggregatedRating().doubleValue()),
                entry.getStatus(),
                entry.getRating(),
                entry.getReview(),
                entry.getHoursPlayed(),
                entry.getPlatformPlayed(),
                entry.getStartedAt(),
                entry.getCompletedAt(),
                entry.getCreatedAt(),
                entry.getUpdatedAt());
    }
}
