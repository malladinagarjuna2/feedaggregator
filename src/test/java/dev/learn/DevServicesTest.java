package dev.learn;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import javax.sql.DataSource;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves Dev Services actually gave us a real PostgreSQL, without anyone
 * configuring a datasource.
 *
 * <p>Nothing in application.properties sets a JDBC URL. If Dev Services were not
 * running, this test could not even start — the datasource would fail to build.
 */
@QuarkusTest
class DevServicesTest {

    @Inject
    DataSource dataSource;

    /**
     * Dev Services writes the URL of the container it started into this config
     * property at runtime. We never wrote it down anywhere.
     */
    @ConfigProperty(name = "quarkus.datasource.jdbc.url")
    String jdbcUrl;

    @Test
    void devServicesStartedARealPostgres() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();

            // Not H2, not an in-memory stand-in. The genuine article.
            assertEquals("PostgreSQL", meta.getDatabaseProductName());
            assertTrue(meta.getDatabaseMajorVersion() >= 16,
                    "expected a modern Postgres, got " + meta.getDatabaseProductVersion());
        }
    }

    @Test
    void jdbcUrlWasInjectedByDevServicesNotByUs() {
        // A container published on an ephemeral localhost port — the signature of
        // Testcontainers. A hand-configured URL would name a fixed host and port.
        assertTrue(jdbcUrl.startsWith("jdbc:postgresql://localhost:"),
                "unexpected JDBC URL: " + jdbcUrl);
    }

    @Test
    void weCanActuallyQueryIt() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("select 1 + 1")) {

            assertTrue(rs.next(), "query returned no rows");
            assertEquals(2, rs.getInt(1));
        }
    }
}
