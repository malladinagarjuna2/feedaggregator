package dev.learn.fetch;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@ApplicationScoped
public class FeedFetcher {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final HttpClient http;
    private final ObjectMapper mapper;

    @Inject
    public FeedFetcher(ObjectMapper mapper) {
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public List<HnHit> fetch(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new FeedFetchException("could not reach " + url + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FeedFetchException("interrupted while fetching " + url, e);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new FeedFetchException(url + " returned HTTP " + response.statusCode());
        }

        try {
            return mapper.readValue(response.body(), HnSearchResponse.class).hitsOrEmpty();
        } catch (IOException e) {
            throw new FeedFetchException("could not parse response from " + url + ": " + e.getMessage(), e);
        }
    }
}
