package com.yuzhi.dts.platform.repository.modeling;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
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
                       d.candidate_version,
                       d.scoped_bundle_checksum, d.status,
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
                        "RELEASE_BUILD",
                        row.getString("scoped_bundle_checksum"),
                        row.getString("status"),
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
        return jdbcTemplate.update(
            """
            update modeling_pipeline_run
               set status = 'FAILED',
                   message = ?,
                   finished_date = coalesce(finished_date, ?),
                   last_modified_date = ?
             where pipeline_run_group_id = ?
               and run_purpose = 'RELEASE_BUILD'
               and status not in ('BUILT', 'PUBLISHED')
            """,
            errorCode.trim(),
            Timestamp.from(now),
            Timestamp.from(now),
            groupId
        );
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
        String runPurpose,
        String scopedBundleChecksum,
        String dispatchStatus,
        String executionTargetKey,
        String adapter,
        String credentialVersionRef
    ) {}
}
