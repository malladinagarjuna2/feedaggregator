package dev.learn.service;

import dev.learn.api.dto.FeedRefreshResult;
import dev.learn.api.dto.RefreshSummary;
import dev.learn.domain.Feed;
import dev.learn.fetch.FeedFetcher;
import dev.learn.fetch.HnHit;
import dev.learn.repository.FeedRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class RefreshService {

    private static final Logger LOG = Logger.getLogger(RefreshService.class);

    private final FeedRepository feeds;
    private final FeedFetcher fetcher;
    private final ItemIngestor ingestor;

    @Inject
    public RefreshService(FeedRepository feeds, FeedFetcher fetcher, ItemIngestor ingestor) {
        this.feeds = feeds;
        this.fetcher = fetcher;
        this.ingestor = ingestor;
    }

    public RefreshSummary refreshAllEnabled() {
        List<Feed> enabled = QuarkusTransaction.requiringNew().call(feeds::listEnabled);

        List<FeedRefreshResult> results = new ArrayList<>(enabled.size());
        for (Feed feed : enabled) {
            results.add(refreshOne(feed.getId(), feed.getName(), feed.getUrl()));
        }
        return RefreshSummary.of(results);
    }

    private FeedRefreshResult refreshOne(Long feedId, String name, String url) {
        try {
            List<HnHit> hits = fetcher.fetch(url);
            IngestResult result = ingestor.store(feedId, hits);
            LOG.infof("refreshed feed %d (%s): %d created, %d updated, %d skipped",
                    feedId, name, result.created(), result.updated(), result.skipped());
            return FeedRefreshResult.success(feedId, name, result.created(), result.updated(), result.skipped());
        } catch (RuntimeException e) {
            LOG.warnf("feed %d (%s) failed to refresh: %s", feedId, name, e.getMessage());
            recordFailureQuietly(feedId, e.getMessage());
            return FeedRefreshResult.failure(feedId, name, e.getMessage());
        }
    }

    private void recordFailureQuietly(Long feedId, String error) {
        try {
            ingestor.recordFailure(feedId, error);
        } catch (RuntimeException e) {
            LOG.warnf("could not record failure for feed %d: %s", feedId, e.getMessage());
        }
    }
}
