package com.routeoptimizer.exception;

import lombok.Getter;

/**
 * Exception thrown when an upstream geocoding provider (e.g. OpenStreetMap Nominatim)
 * returns an HTTP error (4xx/5xx), times out, or fails to connect.
 * 
 * Ensures synthetic coordinates are never fabricated upon upstream failures.
 */
@Getter
public class GeocodingUpstreamException extends RuntimeException {
    private final int upstreamStatusCode;

    public GeocodingUpstreamException(String message, int upstreamStatusCode) {
        super(message);
        this.upstreamStatusCode = upstreamStatusCode;
    }
}
