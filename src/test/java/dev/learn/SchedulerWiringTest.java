package dev.learn;

import dev.learn.scheduler.PollingDisabledPredicate;
import io.quarkus.scheduler.Scheduler;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class SchedulerWiringTest {

    @Inject
    Scheduler scheduler;

    @Inject
    PollingDisabledPredicate pollingDisabled;

    @Test
    void theFeedPollJobIsRegistered() {
        boolean registered = scheduler.getScheduledJobs().stream()
                .anyMatch(trigger -> "feed-poll".equals(trigger.getId()));

        assertTrue(registered,
                "expected a job with identity feed-poll, found: "
                        + scheduler.getScheduledJobs().stream().map(t -> t.getId()).toList());
    }

    @Test
    void pollingIsSkippedUnderTheTestProfile() {
        assertTrue(pollingDisabled.test(null),
                "background polling must be off in tests or it races every other assertion");
    }

    @Test
    void theConfiguredIntervalIsUsed() {
        String interval = org.eclipse.microprofile.config.ConfigProvider.getConfig()
                .getValue("feeds.poll.interval", String.class);

        assertEquals("24h", interval);
    }
}
