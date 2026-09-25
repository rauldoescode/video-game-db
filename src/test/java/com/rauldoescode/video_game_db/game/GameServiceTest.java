package com.rauldoescode.video_game_db.game;

import com.rauldoescode.video_game_db.dto.response.GameDetailResponse;
import com.rauldoescode.video_game_db.dto.response.GameSummaryResponse;
import com.rauldoescode.video_game_db.dto.response.PageResponse;
import com.rauldoescode.video_game_db.exception.GameNotFoundException;
import com.rauldoescode.video_game_db.igdb.IgdbGame;
import com.rauldoescode.video_game_db.igdb.IgdbLookupService;
import com.rauldoescode.video_game_db.igdb.IgdbSearchParams;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameServiceTest {

    private final IgdbLookupService lookup = mock(IgdbLookupService.class);
    private final GameService service = new GameService(lookup);

    @Test
    void searchMapsACardAndPassesPagingThrough() {
        when(lookup.search(new IgdbSearchParams("zelda", "rpg", null, 1998, null, 20, 40)))
                .thenReturn(List.of(fullGame()));

        PageResponse<GameSummaryResponse> page = service.search("zelda", "rpg", null, 1998, null, 2, 20);

        GameSummaryResponse card = page.content().getFirst();
        assertEquals(1026L, card.id());
        assertEquals("The Legend of Zelda", card.name());
        assertEquals("the-legend-of-zelda", card.slug());
        assertEquals(
                "https://images.igdb.com/igdb/image/upload/t_cover_big/co1r7f.jpg",
                card.coverUrl());
        assertEquals(LocalDate.of(1998, 11, 21), card.firstReleaseDate());
        assertEquals(List.of("RPG"), card.genres());
        assertEquals(List.of("Nintendo 64"), card.platforms());
        assertEquals(91.5, card.aggregatedRating());
        assertEquals(2, page.page());
        assertEquals(20, page.size());
        assertEquals(-1, page.totalElements());
        assertEquals(-1, page.totalPages());
    }

    @Test
    void detailUsesTheHeroCoverAndBuildsMediaUrls() {
        when(lookup.detail(1026L)).thenReturn(Optional.of(fullGame()));

        GameDetailResponse detail = service.detail(1026L);

        assertEquals("A hero wakes.", detail.summary());
        assertEquals("Long ago.", detail.storyline());
        assertEquals(
                "https://images.igdb.com/igdb/image/upload/t_cover_big_2x/co1r7f.jpg",
                detail.coverUrl());
        assertEquals(
                List.of("https://images.igdb.com/igdb/image/upload/t_screenshot_huge/sc1.jpg"),
                detail.screenshotUrls());
        assertEquals(List.of("https://www.youtube.com/watch?v=dQw4w9WgXcQ"), detail.videoUrls());
    }

    @Test
    void sparseGameMapsToNullsAndEmptyLists() {
        IgdbGame sparse = new IgdbGame(
                1L, "Untitled", null, null, null, null, null, null, null, null, null, null);
        when(lookup.detail(1L)).thenReturn(Optional.of(sparse));

        GameDetailResponse detail = service.detail(1L);

        assertNull(detail.coverUrl());
        assertNull(detail.firstReleaseDate());
        assertEquals(List.of(), detail.genres());
        assertEquals(List.of(), detail.platforms());
        assertEquals(List.of(), detail.screenshotUrls());
        assertEquals(List.of(), detail.videoUrls());
    }

    @Test
    void missingGameRaisesGameNotFound() {
        when(lookup.detail(999999L)).thenReturn(Optional.empty());

        GameNotFoundException thrown = assertThrows(GameNotFoundException.class, () -> service.detail(999999L));

        assertEquals(999999L, thrown.igdbId());
        verify(lookup).detail(999999L);
    }

    private static IgdbGame fullGame() {
        return new IgdbGame(
                1026L,
                "The Legend of Zelda",
                "the-legend-of-zelda",
                "A hero wakes.",
                "Long ago.",
                new IgdbGame.Cover("co1r7f"),
                911635200L,
                List.of(new IgdbGame.Named("RPG"), new IgdbGame.Named(" ")),
                List.of(new IgdbGame.Named("Nintendo 64")),
                91.5,
                List.of(new IgdbGame.Cover("sc1"), new IgdbGame.Cover("")),
                List.of(new IgdbGame.Video("dQw4w9WgXcQ"), new IgdbGame.Video(null))
        );
    }
}
