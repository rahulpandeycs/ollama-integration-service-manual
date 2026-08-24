package com.app.playerservicejava.controller;

import com.app.playerservicejava.model.Player;
import com.app.playerservicejava.model.Players;
import com.app.playerservicejava.service.PlayerService;
import jakarta.annotation.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    /*
     {
     "players": [
     {
        "playerId": "aaronha01",
        "firstName": "Hank",
        "lastName": "Aaron"
     }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1200,
    "totalPages": 60,
    "first": true,
    "last": false
    }
    * */

    // /v1/players?page=0&size=25&sort=lastName,asc
    // /v1/players?page=0&size=25&sort=lastName,desc
    @GetMapping
    public ResponseEntity<Players> getPlayers(
            @PageableDefault(page=0, size=20, sort = "playerId")Pageable pageable
            ) {

        int safePageSize = Math.min(pageable.getPageSize(), 100);

        Pageable safePageable = PageRequest.of(pageable.getPageNumber(), safePageSize, pageable.getSort());
        Page<Player> playerPage = playerService.getPlayers(safePageable);

        Players response = new Players();
        response.setPlayers(playerPage.getContent());
        response.setPage(playerPage.getNumber());
        response.setSize(playerPage.getSize());
        response.setTotalElements(playerPage.getTotalElements());
        response.setTotalPages(playerPage.getTotalPages());
        response.setFirst(playerPage.isFirst());
        response.setLast(playerPage.isLast());


        return ResponseEntity.ok(response);
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
