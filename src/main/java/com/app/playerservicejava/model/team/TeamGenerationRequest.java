package com.app.playerservicejava.model.team;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record TeamGenerationRequest(
        @Min(1)
        @Max(25)
        Integer teamSize
) {
    public int teamSizeOrDefault() {
        return teamSize == null ? 5 : teamSize;
    }
}
