package dev.learn.api.dto;

/**
 * The JSON body of {@code POST /feeds}.
 *
 * <p>A {@code record} is Java's immutable data class: this one line generates the
 * constructor, accessors {@code name()} and {@code url()}, {@code equals}, {@code hashCode}
 * and {@code toString}. Closest to a Go struct with only exported fields, or a frozen
 * object literal in JS.
 *
 * <p>Jackson binds incoming JSON by matching property names to the record components, so
 * {@code {"name": "hn", "url": "..."}} arrives here with no mapping code. Fields the client
 * omits arrive as {@code null} rather than an error — which is exactly why validation is a
 * separate, explicit step.
 */
public record CreateFeedRequest(String name, String url) {
}
