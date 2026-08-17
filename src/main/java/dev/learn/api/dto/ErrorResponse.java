package dev.learn.api.dto;

/**
 * A uniform error body, so every 4xx from this API looks the same:
 * {@code {"error": "url scheme must be http or https, got: ftp"}}.
 */
public record ErrorResponse(String error) {
}
