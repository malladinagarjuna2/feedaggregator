package dev.learn.repository;

import dev.learn.domain.Feed;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Data access for {@link Feed}.
 *
 * <p>Implementing {@code PanacheRepository<Feed>} is the whole trick: Quarkus generates
 * {@code listAll}, {@code findById}, {@code persist}, {@code delete}, {@code count} and
 * friends at build time. There is no DAO to write and no interface to implement by hand.
 *
 * <p>This is the <em>repository</em> pattern. Panache also offers an active-record style
 * where the entity itself carries the query methods; keeping them apart means the entity
 * stays a plain description of a table, which is what most real codebases do.
 */
@ApplicationScoped
public class FeedRepository implements PanacheRepository<Feed> {

    /**
     * Panache query strings are HQL fragments, not SQL: they name <em>entity fields</em>,
     * not columns. A bare field name expands to {@code where enabled = ?1}.
     */
    public List<Feed> listEnabled() {
        return list("enabled", true);
    }
}
