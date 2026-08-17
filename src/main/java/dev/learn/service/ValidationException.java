package dev.learn.service;

/**
 * Thrown when a request body is not acceptable. Mapped to HTTP 400 by
 * {@code ValidationExceptionMapper}.
 *
 * <p>Extends {@link RuntimeException} — an <em>unchecked</em> exception, so callers are not
 * forced to declare or catch it. Java also has checked exceptions, which every caller must
 * handle or propagate; those are a poor fit here because nothing between the service and the
 * HTTP boundary can meaningfully recover.
 *
 * <p>The message is written to be shown to the client, so it must say what is wrong without
 * leaking internals.
 */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}
