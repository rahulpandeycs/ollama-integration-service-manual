package com.app.playerservicejava.service.team;

import com.app.playerservicejava.model.Player;
import com.app.playerservicejava.model.team.ModelTeamGenerateResponse;
import com.app.playerservicejava.model.team.TeamResponse;
import com.app.playerservicejava.service.PlayerService;
import com.app.playerservicejava.service.model.TeamModelClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TeamService {

    private final PlayerService playerService;
    private final TeamModelClient teamModelClient;

    public TeamService(PlayerService playerService, TeamModelClient teamModelClient) {
        this.playerService = playerService;
        this.teamModelClient = teamModelClient;
    }

    public Optional<TeamResponse> generateTeam(String playerId, int teamSize) {
        if (playerService.getPlayersByIds(List.of(playerId)).isEmpty()) {
            return Optional.empty();
        }

        ModelTeamGenerateResponse modelResponse = teamModelClient.generateTeam(playerId, teamSize);
        validateModelResponse(modelResponse);

        List<String> memberIds = modelResponse.memberIds();
        List<Player> resolvedPlayers = playerService.getPlayersByIds(memberIds);
        Map<String, Player> playersById = resolvedPlayers.stream()
                .collect(Collectors.toMap(Player::getPlayerId, Function.identity()));

        List<Player> orderedPlayers = memberIds.stream()
                .map(playersById::get)
                .toList();

        if (orderedPlayers.stream().anyMatch(player -> player == null)) {
            throw new TeamGenerationException(
                    HttpStatus.BAD_GATEWAY,
                    "Model service returned a player ID that Java could not resolve"
            );
        }

        return Optional.of(new TeamResponse(
                modelResponse.seedId(),
                modelResponse.predictionId(),
                modelResponse.teamSize(),
                memberIds,
                orderedPlayers
        ));
    }

    private void validateModelResponse(ModelTeamGenerateResponse response) {
        if (response.seedId() == null
                || response.predictionId() == null
                || response.memberIds() == null) {
            throw new TeamGenerationException(
                    HttpStatus.BAD_GATEWAY,
                    "Model service returned an incomplete response"
            );
        }
    }
}
