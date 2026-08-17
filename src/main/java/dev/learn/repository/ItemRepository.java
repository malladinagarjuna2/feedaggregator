package dev.learn.repository;

import dev.learn.domain.Feed;
import dev.learn.domain.Item;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.HashMap;
import java.util.Map;
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

    // publishedAt alone is not a total order: two items sharing a timestamp have no
    // defined relative position, so LIMIT/OFFSET can show one twice and another never.
    // id DESC is the tiebreaker that makes the order total, and therefore pageable.
    public static final Sort STABLE_ORDER =
            Sort.by("publishedAt", Sort.Direction.Descending)
                    .and("id", Sort.Direction.Descending);

    public PanacheQuery<Item> search(String titlePattern, Long feedId) {
        StringBuilder where = new StringBuilder();
        Map<String, Object> params = new HashMap<>();

        if (titlePattern != null) {
            where.append("lower(title) like :title escape '!'");
            params.put("title", titlePattern);
        }
        if (feedId != null) {
            if (!where.isEmpty()) {
                where.append(" and ");
            }
            where.append("feed.id = :feedId");
            params.put("feedId", feedId);
        }

        if (where.isEmpty()) {
            return findAll(STABLE_ORDER);
        }
        return find(where.toString(), STABLE_ORDER, params);
    }
}
