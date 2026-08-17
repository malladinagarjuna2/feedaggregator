package dev.learn;

import dev.learn.domain.Feed;
import dev.learn.domain.Item;
import dev.learn.repository.FeedRepository;
import dev.learn.repository.ItemRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class RefreshTest {

    @TestHTTPResource("/test-stub/hn")
    URL stubUrl;

    @TestHTTPResource("/test-stub/broken")
    URL brokenUrl;

    @TestHTTPResource("/test-stub/garbage")
    URL garbageUrl;

    @Inject
    FeedRepository feeds;

    @Inject
    ItemRepository items;

    @BeforeEach
    void clean() {
        StubFeedResource.reset();
        QuarkusTransaction.requiringNew().run(() -> {
            items.deleteAll();
            feeds.deleteAll();
        });
    }

    private Long createFeed(String name, String url) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Feed feed = new Feed(name, url);
            feeds.persist(feed);
            return feed.getId();
        });
    }

    private long countItems() {
        return QuarkusTransaction.requiringNew().call(() -> items.count());
    }

    @Test
    void fetchStoresItemsFromTheFeed() {
        createFeed("stub", stubUrl.toString());

        given().when().post("/refresh")
                .then().statusCode(200)
                .body("feedsProcessed", equalTo(1))
                .body("succeeded", equalTo(1))
                .body("failed", equalTo(0))
                .body("created", equalTo(3))
                .body("updated", equalTo(0));

        assertEquals(3, countItems());
    }

    @Test
    void fetchingTwiceDoesNotDuplicateItems() {
        createFeed("stub", stubUrl.toString());

        given().when().post("/refresh").then().statusCode(200).body("created", equalTo(3));
        assertEquals(3, countItems());

        given().when().post("/refresh")
                .then().statusCode(200)
                .body("created", equalTo(0))
                .body("updated", equalTo(3));

        assertEquals(3, countItems(), "second refresh must not duplicate rows");
    }

    @Test
    void refreshRecordsLastFetchedAt() {
        Long feedId = createFeed("stub", stubUrl.toString());

        given().when().post("/refresh").then().statusCode(200);

        Feed feed = QuarkusTransaction.requiringNew().call(() -> feeds.findById(feedId));
        org.junit.jupiter.api.Assertions.assertNotNull(feed.getLastFetchedAt());
    }

    @Test
    void changedTitlesAreUpdatedInPlace() {
        createFeed("stub", stubUrl.toString());
        given().when().post("/refresh").then().statusCode(200);

        StubFeedResource.setPayload("""
                {"hits": [
                  {"objectID": "1", "title": "Rust ownership explained (v2)",
                   "url": "https://example.com/1", "created_at_i": 1700000300}
                ]}
                """);

        given().when().post("/refresh")
                .then().statusCode(200)
                .body("created", equalTo(0))
                .body("updated", equalTo(1));

        assertEquals(3, countItems());
        String title = QuarkusTransaction.requiringNew().call(
                () -> items.find("externalId", "1").firstResult().getTitle());
        assertEquals("Rust ownership explained (v2)", title);
    }

    @Test
    void itemsWithoutAnExternalIdOrTitleAreSkipped() {
        createFeed("stub", stubUrl.toString());

        StubFeedResource.setPayload("""
                {"hits": [
                  {"objectID": "10", "title": "keeps this", "url": "https://example.com/10",
                   "created_at_i": 1700000300},
                  {"objectID": "11", "url": "https://example.com/11", "created_at_i": 1700000200},
                  {"title": "no external id", "url": "https://example.com/12",
                   "created_at_i": 1700000100},
                  {"objectID": "13", "title": "no timestamp", "url": "https://example.com/13"}
                ]}
                """);

        given().when().post("/refresh")
                .then().statusCode(200)
                .body("created", equalTo(1))
                .body("results[0].skipped", equalTo(3));

        assertEquals(1, countItems());
    }

    @Test
    void commentHitsFallBackToStoryTitle() {
        createFeed("stub", stubUrl.toString());

        StubFeedResource.setPayload("""
                {"hits": [
                  {"objectID": "20", "title": null, "story_title": "Parent story title",
                   "url": "https://example.com/20", "created_at_i": 1700000300}
                ]}
                """);

        given().when().post("/refresh").then().statusCode(200).body("created", equalTo(1));

        String title = QuarkusTransaction.requiringNew().call(
                () -> items.find("externalId", "20").firstResult().getTitle());
        assertEquals("Parent story title", title);
    }

    @Test
    void oneFailingFeedDoesNotStopTheOthers() {
        createFeed("good", stubUrl.toString());
        createFeed("http-error", brokenUrl.toString());
        createFeed("bad-json", garbageUrl.toString());
        createFeed("unreachable", "http://127.0.0.1:1/nothing-here");

        given().when().post("/refresh")
                .then().statusCode(200)
                .body("feedsProcessed", equalTo(4))
                .body("succeeded", equalTo(1))
                .body("failed", equalTo(3))
                .body("created", equalTo(3));

        assertEquals(3, countItems(), "the healthy feed must still have been stored");
    }

    @Test
    void failedFetchesAreRecordedOnTheFeed() {
        Long feedId = createFeed("broken", brokenUrl.toString());

        given().when().post("/refresh").then().statusCode(200).body("failed", equalTo(1));

        Feed feed = QuarkusTransaction.requiringNew().call(() -> feeds.findById(feedId));
        org.junit.jupiter.api.Assertions.assertNull(feed.getLastFetchedAt(),
                "a failed fetch must not look like a successful one");
        org.junit.jupiter.api.Assertions.assertNotNull(feed.getLastAttemptedAt());
        org.junit.jupiter.api.Assertions.assertTrue(
                feed.getLastError().contains("HTTP 500"),
                "unexpected error text: " + feed.getLastError());
    }

    @Test
    void aFeedThatRecoversClearsItsLastError() {
        Long feedId = createFeed("flaky", brokenUrl.toString());
        given().when().post("/refresh").then().statusCode(200).body("failed", equalTo(1));

        QuarkusTransaction.requiringNew().run(
                () -> feeds.findById(feedId).setUrl(stubUrl.toString()));

        given().when().post("/refresh").then().statusCode(200).body("succeeded", equalTo(1));

        Feed feed = QuarkusTransaction.requiringNew().call(() -> feeds.findById(feedId));
        org.junit.jupiter.api.Assertions.assertNull(feed.getLastError());
        org.junit.jupiter.api.Assertions.assertNotNull(feed.getLastFetchedAt());
    }

    @Test
    void unknownJsonFieldsDoNotBreakParsing() {
        createFeed("stub", stubUrl.toString());

        StubFeedResource.setPayload("""
                {"hits": [
                  {"objectID": "30", "title": "has extras", "url": "https://example.com/30",
                   "created_at_i": 1700000300, "brand_new_field": [1,2,3],
                   "another": {"deeply": {"nested": "thing"}}}
                ], "totally_unexpected": "value"}
                """);

        given().when().post("/refresh")
                .then().statusCode(200)
                .body("created", equalTo(1))
                .body("failed", equalTo(0));
    }

    @Test
    void disabledFeedsAreNotFetched() {
        Long feedId = createFeed("stub", stubUrl.toString());
        QuarkusTransaction.requiringNew().run(() -> feeds.findById(feedId).setEnabled(false));

        given().when().post("/refresh")
                .then().statusCode(200)
                .body("feedsProcessed", equalTo(0));

        assertEquals(0, countItems());
    }

    @Test
    void itemsAreLinkedToTheirFeed() {
        Long feedId = createFeed("stub", stubUrl.toString());
        given().when().post("/refresh").then().statusCode(200);

        List<Item> stored = QuarkusTransaction.requiringNew().call(() -> {
            List<Item> found = items.listAll();
            found.forEach(item -> item.getFeed().getName());
            return found;
        });

        assertEquals(3, stored.size());
        stored.forEach(item -> assertEquals(feedId, item.getFeed().getId()));
    }
}
