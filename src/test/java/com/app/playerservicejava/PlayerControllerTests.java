package com.app.playerservicejava;

import com.app.playerservicejava.controller.PlayerController;
import com.app.playerservicejava.model.Player;
import com.app.playerservicejava.model.team.TeamResponse;
import com.app.playerservicejava.service.PlayerService;
import com.app.playerservicejava.service.team.TeamGenerationException;
import com.app.playerservicejava.service.team.TeamService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PlayerController.class)
class PlayerControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PlayerService playerService;

    @MockBean
    private TeamService teamService;

    @Test
    void getsPlayerById() throws Exception {
        when(playerService.getPlayerById("aaronha01"))
                .thenReturn(Optional.of(player("aaronha01")));

        mockMvc.perform(get("/v1/players/aaronha01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerId").value("aaronha01"));
    }

    @Test
    void generatesTeamWithDefaultSize() throws Exception {
        when(teamService.generateTeam("aaronha01", 5))
                .thenReturn(Optional.of(teamResponse()));

        mockMvc.perform(post("/v1/players/aaronha01/team"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.seedId").value("aaronha01"))
                .andExpect(jsonPath("$.predictionId").value("prediction-1"))
                .andExpect(jsonPath("$.modelVersion").value("similarity-test"))
                .andExpect(jsonPath("$.teamSize").value(2))
                .andExpect(jsonPath("$.memberIds[0]").value("aaronha01"))
                .andExpect(jsonPath("$.players[0].playerId").value("aaronha01"));

        verify(teamService).generateTeam("aaronha01", 5);
    }

    @Test
    void forwardsRequestedTeamSize() throws Exception {
        when(teamService.generateTeam("aaronha01", 10))
                .thenReturn(Optional.of(teamResponse()));

        mockMvc.perform(post("/v1/players/aaronha01/team")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamSize\":10}"))
                .andExpect(status().isOk());

        verify(teamService).generateTeam("aaronha01", 10);
    }

    @Test
    void rejectsTeamSizeAboveModelLimit() throws Exception {
        mockMvc.perform(post("/v1/players/aaronha01/team")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamSize\":26}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(teamService);
    }

    @Test
    void returnsNotFoundForUnknownPlayer() throws Exception {
        when(teamService.generateTeam("missing", 5)).thenReturn(Optional.empty());

        mockMvc.perform(post("/v1/players/missing/team"))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsServiceUnavailableWhenModelIsUnavailable() throws Exception {
        when(teamService.generateTeam("aaronha01", 5))
                .thenThrow(new TeamGenerationException(
                        SERVICE_UNAVAILABLE,
                        "Model service is unavailable"
                ));

        mockMvc.perform(post("/v1/players/aaronha01/team"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.message").value("Model service is unavailable"));
    }

    private TeamResponse teamResponse() {
        Player player = player("aaronha01");

        return new TeamResponse(
                "aaronha01",
                "prediction-1",
                "similarity-test",
                2,
                List.of("aaronha01", "member-1"),
                List.of(player)
        );
    }

    private Player player(String playerId) {
        Player player = new Player();
        player.setPlayerId(playerId);
        player.setFirstName("Hank");
        player.setLastName("Aaron");
        return player;
    }
}
