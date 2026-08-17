package dev.learn.api.dto;

import dev.learn.domain.Item;

import java.time.Instant;

public record ItemResponse(
        Long id,
        Long feedId,
        String externalId,
        String title,
        String url,
        Instant publishedAt) {

    // getFeed().getId() reads the foreign key already held by the proxy, so this does
    // not trigger a lazy load. Adding the feed's name here would cost one query per item.
    public static ItemResponse from(Item item) {
        return new ItemResponse(
                item.getId(),
                item.getFeed().getId(),
                item.getExternalId(),
                item.getTitle(),
                item.getUrl(),
                item.getPublishedAt());
    }
}
