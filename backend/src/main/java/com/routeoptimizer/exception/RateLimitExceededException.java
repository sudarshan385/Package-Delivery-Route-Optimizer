package com.routeoptimizer.exception;

import lombok.Getter;

@Getter
public class RateLimitExceededException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final long retryAfterSeconds;

    public RateLimitExceededException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }
}
