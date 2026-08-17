package dev.learn.api.dto;

import java.util.List;

public record ItemPage(
        long count,
        int limit,
        int offset,
        List<ItemResponse> items) {
}
