package com.app.playerservicejava.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlayerResponse(
        String playerId,
        String firstName,
        String lastName
) {
}
