package com.rauldoescode.video_game_db.game;

import com.rauldoescode.video_game_db.dto.response.GameDetailResponse;
import com.rauldoescode.video_game_db.dto.response.GameSummaryResponse;
import com.rauldoescode.video_game_db.dto.response.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/games")
@Validated
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    /**
     * Searches for games.
     * @param q search term
     * @param genre game genre
     * @param platform game platform
     * @param yearFrom earliest release year
     * @param yearTo latest release year
     * @param page page number, starting at 0
     * @param size page size
     * @return a page of game summaries
     */
    @GetMapping("/search")
    public PageResponse<GameSummaryResponse> search(
            @RequestParam @NotBlank String q,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) Integer yearFrom,
            @RequestParam(required = false) Integer yearTo,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {

        return gameService.search(q, genre, platform, yearFrom, yearTo, page, size);
    }

    /**
     * Fetches a game's details.
     * @param igdbId the game's IGDB id
     * @return the game's details
     */
    @GetMapping("/{igdbId}")
    public GameDetailResponse detail(@PathVariable long igdbId) {
        return gameService.detail(igdbId);
    }
}
