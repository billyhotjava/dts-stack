package com.yuzhi.dts.platform.repository.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ModelPublishedReleaseReadRepositoryPostgresTest {

    private static final String TENANT = "tenant-a";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4")
        .withDatabaseName("model_published_release_test")
        .withUsername("published_release")
        .withPassword("published_release");

    private JdbcTemplate jdbc;
    private ModelPublishedReleaseReadRepository releases;

    @BeforeEach
    void resetDatabase() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        jdbc.execute("drop table if exists modeling_model_lifecycle_event");
        // Only the lifecycle ledger columns the read depends on; created_date is timestamp without time zone as in production.
        jdbc.execute(
            "create table modeling_model_lifecycle_event (id uuid primary key, tenant_id varchar(64), model_spec_id uuid, " +
            "model_revision int, event_type varchar(32), status varchar(32), details_json jsonb, created_date timestamp)"
        );
        releases = new ModelPublishedReleaseReadRepository(jdbc);
    }

    @Test
    void returnsTheLatestPublishedReleasePerModelWithinTheTenant() {
        UUID model = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID unpublished = UUID.randomUUID();
        insert(TENANT, model, 2, "RELEASE", "PUBLISHED", "test", "2026-09-20T01:00:00Z");
        UUID latest = insert(TENANT, model, 4, "RELEASE", "PUBLISHED", "prod", "2026-09-22T01:00:00Z");
        insert(TENANT, model, 5, "RELEASE", "REVIEW_PENDING", "prod", "2026-09-23T01:00:00Z");
        insert(TENANT, model, 6, "MATERIALIZATION", "PUBLISHED", "prod", "2026-09-24T01:00:00Z");
        insert("tenant-b", model, 9, "RELEASE", "PUBLISHED", "prod", "2026-09-25T01:00:00Z");
        insert(TENANT, other, 1, "RELEASE", "PUBLISHED", "dev", "2026-09-21T01:00:00Z");

        var result = releases.latestPublished(TENANT, List.of(model, other, unpublished));

        assertThat(result).containsOnlyKeys(model, other);
        assertThat(result.get(model).releaseId()).isEqualTo(latest);
        assertThat(result.get(model).modelRevision()).isEqualTo(4);
        assertThat(result.get(model).environment()).isEqualTo("prod");
        assertThat(result.get(model).publishedAt()).isEqualTo(Timestamp.from(Instant.parse("2026-09-22T01:00:00Z")).toInstant());
        assertThat(result.get(other).environment()).isEqualTo("dev");
    }

    @Test
    void emptyRequestsDoNotQueryAndOversizedRequestsAreRejected() {
        assertThat(releases.latestPublished(TENANT, List.of())).isEmpty();
        var tooMany = Stream.generate(UUID::randomUUID).limit(ModelPublishedReleaseReadRepository.MAX_MODELS + 1).toList();

        assertThatThrownBy(() -> releases.latestPublished(TENANT, tooMany)).isInstanceOf(IllegalArgumentException.class);
    }

    private UUID insert(String tenant, UUID model, int revision, String type, String status, String environment, String at) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "insert into modeling_model_lifecycle_event values (?, ?, ?, ?, ?, ?, cast(? as jsonb), ?)",
            id, tenant, model, revision, type, status, "{\"environment\":\"" + environment + "\"}", Timestamp.from(Instant.parse(at))
        );
        return id;
    }
}
