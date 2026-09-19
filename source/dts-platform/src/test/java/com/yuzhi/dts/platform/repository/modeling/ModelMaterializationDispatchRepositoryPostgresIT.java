package com.yuzhi.dts.platform.repository.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.IntegrationTest;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** Runs the dispatch fencing SQL against real PostgreSQL in an isolated, owned schema. */
@IntegrationTest
class ModelMaterializationDispatchRepositoryPostgresIT {

    private static final Pattern OWNED_SCHEMA = Pattern.compile("^s86_dispatch_fence_[0-9a-f]{32}$");
    private static final Instant NOW = Instant.parse("2026-09-19T08:00:00Z");
    private static final UUID CANDIDATE = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final String CODE = "MODEL_MATERIALIZATION_BUILD_ABANDONED";

    @Autowired
    private DataSource dataSource;

    private String schema;
    private Connection connection;
    private JdbcTemplate jdbc;
    private ModelMaterializationDispatchRepository dispatches;

    @BeforeEach
    void createIsolatedSchema() throws Exception {
        schema = "s86_dispatch_fence_" + UUID.randomUUID().toString().replace("-", "");
        requireOwnedSchema(schema);
        connection = dataSource.getConnection();
        connection.setAutoCommit(true);
        try (Statement statement = connection.createStatement()) {
            statement.execute("create schema " + schema);
            statement.execute("set search_path to " + schema);
            statement.execute(
                """
                create table modeling_materialization_dispatch (
                    id uuid primary key,
                    tenant_id varchar(128) not null,
                    candidate_id uuid not null,
                    candidate_version int not null,
                    attempt int not null,
                    execution_target_key varchar(256) not null,
                    airflow_dag_id varchar(256) not null,
                    airflow_run_id varchar(256) not null,
                    artifact_bundle_checksum varchar(64) not null,
                    scoped_bundle_checksum varchar(64),
                    runtime_token_digest varchar(71),
                    runtime_token_expires_at timestamp,
                    status varchar(24) not null,
                    dispatch_attempts int not null default 0,
                    claimed_at timestamp,
                    next_attempt_at timestamp,
                    recovered boolean not null default false,
                    last_error_code varchar(128),
                    created_at timestamp not null,
                    last_modified_at timestamp not null
                )
                """
            );
            statement.execute(
                """
                create table modeling_model_release_candidate (
                    id uuid primary key,
                    tenant_id varchar(128) not null,
                    version int not null,
                    status varchar(32) not null
                )
                """
            );
            statement.execute(
                """
                create table modeling_pipeline_run (
                    id uuid primary key,
                    pipeline_run_group_id uuid not null,
                    run_purpose varchar(32) not null,
                    status varchar(32) not null,
                    message varchar(512),
                    finished_date timestamp,
                    last_modified_date timestamp
                )
                """
            );
        }
        jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
        dispatches = new ModelMaterializationDispatchRepository(jdbc);
    }

    @AfterEach
    void dropIsolatedSchema() throws Exception {
        requireOwnedSchema(schema);
        try (Statement statement = connection.createStatement()) {
            statement.execute("set search_path to public");
            statement.execute("drop schema " + schema + " cascade");
        } finally {
            connection.close();
        }
    }

    @Test
    void claimCarriesAttemptCountAndActivityTimeForBackoffDecisions() {
        UUID id = insertDispatch(1, 3, "UNKNOWN", 2, null, NOW.minusSeconds(60));

        var claimed = dispatches.claimNext(NOW, Duration.ofMinutes(2)).orElseThrow();

        assertThat(claimed.id()).isEqualTo(id);
        assertThat(claimed.status()).isEqualTo("CLAIMED");
        assertThat(claimed.dispatchAttempts()).isEqualTo(3);
        assertThat(claimed.lastModifiedAt()).isEqualTo(NOW);
    }

    @Test
    void latestDispatchIsTheHighestAttemptOfTheCandidateRegardlessOfVersion() {
        insertDispatch(1, 2, "BLOCKED", 1, null, NOW.minusSeconds(600));
        insertDispatch(2, 4, "FAILED", 1, null, NOW.minusSeconds(300));
        UUID latest = insertDispatch(3, 6, "SUBMITTED", 1, null, NOW.minusSeconds(30));

        assertThat(dispatches.findLatestForCandidate("tenant-a", CANDIDATE))
            .get()
            .satisfies(record -> {
                assertThat(record.id()).isEqualTo(latest);
                assertThat(record.attempt()).isEqualTo(3);
                assertThat(record.lastModifiedAt()).isEqualTo(NOW.minusSeconds(30));
            });
        assertThat(dispatches.lockLatestForCandidate("tenant-a", CANDIDATE)).get().extracting(r -> r.id()).isEqualTo(latest);
        assertThat(dispatches.findLatestForCandidate("tenant-b", CANDIDATE)).isEmpty();
        assertThat(dispatches.findLatestForCandidate("tenant-a", UUID.randomUUID())).isEmpty();
    }

    @Test
    void candidateTransitionGuardMatchesOnlyTheSameBuildingVersion() {
        jdbc.update(
            "insert into modeling_model_release_candidate (id, tenant_id, version, status) values (?, 'tenant-a', 4, 'BUILDING')",
            CANDIDATE
        );

        assertThat(dispatches.isCandidateBuilding("tenant-a", CANDIDATE, 4)).isTrue();
        assertThat(dispatches.isCandidateBuilding("tenant-a", CANDIDATE, 3)).isFalse();
        assertThat(dispatches.isCandidateBuilding("tenant-b", CANDIDATE, 4)).isFalse();
        jdbc.update("update modeling_model_release_candidate set status = 'STALE' where id = ?", CANDIDATE);
        assertThat(dispatches.isCandidateBuilding("tenant-a", CANDIDATE, 4)).isFalse();
    }

    @Test
    void abandonFencesActiveDispatchAndOnlyUnsettledPipelineRows() {
        UUID id = insertDispatch(1, 2, "SUBMITTED", 1, null, NOW.minusSeconds(3600));
        UUID queued = insertRun(id, "QUEUED");
        UUID unknown = insertRun(id, "UNKNOWN");
        UUID built = insertRun(id, "BUILT");

        boolean fenced = dispatches.abandonActive(id, CODE, NOW, NOW.minus(Duration.ofMinutes(2)));

        assertThat(fenced).isTrue();
        assertThat(jdbc.queryForMap("select status, last_error_code, next_attempt_at from modeling_materialization_dispatch where id = ?", id))
            .containsEntry("status", "BLOCKED")
            .containsEntry("last_error_code", CODE)
            .containsEntry("next_attempt_at", null);
        assertThat(runStatus(queued)).isEqualTo("BLOCKED");
        assertThat(runStatus(unknown)).isEqualTo("BLOCKED");
        assertThat(runStatus(built)).isEqualTo("BUILT");
        assertThat(jdbc.queryForObject("select message from modeling_pipeline_run where id = ?", String.class, queued))
            .isEqualTo(CODE);
    }

    @Test
    void abandonLeavesLiveClaimsAndTerminalDispatchesUntouched() {
        UUID liveClaim = insertDispatch(1, 2, "CLAIMED", 1, NOW.minusSeconds(30), NOW.minusSeconds(30));
        UUID staleClaim = insertDispatch(2, 4, "CLAIMED", 1, NOW.minusSeconds(600), NOW.minusSeconds(600));
        UUID completed = insertDispatch(3, 6, "COMPLETED", 1, null, NOW.minusSeconds(600));
        UUID claimAtTtl = insertDispatch(5, 10, "CLAIMED", 1, NOW.minus(Duration.ofMinutes(2)), NOW.minus(Duration.ofMinutes(2)));
        UUID unknown = insertDispatch(4, 8, "UNKNOWN", 5, null, NOW);
        Instant claimStaleBefore = NOW.minus(Duration.ofMinutes(2));

        assertThat(dispatches.abandonActive(liveClaim, CODE, NOW, claimStaleBefore)).isFalse();
        assertThat(dispatches.abandonActive(staleClaim, CODE, NOW, claimStaleBefore)).isTrue();
        assertThat(dispatches.abandonActive(completed, CODE, NOW, claimStaleBefore)).isFalse();
        assertThat(dispatches.abandonActive(unknown, CODE, NOW, claimStaleBefore)).isTrue();
        assertThat(dispatches.abandonActive(unknown, CODE, NOW, claimStaleBefore))
            .as("a fenced dispatch is terminal")
            .isFalse();

        assertThat(dispatches.abandonActive(claimAtTtl, CODE, NOW, claimStaleBefore))
            .as("a claim exactly at the TTL is still owned by its dispatcher")
            .isFalse();

        assertThat(dispatchStatus(liveClaim)).isEqualTo("CLAIMED");
        assertThat(dispatchStatus(completed)).isEqualTo("COMPLETED");
    }

    private UUID insertDispatch(
        int attempt,
        int candidateVersion,
        String status,
        int dispatchAttempts,
        Instant claimedAt,
        Instant lastModifiedAt
    ) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            """
            insert into modeling_materialization_dispatch (
                id, tenant_id, candidate_id, candidate_version, attempt,
                execution_target_key, airflow_dag_id, airflow_run_id,
                artifact_bundle_checksum, status, dispatch_attempts,
                claimed_at, next_attempt_at, created_at, last_modified_at
            ) values (?, 'tenant-a', ?, ?, ?, 'postgres-primary',
                      'dts_release_build_postgres_primary', ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            id,
            CANDIDATE,
            candidateVersion,
            attempt,
            "dts_rc_" + id.toString().replace("-", "") + "_a" + attempt,
            "a".repeat(64),
            status,
            dispatchAttempts,
            claimedAt == null ? null : Timestamp.from(claimedAt),
            "UNKNOWN".equals(status) ? Timestamp.from(NOW.minusSeconds(1)) : null,
            Timestamp.from(lastModifiedAt),
            Timestamp.from(lastModifiedAt)
        );
        return id;
    }

    private UUID insertRun(UUID groupId, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            """
            insert into modeling_pipeline_run (id, pipeline_run_group_id, run_purpose, status, last_modified_date)
            values (?, ?, 'RELEASE_BUILD', ?, ?)
            """,
            id,
            groupId,
            status,
            Timestamp.from(NOW.minusSeconds(60))
        );
        return id;
    }

    private String runStatus(UUID id) {
        return jdbc.queryForObject("select status from modeling_pipeline_run where id = ?", String.class, id);
    }

    private String dispatchStatus(UUID id) {
        return jdbc.queryForObject("select status from modeling_materialization_dispatch where id = ?", String.class, id);
    }

    private static void requireOwnedSchema(String schema) {
        if (schema == null || !OWNED_SCHEMA.matcher(schema).matches()) {
            throw new IllegalArgumentException("refusing non-owned schema: " + schema);
        }
    }
}
