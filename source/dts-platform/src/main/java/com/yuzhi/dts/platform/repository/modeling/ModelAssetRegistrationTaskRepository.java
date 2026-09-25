package com.yuzhi.dts.platform.repository.modeling;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * F15 K3: durable handoff from a confirmed build to catalog asset registration. One row per release
 * candidate; a rebuild of the same candidate re-queues it. Timestamps are timestamptz (UTC).
 */
@Repository
public class ModelAssetRegistrationTaskRepository {

    public enum State {
        PENDING,
        SUCCEEDED,
        FAILED,
    }

    public record TaskView(
        UUID id,
        String tenantId,
        UUID candidateId,
        int builtCandidateVersion,
        UUID planId,
        String environment,
        State state,
        int attempts,
        String lastErrorCode,
        String lastErrorMessage,
        Instant nextAttemptAt,
        Instant succeededAt,
        Instant lastModifiedDate
    ) {}

    private static final String SELECTION = """
        select id, tenant_id, candidate_id, built_candidate_version, plan_id, environment, state, attempts,
               last_error_code, last_error_message, next_attempt_at, succeeded_at, last_modified_date
          from modeling_model_asset_registration_task
        """;

    private final JdbcTemplate jdbcTemplate;

    public ModelAssetRegistrationTaskRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Queues (or re-queues after a rebuild) registration for a candidate that has just become BUILT. */
    public void enqueue(String tenantId, UUID candidateId, int builtCandidateVersion, UUID planId, String environment, Instant now) {
        Timestamp at = Timestamp.from(now);
        jdbcTemplate.update(
            """
            insert into modeling_model_asset_registration_task (
                id, tenant_id, candidate_id, built_candidate_version, plan_id, environment, state, attempts,
                next_attempt_at, created_date, last_modified_date
            ) values (?, ?, ?, ?, ?, ?, 'PENDING', 0, ?, ?, ?)
            on conflict (tenant_id, candidate_id) do update
               set built_candidate_version = excluded.built_candidate_version,
                   plan_id = excluded.plan_id,
                   environment = excluded.environment,
                   state = 'PENDING',
                   attempts = 0,
                   last_error_code = null,
                   last_error_message = null,
                   next_attempt_at = excluded.next_attempt_at,
                   succeeded_at = null,
                   last_modified_date = excluded.last_modified_date
            """,
            UUID.randomUUID(), tenantId, candidateId, builtCandidateVersion, planId, environment, at, at, at
        );
    }

    public Optional<TaskView> findByCandidate(String tenantId, UUID candidateId) {
        return jdbcTemplate
            .query(SELECTION + " where tenant_id = ? and candidate_id = ?", ModelAssetRegistrationTaskRepository::map, tenantId, candidateId)
            .stream()
            .findFirst();
    }

    /**
     * The candidate version at which a person started the build that became BUILT. BUILT itself is recorded by
     * the Airflow service callback, so registration runs under the build initiator's modeling authorization.
     */
    public Optional<Integer> findBuildInitiatorVersion(String tenantId, UUID candidateId, int builtCandidateVersion) {
        return Optional.ofNullable(
            jdbcTemplate.queryForObject(
                """
                select max(candidate_version)
                  from modeling_model_release_candidate_command
                 where tenant_id = ? and candidate_id = ? and to_status = 'BUILDING' and candidate_version < ?
                """,
                Integer.class,
                tenantId, candidateId, builtCandidateVersion
            )
        );
    }

    public List<TaskView> findDue(Instant now, int maxAttempts, int limit) {
        return jdbcTemplate.query(
            SELECTION + " where state <> 'SUCCEEDED' and attempts < ? and next_attempt_at <= ? order by next_attempt_at, id limit ?",
            ModelAssetRegistrationTaskRepository::map,
            maxAttempts, Timestamp.from(now), limit
        );
    }

    /**
     * Claims one attempt by pushing its next attempt out by the lease. Returns false if another worker or a
     * manual registration got there first, so an attempt runs at most once per lease.
     */
    public boolean claim(UUID id, Instant now, Instant leaseUntil) {
        return jdbcTemplate.update(
            """
            update modeling_model_asset_registration_task
               set attempts = attempts + 1, next_attempt_at = ?, last_modified_date = ?
             where id = ? and state <> 'SUCCEEDED' and next_attempt_at <= ?
            """,
            Timestamp.from(leaseUntil), Timestamp.from(now), id, Timestamp.from(now)
        ) == 1;
    }

    public void markSucceeded(String tenantId, UUID candidateId, Instant now) {
        jdbcTemplate.update(
            """
            update modeling_model_asset_registration_task
               set state = 'SUCCEEDED', succeeded_at = ?, last_error_code = null, last_error_message = null, last_modified_date = ?
             where tenant_id = ? and candidate_id = ?
            """,
            Timestamp.from(now), Timestamp.from(now), tenantId, candidateId
        );
    }

    public void markFailed(UUID id, String errorCode, String errorMessage, Instant nextAttemptAt, Instant now) {
        jdbcTemplate.update(
            """
            update modeling_model_asset_registration_task
               set state = 'FAILED', last_error_code = ?, last_error_message = ?, next_attempt_at = ?, last_modified_date = ?
             where id = ? and state <> 'SUCCEEDED'
            """,
            errorCode, truncate(errorMessage), Timestamp.from(nextAttemptAt), Timestamp.from(now), id
        );
    }

    private static String truncate(String value) {
        if (value == null) return null;
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private static TaskView map(ResultSet row, int rowNumber) throws SQLException {
        return new TaskView(
            row.getObject("id", UUID.class),
            row.getString("tenant_id"),
            row.getObject("candidate_id", UUID.class),
            row.getInt("built_candidate_version"),
            row.getObject("plan_id", UUID.class),
            row.getString("environment"),
            State.valueOf(row.getString("state")),
            row.getInt("attempts"),
            row.getString("last_error_code"),
            row.getString("last_error_message"),
            instant(row.getTimestamp("next_attempt_at")),
            instant(row.getTimestamp("succeeded_at")),
            instant(row.getTimestamp("last_modified_date"))
        );
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
