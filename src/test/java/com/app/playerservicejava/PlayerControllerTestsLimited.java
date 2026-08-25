package com.app.playerservicejava;

import com.app.playerservicejava.controller.PlayerController;
import com.app.playerservicejava.model.Player;
import com.app.playerservicejava.model.PlayerResponse;
import com.app.playerservicejava.service.PlayerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class PlayerControllerTestsLimited {

    private PlayerService playerService;
    private PlayerController playerController;

    @BeforeEach
    void setup(){
        playerService = mock(PlayerService.class);
        playerController = new PlayerController(playerService);
    }

    @Test
    void adminCanSeeFirstAndLastName(){
        Player player = player();
        when(playerService.getPlayerById("aaronha01")).thenReturn(Optional.of(player));

        ResponseEntity<?> response = playerController.getPlayerById("aaronha01", authenticatedWithRole("ROLE_ADMIN"));
        assertEquals(200, response.getStatusCode().value());

        var body = (PlayerResponse)response.getBody();
        assertNotNull(body);
        assertEquals("Hank", body.firstName());
        assertEquals("Aaron", body.lastName());
    }

    @Test
    void useCannotSeeLastName(){
        Player player = player();
        when(playerService.getPlayerById("aaronha01")).thenReturn(Optional.of(player));

        ResponseEntity<?> response = playerController.getPlayerById("aaronha01", authenticatedWithRole("ROLE_USER"));
        assertEquals(200, response.getStatusCode().value());

        var body = (PlayerResponse)response.getBody();
        assertNotNull(body);
        assertEquals("Hank", body.firstName());
        assertNull(body.lastName());
    }

    @Test
    void guestCannotAccessAnything(){
        Player player = player();
        when(playerService.getPlayerById("aaronha01")).thenReturn(Optional.of(player));

        ResponseEntity<?> response = playerController.getPlayerById("aaronha01", authenticatedWithRole("ROLE_GUEST"));
        assertEquals(403, response.getStatusCode().value());

        verifyNoInteractions(playerService);
    }

    @Test
    void missingPlayerReturnsNotFound() {
        when(playerService.getPlayerById("missing"))
                .thenReturn(Optional.empty());

        ResponseEntity<?> response = playerController.getPlayerById(
                "missing",
                authenticatedWithRole("ROLE_USER")
        );

        assertEquals(404, response.getStatusCode().value());
    }

    private Authentication authenticatedWithRole(String role) {
        return new UsernamePasswordAuthenticationToken("test-user", null, List.of(new SimpleGrantedAuthority(role)));
    }

    private Player player() {
        Player player = new Player();
        player.setPlayerId("aaronha01");
        player.setFirstName("Hank");
        player.setLastName("Aaron");
        return player;
    }


}
