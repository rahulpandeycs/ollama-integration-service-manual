package com.app.playerservicejava.controller;

import com.app.playerservicejava.model.Player;
import com.app.playerservicejava.model.Players;
import com.app.playerservicejava.service.PlayerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PlayerController.class)
class PlayerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PlayerService playerService;

    @Test
    void getPlayersAsAdminReturnsFirstAndLastNamesOnly() throws Exception {
        when(playerService.getPlayers()).thenReturn(players());

        mockMvc.perform(get("/v1/players").param("isAdmin", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players[0]", aMapWithSize(2)))
                .andExpect(jsonPath("$.players[0].firstName").value("Ada"))
                .andExpect(jsonPath("$.players[0].lastName").value("Lovelace"))
                .andExpect(jsonPath("$.players[1].firstName").value("Grace"))
                .andExpect(jsonPath("$.players[1].lastName").value("Hopper"));

        verify(playerService).getPlayers();
    }

    @Test
    void getPlayersAsRegularUserReturnsFirstNamesOnly() throws Exception {
        when(playerService.getPlayers()).thenReturn(players());

        mockMvc.perform(get("/v1/players").param("isAdmin", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players[0]", aMapWithSize(1)))
                .andExpect(jsonPath("$.players[0].firstName").value("Ada"))
                .andExpect(jsonPath("$.players[0].lastName").doesNotExist())
                .andExpect(jsonPath("$.players[1]", aMapWithSize(1)))
                .andExpect(jsonPath("$.players[1].firstName").value("Grace"))
                .andExpect(jsonPath("$.players[1].lastName").doesNotExist());
    }

    private Players players() {
        Player player = new Player();
        player.setFirstName("Ada");
        player.setLastName("Lovelace");
        player.setBirthYear("1815");

        Player secondPlayer = new Player();
        secondPlayer.setFirstName("Grace");
        secondPlayer.setLastName("Hopper");

        Players players = new Players();
        players.setPlayers(List.of(player, secondPlayer));
        return players;
    }
}
