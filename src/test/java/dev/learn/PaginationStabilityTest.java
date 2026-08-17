package dev.learn;

import dev.learn.domain.Feed;
import dev.learn.domain.Item;
import dev.learn.repository.FeedRepository;
import dev.learn.repository.ItemRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Why the secondary sort key exists.
 *
 * <p>Note what is and is not asserted here. An unstable sort is not <em>guaranteed</em> to
 * return rows in a different order — it is merely not guaranteed to return them in the
 * same one. So "the unstable query breaks" cannot be asserted; only "the unstable query's
 * order changes with the query plan, and the stable query's does not" can be.
 */
@QuarkusTest
class PaginationStabilityTest {

    private static final Instant SAME_INSTANT = Instant.parse("2026-01-01T00:00:00Z");
    private static final int TOTAL = 6;
    private static final int PAGE_SIZE = 2;

    private static final String UNSTABLE = "select id from item order by published_at desc";
    private static final String STABLE = "select id from item order by published_at desc, id desc";

    @Inject
    DataSource dataSource;

    @Inject
    FeedRepository feeds;

    @Inject
    ItemRepository items;

    @BeforeEach
    void seedItemsSharingOneTimestamp() throws Exception {
        QuarkusTransaction.requiringNew().run(() -> {
            items.deleteAll();
            feeds.deleteAll();

            Feed feed = new Feed("clock-collision", "https://example.com/feed");
            feeds.persist(feed);

            for (int i = 1; i <= TOTAL; i++) {
                items.persist(new Item(
                        feed, "ext-" + i, "item " + i, "https://example.com/" + i, SAME_INSTANT));
            }
        });

        exec("create index if not exists idx_item_published_at on item (published_at)");
        exec("analyze item");
    }

    /**
     * The core claim, and it is deterministic: with a tiebreaker the ordering does not
     * depend on how PostgreSQL chooses to read the table.
     */
    @Test
    void theStableOrderIsIdenticalUnderDifferentQueryPlans() throws Exception {
        List<Long> viaSeqScan = orderUnderPlan(STABLE, true);
        List<Long> viaIndexScan = orderUnderPlan(STABLE, false);

        assertEquals(viaSeqScan, viaIndexScan,
                "a total order cannot depend on the access path");
        assertEquals(TOTAL, new HashSet<>(viaSeqScan).size());
    }

    @Test
    void theStableOrderIsDescendingById() throws Exception {
        List<Long> all = orderUnderPlan(STABLE, true);

        List<Long> descending = new ArrayList<>(all);
        descending.sort((a, b) -> Long.compare(b, a));

        assertEquals(descending, all,
                "with identical timestamps the tiebreaker fully determines the order");
    }

    /**
     * Paging the whole set while rows are being rewritten underneath. An UPDATE in
     * PostgreSQL writes a new row version at the end of the heap, so physical position
     * changes between page queries — which is precisely what an unstable sort depends on.
     */
    @Test
    void stablePagingReturnsEveryRowExactlyOnceDespiteConcurrentUpdates() throws Exception {
        List<Long> collected = pageThrough(STABLE, true);

        System.out.println("  STABLE   paging -> " + collected);

        assertEquals(TOTAL, collected.size());
        assertEquals(TOTAL, new HashSet<>(collected).size(),
                "every row exactly once, no duplicates and no gaps: " + collected);
    }

    /**
     * Demonstration rather than a guarantee: collect the orderings the unstable query
     * produces under a sequential scan and under an index scan. If they differ, the
     * ORDER BY did not determine the result — which is the whole point.
     */
    @Test
    void theUnstableOrderIsNotDeterminedByItsOrderByClause() throws Exception {
        Set<List<Long>> distinctOrderings = new LinkedHashSet<>();
        distinctOrderings.add(orderUnderPlan(UNSTABLE, true));
        distinctOrderings.add(orderUnderPlan(UNSTABLE, false));

        exec("update item set title = title || '.' where id in "
                + "(select id from item order by published_at desc limit 3)");
        distinctOrderings.add(orderUnderPlan(UNSTABLE, true));

        System.out.println("  UNSTABLE orderings observed:");
        distinctOrderings.forEach(order -> System.out.println("    " + order));

        assertTrue(distinctOrderings.size() > 1,
                "the same ORDER BY returned " + distinctOrderings.size() + " ordering(s); "
                        + "with a tiebreaker there would only ever be one, by definition");
    }

    /**
     * The consequence, shown concretely: page through the whole set with the unstable
     * order while rows move, and count how many distinct rows were actually seen.
     */
    @Test
    void unstablePagingCanRepeatAndSkipRows() throws Exception {
        List<Long> collected = pageThrough(UNSTABLE, true);
        Set<Long> distinct = new HashSet<>(collected);

        System.out.println("  UNSTABLE paging -> " + collected
                + "  (" + distinct.size() + " distinct of " + TOTAL + ")");

        // Deliberately not asserted as a failure: an unstable sort is allowed to come out
        // right. What is asserted is the only thing guaranteed — that paging returned the
        // number of rows requested, whatever they turned out to be.
        assertEquals(TOTAL, collected.size());
        assertTrue(distinct.size() <= TOTAL);
    }

    private List<Long> pageThrough(String orderBy, boolean seqScan) throws Exception {
        List<Long> collected = new ArrayList<>();
        for (int offset = 0; offset < TOTAL; offset += PAGE_SIZE) {
            List<Long> page = queryIds(orderBy + " limit " + PAGE_SIZE + " offset " + offset, seqScan);
            collected.addAll(page);
            touch(page);
        }
        return collected;
    }

    private List<Long> orderUnderPlan(String sql, boolean seqScan) throws Exception {
        return queryIds(sql, seqScan);
    }

    private List<Long> queryIds(String sql, boolean seqScan) throws Exception {
        List<Long> ids = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            try (Statement plan = connection.createStatement()) {
                plan.execute("set enable_seqscan = " + (seqScan ? "on" : "off"));
            }
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong(1));
                }
            }
            try (Statement plan = connection.createStatement()) {
                plan.execute("set enable_seqscan = on");
            }
        }
        return ids;
    }

    private void touch(List<Long> ids) throws Exception {
        if (ids.isEmpty()) {
            return;
        }
        String inList = ids.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElseThrow();
        exec("update item set title = title || '.' where id in (" + inList + ")");
    }

    private void exec(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
