package dev.learn.fetch;

public class FeedFetchException extends RuntimeException {

    public FeedFetchException(String message) {
        super(message);
    }

    public FeedFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
