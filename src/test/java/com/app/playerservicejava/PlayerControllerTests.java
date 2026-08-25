package com.app.playerservicejava;

import com.app.playerservicejava.controller.PlayerController;
import com.app.playerservicejava.model.Player;
import com.app.playerservicejava.service.PlayerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PlayerController.class)
class PlayerControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PlayerService playerService;

    @Test
    void createsPlayer() throws Exception {
        Player savedPlayer = player();
        savedPlayer.setPlayerId("generated-player-id");
        when(playerService.addPlayer(any())).thenReturn(savedPlayer);

        mockMvc.perform(post("/v1/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Hank",
                                  "lastName": "Aaron",
                                  "birthYear": "1934",
                                  "birthCountry": "USA",
                                  "height": "72",
                                  "weight": "180",
                                  "bats": "R",
                                  "throwStats": "R"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/v1/players/generated-player-id"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.playerId").value("generated-player-id"))
                .andExpect(jsonPath("$.firstName").value("Hank"))
                .andExpect(jsonPath("$.lastName").value("Aaron"));
    }

    @Test
    void rejectsPlayerWithoutFirstName() throws Exception {
        mockMvc.perform(post("/v1/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "lastName": "Aaron"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(playerService);
    }

    @Test
    void rejectsPlayerWithoutLastName() throws Exception {
        mockMvc.perform(post("/v1/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Hank"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(playerService);
    }

    private Player player() {
        Player player = new Player();
        player.setFirstName("Hank");
        player.setLastName("Aaron");
        return player;
    }
}
