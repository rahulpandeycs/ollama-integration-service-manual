package com.app.playerservicejava;

import com.app.playerservicejava.controller.PlayerController;
import com.app.playerservicejava.exception.PageOutOfRangeException;
import com.app.playerservicejava.model.Player;
import com.app.playerservicejava.service.PlayerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.*;
import org.springframework.test.web.servlet.MockMvc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PlayerController.class)
public class PlayerControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PlayerService playerService;

    @Test
    void getPlayersReturnPaginatedResponse() throws Exception {

        List<Player> players = loadPlayersFromCsv();

        Pageable pageable = PageRequest.of(
                0,
                5,
                Sort.by("playerId")
        );

        Page<Player> page = new PageImpl<>(
                players.subList(0, 5),
                pageable,
                players.size()
        );

        when(playerService.getPlayers(any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/v1/players")
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.length()").value(5))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(39))
                .andExpect(jsonPath("$.totalPages").value(8))
                .andExpect(jsonPath("$.players[0].playerId")
                        .value("aardsda01"));
    }

    @Test
    void outOfRangePageReturnsBadRequest() throws Exception {
        when(playerService.getPlayers(any(Pageable.class)))
                .thenThrow(new PageOutOfRangeException(99, 3));

        mockMvc.perform(get("/v1/players")
                        .param("page", "99")
                        .param("size", "20"))
                .andExpect(status().isBadRequest());
    }

    private List<Player> loadPlayersFromCsv() throws IOException {
        InputStream inputStream =
                getClass().getResourceAsStream("/test-players.csv");

        if (inputStream == null) {
            throw new IllegalStateException("test-players.csv was not found");
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        inputStream,
                        StandardCharsets.UTF_8))) {

            return reader.lines()
                    .skip(1)
                    .map(line -> {
                        String[] columns = line.split(",", -1);

                        Player player = new Player();
                        player.setPlayerId(columns[0]);
                        player.setFirstName(columns[1]);
                        player.setLastName(columns[2]);

                        return player;
                    })
                    .toList();
        }
    }
}
