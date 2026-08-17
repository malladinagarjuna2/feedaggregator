package dev.learn.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A configured source we poll for items.
 *
 * <p>This class is the schema. Hibernate reads these annotations and generates the
 * {@code create table feed (...)} statement — there is no separate SQL file.
 */
@Entity
@Table(name = "feed")
public class Feed {

    /**
     * {@code @GeneratedValue} with no strategy means AUTO, which on PostgreSQL becomes a
     * sequence ({@code feed_seq}). Hibernate asks the sequence for the next id before the
     * insert, so it knows the id without a round trip after writing.
     */
    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    /** Feed URLs can be long; the JPA default of varchar(255) is not enough. */
    @Column(nullable = false, length = 2048)
    private String url;

    @Column(nullable = false)
    private boolean enabled = true;

    /** Null until the first successful fetch. */
    @Column(name = "last_fetched_at")
    private Instant lastFetchedAt;

    /** Set on every attempt, success or failure, so a silently failing feed is visible. */
    @Column(name = "last_attempted_at")
    private Instant lastAttemptedAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    /**
     * The other half of {@link Item#getFeed()}. {@code mappedBy} says Item owns the
     * foreign key — this side is a read view of it, not a second column.
     *
     * <p>{@code cascade = ALL} plus {@code orphanRemoval} is what makes deleting a feed
     * delete its items. Without it, {@code DELETE /feeds/{id}} would violate the foreign
     * key constraint as soon as any items existed.
     */
    @OneToMany(mappedBy = "feed", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Item> items = new ArrayList<>();

    /**
     * Hibernate instantiates entities reflectively and needs a no-argument constructor.
     * It is {@code protected} so application code is pushed towards the real one below.
     */
    protected Feed() {
    }

    public Feed(String name, String url) {
        this.name = name;
        this.url = url;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getLastFetchedAt() {
        return lastFetchedAt;
    }

    public void setLastFetchedAt(Instant lastFetchedAt) {
        this.lastFetchedAt = lastFetchedAt;
    }

    public Instant getLastAttemptedAt() {
        return lastAttemptedAt;
    }

    public void setLastAttemptedAt(Instant lastAttemptedAt) {
        this.lastAttemptedAt = lastAttemptedAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public List<Item> getItems() {
        return items;
    }
}
