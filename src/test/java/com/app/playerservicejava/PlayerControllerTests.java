package com.app.playerservicejava;

import com.app.playerservicejava.config.SecurityConfig;
import com.app.playerservicejava.controller.PlayerController;
import com.app.playerservicejava.model.Player;
import com.app.playerservicejava.model.Players;
import com.app.playerservicejava.service.PlayerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PlayerController.class)
@Import(SecurityConfig.class)
class PlayerControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PlayerService playerService;

    @Test
    void adminCanSeeFirstAndLastName() throws Exception {
        when(playerService.getPlayerById("aaronha01"))
                .thenReturn(Optional.of(player()));

        mockMvc.perform(get("/v1/players/aaronha01")
                        .with(httpBasic("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.playerId").value("aaronha01"))
                .andExpect(jsonPath("$.firstName").value("Hank"))
                .andExpect(jsonPath("$.lastName").value("Aaron"));
    }

    @Test
    void regularUserCannotSeeLastName() throws Exception {
        when(playerService.getPlayerById("aaronha01"))
                .thenReturn(Optional.of(player()));

        mockMvc.perform(get("/v1/players/aaronha01")
                        .with(httpBasic("user", "user123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerId").value("aaronha01"))
                .andExpect(jsonPath("$.firstName").value("Hank"))
                .andExpect(jsonPath("$.lastName").doesNotExist());
    }

    @Test
    void anonymousUserCannotAccessPlayerDetails() throws Exception {
        mockMvc.perform(get("/v1/players/aaronha01"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(playerService);
    }

    @Test
    void missingPlayerReturnsNotFound() throws Exception {
        when(playerService.getPlayerById("missing"))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/v1/players/missing")
                        .with(httpBasic("user", "user123")))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCanSeePlayerListIncludingLastNames() throws Exception {
        Players players = new Players();
        players.setPlayers(List.of(player()));

        when(playerService.getPlayers()).thenReturn(players);

        mockMvc.perform(get("/v1/players")
                        .with(httpBasic("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].playerId").value("aaronha01"))
                .andExpect(jsonPath("$[0].firstName").value("Hank"))
                .andExpect(jsonPath("$[0].lastName").value("Aaron"));
    }

    @Test
    void regularUserCanSeePlayerListWithoutLastNames() throws Exception {
        Players players = new Players();
        players.setPlayers(List.of(player()));

        when(playerService.getPlayers()).thenReturn(players);

        mockMvc.perform(get("/v1/players")
                        .with(httpBasic("user", "user123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].playerId").value("aaronha01"))
                .andExpect(jsonPath("$[0].firstName").value("Hank"))
                .andExpect(jsonPath("$[0].lastName").doesNotExist());
    }

    @Test
    void anonymousUserCannotAccessPlayerList() throws Exception {
        mockMvc.perform(get("/v1/players"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(playerService);
    }

    private Player player() {
        Player player = new Player();
        player.setPlayerId("aaronha01");
        player.setFirstName("Hank");
        player.setLastName("Aaron");
        return player;
    }
}