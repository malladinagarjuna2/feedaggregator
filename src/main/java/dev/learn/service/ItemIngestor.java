package dev.learn.service;

import dev.learn.domain.Feed;
import dev.learn.domain.Item;
import dev.learn.fetch.HnHit;
import dev.learn.repository.FeedRepository;
import dev.learn.repository.ItemRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ItemIngestor {

    private static final int MAX_TITLE_LENGTH = 1000;
    private static final int MAX_URL_LENGTH = 2048;
    private static final int MAX_ERROR_LENGTH = 1000;

    private final FeedRepository feeds;
    private final ItemRepository items;

    @Inject
    public ItemIngestor(FeedRepository feeds, ItemRepository items) {
        this.feeds = feeds;
        this.items = items;
    }

    @Transactional
    public IngestResult store(long feedId, List<HnHit> hits) {
        Feed feed = feeds.findById(feedId);
        if (feed == null) {
            throw new FeedNotFoundException(feedId);
        }

        int created = 0;
        int updated = 0;
        int skipped = 0;

        for (HnHit hit : hits) {
            String externalId = hit.objectId();
            String title = truncate(hit.effectiveTitle(), MAX_TITLE_LENGTH);
            Instant publishedAt = hit.publishedAt();

            if (isBlank(externalId) || isBlank(title) || publishedAt == null) {
                skipped++;
                continue;
            }

            String url = truncate(hit.url(), MAX_URL_LENGTH);
            Optional<Item> existing = items.findByFeedAndExternalId(feed, externalId);

            if (existing.isPresent()) {
                Item item = existing.get();
                item.setTitle(title);
                item.setUrl(url);
                item.setPublishedAt(publishedAt);
                updated++;
            } else {
                items.persist(new Item(feed, externalId, title, url, publishedAt));
                created++;
            }
        }

        Instant now = Instant.now();
        feed.setLastFetchedAt(now);
        feed.setLastAttemptedAt(now);
        feed.setLastError(null);
        return new IngestResult(created, updated, skipped);
    }

    @Transactional
    public void recordFailure(long feedId, String error) {
        Feed feed = feeds.findById(feedId);
        if (feed == null) {
            return;
        }
        feed.setLastAttemptedAt(Instant.now());
        feed.setLastError(truncate(error, MAX_ERROR_LENGTH));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
