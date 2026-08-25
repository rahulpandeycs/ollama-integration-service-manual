package com.app.playerservicejava.model.team;

import com.app.playerservicejava.model.Player;

import java.util.List;

public record TeamResponse(
        String seedId,
        String predictionId,
        int teamSize,
        List<String> memberIds,
        List<Player> players
) {
}
