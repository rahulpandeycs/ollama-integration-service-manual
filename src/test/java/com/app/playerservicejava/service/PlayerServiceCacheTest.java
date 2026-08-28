package com.app.playerservicejava.service;

import com.app.playerservicejava.model.Player;
import com.app.playerservicejava.repository.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class PlayerServiceCacheTest {

    @Autowired
    private PlayerService playerService;

    @Autowired
    private CacheManager cacheManager;

    @MockBean
    private PlayerRepository playerRepository;

    @BeforeEach
    void clearCache() {
        cacheManager.getCache("players").clear();
    }

    @Test
    void repeatedLookupUsesCache() {
        Player player = new Player();
        player.setPlayerId("cached-player");
        player.setFirstName("Ada");
        when(playerRepository.findById("cached-player"))
                .thenReturn(Optional.of(player));

        Optional<Player> firstLookup = playerService.getPlayerById("cached-player");
        Optional<Player> secondLookup = playerService.getPlayerById("cached-player");

        assertThat(firstLookup).containsSame(player);
        assertThat(secondLookup).containsSame(player);
        verify(playerRepository, times(1)).findById("cached-player");
    }

    @Test
    void emptyLookupIsNotCached() {
        when(playerRepository.findById("missing-player"))
                .thenReturn(Optional.empty());

        assertThat(playerService.getPlayerById("missing-player")).isEmpty();
        assertThat(playerService.getPlayerById("missing-player")).isEmpty();

        verify(playerRepository, times(2)).findById("missing-player");
    }
}
