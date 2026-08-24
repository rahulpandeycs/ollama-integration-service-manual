package com.app.playerservicejava.controller;

import com.app.playerservicejava.model.Player;
import com.app.playerservicejava.model.PlayerResponse;
import com.app.playerservicejava.model.Players;
import com.app.playerservicejava.model.Role;
import com.app.playerservicejava.service.PlayerService;
import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

import static org.springframework.http.ResponseEntity.ok;

@RestController
@RequestMapping(value = "v1/players", produces = { MediaType.APPLICATION_JSON_VALUE })
public class PlayerController {
    @Resource
    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    @RequestMapping(method = RequestMethod.GET)
    public ResponseEntity<List<PlayerResponse>> getPlayers(Authentication authentication) {
        Role role = getRole(authentication);

        if(role == Role.GUEST){
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Players players = playerService.getPlayers();
        List<PlayerResponse> response = players.getPlayers().stream().map(player -> toResponse(player,role)).toList();
        return ResponseEntity.ok(response);
    }

    private PlayerResponse toResponse(Player player, Role role) {
        String lastName = role == Role.ADMIN ? player.getLastName() : null;
        return new PlayerResponse(player.getPlayerId(), player.getFirstName(),lastName);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlayerResponse> getPlayerById(@PathVariable("id") String id, Authentication authentication) {
        Role role = getRole(authentication);

        if(role == Role.GUEST){
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Optional<PlayerResponse> response = playerService.getPlayerById(id).map(player -> toResponse(player,role));

        return response.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Role getRole(Authentication authentication) {
        if(authentication == null){
            return Role.GUEST;
        }

        boolean isAdmin = authentication.getAuthorities().stream().anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals("ROLE_ADMIN"));

        if(isAdmin){
            return Role.ADMIN;
        }

        boolean isRegularUser = authentication.getAuthorities().stream().anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals("ROLE_USER"));

        if(isRegularUser){
            return Role.USER;
        }
        return Role.GUEST;
    }
}
