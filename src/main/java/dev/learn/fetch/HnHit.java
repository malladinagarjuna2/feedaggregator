package dev.learn.fetch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HnHit(
        @JsonProperty("objectID") String objectId,
        String title,
        @JsonProperty("story_title") String storyTitle,
        String url,
        @JsonProperty("created_at_i") Long createdAtEpochSeconds) {

    public String effectiveTitle() {
        if (title != null && !title.isBlank()) {
            return title;
        }
        return storyTitle;
    }

    public Instant publishedAt() {
        return createdAtEpochSeconds == null ? null : Instant.ofEpochSecond(createdAtEpochSeconds);
    }
}
