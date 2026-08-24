package com.app.playerservicejava.model;

public record NickNameResponse(
        String playerId,
        String country,
        String nickname
) {
}
