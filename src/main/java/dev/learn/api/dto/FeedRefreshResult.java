package dev.learn.api.dto;

public record FeedRefreshResult(
        Long feedId,
        String name,
        boolean ok,
        int created,
        int updated,
        int skipped,
        String error) {

    public static FeedRefreshResult success(Long feedId, String name, int created, int updated, int skipped) {
        return new FeedRefreshResult(feedId, name, true, created, updated, skipped, null);
    }

    public static FeedRefreshResult failure(Long feedId, String name, String error) {
        return new FeedRefreshResult(feedId, name, false, 0, 0, 0, error);
    }
}
