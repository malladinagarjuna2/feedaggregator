package dev.learn.service;

public class FeedNotFoundException extends RuntimeException {

    public FeedNotFoundException(long id) {
        super("no feed with id " + id);
    }
}
