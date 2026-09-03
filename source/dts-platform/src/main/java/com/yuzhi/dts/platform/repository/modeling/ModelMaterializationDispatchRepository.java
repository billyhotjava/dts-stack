package com.yuzhi.dts.platform.repository.modeling;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL outbox adapter for one external materialization side effect per build group. */
@Repository
public class ModelMaterializationDispatchRepository {

    private static final String SELECTION = """
        select id, tenant_id, candidate_id, candidate_version, attempt,
               execution_target_key, airflow_dag_id, airflow_run_id,
               artifact_bundle_checksum, status, scoped_bundle_checksum,
               runtime_token_digest, runtime_token_expires_at
          from modeling_materialization_dispatch
        """;

    private final JdbcTemplate jdbcTemplate;

    public ModelMaterializationDispatchRepository(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Optional<DispatchRecord> claimNext(
        Instant now,
        Duration staleClaimTtl
    ) {
        if (now == null) throw new IllegalArgumentException("now is required");
        if (
            staleClaimTtl == null ||
            staleClaimTtl.isNegative() ||
            staleClaimTtl.isZero()
        ) {
            throw new IllegalArgumentException(
                "staleClaimTtl must be positive"
            );
        }
        Instant staleBefore = now.minus(staleClaimTtl);
        return jdbcTemplate
            .query(
                """
                with next_dispatch as (
                    select id
                      from modeling_materialization_dispatch
                     where (
                            status in ('PENDING', 'UNKNOWN')
                            and (next_attempt_at is null or next_attempt_at <= ?)
                         )
                        or (
                            status = 'CLAIMED'
                            and claimed_at < ?
                         )
                     order by
                        coalesce(next_attempt_at, created_at),
                        created_at,
                        id
                     for update skip locked
                     limit 1
                )
                update modeling_materialization_dispatch d
                   set status = 'CLAIMED',
                       claimed_at = ?,
                       dispatch_attempts = dispatch_attempts + 1,
                       last_modified_at = ?
                  from next_dispatch n
                 where d.id = n.id
                returning d.id, d.tenant_id, d.candidate_id,
                          d.candidate_version, d.attempt,
                          d.execution_target_key, d.airflow_dag_id,
                          d.airflow_run_id, d.artifact_bundle_checksum,
                          d.status, d.scoped_bundle_checksum,
                          d.runtime_token_digest,
                          d.runtime_token_expires_at
                """,
                (row, rowNumber) -> map(row),
                Timestamp.from(now),
                Timestamp.from(staleBefore),
                Timestamp.from(now),
                Timestamp.from(now)
            )
            .stream()
            .findFirst();
    }

    @Transactional(readOnly = true)
    public List<DispatchRecord> findSubmittedBefore(
        Instant cutoff,
        int limit
    ) {
        if (cutoff == null || limit < 1 || limit > 100) {
            throw new IllegalArgumentException(
                "cutoff and a limit between 1 and 100 are required"
            );
        }
        return jdbcTemplate.query(
            SELECTION +
            """
             where status = 'SUBMITTED'
               and last_modified_at <= ?
             order by last_modified_at, created_at, id
             limit ?
            """,
            (row, rowNumber) -> map(row),
            Timestamp.from(cutoff),
            limit
        );
    }

    @Transactional
    public void markPrepared(
        UUID dispatchId,
        String scopedBundleChecksum,
        String tokenDigest,
        Instant tokenExpiresAt,
        Instant now
    ) {
        int updated = jdbcTemplate.update(
            """
            update modeling_materialization_dispatch
               set scoped_bundle_checksum = ?,
                   runtime_token_digest = ?,
                   runtime_token_expires_at = ?,
                   last_error_code = null,
                   last_modified_at = ?
             where id = ? and status = 'CLAIMED'
               and (
                    scoped_bundle_checksum is null
                    or scoped_bundle_checksum = ?
               )
               and (
                    runtime_token_digest is null
                    or runtime_token_digest = ?
               )
            """,
            scopedBundleChecksum,
            tokenDigest,
            Timestamp.from(tokenExpiresAt),
            Timestamp.from(now),
            dispatchId,
            scopedBundleChecksum,
            tokenDigest
        );
        requireOne(updated, "Prepared dispatch could not be persisted");
        int runRows = jdbcTemplate.update(
            """
            update modeling_pipeline_run
               set scoped_bundle_checksum = ?, last_modified_date = ?
             where pipeline_run_group_id = ?
               and run_purpose = 'RELEASE_BUILD'
               and (
                    scoped_bundle_checksum is null
                    or scoped_bundle_checksum = ?
               )
            """,
            scopedBundleChecksum,
            Timestamp.from(now),
            dispatchId,
            scopedBundleChecksum
        );
        if (runRows < 1) {
            throw new IllegalStateException(
                "Prepared dispatch has no pipeline rows"
            );
        }
    }

    @Transactional
    public void markSubmitted(
        UUID dispatchId,
        boolean recovered,
        Instant now
    ) {
        int updated = jdbcTemplate.update(
            """
            update modeling_materialization_dispatch
               set status = 'SUBMITTED', recovered = ?,
                   claimed_at = null, next_attempt_at = null,
                   last_error_code = null, last_modified_at = ?
             where id = ? and status = 'CLAIMED'
               and scoped_bundle_checksum is not null
               and runtime_token_digest is not null
            """,
            recovered,
            Timestamp.from(now),
            dispatchId
        );
        if (
            updated == 0 &&
            hasDispatchStatus(
                dispatchId,
                "SUBMITTED",
                "COMPLETED",
                "FAILED"
            )
        ) {
            return;
        }
        requireOne(
            updated,
            "Submitted dispatch could not be persisted"
        );
        requirePipelineRows(
            jdbcTemplate.update(
                """
                update modeling_pipeline_run
                   set status = case
                           when status in ('QUEUED', 'UNKNOWN')
                               then 'SUBMITTED'
                           else status
                       end,
                       message = case
                           when status in ('QUEUED', 'UNKNOWN')
                               then 'Release build submitted to Airflow'
                           else message
                       end,
                       started_date = coalesce(started_date, ?),
                       last_modified_date = ?
                 where pipeline_run_group_id = ?
                   and run_purpose = 'RELEASE_BUILD'
                   and status in (
                        'QUEUED', 'UNKNOWN',
                        'DBT_SUCCEEDED', 'BUILT', 'FAILED'
                   )
                """,
                Timestamp.from(now),
                Timestamp.from(now),
                dispatchId
            )
        );
    }

    @Transactional
    public void markUnknown(
        UUID dispatchId,
        String errorCode,
        Instant nextAttemptAt,
        Instant now
    ) {
        int updated = jdbcTemplate.update(
            """
            update modeling_materialization_dispatch
               set status = 'UNKNOWN', claimed_at = null,
                   next_attempt_at = ?, last_error_code = ?,
                   last_modified_at = ?
             where id = ? and status = 'CLAIMED'
            """,
            Timestamp.from(nextAttemptAt),
            required(errorCode, "errorCode"),
            Timestamp.from(now),
            dispatchId
        );
        if (
            updated == 0 &&
            hasDispatchStatus(
                dispatchId,
                "SUBMITTED",
                "COMPLETED",
                "FAILED"
            )
        ) {
            return;
        }
        requireOne(
            updated,
            "Unknown dispatch state could not be persisted"
        );
        requirePipelineRows(
            jdbcTemplate.update(
                """
                update modeling_pipeline_run
                   set status = case
                           when status in ('QUEUED', 'UNKNOWN')
                               then 'UNKNOWN'
                           else status
                       end,
                       message = case
                           when status in ('QUEUED', 'UNKNOWN')
                               then 'Airflow trigger outcome requires reconciliation'
                           else message
                       end,
                       last_modified_date = ?
                 where pipeline_run_group_id = ?
                   and run_purpose = 'RELEASE_BUILD'
                   and status in (
                        'QUEUED', 'UNKNOWN',
                        'DBT_SUCCEEDED', 'BUILT', 'FAILED'
                   )
                """,
                Timestamp.from(now),
                dispatchId
            )
        );
    }

    @Transactional
    public void markBlocked(
        UUID dispatchId,
        String errorCode,
        Instant now
    ) {
        int updated = jdbcTemplate.update(
            """
            update modeling_materialization_dispatch
               set status = 'BLOCKED', claimed_at = null,
                   next_attempt_at = null, last_error_code = ?,
                   last_modified_at = ?
             where id = ? and status = 'CLAIMED'
            """,
            required(errorCode, "errorCode"),
            Timestamp.from(now),
            dispatchId
        );
        if (
            updated == 0 &&
            hasDispatchStatus(
                dispatchId,
                "SUBMITTED",
                "COMPLETED",
                "FAILED"
            )
        ) {
            return;
        }
        requireOne(
            updated,
            "Blocked dispatch state could not be persisted"
        );
        requirePipelineRows(
            jdbcTemplate.update(
                """
                update modeling_pipeline_run
                   set status = 'BLOCKED',
                       message = ?,
                       finished_date = ?,
                       last_modified_date = ?
                 where pipeline_run_group_id = ?
                   and run_purpose = 'RELEASE_BUILD'
                   and status in ('QUEUED', 'UNKNOWN')
                """,
                required(errorCode, "errorCode"),
                Timestamp.from(now),
                Timestamp.from(now),
                dispatchId
            )
        );
    }

    @Transactional
    public Optional<RuntimeSpecRecord> lockRuntimeSpec(
        String tokenDigest
    ) {
        return jdbcTemplate
            .query(
                """
                with locked_dispatch as (
                    select *
                     from modeling_materialization_dispatch
                     where runtime_token_digest = ?
                       and status in ('CLAIMED', 'SUBMITTED', 'UNKNOWN')
                     for update
                )
                select d.id, d.tenant_id, d.candidate_id,
                       d.candidate_version, d.attempt,
                       d.execution_target_key, d.airflow_dag_id,
                       d.airflow_run_id, d.scoped_bundle_checksum,
                       d.runtime_token_digest, d.runtime_token_expires_at,
                       d.profile_lease_id, d.runtime_consumed_at,
                       c.environment, c.target_name,
                       string_agg(pr.dbt_selector, ' ' order by pr.model_spec_id) as selector,
                       (
                           array_agg(
                               pr.id
                               order by pr.model_spec_id, pr.id
                           )
                       )[1] as pipeline_run_id
                  from locked_dispatch d
                  join modeling_model_release_candidate c
                    on c.tenant_id = d.tenant_id
                   and c.id = d.candidate_id
                  join modeling_pipeline_run pr
                    on pr.tenant_id = d.tenant_id
                   and pr.pipeline_run_group_id = d.id
                   and pr.run_purpose = 'RELEASE_BUILD'
                 group by
                       d.id, d.tenant_id, d.candidate_id,
                       d.candidate_version, d.attempt,
                       d.execution_target_key, d.airflow_dag_id,
                       d.airflow_run_id, d.scoped_bundle_checksum,
                       d.runtime_token_digest, d.runtime_token_expires_at,
                       d.profile_lease_id, d.runtime_consumed_at,
                       c.environment, c.target_name
                """,
                (row, rowNumber) ->
                    new RuntimeSpecRecord(
                        row.getObject("id", UUID.class),
                        row.getString("tenant_id"),
                        row.getObject("candidate_id", UUID.class),
                        row.getInt("candidate_version"),
                        row.getInt("attempt"),
                        row.getString("execution_target_key"),
                        row.getString("airflow_dag_id"),
                        row.getString("airflow_run_id"),
                        row.getString("scoped_bundle_checksum"),
                        row.getString("runtime_token_digest"),
                        instant(
                            row.getTimestamp(
                                "runtime_token_expires_at"
                            )
                        ),
                        row.getObject("profile_lease_id", UUID.class),
                        instant(row.getTimestamp("runtime_consumed_at")),
                        row.getString("environment"),
                        row.getString("target_name"),
                        row.getString("selector"),
                        row.getObject("pipeline_run_id", UUID.class)
                    ),
                tokenDigest
            )
            .stream()
            .findFirst();
    }

    @Transactional
    public boolean attachRuntimeLease(
        UUID dispatchId,
        UUID leaseId,
        Instant consumedAt
    ) {
        return (
            jdbcTemplate.update(
                """
                update modeling_materialization_dispatch
                   set profile_lease_id = ?, runtime_consumed_at = ?,
                       last_modified_at = ?
                 where id = ?
                   and status in ('CLAIMED', 'SUBMITTED', 'UNKNOWN')
                   and profile_lease_id is null
                   and runtime_consumed_at is null
                """,
                leaseId,
                Timestamp.from(consumedAt),
                Timestamp.from(consumedAt),
                dispatchId
            ) ==
            1
        );
    }

    private DispatchRecord map(java.sql.ResultSet row)
        throws java.sql.SQLException {
        return new DispatchRecord(
            row.getObject("id", UUID.class),
            row.getString("tenant_id"),
            row.getObject("candidate_id", UUID.class),
            row.getInt("candidate_version"),
            row.getInt("attempt"),
            row.getString("execution_target_key"),
            row.getString("airflow_dag_id"),
            row.getString("airflow_run_id"),
            row.getString("artifact_bundle_checksum"),
            row.getString("status"),
            row.getString("scoped_bundle_checksum"),
            row.getString("runtime_token_digest"),
            instant(row.getTimestamp("runtime_token_expires_at"))
        );
    }

    private static void requireOne(int updated, String message) {
        if (updated != 1) throw new IllegalStateException(message);
    }

    private static void requirePipelineRows(int updated) {
        if (updated < 1) {
            throw new IllegalStateException(
                "Dispatch has no mutable pipeline rows"
            );
        }
    }

    private boolean hasDispatchStatus(
        UUID dispatchId,
        String... statuses
    ) {
        if (statuses == null || statuses.length == 0) {
            return false;
        }
        String status = jdbcTemplate.query(
            """
            select status
              from modeling_materialization_dispatch
             where id = ?
            """,
            (row, rowNumber) -> row.getString("status"),
            dispatchId
        )
            .stream()
            .findFirst()
            .orElse(null);
        return java.util.Arrays.asList(statuses).contains(status);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    public record DispatchRecord(
        UUID id,
        String tenantId,
        UUID candidateId,
        int candidateVersion,
        int attempt,
        String executionTargetKey,
        String airflowDagId,
        String airflowRunId,
        String artifactBundleChecksum,
        String status,
        String scopedBundleChecksum,
        String runtimeTokenDigest,
        Instant runtimeTokenExpiresAt
    ) {}

    public record RuntimeSpecRecord(
        UUID dispatchId,
        String tenantId,
        UUID candidateId,
        int candidateVersion,
        int attempt,
        String executionTargetKey,
        String airflowDagId,
        String airflowRunId,
        String scopedBundleChecksum,
        String runtimeTokenDigest,
        Instant runtimeTokenExpiresAt,
        UUID profileLeaseId,
        Instant runtimeConsumedAt,
        String environment,
        String targetName,
        String selector,
        UUID pipelineRunId
    ) {}
}
