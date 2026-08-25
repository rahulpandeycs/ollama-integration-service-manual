package com.app.playerservicejava.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.List;

public record PlayerResponse(List<Name> players) implements Serializable {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Name(String firstName, String lastName) implements Serializable {
    }
}
