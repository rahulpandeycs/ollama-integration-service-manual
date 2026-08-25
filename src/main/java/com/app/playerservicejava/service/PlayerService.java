package com.app.playerservicejava.service;

import com.app.playerservicejava.model.Player;
import com.app.playerservicejava.model.PlayerCreateRequest;
import com.app.playerservicejava.model.Players;
import com.app.playerservicejava.repository.PlayerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PlayerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerService.class);

    @Autowired
    private PlayerRepository playerRepository;

    public Players getPlayers() {
        Players players = new Players();
        playerRepository.findAll()
                .forEach(players.getPlayers()::add);
        return players;
    }

    public Player addPlayer(PlayerCreateRequest request) {
        Player player = new Player();
        player.setBirthYear(request.birthYear());
        player.setBirthMonth(request.birthMonth());
        player.setBirthDay(request.birthDay());
        player.setBirthCountry(request.birthCountry());
        player.setBirthState(request.birthState());
        player.setBirthCity(request.birthCity());
        player.setDeathYear(request.deathYear());
        player.setDeathMonth(request.deathMonth());
        player.setDeathDay(request.deathDay());
        player.setDeathCountry(request.deathCountry());
        player.setDeathState(request.deathState());
        player.setDeathCity(request.deathCity());
        player.setFirstName(request.firstName());
        player.setLastName(request.lastName());
        player.setGivenName(request.givenName());
        player.setWeight(request.weight());
        player.setHeight(request.height());
        player.setBats(request.bats());
        player.setThrowStats(request.throwStats());
        player.setDebut(request.debut());
        player.setFinalGame(request.finalGame());
        player.setRetroId(request.retroId());
        player.setBbrefId(request.bbrefId());

        return playerRepository.save(player);
    }

    public Optional<Player> getPlayerById(String playerId) {
        Optional<Player> player = null;

        /* simulated network delay */
        try {
            player = playerRepository.findById(playerId);
            Thread.sleep((long)(Math.random() * 2000));
        } catch (Exception e) {
            LOGGER.error("message=Exception in getPlayerById; exception={}", e.toString());
            return Optional.empty();
        }
        return player;
    }

}
