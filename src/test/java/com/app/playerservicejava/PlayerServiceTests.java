package com.app.playerservicejava;

import com.app.playerservicejava.model.Player;
import com.app.playerservicejava.model.PlayerCreateRequest;
import com.app.playerservicejava.repository.PlayerRepository;
import com.app.playerservicejava.service.PlayerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayerServiceTests {

    @Mock
    private PlayerRepository playerRepository;

    @InjectMocks
    private PlayerService playerService;

    @Test
    void mapsCreateRequestAndSavesPlayer() {
        PlayerCreateRequest request = new PlayerCreateRequest(
                "1934", "2", "5", "USA", "AL", "Mobile",
                null, null, null, null, null, null,
                "Hank", "Aaron", "Henry Louis", "180", "72", "R", "R",
                "1954-04-13", "1976-10-03", "aaroh101", "aaronha01"
        );
        Player savedPlayer = player();

        when(playerRepository.save(org.mockito.ArgumentMatchers.any(Player.class)))
                .thenReturn(savedPlayer);

        Player result = playerService.addPlayer(request);

        ArgumentCaptor<Player> playerCaptor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepository).save(playerCaptor.capture());

        Player persistedPlayer = playerCaptor.getValue();
        assertEquals("Hank", persistedPlayer.getFirstName());
        assertEquals("Aaron", persistedPlayer.getLastName());
        assertEquals("1934", persistedPlayer.getBirthYear());
        assertEquals("USA", persistedPlayer.getBirthCountry());
        assertEquals("aaronha01", persistedPlayer.getBbrefId());
        assertSame(savedPlayer, result);
    }

    private Player player() {
        Player player = new Player();
        player.setPlayerId("generated-player-id");
        player.setFirstName("Hank");
        player.setLastName("Aaron");
        return player;
    }
}
