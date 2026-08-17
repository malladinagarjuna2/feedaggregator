package dev.learn.service;

import dev.learn.api.dto.ItemPage;
import dev.learn.api.dto.ItemResponse;
import dev.learn.domain.Item;
import dev.learn.repository.ItemRepository;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Locale;

@ApplicationScoped
public class ItemSearchService {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;

    private static final char ESCAPE = '!';

    private final ItemRepository items;

    @Inject
    public ItemSearchService(ItemRepository items) {
        this.items = items;
    }

    public ItemPage search(String q, Long feedId, int limit, int offset) {
        if (offset < 0) {
            throw new ValidationException("offset must not be negative");
        }
        if (limit < 1) {
            throw new ValidationException("limit must be at least 1");
        }
        int effectiveLimit = Math.min(limit, MAX_LIMIT);

        PanacheQuery<Item> query = items.search(likePattern(q), feedId);
        long total = query.count();

        List<ItemResponse> page = query
                .range(offset, offset + effectiveLimit - 1)
                .list()
                .stream()
                .map(ItemResponse::from)
                .toList();

        return new ItemPage(total, effectiveLimit, offset, page);
    }

    // Locale.ROOT because Locale-sensitive lowercasing differs by JVM default: in a
    // Turkish locale "I".toLowerCase() is "ı", not "i", so a search for TITLE would
    // silently stop matching title.
    static String likePattern(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String escaped = q.strip()
                .toLowerCase(Locale.ROOT)
                .replace(String.valueOf(ESCAPE), ESCAPE + "" + ESCAPE)
                .replace("%", ESCAPE + "%")
                .replace("_", ESCAPE + "_");

        return "%" + escaped + "%";
    }
}
