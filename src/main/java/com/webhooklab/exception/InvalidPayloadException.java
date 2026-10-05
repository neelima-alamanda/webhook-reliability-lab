package com.webhooklab.exception;

import lombok.Getter;

import java.util.Map;

@Getter
public class InvalidPayloadException extends RuntimeException {

    private final Map<String, String> details;

    public InvalidPayloadException(String message) {
        super(message);
        this.details = null;
    }

    public InvalidPayloadException(String message, Map<String, String> details) {
        super(message);
        this.details = details;
    }
}
