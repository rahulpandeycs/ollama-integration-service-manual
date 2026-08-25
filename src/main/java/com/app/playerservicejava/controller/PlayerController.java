package com.app.playerservicejava.controller;

import com.app.playerservicejava.model.Player;
import com.app.playerservicejava.model.PlayerResponse;
import com.app.playerservicejava.model.Players;
import com.app.playerservicejava.service.PlayerService;
import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.stream.Collectors;

import static org.springframework.http.ResponseEntity.ok;

@RestController
@RequestMapping(value = "v1/players", produces = { MediaType.APPLICATION_JSON_VALUE })
public class PlayerController {
    @Resource
    private PlayerService playerService;

    @RequestMapping(method = RequestMethod.GET)
    public ResponseEntity<PlayerResponse> getPlayers(
            @RequestParam(value = "isAdmin", defaultValue = "false") boolean isAdmin) {
        Players players = playerService.getPlayers();
        PlayerResponse playerResponse = new PlayerResponse(players.getPlayers().stream()
                .map(player -> new PlayerResponse.Name(
                        player.getFirstName(),
                        isAdmin ? player.getLastName() : null))
                .collect(Collectors.toList()));
        return ok(playerResponse);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Player> getPlayerById(@PathVariable("id") String id) {
        Optional<Player> player = playerService.getPlayerById(id);

        if (player.isPresent()) {
            return new ResponseEntity<>(player.get(), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }
}
