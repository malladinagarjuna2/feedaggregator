package dev.learn;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Asserts the schema Hibernate generated from the entity classes.
 *
 * <p>The DDL is also printed to the log, but a log line is not a check. These read the
 * live catalogue, so they fail if an annotation is dropped or renamed.
 */
@QuarkusTest
class SchemaTest {

    @Inject
    DataSource dataSource;

    private List<String> queryColumn(String sql, Object... params) throws Exception {
        List<String> out = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        }
        return out;
    }

    @Test
    void bothTablesWereCreated() throws Exception {
        List<String> tables = queryColumn("""
                select table_name
                from information_schema.tables
                where table_schema = 'public' and table_type = 'BASE TABLE'
                order by table_name
                """);

        assertTrue(tables.contains("feed"), "missing feed table, found: " + tables);
        assertTrue(tables.contains("item"), "missing item table, found: " + tables);
    }

    @Test
    void feedColumnsMatchTheEntityFields() throws Exception {
        List<String> columns = queryColumn("""
                select column_name
                from information_schema.columns
                where table_schema = 'public' and table_name = 'feed'
                order by column_name
                """);

        // Note the naming: lastFetchedAt became last_fetched_at. Hibernate converts
        // camelCase field names to snake_case columns by default.
        assertEquals(
                List.of("enabled", "id", "last_attempted_at", "last_error",
                        "last_fetched_at", "name", "url"),
                columns);
    }

    @Test
    void itemColumnsMatchTheEntityFields() throws Exception {
        List<String> columns = queryColumn("""
                select column_name
                from information_schema.columns
                where table_schema = 'public' and table_name = 'item'
                order by column_name
                """);

        assertEquals(
                List.of("external_id", "feed_id", "id", "published_at", "title", "url"),
                columns);
    }

    @Test
    void itemHasAForeignKeyToFeed() throws Exception {
        List<String> referenced = queryColumn("""
                select ccu.table_name
                from information_schema.table_constraints tc
                join information_schema.constraint_column_usage ccu
                  on tc.constraint_name = ccu.constraint_name
                where tc.table_name = 'item' and tc.constraint_type = 'FOREIGN KEY'
                """);

        assertEquals(List.of("feed"), referenced);
    }

    /**
     * The constraint Phase 3's upsert depends on. If this is ever dropped, refreshing a
     * feed twice concurrently can insert the same item twice and nothing will complain.
     */
    @Test
    void itemIsUniqueOnFeedAndExternalId() throws Exception {
        List<String> columns = queryColumn("""
                select kcu.column_name
                from information_schema.table_constraints tc
                join information_schema.key_column_usage kcu
                  on tc.constraint_name = kcu.constraint_name
                where tc.table_name = 'item'
                  and tc.constraint_type = 'UNIQUE'
                  and tc.constraint_name = 'uk_item_feed_external_id'
                order by kcu.ordinal_position
                """);

        assertEquals(List.of("feed_id", "external_id"), columns);
    }

    @Test
    void urlColumnsAreWideEnoughForRealUrls() throws Exception {
        List<String> lengths = queryColumn("""
                select character_maximum_length::text
                from information_schema.columns
                where table_schema = 'public' and table_name = 'item' and column_name = 'url'
                """);

        // The JPA default is varchar(255), which real feed URLs overflow.
        assertEquals(List.of("2048"), lengths);
    }
}
