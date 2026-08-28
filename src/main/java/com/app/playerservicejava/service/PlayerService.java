package com.app.playerservicejava.service;

import com.app.playerservicejava.model.Player;
import com.app.playerservicejava.model.Players;
import com.app.playerservicejava.repository.PlayerRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PlayerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerService.class);
    private static final String FAILURE_METRIC = "player.service.failures";

    private final PlayerRepository playerRepository;
    private final MeterRegistry meterRegistry;

    public PlayerService(PlayerRepository playerRepository, MeterRegistry meterRegistry) {
        this.playerRepository = playerRepository;
        this.meterRegistry = meterRegistry;
    }

    public Players getPlayers() {
        try {
            LOGGER.debug("Fetching all players");

            Players players = new Players();
            playerRepository.findAll()
                    .forEach(players.getPlayers()::add);

            LOGGER.info("Fetched {} players", players.getPlayers().size());
            return players;
        } catch (RuntimeException e) {
            recordFailure("getPlayers");
            LOGGER.error("Failed to fetch players", e);
            throw e;
        }
    }

    public Optional<Player> getPlayerById(String playerId) {
        /* simulated network delay */
        try {
            LOGGER.debug("Fetching player playerId={}", playerId);

            Optional<Player> player = playerRepository.findById(playerId);
            Thread.sleep((long)(Math.random() * 2000));

            if (player.isEmpty()) {
                LOGGER.warn("Player not found playerId={}", playerId);
            }
            return player;
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            recordFailure("getPlayerById");
            LOGGER.error("Failed to fetch player playerId={}", playerId, e);
            return Optional.empty();
        }
    }

    private void recordFailure(String operation) {
        meterRegistry.counter(FAILURE_METRIC, "operation", operation).increment();
    }

}
