# Feed Aggregator

A small Quarkus service that polls configured JSON feeds on a timer, stores their items
without duplicating them, and exposes a paginated search over what it has collected.

Built as a Java/Quarkus learning project by someone who writes Go and JavaScript. The
running commentary on each concept is in [NOTES.md](NOTES.md).

**Stack:** Java 21 · Quarkus 3.38 · Maven · PostgreSQL 18 via Dev Services · Hibernate ORM
with Panache · Jackson · Quarkus Scheduler

---

## Running it

**Docker must be running.** No database is configured anywhere in this repo — Quarkus Dev
Services starts a PostgreSQL container automatically in dev and test mode. That is
deliberate, and [DevServicesTest](src/test/java/dev/learn/DevServicesTest.java) asserts it.

```bash
./mvnw quarkus:dev
```

The app listens on `http://localhost:8080`. If something else holds that port:

```bash
./mvnw quarkus:dev -Dquarkus.http.port=8099
```

Tests need no free port — `quarkus.http.test-port=0` asks the OS for one.

```bash
./mvnw test
```

---

## API

### `POST /feeds` — add a source

```bash
curl -X POST localhost:8080/feeds -H 'Content-Type: application/json' \
  -d '{"name":"hn","url":"https://hn.algolia.com/api/v1/search?tags=front_page"}'
```

```json
{ "id": 1, "name": "hn", "url": "https://hn.algolia.com/api/v1/search?tags=front_page",
  "enabled": true, "lastFetchedAt": null, "lastAttemptedAt": null, "lastError": null }
```

The name must be non-blank and the URL must be an absolute `http`/`https` URL with a host.
Anything else is a `400`:

```bash
curl -X POST localhost:8080/feeds -H 'Content-Type: application/json' \
  -d '{"name":"bad","url":"ftp://example.com"}'
# 400 {"error":"url scheme must be http or https, got: ftp"}
```

### `GET /feeds` — list sources

```bash
curl localhost:8080/feeds
```

`lastError` and `lastAttemptedAt` are how a silently failing feed stays visible: a feed that
has been erroring for a week otherwise looks identical to one nobody ever polled.

### `DELETE /feeds/{id}` — remove a source

```bash
curl -X DELETE localhost:8080/feeds/1     # 204, or 404 if unknown
```

Its items are cascaded away with it.

### `POST /refresh` — fetch now

```bash
curl -X POST localhost:8080/refresh
```

```json
{ "feedsProcessed": 2, "succeeded": 1, "failed": 1, "created": 20, "updated": 0,
  "results": [
    { "feedId": 1, "name": "hn",     "ok": true,  "created": 20, "updated": 0, "skipped": 0, "error": null },
    { "feedId": 2, "name": "broken", "ok": false, "created": 0,  "updated": 0, "skipped": 0,
      "error": "https://example.com/nope returned HTTP 404" }
  ] }
```

One feed failing never stops the others. Run it twice and the second call reports
`created: 0, updated: N` — items are upserted on `(feed, externalId)`, so refreshing does
not duplicate rows.

### `GET /items` — search

```bash
curl 'localhost:8080/items?q=rust&feedId=1&limit=20&offset=0'
```

```json
{ "count": 3, "limit": 20, "offset": 0,
  "items": [
    { "id": 12, "feedId": 1, "externalId": "49323686",
      "title": "Rust ownership explained", "url": "https://example.com/1",
      "publishedAt": "2026-08-16T21:07:10Z" }
  ] }
```

- `q` matches the title, case-insensitively. `%` and `_` are literals, not wildcards.
- `count` is the total number of matches, not the size of this page.
- `limit` defaults to 20 and is clamped to 100. `limit < 1` and `offset < 0` are `400`.
- Results are sorted by `publishedAt DESC, id DESC`. The tiebreaker is load-bearing — see
  below.

---

## Scheduled polling

Every enabled feed is fetched on a timer.

```properties
feeds.poll.interval=5m
feeds.poll.enabled=true
```

```bash
./mvnw quarkus:dev -Dfeeds.poll.interval=10s
```

Under the test profile `feeds.poll.enabled=false`, so background fetches never race the
assertions. The job stays *registered* and only its execution is skipped, so the wiring is
still under test.

---

## Five things this project taught me

1. **An entity is the schema.** `@Entity` annotations on a Java class are what Hibernate
   turns into `create table`; Panache then generates `listAll`/`findById`/`persist` from the
   `PanacheRepository<T>` type parameter, so there is no DAO to write.
2. **`@Transactional` is implemented by a generated proxy, not by the method.** It opens a
   transaction on entry and commits on return — and calling such a method from inside the
   same class bypasses the proxy entirely, so the annotation silently does nothing.
3. **Jackson refuses shapes it was not told about.** `@JsonProperty("objectID")` fixes a name
   mismatch and `@JsonIgnoreProperties(ignoreUnknown = true)` stops an unexpected field
   throwing. Coming from `JSON.parse`, the strictness is the feature: you find out at the
   boundary rather than three layers in.
4. **Dev Services starts a real PostgreSQL because no JDBC URL is configured.** The absence
   of config is the trigger. Tests therefore run against the database that ships, not an
   in-memory substitute — and in production a missing URL is a startup failure instead.
5. **Pagination needs a total order.** `publishedAt` alone is not one. Two items sharing a
   timestamp have no defined relative position, so `LIMIT`/`OFFSET` picks from an arbitrary
   ordering and an item can appear on two pages or none.

### The fifth one, demonstrated

[PaginationStabilityTest](src/test/java/dev/learn/PaginationStabilityTest.java) seeds six
items with identical timestamps and runs the same `order by published_at desc` under
different query plans:

```
seq scan      → [7, 8, 9, 10, 11, 12]
index scan    → [12, 11, 10, 9, 8, 7]
after updates → [10, 11, 12, 7, 8, 9]
```

Same query, same rows, three answers. Paging through with `limit=2` while rows move gives:

```
UNSTABLE paging -> [2, 1, 5, 6, 5, 6]   (4 distinct of 6)
```

Two items twice, two items never.

Note what the test does **not** assert. An unstable sort is not guaranteed to return rows in
a *different* order — only not guaranteed to return them in the *same* one. So "the unstable
query breaks" is unassertable, and a demo written that way passes about as often as it
fails. What is asserted is that more than one ordering exists, and that with `, id DESC`
exactly one does regardless of access path.

---

## What I would do differently

- **Migrations instead of `drop-and-create`.** The schema is rebuilt from the entities on
  every start, which is why there are no SQL files here. That cannot survive contact with
  real data; Flyway would be the first thing added.
- **`INSERT ... ON CONFLICT DO UPDATE` instead of check-then-insert.** The current upsert
  reads and then writes, which is a race between the scheduler and a manual refresh. The
  unique constraint on `(feed_id, external_id)` is what actually prevents duplicates, and a
  single-statement upsert would make the application code agree with that.
- **Keyset pagination instead of `OFFSET`.** A stable sort makes `OFFSET` *correct*, but
  `OFFSET 10000` still makes the database walk 10,000 rows to discard them. Paging on
  `(publishedAt, id) < (lastSeen…)` is both correct and cheap.
- **One HTTP call at a time is the wrong shape.** Feeds are fetched sequentially, so 50 feeds
  with a 15-second timeout is a worst case of 12 minutes. A bounded concurrent fetch would
  fix it.
- **`LIKE '%term%'` will not scale.** It cannot use a plain B-tree index. Postgres full-text
  search with a `tsvector` column and a GIN index is the real answer.
- **No conditional requests.** Every poll refetches everything. `ETag`/`If-Modified-Since`
  would make an unchanged feed nearly free.
- **No backoff.** A feed returning 500 is retried at full rate forever. It should back off
  and eventually disable itself.
- **Only one feed format is understood.** `HnHit` is shaped for HN Algolia specifically.
  Anything genuinely general needs a parser abstraction per source type.
