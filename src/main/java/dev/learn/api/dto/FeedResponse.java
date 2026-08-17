package dev.learn.api.dto;

import dev.learn.domain.Feed;

import java.time.Instant;

/**
 * What {@code GET /feeds} and {@code POST /feeds} return.
 *
 * <p>This exists so the {@link Feed} entity never leaves the service layer. Serialising an
 * entity directly causes two real problems:
 *
 * <ul>
 *   <li>Lazy associations. {@code Feed.items} is a Hibernate proxy; Jackson touching it
 *       outside an open session throws {@code LazyInitializationException}, and inside one
 *       it silently issues an extra query and dumps every item into the response.</li>
 *   <li>Accidental exposure. Any field added to the entity later — an API token, an internal
 *       error message — appears in the API the moment it is added, with nobody deciding
 *       that it should.</li>
 * </ul>
 *
 * <p>The mapping below is the boundary: what is listed here is the API, and nothing else is.
 */
public record FeedResponse(
        Long id,
        String name,
        String url,
        boolean enabled,
        Instant lastFetchedAt) {

    public static FeedResponse from(Feed feed) {
        return new FeedResponse(
                feed.getId(),
                feed.getName(),
                feed.getUrl(),
                feed.isEnabled(),
                feed.getLastFetchedAt());
    }
}
