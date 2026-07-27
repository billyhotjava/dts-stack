package com.yuzhi.dts.platform.repository.modeling;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Durable dbt-build evidence consumed by the publication quality reconciler.
 *
 * <p>The canonical Airflow release DAG executes {@code dbt build}; a COMPLETED dispatch plus
 * terminal BUILT rows therefore proves both selected model computation and dbt tests completed.
 */
@Repository
public class ModelPublicationQualityEvidenceRepository {

    private final JdbcTemplate jdbcTemplate;

    public ModelPublicationQualityEvidenceRepository(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<QualityWorkItem> findQualityRunning(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException(
                "limit must be between 1 and 100"
            );
        }
        return jdbcTemplate.query(
            """
            select candidate.tenant_id,
                   candidate.id as candidate_id,
                   candidate.plan_id,
                   candidate.version as candidate_version,
                   quality_command.id as quality_command_event_id,
                   dispatch.id as pipeline_run_group_id,
                   dispatch.status as dispatch_status,
                   dispatch.last_error_code,
                   (
                       select count(*)
                         from modeling_model_release_candidate_entry entry
                        where entry.tenant_id = candidate.tenant_id
                          and entry.candidate_id = candidate.id
                   ) as candidate_entry_count,
                   coalesce(runs.run_count, 0) as run_count,
                   coalesce(runs.verified_count, 0) as verified_count
              from modeling_model_release_candidate candidate
              left join lateral (
                    select command.id
                      from modeling_model_release_candidate_command command
                     where command.tenant_id = candidate.tenant_id
                       and command.candidate_id = candidate.id
                       and (
                           command.event_type = 'PUBLICATION_REQUESTED'
                           or (
                               command.event_type = 'STATUS_CHANGED'
                               and command.to_status = 'QUALITY_RUNNING'
                               and command.from_status in ('BUILT', 'QUALITY_FAILED')
                           )
                       )
                     order by command.candidate_version desc, command.occurred_at desc
                     limit 1
              ) quality_command on true
              left join lateral (
                    select materialization.id,
                           materialization.status,
                           materialization.last_error_code
                      from modeling_materialization_dispatch materialization
                     where materialization.tenant_id = candidate.tenant_id
                       and materialization.candidate_id = candidate.id
                     order by materialization.attempt desc,
                              materialization.created_at desc,
                              materialization.id
                     limit 1
              ) dispatch on true
              left join lateral (
                    select count(*) as run_count,
                           count(*) filter (
                               where run.status = 'BUILT'
                                 and run.finished_date is not null
                                 and run.dbt_invocation_id is not null
                           ) as verified_count
                      from modeling_pipeline_run run
                     where run.pipeline_run_group_id = dispatch.id
                       and run.run_purpose = 'RELEASE_BUILD'
              ) runs on true
             where candidate.status = 'QUALITY_RUNNING'
             order by candidate.last_modified_date, candidate.id
             limit ?
            """,
            (row, rowNumber) ->
                new QualityWorkItem(
                    row.getString("tenant_id"),
                    row.getObject("candidate_id", UUID.class),
                    row.getObject("plan_id", UUID.class),
                    row.getInt("candidate_version"),
                    row.getObject("quality_command_event_id", UUID.class),
                    row.getObject("pipeline_run_group_id", UUID.class),
                    row.getString("dispatch_status"),
                    row.getString("last_error_code"),
                    row.getInt("candidate_entry_count"),
                    row.getInt("run_count"),
                    row.getInt("verified_count")
                ),
            limit
        );
    }

    public record QualityWorkItem(
        String tenantId,
        UUID candidateId,
        UUID planId,
        int candidateVersion,
        UUID qualityCommandEventId,
        UUID pipelineRunGroupId,
        String dispatchStatus,
        String lastErrorCode,
        int candidateEntryCount,
        int runCount,
        int verifiedCount
    ) {
        public EvidenceState evidenceState() {
            if (
                qualityCommandEventId == null ||
                pipelineRunGroupId == null ||
                candidateEntryCount < 1 ||
                runCount < 1
            ) {
                return EvidenceState.MISSING;
            }
            if (
                "FAILED".equals(dispatchStatus) ||
                "BLOCKED".equals(dispatchStatus)
            ) {
                return EvidenceState.FAILED;
            }
            if (
                "COMPLETED".equals(dispatchStatus) &&
                runCount == candidateEntryCount &&
                verifiedCount == runCount
            ) {
                return EvidenceState.PASSED;
            }
            return EvidenceState.PENDING;
        }
    }

    public enum EvidenceState {
        PENDING,
        PASSED,
        FAILED,
        MISSING,
    }
}
