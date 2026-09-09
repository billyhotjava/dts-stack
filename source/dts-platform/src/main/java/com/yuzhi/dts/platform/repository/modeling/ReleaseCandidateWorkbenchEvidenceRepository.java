package com.yuzhi.dts.platform.repository.modeling;

import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EntryEvidenceView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.MaterializationAttemptView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.ModelMaterializationStatusView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.RelationEvidenceState;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ReleaseCandidateWorkbenchEvidencePort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Projects the latest durable Airflow/dbt attempt and append-only relation observation for each Candidate entry. */
@Repository
public class ReleaseCandidateWorkbenchEvidenceRepository
    implements ReleaseCandidateWorkbenchEvidencePort {

    private final JdbcTemplate jdbcTemplate;

    public ReleaseCandidateWorkbenchEvidenceRepository(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public List<EntryEvidenceView> findCurrent(
        CandidateView candidate
    ) {
        if (candidate == null) {
            throw new IllegalArgumentException("candidate is required");
        }
        return List.copyOf(
            jdbcTemplate.query(
                """
                select e.id as candidate_entry_id,
                       e.model_spec_id, e.revision as model_revision,
                       e.implementation_revision, e.target_identifier,
                       coalesce(
                           nullif(btrim(r.snapshot_json ->> 'name'), ''),
                           nullif(btrim(r.snapshot_json ->> 'code'), ''),
                           e.model_spec_id::text
                       ) as model_name,
                       latest.pipeline_run_group_id,
                       latest.airflow_dag_id, latest.airflow_run_id,
                       latest.attempt, latest.dispatch_status,
                       latest.last_error_code,
                       latest.dbt_invocation_id, latest.run_status, latest.run_message,
                       latest.started_date, latest.finished_date,
                       observation.verified,
                       observation.relation_exists,
                       observation.database_name,
                       observation.schema_name,
                       observation.identifier,
                       observation.observed_at,
                       observation.error_code as observation_error_code
                  from modeling_model_release_candidate_entry e
                  left join modeling_model_spec_revision r
                    on r.model_spec_id = e.model_spec_id
                   and r.revision = e.revision
                  left join lateral (
                        select pr.id as pipeline_run_id,
                               d.id as pipeline_run_group_id,
                               d.airflow_dag_id, d.airflow_run_id,
                               d.attempt, d.status as dispatch_status,
                               d.last_error_code, d.last_modified_at,
                               pr.dbt_invocation_id, pr.status as run_status, pr.message as run_message,
                               pr.started_date, pr.finished_date
                          from modeling_pipeline_run pr
                          join modeling_materialization_dispatch d
                            on d.tenant_id = pr.tenant_id
                           and d.id = pr.pipeline_run_group_id
                           and d.candidate_id = pr.release_candidate_id
                         where pr.tenant_id = e.tenant_id
                           and pr.release_candidate_id = e.candidate_id
                           and pr.release_candidate_entry_id = e.id
                           and pr.run_purpose = 'RELEASE_BUILD'
                           and pr.model_revision = e.revision
                           and pr.model_checksum = e.checksum
                           and pr.implementation_revision = e.implementation_revision
                           and pr.implementation_checksum = e.implementation_checksum
                         order by d.attempt desc,
                                  d.last_modified_at desc,
                                  d.id desc,
                                  pr.id desc
                         limit 1
                  ) latest on true
                  left join lateral (
                        select observed.verified, observed.relation_exists,
                               observed.database_name, observed.schema_name,
                               observed.identifier, observed.observed_at,
                               observed.error_code
                          from modeling_physical_relation_observation observed
                         where observed.pipeline_run_id = latest.pipeline_run_id
                         order by observed.observation_attempt desc,
                                  observed.created_date desc,
                                  observed.id desc
                         limit 1
                  ) observation on true
                 where e.tenant_id = ?
                   and e.candidate_id = ?
                 order by e.sort_order, e.id
                """,
                ReleaseCandidateWorkbenchEvidenceRepository::mapEvidence,
                candidate.tenantId(),
                candidate.id()
            )
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<ModelMaterializationStatusView> findLatest(
        String tenantId,
        UUID planId,
        List<UUID> modelSpecIds
    ) {
        String tenant = tenantId == null ? "" : tenantId.trim();
        if (
            tenant.isEmpty() ||
            planId == null ||
            modelSpecIds == null ||
            modelSpecIds.isEmpty() ||
            modelSpecIds.stream().anyMatch(id -> id == null)
        ) {
            throw new IllegalArgumentException("tenantId, planId and modelSpecIds are required");
        }
        List<UUID> ids = List.copyOf(modelSpecIds);
        if (ids.size() > ModelReleaseCandidateContract.MAX_SCOPE_ENTRIES || new HashSet<>(ids).size() != ids.size()) {
            throw new IllegalArgumentException("modelSpecIds must be unique and contain at most 100 entries");
        }
        String placeholders = String.join(",", ids.stream().map(ignored -> "?").toList());
        List<Object> arguments = new ArrayList<>(ids.size() + 2);
        arguments.add(tenant);
        arguments.add(planId);
        arguments.addAll(ids);
        return List.copyOf(
            jdbcTemplate.query(
                """
                with ranked_entry as (
                    select e.*,
                           c.environment as candidate_environment,
                           c.status as candidate_status,
                           c.version as candidate_version,
                           c.last_modified_date as candidate_updated_at,
                           row_number() over (
                               partition by e.model_spec_id
                               order by case when c.status in ('CANCELLED', 'REJECTED') then 1 else 0 end,
                                        c.created_date desc, c.last_modified_date desc, c.id desc
                           ) as candidate_rank
                      from modeling_model_release_candidate_entry e
                      join modeling_model_release_candidate c
                        on c.tenant_id = e.tenant_id
                       and c.id = e.candidate_id
                     where e.tenant_id = ?
                       and c.plan_id = ?
                       and e.model_spec_id in (%s)
                )
                select e.id as candidate_entry_id,
                       e.model_spec_id, e.revision as model_revision,
                       e.implementation_revision, e.target_identifier,
                       e.candidate_id, e.candidate_environment,
                       e.candidate_status, e.candidate_version,
                       e.candidate_updated_at,
                       current_implementation.implementation_revision as current_implementation_revision,
                       coalesce(
                           nullif(btrim(r.snapshot_json ->> 'name'), ''),
                           nullif(btrim(r.snapshot_json ->> 'code'), ''),
                           e.model_spec_id::text
                       ) as model_name,
                       latest.pipeline_run_group_id,
                       latest.airflow_dag_id, latest.airflow_run_id,
                       latest.attempt, latest.dispatch_status,
                       latest.last_error_code,
                       latest.dbt_invocation_id, latest.run_status, latest.run_message,
                       latest.started_date, latest.finished_date,
                       observation.verified,
                       observation.relation_exists,
                       observation.database_name,
                       observation.schema_name,
                       observation.identifier,
                       observation.observed_at,
                       observation.error_code as observation_error_code
                  from ranked_entry e
                  left join modeling_model_spec_revision r
                    on r.model_spec_id = e.model_spec_id
                   and r.revision = e.revision
                  left join modeling_model_implementation current_implementation
                    on current_implementation.tenant_id = e.tenant_id
                   and current_implementation.model_spec_id = e.model_spec_id
                  left join lateral (
                        select pr.id as pipeline_run_id,
                               d.id as pipeline_run_group_id,
                               d.airflow_dag_id, d.airflow_run_id,
                               d.attempt, d.status as dispatch_status,
                               d.last_error_code, d.last_modified_at,
                               pr.dbt_invocation_id, pr.status as run_status, pr.message as run_message,
                               pr.started_date, pr.finished_date
                          from modeling_pipeline_run pr
                          join modeling_materialization_dispatch d
                            on d.tenant_id = pr.tenant_id
                           and d.id = pr.pipeline_run_group_id
                           and d.candidate_id = pr.release_candidate_id
                         where pr.tenant_id = e.tenant_id
                           and pr.release_candidate_id = e.candidate_id
                           and pr.release_candidate_entry_id = e.id
                           and pr.run_purpose = 'RELEASE_BUILD'
                           and pr.model_revision = e.revision
                           and pr.model_checksum = e.checksum
                           and pr.implementation_revision = e.implementation_revision
                           and pr.implementation_checksum = e.implementation_checksum
                         order by d.attempt desc,
                                  d.last_modified_at desc,
                                  d.id desc,
                                  pr.id desc
                         limit 1
                  ) latest on true
                  left join lateral (
                        select observed.verified, observed.relation_exists,
                               observed.database_name, observed.schema_name,
                               observed.identifier, observed.observed_at,
                               observed.error_code
                          from modeling_physical_relation_observation observed
                         where observed.pipeline_run_id = latest.pipeline_run_id
                         order by observed.observation_attempt desc,
                                  observed.created_date desc,
                                  observed.id desc
                         limit 1
                  ) observation on true
                 where e.candidate_rank = 1
                 order by e.model_spec_id
                """.formatted(placeholders),
                (row, rowNumber) -> {
                    EntryEvidenceView evidence = mapEvidence(row, rowNumber);
                    return new ModelMaterializationStatusView(
                        evidence.modelSpecId(),
                        row.getObject("candidate_id", UUID.class),
                        row.getInt("candidate_version"),
                        row.getString("candidate_environment"),
                        DeliveryStatus.valueOf(row.getString("candidate_status")),
                        row.getTimestamp("candidate_updated_at").toInstant(),
                        row.getObject("current_implementation_revision", Integer.class),
                        evidence
                    );
                },
                arguments.toArray()
            )
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<MaterializationAttemptView> findHistory(CandidateView candidate) {
        if (candidate == null) {
            throw new IllegalArgumentException("candidate is required");
        }
        List<AttemptEvidenceRow> rows = jdbcTemplate.query(
            """
            select e.id as candidate_entry_id,
                   e.model_spec_id, e.revision as model_revision,
                   e.implementation_revision, e.target_identifier,
                   coalesce(
                       nullif(btrim(r.snapshot_json ->> 'name'), ''),
                       nullif(btrim(r.snapshot_json ->> 'code'), ''),
                       e.model_spec_id::text
                   ) as model_name,
                   d.id as pipeline_run_group_id,
                   d.candidate_version, d.attempt,
                   d.status as dispatch_status, d.last_error_code,
                   d.created_at as dispatch_created_at,
                   d.last_modified_at as dispatch_updated_at,
                   d.airflow_dag_id, d.airflow_run_id,
                   pr.dbt_invocation_id, pr.status as run_status, pr.message as run_message,
                   pr.started_date, pr.finished_date,
                   observation.verified,
                   observation.relation_exists,
                   observation.database_name,
                   observation.schema_name,
                   observation.identifier,
                   observation.observed_at,
                   observation.error_code as observation_error_code
              from modeling_materialization_dispatch d
              join modeling_model_release_candidate_entry e
                on e.tenant_id = d.tenant_id
               and e.candidate_id = d.candidate_id
              left join modeling_model_spec_revision r
                on r.model_spec_id = e.model_spec_id
               and r.revision = e.revision
              left join modeling_pipeline_run pr
                on pr.tenant_id = e.tenant_id
               and pr.release_candidate_id = e.candidate_id
               and pr.release_candidate_entry_id = e.id
               and pr.pipeline_run_group_id = d.id
               and pr.run_purpose = 'RELEASE_BUILD'
              left join lateral (
                    select observed.verified, observed.relation_exists,
                           observed.database_name, observed.schema_name,
                           observed.identifier, observed.observed_at,
                           observed.error_code
                      from modeling_physical_relation_observation observed
                     where observed.pipeline_run_id = pr.id
                     order by observed.observation_attempt desc,
                              observed.created_date desc,
                              observed.id desc
                     limit 1
              ) observation on true
             where d.tenant_id = ?
               and d.candidate_id = ?
             order by d.attempt desc, e.sort_order, e.id
            """,
            (row, rowNumber) ->
                new AttemptEvidenceRow(
                    row.getObject("pipeline_run_group_id", UUID.class),
                    row.getInt("candidate_version"),
                    row.getInt("attempt"),
                    row.getString("dispatch_status"),
                    row.getString("last_error_code"),
                    row.getTimestamp("dispatch_created_at").toInstant(),
                    row.getTimestamp("dispatch_updated_at").toInstant(),
                    mapEvidence(row, rowNumber)
                ),
            candidate.tenantId(),
            candidate.id()
        );
        Map<UUID, List<AttemptEvidenceRow>> byAttempt = new LinkedHashMap<>();
        for (AttemptEvidenceRow row : rows) {
            byAttempt.computeIfAbsent(row.groupId(), ignored -> new ArrayList<>()).add(row);
        }
        List<MaterializationAttemptView> history = new ArrayList<>(byAttempt.size());
        for (List<AttemptEvidenceRow> attemptRows : byAttempt.values()) {
            AttemptEvidenceRow first = attemptRows.getFirst();
            history.add(
                new MaterializationAttemptView(
                    candidate.id(),
                    first.candidateVersion(),
                    first.groupId(),
                    first.attempt(),
                    first.dispatchStatus(),
                    first.errorCode(),
                    first.createdAt(),
                    first.updatedAt(),
                    attemptRows.stream().map(AttemptEvidenceRow::evidence).toList()
                )
            );
        }
        return List.copyOf(history);
    }

    private static EntryEvidenceView mapEvidence(ResultSet row, int rowNumber) throws SQLException {
        String runStatus = row.getString("run_status");
        Boolean verified = row.getObject("verified", Boolean.class);
        Boolean relationExists = row.getObject("relation_exists", Boolean.class);
        return new EntryEvidenceView(
            row.getObject("candidate_entry_id", UUID.class),
            row.getObject("model_spec_id", UUID.class),
            row.getString("model_name"),
            row.getInt("model_revision"),
            row.getObject("implementation_revision", Integer.class),
            targetRelation(
                row.getString("database_name"),
                row.getString("schema_name"),
                row.getString("identifier"),
                row.getString("target_identifier")
            ),
            runStatus,
            relationState(runStatus, verified, relationExists),
            row.getObject("pipeline_run_group_id", UUID.class),
            row.getObject("dbt_invocation_id", UUID.class),
            row.getString("airflow_dag_id"),
            row.getString("airflow_run_id"),
            row.getObject("attempt", Integer.class),
            instant(row.getTimestamp("started_date")),
            instant(row.getTimestamp("finished_date")),
            instant(row.getTimestamp("observed_at")),
            repairCode(
                row.getString("observation_error_code"),
                row.getString("last_error_code"),
                runStatus,
                row.getString("dispatch_status")
            ),
            Set.of("FAILED", "FAILED_STALE", "BLOCKED", "SKIPPED_DEPENDENCY_FAILED").contains(runStatus == null ? "" : runStatus)
                ? row.getString("run_message") : null
        );
    }

    private static RelationEvidenceState relationState(
        String runStatus,
        Boolean verified,
        Boolean relationExists
    ) {
        if (runStatus == null) {
            return RelationEvidenceState.NOT_STARTED;
        }
        if (verified != null || relationExists != null) {
            return Boolean.TRUE.equals(verified) &&
                Boolean.TRUE.equals(relationExists)
                ? RelationEvidenceState.VERIFIED
                : RelationEvidenceState.FAILED;
        }
        return switch (runStatus) {
            case "DBT_SUCCEEDED" -> RelationEvidenceState.PROBING;
            case "FAILED" -> RelationEvidenceState.FAILED;
            case "BLOCKED" -> RelationEvidenceState.UNKNOWN;
            case "UNKNOWN" -> RelationEvidenceState.UNKNOWN;
            default -> RelationEvidenceState.PENDING;
        };
    }

    private static String targetRelation(
        String database,
        String schema,
        String identifier,
        String fallback
    ) {
        if (
            database != null &&
            !database.isBlank() &&
            schema != null &&
            !schema.isBlank() &&
            identifier != null &&
            !identifier.isBlank()
        ) {
            return (
                database.trim() +
                "." +
                schema.trim() +
                "." +
                identifier.trim()
            );
        }
        return fallback;
    }

    private static String repairCode(
        String observationCode,
        String dispatchCode,
        String runStatus,
        String dispatchStatus
    ) {
        if (observationCode != null && !observationCode.isBlank()) {
            return observationCode;
        }
        if (dispatchCode != null && !dispatchCode.isBlank()) {
            return dispatchCode;
        }
        if ("FAILED".equals(runStatus)) {
            return "MODEL_MATERIALIZATION_BUILD_FAILED";
        }
        if ("BLOCKED".equals(runStatus) || "BLOCKED".equals(dispatchStatus)) {
            return "MODEL_MATERIALIZATION_DISPATCH_BLOCKED";
        }
        if ("UNKNOWN".equals(runStatus) || "UNKNOWN".equals(dispatchStatus)) {
            return "MODEL_MATERIALIZATION_DISPATCH_UNKNOWN";
        }
        return null;
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record AttemptEvidenceRow(
        UUID groupId,
        int candidateVersion,
        int attempt,
        String dispatchStatus,
        String errorCode,
        Instant createdAt,
        Instant updatedAt,
        EntryEvidenceView evidence
    ) {}
}
