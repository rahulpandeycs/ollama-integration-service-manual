package com.app.playerservicejava.service;

import com.app.playerservicejava.repository.PlayerRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayerServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    private SimpleMeterRegistry meterRegistry;
    private PlayerService playerService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        playerService = new PlayerService(playerRepository, meterRegistry);
    }

    @Test
    void getPlayersRecordsFailureMetricAndRethrowsException() {
        RuntimeException failure = new RuntimeException("database unavailable");
        when(playerRepository.findAll()).thenThrow(failure);

        assertThatThrownBy(() -> playerService.getPlayers())
                .isSameAs(failure);

        assertThatFailureCount("getPlayers");
    }

    @Test
    void getPlayerByIdRecordsFailureMetricAndReturnsEmpty() {
        when(playerRepository.findById("invalid"))
                .thenThrow(new RuntimeException("database unavailable"));

        assertThat(playerService.getPlayerById("invalid")).isEmpty();

        assertThatFailureCount("getPlayerById");
    }

    private void assertThatFailureCount(String operation) {
        assertThat(meterRegistry.get("player.service.failures")
                .tag("operation", operation)
                .counter()
                .count()).isEqualTo(1.0);
    }
}
