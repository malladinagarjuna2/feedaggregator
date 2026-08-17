package dev.learn;

import dev.learn.domain.Feed;
import dev.learn.repository.FeedRepository;
import dev.learn.repository.ItemRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@TestProfile(ScheduledPollingTest.PollingEnabled.class)
class ScheduledPollingTest {

    public static class PollingEnabled implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "feeds.poll.enabled", "true",
                    "feeds.poll.interval", "1s");
        }
    }

    @TestHTTPResource("/test-stub/hn")
    URL stubUrl;

    @Inject
    FeedRepository feeds;

    @Inject
    ItemRepository items;

    @Test
    void theSchedulerFetchesEnabledFeedsWithoutAnyHttpCall() {
        Long feedId = QuarkusTransaction.requiringNew().call(() -> {
            Feed feed = new Feed("stub", stubUrl.toString());
            feeds.persist(feed);
            return feed.getId();
        });

        long stored = waitForItems(Duration.ofSeconds(30));

        assertEquals(3, stored, "the scheduled poll should have stored the stub's 3 items");

        Feed feed = QuarkusTransaction.requiringNew().call(() -> feeds.findById(feedId));
        assertNotNull(feed.getLastFetchedAt(), "the poll should have recorded lastFetchedAt");
    }

    private long waitForItems(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        long count = 0;
        while (System.nanoTime() < deadline) {
            count = QuarkusTransaction.requiringNew().call(() -> items.count());
            if (count > 0) {
                return count;
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return count;
    }
}
