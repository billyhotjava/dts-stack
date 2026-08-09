package com.yuzhi.dts.platform.repository.modeling;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Persists dbt artifact synchronization and terminal Airflow run-group truth. */
@Repository
public class ModelMaterializationRunRepository {

    private final JdbcTemplate jdbcTemplate;

    public ModelMaterializationRunRepository(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public Optional<RunGroupRecord> findRunGroup(UUID groupId) {
        if (groupId == null) {
            throw new IllegalArgumentException("groupId is required");
        }
        return jdbcTemplate
            .query(
                """
                select d.id, d.tenant_id, d.candidate_id,
                       d.candidate_version, d.attempt,
                       d.scoped_bundle_checksum, d.status, d.last_error_code,
                       c.version as candidate_current_version,
                       c.status as candidate_current_status,
                       c.execution_target_key, c.adapter,
                       l.credential_version_ref
                  from modeling_materialization_dispatch d
                  join modeling_model_release_candidate c
                    on c.tenant_id = d.tenant_id
                   and c.id = d.candidate_id
                  left join modeling_dbt_runtime_profile_lease l
                    on l.id = d.profile_lease_id
                 where d.id = ?
                """,
                (row, rowNumber) ->
                    new RunGroupRecord(
                        row.getObject("id", UUID.class),
                        row.getString("tenant_id"),
                        row.getObject("candidate_id", UUID.class),
                        row.getInt("candidate_version"),
                        row.getInt("attempt"),
                        row.getInt("candidate_current_version"),
                        row.getString("candidate_current_status"),
                        "RELEASE_BUILD",
                        row.getString("scoped_bundle_checksum"),
                        row.getString("status"),
                        row.getString("last_error_code"),
                        row.getString("execution_target_key"),
                        row.getString("adapter"),
                        row.getString("credential_version_ref")
                    ),
                groupId
            )
            .stream()
            .findFirst();
    }

    @Transactional
    public void markDbtSucceeded(
        UUID groupId,
        UUID invocationId,
        int expectedModelCount,
        Instant now
    ) {
        if (
            groupId == null ||
            invocationId == null ||
            expectedModelCount < 1 ||
            now == null
        ) {
            throw new IllegalArgumentException(
                "groupId, invocationId, expectedModelCount and now are required"
            );
        }
        int updated = jdbcTemplate.update(
            """
            update modeling_pipeline_run
               set status = 'DBT_SUCCEEDED',
                   dbt_invocation_id = ?,
                   dbt_run_id = ?,
                   started_date = coalesce(started_date, ?),
                   message = 'dbt build artifacts synchronized',
                   last_modified_date = ?
             where pipeline_run_group_id = ?
               and run_purpose = 'RELEASE_BUILD'
               and status in (
                    'SUBMITTED', 'UNKNOWN', 'DBT_SUCCEEDED'
               )
            """,
            invocationId,
            invocationId.toString(),
            Timestamp.from(now),
            Timestamp.from(now),
            groupId
        );
        if (updated != expectedModelCount) {
            throw new IllegalStateException(
                "dbt artifact synchronization row count does not match candidate scope"
            );
        }
    }

    /** Persists one terminal dbt result per candidate entry before the batch failure is finalized. */
    @Transactional
    public void recordDbtResults(
        UUID groupId,
        UUID invocationId,
        Map<UUID, String> statusesByPipelineRun,
        Instant now
    ) {
        if (
            groupId == null ||
            invocationId == null ||
            statusesByPipelineRun == null ||
            statusesByPipelineRun.isEmpty() ||
            statusesByPipelineRun.size() > 500 ||
            now == null
        ) {
            throw new IllegalArgumentException("groupId, invocationId, bounded statuses and now are required");
        }
        Set<String> allowed = Set.of("DBT_SUCCEEDED", "FAILED", "SKIPPED_DEPENDENCY_FAILED");
        if (
            statusesByPipelineRun.entrySet().stream().anyMatch(entry -> entry.getKey() == null || !allowed.contains(entry.getValue()))
        ) {
            throw new IllegalArgumentException("Unsupported per-model dbt result status");
        }
        List<Map.Entry<UUID, String>> results = List.copyOf(statusesByPipelineRun.entrySet());
        int[] updated = jdbcTemplate.batchUpdate(
            """
            update modeling_pipeline_run
               set status = ?, dbt_invocation_id = ?,
                   started_date = coalesce(started_date, ?),
                   finished_date = coalesce(finished_date, ?),
                   message = ?, last_modified_date = ?
             where id = ?
               and pipeline_run_group_id = ?
               and run_purpose = 'RELEASE_BUILD'
               and status in ('QUEUED', 'SUBMITTED', 'UNKNOWN')
            """,
            new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement statement, int index) throws SQLException {
                    Map.Entry<UUID, String> result = results.get(index);
                    Timestamp occurredAt = Timestamp.from(now);
                    statement.setString(1, result.getValue());
                    statement.setObject(2, invocationId);
                    statement.setTimestamp(3, occurredAt);
                    statement.setTimestamp(4, occurredAt);
                    statement.setString(5, resultMessage(result.getValue()));
                    statement.setTimestamp(6, occurredAt);
                    statement.setObject(7, result.getKey());
                    statement.setObject(8, groupId);
                }

                @Override
                public int getBatchSize() {
                    return results.size();
                }
            }
        );
        int changed = java.util.Arrays.stream(updated).sum();
        if (changed != results.size()) {
            throw new IllegalStateException("Per-model dbt result row count does not match candidate scope");
        }
    }

    @Transactional
    public void markFailed(
        UUID groupId,
        String errorCode,
        Instant now
    ) {
        int rows = failPipelineRows(groupId, errorCode, now);
        if (rows < 1) {
            throw new IllegalStateException(
                "Failed materialization group has no pipeline rows"
            );
        }
        markDispatchTerminal(groupId, "FAILED", errorCode, now);
    }

    /**
     * Commits the fail-closed terminal truth for a callback whose runtime source pin is stale.
     * Returns {@code true} only for the first stale transition; retries preserve the original
     * terminal evidence and cannot downgrade it to a generic failure.
     */
    @Transactional
    public boolean markAvailabilityStale(
        UUID groupId,
        String reasonCode,
        Instant now
    ) {
        if (groupId == null || reasonCode == null || reasonCode.isBlank() || now == null) {
            throw new IllegalArgumentException("groupId, reasonCode and now are required");
        }
        String stableReason = reasonCode.trim();
        int dispatchRows = jdbcTemplate.update(
            """
            update modeling_materialization_dispatch
               set status = 'FAILED', claimed_at = null,
                   next_attempt_at = null, last_error_code = ?,
                   last_modified_at = ?
             where id = ?
               and status in ('CLAIMED', 'SUBMITTED', 'UNKNOWN')
            """,
            stableReason,
            Timestamp.from(now),
            groupId
        );
        if (dispatchRows == 0) {
            Integer staleRows = jdbcTemplate.queryForObject(
                """
                select count(*)
                  from modeling_materialization_dispatch d
                 where d.id = ?
                   and d.status = 'FAILED'
                   and d.last_error_code = ?
                   and exists (
                       select 1
                         from modeling_pipeline_run pr
                        where pr.pipeline_run_group_id = d.id
                          and pr.run_purpose = 'RELEASE_BUILD'
                          and pr.status = 'FAILED_STALE'
                   )
                """,
                Integer.class,
                groupId,
                stableReason
            );
            if (staleRows == null || staleRows < 1) {
                throw new IllegalStateException("stale materialization terminal truth is incomplete");
            }
            return false;
        }
        if (dispatchRows != 1) {
            throw new IllegalStateException("materialization dispatch stale transition is not unique");
        }
        int pipelineRows = jdbcTemplate.update(
            """
            update modeling_pipeline_run
               set status = 'FAILED_STALE', message = ?,
                   finished_date = coalesce(finished_date, ?),
                   last_modified_date = ?
             where pipeline_run_group_id = ?
               and run_purpose = 'RELEASE_BUILD'
               and status <> 'PUBLISHED'
            """,
            stableReason,
            Timestamp.from(now),
            Timestamp.from(now),
            groupId
        );
        if (pipelineRows < 1) {
            throw new IllegalStateException("stale materialization group has no pipeline rows");
        }
        return true;
    }

    @Transactional
    public void markRelationsVerified(
        UUID groupId,
        int expectedModelCount,
        Instant now
    ) {
        if (
            groupId == null ||
            expectedModelCount < 1 ||
            now == null
        ) {
            throw new IllegalArgumentException(
                "groupId, expectedModelCount and now are required"
            );
        }
        int updated = jdbcTemplate.update(
            """
            update modeling_pipeline_run
               set status = 'BUILT',
                   message = 'dbt build and physical relation verified',
                   last_modified_date = ?
             where pipeline_run_group_id = ?
               and run_purpose = 'RELEASE_BUILD'
               and status in ('DBT_SUCCEEDED', 'BUILT')
            """,
            Timestamp.from(now),
            groupId
        );
        if (updated != expectedModelCount) {
            throw new IllegalStateException(
                "Physical relation verification row count does not match candidate scope"
            );
        }
    }

    @Transactional
    public int finalizeSucceeded(UUID groupId, Instant now) {
        Integer modelCount = jdbcTemplate.queryForObject(
            """
            select count(*)
              from modeling_pipeline_run
             where pipeline_run_group_id = ?
               and run_purpose = 'RELEASE_BUILD'
            """,
            Integer.class,
            groupId
        );
        Integer succeededCount = jdbcTemplate.queryForObject(
            """
            select count(*)
              from modeling_pipeline_run
             where pipeline_run_group_id = ?
               and run_purpose = 'RELEASE_BUILD'
               and status = 'BUILT'
            """,
            Integer.class,
            groupId
        );
        if (
            modelCount == null ||
            modelCount < 1 ||
            !modelCount.equals(succeededCount)
        ) {
            throw new IllegalStateException(
                "Airflow success cannot finalize before every model is BUILT"
            );
        }
        int updated = jdbcTemplate.update(
            """
            update modeling_pipeline_run
               set finished_date = coalesce(finished_date, ?),
                   message = 'dbt build and physical relation verification succeeded',
                   last_modified_date = ?
             where pipeline_run_group_id = ?
               and run_purpose = 'RELEASE_BUILD'
               and status = 'BUILT'
            """,
            Timestamp.from(now),
            Timestamp.from(now),
            groupId
        );
        if (updated != modelCount) {
            throw new IllegalStateException(
                "Airflow success finalization row count changed concurrently"
            );
        }
        markDispatchTerminal(
            groupId,
            "COMPLETED",
            null,
            now
        );
        return updated;
    }

    @Transactional
    public int finalizeFailed(
        UUID groupId,
        String errorCode,
        Instant now
    ) {
        int rows = failPipelineRows(groupId, errorCode, now);
        if (rows < 1) {
            throw new IllegalStateException(
                "Airflow failure has no materialization pipeline rows"
            );
        }
        markDispatchTerminal(groupId, "FAILED", errorCode, now);
        return rows;
    }

    private int failPipelineRows(
        UUID groupId,
        String errorCode,
        Instant now
    ) {
        if (
            groupId == null ||
            errorCode == null ||
            errorCode.isBlank() ||
            now == null
        ) {
            throw new IllegalArgumentException(
                "groupId, errorCode and now are required"
            );
        }
        jdbcTemplate.update(
            """
            update modeling_pipeline_run
               set status = 'FAILED',
                   message = ?,
                   finished_date = coalesce(finished_date, ?),
                   last_modified_date = ?
             where pipeline_run_group_id = ?
               and run_purpose = 'RELEASE_BUILD'
               and status not in (
                    'BUILT', 'PUBLISHED', 'FAILED_STALE',
                    'DBT_SUCCEEDED', 'SKIPPED_DEPENDENCY_FAILED', 'FAILED'
               )
            """,
            errorCode.trim(),
            Timestamp.from(now),
            Timestamp.from(now),
            groupId
        );
        Integer terminalRows = jdbcTemplate.queryForObject(
            """
            select count(*)
              from modeling_pipeline_run
             where pipeline_run_group_id = ?
               and run_purpose = 'RELEASE_BUILD'
               and status in (
                    'BUILT', 'PUBLISHED', 'FAILED_STALE',
                    'DBT_SUCCEEDED', 'SKIPPED_DEPENDENCY_FAILED', 'FAILED'
               )
            """,
            Integer.class,
            groupId
        );
        return terminalRows == null ? 0 : terminalRows;
    }

    private static String resultMessage(String status) {
        return switch (status) {
            case "DBT_SUCCEEDED" -> "dbt model succeeded before batch completion";
            case "SKIPPED_DEPENDENCY_FAILED" -> "dbt model skipped because an upstream dependency failed";
            default -> "dbt model failed";
        };
    }

    private void markDispatchTerminal(
        UUID groupId,
        String status,
        String errorCode,
        Instant now
    ) {
        int updated = jdbcTemplate.update(
            """
            update modeling_materialization_dispatch
               set status = ?, claimed_at = null,
                   next_attempt_at = null, last_error_code = ?,
                   last_modified_at = ?
             where id = ?
               and status in (
                    'CLAIMED', 'SUBMITTED', 'UNKNOWN',
                    'COMPLETED', 'FAILED'
               )
            """,
            status,
            errorCode,
            Timestamp.from(now),
            groupId
        );
        if (updated != 1) {
            throw new IllegalStateException(
                "Materialization dispatch cannot be finalized"
            );
        }
    }

    public record RunGroupRecord(
        UUID groupId,
        String tenantId,
        UUID candidateId,
        int candidateVersion,
        int attempt,
        int candidateCurrentVersion,
        String candidateCurrentStatus,
        String runPurpose,
        String scopedBundleChecksum,
        String dispatchStatus,
        String lastErrorCode,
        String executionTargetKey,
        String adapter,
        String credentialVersionRef
    ) {}
}
