package com.narturebelle.payments;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.io.IOException;

/**
 * One private Postgres per test run, so tests never need real DB env vars. It holds a stand-in for Care's tables,
 * or Care's real ones when CARE_MIGRATIONS_DIR points at a copy of its db/migration folder.
 */
public class TestDatabase implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static EmbeddedPostgres postgres;

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        TestPropertyValues.of(
                "spring.datasource.url=" + start().getJdbcUrl("postgres", "postgres"),
                "spring.datasource.username=postgres",
                "spring.datasource.password=postgres"
        ).applyTo(context);
    }

    private static synchronized EmbeddedPostgres start() {
        if (postgres == null) {
            try {
                postgres = EmbeddedPostgres.builder()
                        .setServerConfig("shared_buffers", "16MB")
                        .setServerConfig("max_connections", "20")
                        .start();
            } catch (IOException e) {
                throw new IllegalStateException("Could not start the test Postgres", e);
            }
            String careMigrations = System.getenv("CARE_MIGRATIONS_DIR");
            if (careMigrations == null) {
                new ResourceDatabasePopulator(new ClassPathResource("db/care-tables.sql")).execute(postgres.getPostgresDatabase());
            } else {
                Flyway.configure().dataSource(postgres.getPostgresDatabase())
                        .locations("filesystem:" + careMigrations).load().migrate();
                // Care's orders and refunds link to users, so every user ID the tests use must exist.
                new JdbcTemplate(postgres.getPostgresDatabase()).execute("insert into public.users (id) values (1), (2), (3), (5), (7)");
            }
        }
        return postgres;
    }

    /** Foreign keys are real here, so delete everything order-related together. Seeded plans stay. */
    public static void clearOrders(JdbcTemplate jdbc) {
        jdbc.execute("truncate table public.orders restart identity cascade");
        jdbc.execute("truncate table money.outbox_events, money.audit_events, money.plan_validity restart identity");
    }
}
