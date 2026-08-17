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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class ItemSearchTest {

    private static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");

    @Inject
    FeedRepository feeds;

    @Inject
    ItemRepository items;

    private Long feedA;
    private Long feedB;

    @BeforeEach
    void seed() {
        Long[] ids = QuarkusTransaction.requiringNew().call(() -> {
            items.deleteAll();
            feeds.deleteAll();

            Feed a = new Feed("alpha", "https://example.com/a");
            Feed b = new Feed("beta", "https://example.com/b");
            feeds.persist(a);
            feeds.persist(b);

            persist(a, "1", "Rust ownership explained", BASE.plusSeconds(500));
            persist(a, "2", "Learning RUST the hard way", BASE.plusSeconds(400));
            persist(a, "3", "Go generics in practice", BASE.plusSeconds(300));
            persist(b, "4", "rust for embedded systems", BASE.plusSeconds(200));
            persist(b, "5", "Postgres index internals", BASE.plusSeconds(100));
            persist(b, "6", "100% coverage is a trap", BASE.plusSeconds(50));
            persist(b, "7", "snake_case vs camelCase", BASE.plusSeconds(40));

            return new Long[]{a.getId(), b.getId()};
        });
        feedA = ids[0];
        feedB = ids[1];
    }

    private void persist(Feed feed, String externalId, String title, Instant publishedAt) {
        items.persist(new Item(feed, externalId, title, "https://example.com/" + externalId, publishedAt));
    }

    @Test
    void listsEverythingNewestFirstByDefault() {
        given().when().get("/items")
                .then().statusCode(200)
                .body("count", equalTo(7))
                .body("limit", equalTo(20))
                .body("offset", equalTo(0))
                .body("items", hasSize(7))
                .body("items.title", contains(
                        "Rust ownership explained",
                        "Learning RUST the hard way",
                        "Go generics in practice",
                        "rust for embedded systems",
                        "Postgres index internals",
                        "100% coverage is a trap",
                        "snake_case vs camelCase"));
    }

    @Test
    void searchIsCaseInsensitiveOnTitle() {
        given().queryParam("q", "rust")
                .when().get("/items")
                .then().statusCode(200)
                .body("count", equalTo(3))
                .body("items.title", contains(
                        "Rust ownership explained",
                        "Learning RUST the hard way",
                        "rust for embedded systems"));
    }

    @Test
    void searchMatchesNothingWhenNoTitleContainsTheTerm() {
        given().queryParam("q", "kubernetes")
                .when().get("/items")
                .then().statusCode(200)
                .body("count", equalTo(0))
                .body("items", hasSize(0));
    }

    @Test
    void searchCanBeRestrictedToOneFeed() {
        given().queryParam("q", "rust").queryParam("feedId", feedA)
                .when().get("/items")
                .then().statusCode(200)
                .body("count", equalTo(2))
                .body("items.title", contains(
                        "Rust ownership explained",
                        "Learning RUST the hard way"));
    }

    @Test
    void feedIdAloneFiltersWithoutASearchTerm() {
        given().queryParam("feedId", feedB)
                .when().get("/items")
                .then().statusCode(200)
                .body("count", equalTo(4));
    }

    // Without escaping, % is a LIKE wildcard and this would match every row.
    @Test
    void percentInTheQueryIsALiteralNotAWildcard() {
        given().queryParam("q", "100%")
                .when().get("/items")
                .then().statusCode(200)
                .body("count", equalTo(1))
                .body("items.title", contains("100% coverage is a trap"));
    }

    // Without escaping, _ matches any single character, so "snake_case" would also
    // match "snakeXcase" and friends.
    @Test
    void underscoreInTheQueryIsALiteralNotAWildcard() {
        given().queryParam("q", "snake_case")
                .when().get("/items")
                .then().statusCode(200)
                .body("count", equalTo(1))
                .body("items.title", contains("snake_case vs camelCase"));
    }

    @Test
    void aLoneWildcardDoesNotMatchEverything() {
        given().queryParam("q", "%")
                .when().get("/items")
                .then().statusCode(200)
                .body("count", equalTo(1));
    }

    @Test
    void countIsTheTotalMatchNotThePageSize() {
        given().queryParam("q", "rust").queryParam("limit", 1)
                .when().get("/items")
                .then().statusCode(200)
                .body("count", equalTo(3))
                .body("items", hasSize(1));
    }

    @Test
    void limitIsClampedToTheMaximum() {
        given().queryParam("limit", 5000)
                .when().get("/items")
                .then().statusCode(200)
                .body("limit", equalTo(100));
    }

    @Test
    void negativeOffsetIsRejected() {
        given().queryParam("offset", -1)
                .when().get("/items")
                .then().statusCode(400)
                .body("error", equalTo("offset must not be negative"));
    }

    @Test
    void zeroLimitIsRejected() {
        given().queryParam("limit", 0)
                .when().get("/items")
                .then().statusCode(400)
                .body("error", equalTo("limit must be at least 1"));
    }

    @Test
    void offsetPastTheEndReturnsAnEmptyPageNotAnError() {
        given().queryParam("offset", 500)
                .when().get("/items")
                .then().statusCode(200)
                .body("count", equalTo(7))
                .body("items", hasSize(0));
    }

    @Test
    void pagingThroughTheWholeSetReturnsEachItemExactlyOnce() {
        List<Integer> seen = new ArrayList<>();

        for (int offset = 0; offset < 7; offset += 2) {
            List<Integer> page = given()
                    .queryParam("limit", 2)
                    .queryParam("offset", offset)
                    .when().get("/items")
                    .then().statusCode(200)
                    .extract().path("items.id");
            seen.addAll(page);
        }

        assertEquals(7, seen.size(), "expected 4 pages totalling 7 items, got " + seen);
        assertEquals(7, new HashSet<>(seen).size(), "duplicate across pages: " + seen);
    }

    @Test
    void pagingIsStableEvenWhenEveryItemSharesATimestamp() {
        QuarkusTransaction.requiringNew().run(
                () -> items.update("publishedAt = ?1", BASE));

        List<Integer> seen = new ArrayList<>();
        for (int offset = 0; offset < 7; offset += 2) {
            List<Integer> page = given()
                    .queryParam("limit", 2)
                    .queryParam("offset", offset)
                    .when().get("/items")
                    .then().statusCode(200)
                    .extract().path("items.id");
            seen.addAll(page);
        }

        assertEquals(7, seen.size(), "got " + seen);
        assertEquals(7, new HashSet<>(seen).size(),
                "identical timestamps must still page cleanly thanks to the id tiebreaker: " + seen);
    }

    @Test
    void itemsCarryTheirFeedId() {
        given().queryParam("feedId", feedA).queryParam("limit", 1)
                .when().get("/items")
                .then().statusCode(200)
                .body("items[0].feedId", equalTo(feedA.intValue()))
                .body("items[0].externalId", equalTo("1"));
    }
}
