package com.app.playerservicejava;

import com.app.playerservicejava.model.Player;
import com.app.playerservicejava.model.team.ModelTeamGenerateResponse;
import com.app.playerservicejava.model.team.TeamResponse;
import com.app.playerservicejava.service.PlayerService;
import com.app.playerservicejava.service.model.TeamModelClient;
import com.app.playerservicejava.service.team.TeamGenerationException;
import com.app.playerservicejava.service.team.TeamService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@ExtendWith(MockitoExtension.class)
class TeamServiceTests {

    @Mock
    private PlayerService playerService;

    @Mock
    private TeamModelClient teamModelClient;

    @Test
    void preservesModelOrderWhenEnrichingPlayers() {
        TeamService teamService = new TeamService(playerService, teamModelClient);
        List<String> memberIds = List.of("member-2", "aaronha01", "member-1");

        when(playerService.getPlayersByIds(List.of("aaronha01")))
                .thenReturn(List.of(player("aaronha01")));
        when(teamModelClient.generateTeam("aaronha01", 5))
                .thenReturn(modelResponse(memberIds));
        when(playerService.getPlayersByIds(memberIds))
                .thenReturn(List.of(
                        player("member-1"),
                        player("aaronha01"),
                        player("member-2")
                ));

        Optional<TeamResponse> result = teamService.generateTeam("aaronha01", 5);

        TeamResponse response = result.orElseThrow();
        assertEquals(memberIds, response.memberIds());
        assertEquals("member-2", response.players().get(0).getPlayerId());
        assertEquals("aaronha01", response.players().get(1).getPlayerId());
        assertEquals("member-1", response.players().get(2).getPlayerId());
        verify(playerService).getPlayersByIds(memberIds);
    }

    @Test
    void doesNotCallModelForUnknownSeedPlayer() {
        TeamService teamService = new TeamService(playerService, teamModelClient);
        when(playerService.getPlayersByIds(List.of("missing"))).thenReturn(List.of());

        Optional<TeamResponse> result = teamService.generateTeam("missing", 5);

        assertEquals(Optional.empty(), result);
        verifyNoInteractions(teamModelClient);
    }

    @Test
    void rejectsModelMemberThatJavaCannotResolve() {
        TeamService teamService = new TeamService(playerService, teamModelClient);
        List<String> memberIds = List.of("aaronha01", "missing-member");

        when(playerService.getPlayersByIds(List.of("aaronha01")))
                .thenReturn(List.of(player("aaronha01")));
        when(teamModelClient.generateTeam("aaronha01", 5))
                .thenReturn(modelResponse(memberIds));
        when(playerService.getPlayersByIds(memberIds))
                .thenReturn(List.of(player("aaronha01")));

        TeamGenerationException exception = assertThrows(
                TeamGenerationException.class,
                () -> teamService.generateTeam("aaronha01", 5)
        );

        assertEquals(BAD_GATEWAY, exception.getStatus());
    }

    private ModelTeamGenerateResponse modelResponse(List<String> memberIds) {
        return new ModelTeamGenerateResponse(
                "aaronha01",
                "prediction-1",
                memberIds.size(),
                memberIds
        );
    }

    private Player player(String playerId) {
        Player player = new Player();
        player.setPlayerId(playerId);
        return player;
    }
}
