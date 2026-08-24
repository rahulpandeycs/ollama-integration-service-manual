package com.app.playerservicejava.controller;

import com.app.playerservicejava.model.NickNameRequest;
import com.app.playerservicejava.model.NickNameResponse;
import com.app.playerservicejava.model.Player;
import com.app.playerservicejava.model.Players;
import com.app.playerservicejava.service.PlayerService;
import com.app.playerservicejava.service.chat.ChatClientService;
import io.github.ollama4j.exceptions.OllamaBaseException;
import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Optional;

import static org.springframework.http.ResponseEntity.ok;

@RestController
@RequestMapping(value = "v1/players", produces = { MediaType.APPLICATION_JSON_VALUE })
public class PlayerController {
    @Resource
    private final PlayerService playerService;
    private final ChatClientService chatClientService;

    public PlayerController(PlayerService playerService, ChatClientService chatClientService) {
        this.playerService = playerService;
        this.chatClientService = chatClientService;
    }

    @PostMapping("/{id}/nickname")
    public ResponseEntity<NickNameResponse> generateNickName(@PathVariable("id") String id,@RequestBody NickNameRequest nickNameRequest)
    throws OllamaBaseException, IOException, InterruptedException {

        if(nickNameRequest == null || nickNameRequest.country() == null || nickNameRequest.country().isBlank()){
            return ResponseEntity.badRequest().build();
        }

        String country = nickNameRequest.country().trim();
        Optional<Player> player = playerService.getPlayerById(id);

        if(player.isEmpty()){
            return ResponseEntity.notFound().build();
        }

        String nickname = chatClientService.generateNickname(
                player.get(),
                country
        );

        if (nickname == null || nickname.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }

        NickNameResponse response = new NickNameResponse(
                player.get().getPlayerId(),
                country,
                nickname.trim()
        );

        return ResponseEntity.ok(response);
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
}
