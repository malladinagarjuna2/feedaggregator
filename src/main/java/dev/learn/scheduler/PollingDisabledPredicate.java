package dev.learn.scheduler;

import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.ScheduledExecution;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class PollingDisabledPredicate implements Scheduled.SkipPredicate {

    @ConfigProperty(name = "feeds.poll.enabled", defaultValue = "true")
    boolean pollingEnabled;

    @Override
    public boolean test(ScheduledExecution execution) {
        return !pollingEnabled;
    }
}
