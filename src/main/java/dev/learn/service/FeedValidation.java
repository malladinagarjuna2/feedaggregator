package dev.learn.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

final class FeedValidation {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final int MAX_NAME_LENGTH = 200;
    private static final int MAX_URL_LENGTH = 2048;

    private FeedValidation() {
    }

    static String requireName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException("name must not be blank");
        }
        String name = raw.strip();
        if (name.length() > MAX_NAME_LENGTH) {
            throw new ValidationException("name must be at most " + MAX_NAME_LENGTH + " characters");
        }
        return name;
    }

    static String requireUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException("url must not be blank");
        }
        String url = raw.strip();
        if (url.length() > MAX_URL_LENGTH) {
            throw new ValidationException("url must be at most " + MAX_URL_LENGTH + " characters");
        }

        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new ValidationException("url is not a valid URI: " + e.getReason());
        }

        if (!uri.isAbsolute()) {
            throw new ValidationException("url must be absolute and include a scheme, e.g. https://example.com/feed");
        }

        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!ALLOWED_SCHEMES.contains(scheme)) {
            throw new ValidationException("url scheme must be http or https, got: " + scheme);
        }

        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new ValidationException("url must include a host");
        }

        return url;
    }
}
