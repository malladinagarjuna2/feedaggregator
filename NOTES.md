# Learning notes

Running notes on the Java/Quarkus concepts this project exists to teach.
Written for someone fluent in Go and JavaScript.

---

## Phase 0 — Scaffold and Dev Services

### What Maven is

`pom.xml` is the build file. It is Maven's equivalent of `go.mod` + `package.json`,
with one extra job: it also configures the build plugins, so it is closer to
`go.mod` and a `Makefile` fused together.

- `<dependencies>` — like `require` in `go.mod` or `dependencies` in `package.json`.
- `<dependencyManagement>` importing `quarkus-bom` — a "bill of materials". It pins
  compatible versions for the whole Quarkus ecosystem so you never write a version
  number on an individual Quarkus dependency. Nothing in Go or npm does this
  cleanly; the nearest idea is a lockfile that someone else maintains for you.
- `./mvnw` — the Maven wrapper. Same idea as a vendored toolchain: it downloads the
  exact Maven version this project expects, so `./mvnw` works even on a machine with
  no Maven installed. Prefer it over `mvn`.

### What Quarkus Dev Services is

`application.properties` sets no `quarkus.datasource.jdbc.url`. That omission is the
whole point.

In dev and test mode Quarkus notices there is a JDBC driver on the classpath
(`quarkus-jdbc-postgresql`) but no URL telling it where the database lives. Rather
than failing, it starts a real PostgreSQL container using Testcontainers and wires
the datasource to it. When the app stops, the container stops.

Why this matters: the tests later in this project run against genuine PostgreSQL, not
an in-memory substitute like H2. Behaviour that depends on the database — collation,
constraint enforcement, how the planner orders rows — is the same in tests as in
production. In Go you would typically hand-roll this with a `docker-compose.yml` and a
`TestMain` that waits for a port.

In production Dev Services is inactive. The URL must be supplied or the app refuses
to start, which is the correct failure.

### Schema generation

`quarkus.hibernate-orm.database.generation=drop-and-create` rebuilds every table from
the Java entity classes on each start. It means Phase 1 needs no migration files: you
write a class, restart, and the table exists.

This is a learning-mode setting. Real services use versioned migrations (Flyway or
Liquibase) because you cannot drop production tables to add a column.

### Reading the generated SQL

`log.sql=true` prints the SQL Hibernate generates. Watch it. The single biggest risk
with an ORM coming from hand-written Go SQL is losing sight of what actually runs
against the database — the log is how you keep that visible.
