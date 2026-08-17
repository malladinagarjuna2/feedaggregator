package dev.learn.api.dto;

import java.util.List;

public record RefreshSummary(
        int feedsProcessed,
        int succeeded,
        int failed,
        int created,
        int updated,
        List<FeedRefreshResult> results) {

    public static RefreshSummary of(List<FeedRefreshResult> results) {
        int succeeded = 0;
        int failed = 0;
        int created = 0;
        int updated = 0;

        for (FeedRefreshResult result : results) {
            if (result.ok()) {
                succeeded++;
                created += result.created();
                updated += result.updated();
            } else {
                failed++;
            }
        }
        return new RefreshSummary(results.size(), succeeded, failed, created, updated, results);
    }
}
