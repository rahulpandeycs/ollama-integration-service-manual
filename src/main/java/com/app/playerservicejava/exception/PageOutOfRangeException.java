package com.app.playerservicejava.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class PageOutOfRangeException extends RuntimeException {

    public PageOutOfRangeException(int page, int totalPages) {
        super(
                "Requested page " + page +
                        " is outside the available range. " +
                        "Total pages: " + totalPages
        );
    }
}
