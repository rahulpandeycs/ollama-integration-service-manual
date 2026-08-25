package com.app.playerservicejava;

import com.app.playerservicejava.model.Player;
import com.app.playerservicejava.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class PlayerRepositoryTests {

    @Autowired
    private PlayerRepository playerRepository;

    @Test
    void generatesPlayerIdWhenSavingNewPlayer() {
        Player player = new Player();
        player.setFirstName("New");
        player.setLastName("Player");

        Player savedPlayer = playerRepository.saveAndFlush(player);

        assertNotNull(savedPlayer.getPlayerId());
        assertTrue(playerRepository.findById(savedPlayer.getPlayerId()).isPresent());
    }
}
