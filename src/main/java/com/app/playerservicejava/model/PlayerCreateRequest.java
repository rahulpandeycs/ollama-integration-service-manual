package com.app.playerservicejava.model;

import jakarta.validation.constraints.NotBlank;

public record PlayerCreateRequest(
        String birthYear,
        String birthMonth,
        String birthDay,
        String birthCountry,
        String birthState,
        String birthCity,
        String deathYear,
        String deathMonth,
        String deathDay,
        String deathCountry,
        String deathState,
        String deathCity,
        @NotBlank String firstName,
        @NotBlank String lastName,
        String givenName,
        String weight,
        String height,
        String bats,
        String throwStats,
        String debut,
        String finalGame,
        String retroId,
        String bbrefId
) {
}
