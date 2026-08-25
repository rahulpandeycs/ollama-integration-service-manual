package com.app.playerservicejava.controller;

import com.app.playerservicejava.model.Player;
import com.app.playerservicejava.model.Players;
import com.app.playerservicejava.service.PlayerService;
import com.app.playerservicejava.model.team.TeamGenerationRequest;
import com.app.playerservicejava.model.team.TeamResponse;
import com.app.playerservicejava.service.team.TeamGenerationException;
import com.app.playerservicejava.service.team.TeamService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

import static org.springframework.http.ResponseEntity.ok;

@RestController
@RequestMapping(value = "v1/players", produces = { MediaType.APPLICATION_JSON_VALUE })
public class PlayerController {
    private final PlayerService playerService;
    private final TeamService teamService;

    public PlayerController(PlayerService playerService, TeamService teamService) {
        this.playerService = playerService;
        this.teamService = teamService;
    }

    @RequestMapping(method = RequestMethod.GET)
    public ResponseEntity<Players> getPlayers() {
        Players players = playerService.getPlayers();
        return ok(players);
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

    @PostMapping("/{playerId}/team")
    public ResponseEntity<TeamResponse> generateTeam(
            @PathVariable String playerId,
            @Valid @RequestBody(required = false) TeamGenerationRequest request
    ) {
        int teamSize = request == null ? 5 : request.teamSizeOrDefault();

        return teamService.generateTeam(playerId, teamSize)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @ExceptionHandler(TeamGenerationException.class)
    public ResponseEntity<Map<String, String>> handleTeamGenerationException(
            TeamGenerationException exception
    ) {
        HttpStatus status = exception.getStatus();
        return ResponseEntity.status(status).body(Map.of(
                "error", status.getReasonPhrase(),
                "message", exception.getMessage()
        ));
    }
}
