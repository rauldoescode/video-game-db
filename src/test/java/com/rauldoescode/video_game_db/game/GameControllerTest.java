package com.rauldoescode.video_game_db.game;

import com.rauldoescode.video_game_db.config.SecurityConfig;
import com.rauldoescode.video_game_db.igdb.IgdbCategory;
import com.rauldoescode.video_game_db.dto.response.GameSummaryResponse;
import com.rauldoescode.video_game_db.dto.response.PageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Hits {@link SecurityConfig} so an anonymous call uses the real permit list.
 * {@code GlobalExceptionHandlerTest} signs in with {@code @WithMockUser} and never reaches that rule.
 */
@WebMvcTest(GameController.class)
@Import(SecurityConfig.class)
class GameControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GameService gameService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void anonymousSearchReturnsThePage() throws Exception {
        when(gameService.search(any(), isNull(), isNull(), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(
                        List.of(new GameSummaryResponse(
                                1026L,
                                "The Legend of Zelda",
                                "the-legend-of-zelda",
                                "https://images.igdb.com/igdb/image/upload/t_cover_big/co1r7f.jpg",
                                LocalDate.of(1998, 11, 21),
                                List.of("RPG"),
                                List.of("Nintendo 64"),
                                91.5)),
                        0,
                        20,
                        -1,
                        -1));

        mockMvc.perform(get("/api/games/search").param("q", "zelda"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content[0].id").value(1026))
                .andExpect(jsonPath("$.content[0].name").value("The Legend of Zelda"))
                .andExpect(jsonPath("$.content[0].coverUrl")
                        .value("https://images.igdb.com/igdb/image/upload/t_cover_big/co1r7f.jpg"));
    }

    @Test
    void anonymousPopularReturnsTheCards() throws Exception {
        when(gameService.popular(eq(IgdbCategory.TRENDING), eq(20)))
                .thenReturn(List.of(new GameSummaryResponse(
                        1026L,
                        "The Legend of Zelda",
                        "the-legend-of-zelda",
                        "https://images.igdb.com/igdb/image/upload/t_cover_big/co1r7f.jpg",
                        LocalDate.of(1998, 11, 21),
                        List.of("RPG"),
                        List.of("Nintendo 64"),
                        91.5)));

        mockMvc.perform(get("/api/games/popular").param("category", "TRENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1026))
                .andExpect(jsonPath("$[0].coverUrl")
                        .value("https://images.igdb.com/igdb/image/upload/t_cover_big/co1r7f.jpg"));
    }

    @Test
    void unknownCategoryIs400() throws Exception {
        mockMvc.perform(get("/api/games/popular").param("category", "NOPE"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.errors[0].field").value("category"))
                .andExpect(jsonPath("$.errors[0].message").value("must be one of TRENDING, WANT_TO_PLAY"));
    }

    @Test
    void limitAbove50Is400() throws Exception {
        mockMvc.perform(get("/api/games/popular").param("category", "TRENDING").param("limit", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.errors[0].field").value("limit"));
    }

    @Test
    void sizeAbove50Is400() throws Exception {
        mockMvc.perform(get("/api/games/search").param("q", "zelda").param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }
}
