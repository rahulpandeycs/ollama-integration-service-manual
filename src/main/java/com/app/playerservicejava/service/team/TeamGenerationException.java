package com.app.playerservicejava.service.team;

import org.springframework.http.HttpStatus;

public class TeamGenerationException extends RuntimeException {

    private final HttpStatus status;

    public TeamGenerationException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public TeamGenerationException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
