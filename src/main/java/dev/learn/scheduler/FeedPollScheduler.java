package dev.learn.scheduler;

import dev.learn.api.dto.RefreshSummary;
import dev.learn.service.RefreshService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class FeedPollScheduler {

    private static final Logger LOG = Logger.getLogger(FeedPollScheduler.class);

    private final RefreshService refresh;

    @Inject
    public FeedPollScheduler(RefreshService refresh) {
        this.refresh = refresh;
    }

    @Scheduled(
            identity = "feed-poll",
            every = "{feeds.poll.interval}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP,
            skipExecutionIf = PollingDisabledPredicate.class)
    void pollAllFeeds() {
        RefreshSummary summary = refresh.refreshAllEnabled();

        if (summary.feedsProcessed() == 0) {
            LOG.debug("scheduled poll: no enabled feeds");
            return;
        }
        LOG.infof("scheduled poll: %d feeds, %d ok, %d failed, %d created, %d updated",
                summary.feedsProcessed(), summary.succeeded(), summary.failed(),
                summary.created(), summary.updated());
    }
}
