package dev.learn.fetch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HnSearchResponse(List<HnHit> hits) {

    public List<HnHit> hitsOrEmpty() {
        return hits == null ? List.of() : hits;
    }
}
