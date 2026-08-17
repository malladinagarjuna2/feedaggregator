package dev.learn;

import dev.learn.domain.Feed;
import dev.learn.domain.Item;
import dev.learn.repository.FeedRepository;
import dev.learn.repository.ItemRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class FeedResourceTest {

    @Inject
    FeedRepository feeds;

    @Inject
    ItemRepository items;

    // Not @TestTransaction: these tests drive the app over HTTP, and the request runs
    // in its own transaction on another thread, so a test-scoped rollback would not
    // undo it. Explicit cleanup is what actually isolates them.
    @BeforeEach
    void clean() {
        QuarkusTransaction.requiringNew().run(() -> {
            items.deleteAll();
            feeds.deleteAll();
        });
    }

    @Test
    void createsListsAndDeletesAFeed() {
        int id = given()
                .contentType("application/json")
                .body("""
                        {"name": "hn", "url": "https://hn.algolia.com/api/v1/search?tags=front_page"}
                        """)
                .when().post("/feeds")
                .then()
                .statusCode(201)
                .body("name", equalTo("hn"))
                .body("enabled", equalTo(true))
                .body("lastFetchedAt", equalTo(null))
                .extract().path("id");

        given().when().get("/feeds")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("name", contains("hn"));

        given().when().delete("/feeds/" + id)
                .then().statusCode(204);

        given().when().get("/feeds")
                .then().statusCode(200).body("$", hasSize(0));
    }

    @Test
    void trimsWhitespaceFromName() {
        given().contentType("application/json")
                .body("""
                        {"name": "  hn  ", "url": "https://example.com/feed"}
                        """)
                .when().post("/feeds")
                .then().statusCode(201)
                .body("name", equalTo("hn"));
    }

    @Test
    void rejectsBlankName() {
        given().contentType("application/json")
                .body("""
                        {"name": "   ", "url": "https://example.com/feed"}
                        """)
                .when().post("/feeds")
                .then().statusCode(400)
                .body("error", equalTo("name must not be blank"));
    }

    @Test
    void rejectsMissingName() {
        given().contentType("application/json")
                .body("""
                        {"url": "https://example.com/feed"}
                        """)
                .when().post("/feeds")
                .then().statusCode(400)
                .body("error", equalTo("name must not be blank"));
    }

    @Test
    void rejectsNonHttpScheme() {
        given().contentType("application/json")
                .body("""
                        {"name": "ftp feed", "url": "ftp://example.com/feed"}
                        """)
                .when().post("/feeds")
                .then().statusCode(400)
                .body("error", equalTo("url scheme must be http or https, got: ftp"));
    }

    @Test
    void rejectsRelativeUrl() {
        given().contentType("application/json")
                .body("""
                        {"name": "relative", "url": "not-a-url"}
                        """)
                .when().post("/feeds")
                .then().statusCode(400)
                .body("error", equalTo(
                        "url must be absolute and include a scheme, e.g. https://example.com/feed"));
    }

    @Test
    void rejectsUrlWithoutHost() {
        given().contentType("application/json")
                .body("""
                        {"name": "hostless", "url": "https:///feed"}
                        """)
                .when().post("/feeds")
                .then().statusCode(400)
                .body("error", equalTo("url must include a host"));
    }

    @Test
    void deletingAnUnknownFeedReturns404() {
        given().when().delete("/feeds/999999")
                .then().statusCode(404)
                .body("error", equalTo("no feed with id 999999"));
    }

    @Test
    void deletingAFeedAlsoDeletesItsItems() {
        Long feedId = QuarkusTransaction.requiringNew().call(() -> {
            Feed feed = new Feed("hn", "https://example.com/feed");
            feeds.persist(feed);
            items.persist(new Item(feed, "a1", "first", "https://example.com/1", Instant.now()));
            items.persist(new Item(feed, "a2", "second", "https://example.com/2", Instant.now()));
            return feed.getId();
        });

        assertEquals(2, countItems());

        given().when().delete("/feeds/" + feedId)
                .then().statusCode(204);

        assertEquals(0, countItems(), "items should have been cascaded away with the feed");
    }

    private long countItems() {
        return QuarkusTransaction.requiringNew().call(() -> items.count());
    }
}
