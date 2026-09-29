package com.rauldoescode.video_game_db.game;

import com.rauldoescode.video_game_db.dto.response.GameDetailResponse;
import com.rauldoescode.video_game_db.dto.response.GameSummaryResponse;
import com.rauldoescode.video_game_db.dto.response.PageResponse;
import com.rauldoescode.video_game_db.exception.GameNotFoundException;
import com.rauldoescode.video_game_db.igdb.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class GameService {

    private static final String YOUTUBE_WATCH = "https://www.youtube.com/watch?v=";

    private final IgdbLookupService igdbLookupService;

    /**
     * @param igdbLookupService cached, throttled IGDB reads
     */
    public GameService(IgdbLookupService igdbLookupService) {
        this.igdbLookupService = igdbLookupService;
    }

    /**
     * Searches IGDB for games.
     * @param q search term
     * @param genre game genre
     * @param platform game platform
     * @param yearFrom earliest release year
     * @param yearTo latest release year
     * @param page page number, starting at 0
     * @param size page size
     * @return a page of game summaries
     */
    public PageResponse<GameSummaryResponse> search(String q, String genre, String platform,
                                                    Integer yearFrom, Integer yearTo, int page,
                                                    int size) {

        IgdbSearchParams params = new IgdbSearchParams(q, genre, platform, yearFrom, yearTo, size, page * size);
        List<GameSummaryResponse> content =
                igdbLookupService.search(params).stream()
                .map(GameService::toSummary)
                .toList();
        return new PageResponse<>(content, page, size, -1, -1);
    }

    /**
     * Fetches a game's details from IGDB.
     * @param igdbId the game's IGDB id
     * @return the game's details
     */
    public GameDetailResponse detail(long igdbId) {
        IgdbGame game = igdbLookupService.detail(igdbId)
                .orElseThrow(() -> new GameNotFoundException(igdbId));
        return toDetail(game);
    }

    /**
     * Fetches the most popular games in a category.
     * @param category the IGDB category
     * @param limit how many games to return
     * @return the most popular games in that category, empty when IGDB has none
     */
    public List<GameSummaryResponse> popular(IgdbCategory category, int limit) {
        return igdbLookupService.popular(category, limit).stream()
                .map(GameService::toSummary)
                .toList();
    }

    /**
     * Maps one IGDB game onto a search card. Cover uses the card size.
     * @param game the IGDB game
     * @return the card
     */
    private static GameSummaryResponse toSummary(IgdbGame game) {
        return new GameSummaryResponse(
                game.id(),
                game.name(),
                game.slug(),
                coverUrl(game, IgdbImageSize.COVER_BIG),
                releaseDate(game.firstReleaseDate()),
                names(game.genres()),
                names(game.platforms()),
                game.aggregatedRating()
        );
    }

    /**
     * Maps one IGDB game onto the detail page. Cover uses the hero size.
     * @param game the IGDB game
     * @return the detail
     */
    private static GameDetailResponse toDetail(IgdbGame game) {
        return new GameDetailResponse(
                game.id(),
                game.name(),
                game.summary(),
                game.storyline(),
                coverUrl(game, IgdbImageSize.COVER_BIG_2X),
                releaseDate(game.firstReleaseDate()),
                names(game.genres()),
                names(game.platforms()),
                game.aggregatedRating(),
                screenshotUrls(game),
                videoUrls(game)
        );
    }

    /**
     * Builds an https cover URL. A game with no cover stays null so the client can show a placeholder.
     * @param game the IGDB game
     * @param size which IGDB size token to request
     * @return the URL, or null when there is no cover
     */
    private static String coverUrl(IgdbGame game, IgdbImageSize size) {
        if (game.cover() == null) {
            return null;
        }
        return IgdbImageUrls.url(game.cover().imageId(), size);
    }

    /**
     * IGDB stores release dates as Unix seconds. Convert once so the API returns a calendar date.
     * @param epochSeconds Unix seconds, or null when IGDB omitted the date
     * @return the UTC date, or null
     */
    private static LocalDate releaseDate(Long epochSeconds) {
        if (epochSeconds == null) {
            return null;
        }
        return Instant.ofEpochSecond(epochSeconds).atZone(ZoneOffset.UTC).toLocalDate();
    }

    /**
     * Flattens IGDB's {@code {name}} objects into strings. A missing list becomes empty.
     * @param named genres or platforms
     * @return the names, never null
     */
    private static List<String> names(List<IgdbGame.Named> named) {
        if (named == null) {
            return List.of();
        }
        return named.stream()
                .map(IgdbGame.Named::name)
                .filter(name -> name != null && !name.isBlank())
                .toList();
    }

    /**
     * Gallery URLs at the screenshot size. A blank image id is dropped.
     * @param game the IGDB game
     * @return screenshot URLs, never null
     */
    private static List<String> screenshotUrls(IgdbGame game) {
        if (game.screenshots() == null) {
            return List.of();
        }
        return game.screenshots().stream()
                .map(shot -> IgdbImageUrls.url(shot.imageId(), IgdbImageSize.SCREENSHOT_HUGE))
                .filter(url -> url != null)
                .toList();
    }

    /**
     * YouTube watch URLs from IGDB video ids. A blank id is dropped.
     * @param game the IGDB game
     * @return watch URLs, never null
     */
    private static List<String> videoUrls(IgdbGame game) {
        if (game.videos() == null) {
            return List.of();
        }
        return game.videos().stream()
                .map(IgdbGame.Video::videoId)
                .filter(id -> id != null && !id.isBlank())
                .map(id -> YOUTUBE_WATCH + id)
                .toList();
    }
}
