package dev.learn.repository;

import dev.learn.domain.Feed;
import dev.learn.domain.Item;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

/**
 * Data access for {@link Item}.
 */
@ApplicationScoped
public class ItemRepository implements PanacheRepository<Item> {

    /**
     * The lookup half of the Phase 3 upsert. {@code ?1} and {@code ?2} are positional
     * bind parameters — the values never become part of the query string, so this is
     * parameterised and not string concatenation.
     */
    public Optional<Item> findByFeedAndExternalId(Feed feed, String externalId) {
        return find("feed = ?1 and externalId = ?2", feed, externalId).firstResultOptional();
    }

    public long countForFeed(Feed feed) {
        return count("feed", feed);
    }
}
