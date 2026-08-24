package com.app.playerservicejava;

import com.app.playerservicejava.controller.PlayerController;
import com.app.playerservicejava.model.Player;
import com.app.playerservicejava.service.PlayerService;
import com.app.playerservicejava.service.chat.ChatClientService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PlayerController.class)
class PlayerControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PlayerService playerService;

    @MockBean
    private ChatClientService chatClientService;

    @Test
    void generatesNicknameForExistingPlayer() throws Exception {
        Player player = player();

        when(playerService.getPlayerById("aaronha01"))
                .thenReturn(Optional.of(player));

        when(chatClientService.generateNickname(
                any(Player.class),
                eq("USA")
        )).thenReturn("The Hammer");

        mockMvc.perform(post("/v1/players/aaronha01/nickname")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "country": "USA"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerId")
                        .value("aaronha01"))
                .andExpect(jsonPath("$.country")
                        .value("USA"))
                .andExpect(jsonPath("$.nickname")
                        .value("The Hammer"));

        verify(playerService)
                .getPlayerById("aaronha01");

        verify(chatClientService)
                .generateNickname(any(Player.class), eq("USA"));
    }

    @Test
    void returnsNotFoundWhenPlayerDoesNotExist() throws Exception {
        when(playerService.getPlayerById("missing"))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/v1/players/missing/nickname")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "country": "USA"
                                }
                                """))
                .andExpect(status().isNotFound());

        verifyNoInteractions(chatClientService);
    }

    @Test
    void returnsBadRequestWhenCountryIsBlank() throws Exception {
        mockMvc.perform(post("/v1/players/aaronha01/nickname")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "country": " "
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(playerService);
        verifyNoInteractions(chatClientService);
    }

    @Test
    void trimsCountryBeforeCallingLlm() throws Exception {
        when(playerService.getPlayerById("aaronha01"))
                .thenReturn(Optional.of(player()));

        when(chatClientService.generateNickname(
                any(Player.class),
                eq("USA")
        )).thenReturn("The Hammer");

        mockMvc.perform(post("/v1/players/aaronha01/nickname")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "country": " USA "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.country").value("USA"));

        verify(chatClientService)
                .generateNickname(any(Player.class), eq("USA"));
    }

    private Player player() {
        Player player = new Player();
        player.setPlayerId("aaronha01");
        player.setFirstName("Hank");
        player.setLastName("Aaron");
        return player;
    }
}
