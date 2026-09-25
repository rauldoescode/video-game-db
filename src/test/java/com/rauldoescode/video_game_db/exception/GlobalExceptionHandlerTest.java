package com.rauldoescode.video_game_db.exception;

import com.rauldoescode.video_game_db.game.GameController;
import com.rauldoescode.video_game_db.game.GameService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GameController.class)
@WithMockUser
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GameService gameService;

    @Test
    void blankQueryIs400WithFieldError() throws Exception {
        mockMvc.perform(get("/api/games/search").param("q", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[0].field").value("q"))
                .andExpect(jsonPath("$.instance").value("/api/games/search"));
    }

    @Test
    void missingGameIs404ProblemDetail() throws Exception {
        when(gameService.detail(999999L)).thenThrow(new GameNotFoundException(999999L));

        mockMvc.perform(get("/api/games/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail").value("Game 999999 was not found"))
                .andExpect(jsonPath("$.instance").value("/api/games/999999"));
    }
}
