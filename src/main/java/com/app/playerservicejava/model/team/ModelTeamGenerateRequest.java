package com.app.playerservicejava.model.team;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ModelTeamGenerateRequest(
        @JsonProperty("seed_id") String seedId,
        @JsonProperty("team_size") int teamSize
) {
}
