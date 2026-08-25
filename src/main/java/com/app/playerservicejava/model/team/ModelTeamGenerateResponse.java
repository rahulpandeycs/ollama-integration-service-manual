package com.app.playerservicejava.model.team;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ModelTeamGenerateResponse(
        @JsonProperty("seed_id") String seedId,
        @JsonProperty("prediction_id") String predictionId,
        @JsonProperty("team_size") int teamSize,
        @JsonProperty("member_ids") List<String> memberIds
) {
}
