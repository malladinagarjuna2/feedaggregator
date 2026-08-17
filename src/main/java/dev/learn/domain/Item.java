package dev.learn.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * One entry pulled from a {@link Feed}.
 *
 * <p>The unique constraint on {@code (feed_id, external_id)} is load-bearing. Phase 3
 * upserts by that pair so repeated fetches do not duplicate rows, and the check-then-insert
 * it performs is a race: the scheduler and a manual {@code POST /refresh} can both look,
 * both find nothing, and both insert. The database constraint is what actually prevents
 * the duplicate — the application check only avoids the error in the common case.
 */
@Entity
@Table(
        name = "item",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_item_feed_external_id",
                columnNames = {"feed_id", "external_id"}
        )
)
public class Item {

    @Id
    @GeneratedValue
    private Long id;

    /**
     * The foreign key. This side owns it — {@code Feed.items} is {@code mappedBy} this
     * field, so there is exactly one {@code feed_id} column.
     *
     * <p>{@code LAZY} means loading an Item does not automatically load its Feed; Hibernate
     * hands back a proxy and fetches on first access. That is why entities must never be
     * returned straight from a REST endpoint: serialising a proxy outside an open session
     * throws. Phase 2 maps to records instead.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "feed_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_item_feed")
    )
    private Feed feed;

    /** The id the source gave this item — HN Algolia calls it {@code objectID}. */
    @Column(name = "external_id", nullable = false, length = 200)
    private String externalId;

    @Column(nullable = false, length = 1000)
    private String title;

    @Column(length = 2048)
    private String url;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    protected Item() {
    }

    public Item(Feed feed, String externalId, String title, String url, Instant publishedAt) {
        this.feed = feed;
        this.externalId = externalId;
        this.title = title;
        this.url = url;
        this.publishedAt = publishedAt;
    }

    public Long getId() {
        return id;
    }

    public Feed getFeed() {
        return feed;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }
}
