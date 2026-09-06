package com.yuzhi.dts.platform.repository.modeling;

import com.yuzhi.dts.platform.service.etl.DbtScopedProjectService.CandidateArtifact;
import com.yuzhi.dts.platform.service.etl.DbtScopedProjectService.CandidateArtifactEntry;
import com.yuzhi.dts.platform.service.modeling.PlanExecutionException;
import com.yuzhi.dts.platform.service.modeling.PlanExecutionException.Kind;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists plan runs in modeling_pipeline_run and uses a separate outbox only for the Airflow
 * side effect. No second business run ledger is introduced.
 */
@Repository
public class PlanOperationalRunRepository {

    private final JdbcTemplate jdbcTemplate;

    public PlanOperationalRunRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public OpenedRun open(
        String tenantId,
        UUID planId,
        UUID bindingId,
        String dagRunId,
        String triggerType,
        Instant logicalDate,
        String targetName,
        String expectedDeploymentChecksum,
        Instant now
    ) {
        String tenant = required(tenantId, "tenantId");
        String trigger = required(triggerType, "triggerType");
        if (
            bindingId == null ||
            dagRunId == null ||
            targetName == null ||
            now == null ||
            !List.of("MANUAL", "CRON").contains(trigger) ||
            ("MANUAL".equals(trigger) && logicalDate != null) ||
            ("CRON".equals(trigger) && logicalDate == null)
        ) {
            throw failure(
                "MODEL_OPERATIONAL_RUN_REQUEST_INVALID",
                "Operational run identity is invalid",
                Kind.INVALID
            );
        }
        BindingScope binding = lockBinding(
            tenant,
            planId,
            bindingId
        );
        if (
            "CRON".equals(trigger) &&
            (
                expectedDeploymentChecksum == null ||
                !expectedDeploymentChecksum.matches(
                    "^[0-9a-f]{64}$"
                ) ||
                !expectedDeploymentChecksum.equals(
                    binding.desiredDeploymentChecksum()
                ) ||
                !expectedDeploymentChecksum.equals(
                    binding.deployedChecksum()
                )
            )
        ) {
            throw failure(
                "MODEL_PLAN_DAG_DEPLOYMENT_STALE",
                "Scheduled DagRun belongs to a stale plan deployment",
                Kind.CONFLICT
            );
        }
        Optional<OpenedRun> replay = findOpened(
            tenant,
            bindingId,
            dagRunId
        );
        if (replay.isPresent()) return replay.orElseThrow().asReplay();
        if (hasActiveRun(tenant, bindingId)) {
            throw failure(
                "MODEL_OPERATIONAL_RUN_CONCURRENT",
                "Another operational run is active for this plan binding",
                Kind.CONFLICT
            );
        }
        if (
            !"ACTIVE".equals(binding.deploymentStatus()) ||
            !binding
                .desiredDeploymentChecksum()
                .equals(binding.deployedChecksum())
        ) {
            throw failure(
                "MODEL_PLAN_BINDING_ACTIVE_REQUIRED",
                "Plan execution binding is not active",
                Kind.CONFLICT
            );
        }
        if (!operationalDagId(binding.id()).equals(binding.dagId())) {
            throw failure(
                "MODEL_PLAN_BINDING_OPERATIONAL_DAG_REQUIRED",
                "Plan execution binding must use its isolated operational DAG",
                Kind.CONFLICT
            );
        }
        List<ScopeEntry> entries = loadEntries(binding);
        if (entries.isEmpty()) {
            throw failure(
                "MODEL_PLAN_BINDING_SCOPE_EMPTY",
                "Plan execution binding has no published models",
                Kind.CONFLICT
            );
        }
        UUID groupId = stableUuid(
            "plan-operational-run:" +
            tenant +
            ":" +
            bindingId +
            ":" +
            dagRunId
        );
        UUID invocationId = stableUuid(
            "plan-operational-invocation:" + groupId
        );
        String activeClaim = digest(
            "plan-operational-active:" + tenant + ":" + bindingId
        );
        for (int index = 0; index < entries.size(); index++) {
            ScopeEntry entry = entries.get(index);
            jdbcTemplate.update(
                    """
                    insert into modeling_pipeline_run (
                        id, tenant_id, model_spec_id, idempotency_key,
                        status, airflow_dag_id, airflow_run_id,
                        dbt_selector, target, started_date,
                        version, created_date, last_modified_date,
                        plan_id, model_revision, model_checksum, repair_path,
                        pipeline_run_group_id, implementation_revision,
                        implementation_checksum, environment, run_purpose,
                        attempt, artifact_bundle_checksum,
                        dbt_invocation_id, execution_binding_id,
                        binding_version, trigger_type, logical_date,
                        scope_checksum, operational_active_claim_key
                    ) values (
                        ?, ?, ?, ?, 'QUEUED', ?, ?, ?, ?, null,
                        1, ?, ?, ?, ?, ?, ?,
                        ?, ?, ?, ?, 'OPERATIONAL_RUN',
                        1, ?, ?, ?, ?, ?, ?, ?, ?
                    )
                    """,
                    stableUuid(
                        "plan-operational-row:" +
                        groupId +
                        ":" +
                        entry.modelSpecId()
                    ),
                    tenant,
                    entry.modelSpecId(),
                    "operational:" + groupId + ":" + entry.modelSpecId(),
                    binding.dagId(),
                    dagRunId,
                    selector(entry.dbtUniqueId()),
                    targetName.trim(),
                    Timestamp.from(now),
                    Timestamp.from(now),
                    binding.planId(),
                    entry.modelRevision(),
                    entry.modelChecksum(),
                    "/modeling/plans/" +
                    binding.planId() +
                    "/delivery?bindingId=" +
                    binding.id(),
                    groupId,
                    entry.implementationRevision(),
                    entry.implementationChecksum(),
                    binding.environment(),
                    binding.desiredScopeChecksum(),
                    invocationId,
                    binding.id(),
                    binding.version(),
                    trigger,
                    logicalDate == null
                        ? null
                        : Timestamp.from(logicalDate),
                    binding.desiredScopeChecksum(),
                    index == 0 ? activeClaim : null
            );
        }
        jdbcTemplate.update(
                """
                insert into modeling_operational_run_dispatch (
                    id, tenant_id, binding_id, binding_version,
                    trigger_type, logical_date, execution_target_key,
                    target_name, airflow_dag_id, airflow_run_id,
                    scope_checksum, status, dispatch_attempts,
                    next_attempt_at, created_at, last_modified_at
                ) values (
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    'PENDING', 0, ?, ?, ?
                )
                """,
                groupId,
                tenant,
                binding.id(),
                binding.version(),
                trigger,
                logicalDate == null
                    ? null
                    : Timestamp.from(logicalDate),
                binding.executionTargetKey(),
                targetName.trim(),
                binding.dagId(),
                dagRunId,
                binding.desiredScopeChecksum(),
                Timestamp.from(now),
                Timestamp.from(now),
                Timestamp.from(now)
        );
        return new OpenedRun(
            groupId,
            tenant,
            binding.planId(),
            binding.id(),
            binding.version(),
            trigger,
            binding.dagId(),
            dagRunId,
            binding.desiredScopeChecksum(),
            null,
            "PENDING",
            false
        );
    }

    @Transactional(readOnly = true)
    public OperationalScope loadScope(UUID groupId) {
        List<ScopeArtifactRow> rows = jdbcTemplate.query(
            """
            select d.id as group_id, d.tenant_id, d.binding_id,
                   d.binding_version, d.execution_target_key,
                   d.target_name, d.airflow_dag_id, d.airflow_run_id,
                   d.scope_checksum, b.desired_deployment_checksum,
                   d.project_bundle_checksum,
                   pr.id as pipeline_run_id, pr.model_spec_id,
                   pr.model_revision, pr.model_checksum,
                   pr.implementation_revision,
                   pr.implementation_checksum, pr.dbt_selector,
                   i.dbt_unique_id,
                   a.path, a.content_checksum, a.content
              from modeling_operational_run_dispatch d
              join modeling_plan_execution_binding b
                on b.id = d.binding_id
               and b.tenant_id = d.tenant_id
               and b.version = d.binding_version
              join modeling_pipeline_run pr
                on pr.tenant_id = d.tenant_id
               and pr.pipeline_run_group_id = d.id
               and pr.run_purpose = 'OPERATIONAL_RUN'
              join modeling_model_implementation i
                on i.tenant_id = pr.tenant_id
               and i.model_spec_id = pr.model_spec_id
               and i.model_revision = pr.model_revision
               and i.implementation_revision =
                   pr.implementation_revision
               and i.current_implementation_checksum =
                   pr.implementation_checksum
              join modeling_dbt_artifact a
                on a.model_spec_id = pr.model_spec_id
               and a.revision = pr.model_revision
               and a.model_checksum = pr.model_checksum
               and a.implementation_revision =
                   pr.implementation_revision
               and a.ownership = i.ownership
               and (
                    a.status = 'COMPILED'
                    or (
                        a.status = 'IMPORTED'
                        and a.node_kind in ('STG', 'EPHEMERAL')
                        and lower(a.path) like 'models/%.sql'
                    )
               )
               and (
                    a.artifact_type in ('SQL', 'TEST', 'STG_SQL')
                    or (
                        a.artifact_type = 'SCHEMA'
                        and (
                            lower(a.path) like '%.yml'
                            or lower(a.path) like '%.yaml'
                        )
                    )
               )
             where d.id = ?
             order by pr.model_spec_id, a.artifact_key, a.id
            """,
            (row, rowNumber) ->
                new ScopeArtifactRow(
                    row.getObject("group_id", UUID.class),
                    row.getString("tenant_id"),
                    row.getObject("binding_id", UUID.class),
                    row.getInt("binding_version"),
                    row.getString("execution_target_key"),
                    row.getString("target_name"),
                    row.getString("airflow_dag_id"),
                    row.getString("airflow_run_id"),
                    row.getString("scope_checksum"),
                    row.getString("desired_deployment_checksum"),
                    row.getString("project_bundle_checksum"),
                    row.getObject("pipeline_run_id", UUID.class),
                    row.getObject("model_spec_id", UUID.class),
                    row.getInt("model_revision"),
                    row.getString("model_checksum"),
                    row.getInt("implementation_revision"),
                    row.getString("implementation_checksum"),
                    row.getString("dbt_selector"),
                    row.getString("dbt_unique_id"),
                    row.getString("path"),
                    row.getString("content_checksum"),
                    row.getString("content")
                ),
            groupId
        );
        if (rows.isEmpty()) {
            throw failure(
                "MODEL_OPERATIONAL_RUN_NOT_FOUND",
                "Operational run scope does not exist",
                Kind.NOT_FOUND
            );
        }
        ScopeArtifactRow first = rows.getFirst();
        Map<UUID, List<ScopeArtifactRow>> grouped =
            new LinkedHashMap<>();
        rows.forEach(row ->
            grouped
                .computeIfAbsent(
                    row.modelSpecId(),
                    ignored -> new ArrayList<>()
                )
                .add(row)
        );
        List<CandidateArtifactEntry> entries = grouped
            .values()
            .stream()
            .map(modelRows -> {
                ScopeArtifactRow model = modelRows.getFirst();
                return new CandidateArtifactEntry(
                    model.modelSpecId(),
                    model.modelRevision(),
                    model.modelChecksum(),
                    model.implementationRevision(),
                    model.implementationChecksum(),
                    model.dbtUniqueId(),
                    modelRows
                        .stream()
                        .map(row ->
                            new CandidateArtifact(
                                row.path(),
                                row.contentChecksum(),
                                row.content()
                            )
                        )
                        .toList()
                );
            })
            .toList();
        return new OperationalScope(
            first.groupId(),
            first.tenantId(),
            first.bindingId(),
            first.bindingVersion(),
            first.executionTargetKey(),
            first.targetName(),
            first.airflowDagId(),
            first.airflowRunId(),
            first.scopeChecksum(),
            first.deploymentChecksum(),
            first.projectBundleChecksum(),
            entries
        );
    }

    @Transactional(readOnly = true)
    public String tenantForBinding(UUID bindingId) {
        if (bindingId == null) {
            throw new IllegalArgumentException("bindingId is required");
        }
        List<String> tenants = jdbcTemplate.query(
            """
            select tenant_id
              from modeling_plan_execution_binding
             where id = ?
            """,
            (row, rowNumber) -> row.getString("tenant_id"),
            bindingId
        );
        if (tenants.isEmpty()) {
            throw failure(
                "MODEL_PLAN_BINDING_NOT_FOUND",
                "Plan execution binding does not exist",
                Kind.NOT_FOUND
            );
        }
        return tenants.getFirst();
    }

    @Transactional(readOnly = true)
    public Optional<PreparedDispatch> findPrepared(UUID groupId) {
        return jdbcTemplate
            .query(
                """
                select id, project_bundle_checksum,
                       runtime_token_digest,
                       runtime_token_expires_at, status
                  from modeling_operational_run_dispatch
                 where id = ?
                   and project_bundle_checksum is not null
                   and runtime_token_digest is not null
                   and runtime_token_expires_at is not null
                """,
                (row, rowNumber) ->
                    new PreparedDispatch(
                        row.getObject("id", UUID.class),
                        row.getString("project_bundle_checksum"),
                        row.getString("runtime_token_digest"),
                        instant(
                            row.getTimestamp(
                                "runtime_token_expires_at"
                            )
                        ),
                        row.getString("status")
                    ),
                groupId
            )
            .stream()
            .findFirst();
    }

    @Transactional
    public Optional<OpenedRun> claimRecoverableManual(
        Instant now
    ) {
        Instant staleClaim = now.minusSeconds(30);
        Instant staleSubmission = now.minusSeconds(300);
        Instant nextAttempt = now.plusSeconds(30);
        List<UUID> claimed = jdbcTemplate.query(
            """
            with candidate as (
                select d.id
                  from modeling_operational_run_dispatch d
                 where d.trigger_type = 'MANUAL'
                   and d.status in (
                       'PENDING', 'CLAIMED', 'SUBMITTED', 'UNKNOWN'
                   )
                   and (
                       d.status <> 'SUBMITTED'
                       or d.last_modified_at <= ?
                   )
                   and (
                       d.next_attempt_at is null
                       or d.next_attempt_at <= ?
                   )
                   and (
                       d.claimed_at is null
                       or d.claimed_at <= ?
                   )
                 order by d.next_attempt_at nulls first,
                          d.created_at, d.id
                 for update skip locked
                 limit 1
            )
            update modeling_operational_run_dispatch d
               set claimed_at = ?, next_attempt_at = ?,
                   dispatch_attempts = dispatch_attempts + 1,
                   last_modified_at = ?
              from candidate
             where d.id = candidate.id
            returning d.id
            """,
            (row, rowNumber) -> row.getObject("id", UUID.class),
            Timestamp.from(staleSubmission),
            Timestamp.from(now),
            Timestamp.from(staleClaim),
            Timestamp.from(now),
            Timestamp.from(nextAttempt),
            Timestamp.from(now)
        );
        if (claimed.isEmpty()) return Optional.empty();
        return findOpened(claimed.getFirst());
    }

    @Transactional
    public boolean attachPreparedRuntime(
        UUID groupId,
        String projectBundleChecksum,
        String tokenDigest,
        Instant expiresAt,
        boolean airflowAlreadyOpened,
        Instant now
    ) {
        String status = airflowAlreadyOpened ? "SUBMITTED" : "CLAIMED";
        int dispatch = jdbcTemplate.update(
            """
            update modeling_operational_run_dispatch
               set project_bundle_checksum = ?,
                   runtime_token_digest = ?,
                   runtime_token_expires_at = ?,
                   status = ?, claimed_at = ?,
                   dispatch_attempts = dispatch_attempts + 1,
                   last_modified_at = ?
             where id = ? and status in ('PENDING', 'UNKNOWN')
               and project_bundle_checksum is null
               and runtime_token_digest is null
            """,
            projectBundleChecksum,
            tokenDigest,
            Timestamp.from(expiresAt),
            status,
            Timestamp.from(now),
            Timestamp.from(now),
            groupId
        );
        if (dispatch != 1) return false;
        jdbcTemplate.update(
            """
            update modeling_pipeline_run
               set artifact_bundle_checksum = ?,
                   scoped_bundle_checksum = ?,
                   last_modified_date = ?
             where pipeline_run_group_id = ?
               and run_purpose = 'OPERATIONAL_RUN'
               and status = 'QUEUED'
            """,
            projectBundleChecksum,
            projectBundleChecksum,
            Timestamp.from(now),
            groupId
        );
        if (airflowAlreadyOpened) {
            jdbcTemplate.update(
                """
                update modeling_pipeline_run
                   set status = 'RUNNING',
                       started_date = coalesce(started_date, ?),
                       last_modified_date = ?
                 where pipeline_run_group_id = ?
                   and run_purpose = 'OPERATIONAL_RUN'
                   and status = 'QUEUED'
                """,
                Timestamp.from(now),
                Timestamp.from(now),
                groupId
            );
        }
        return true;
    }

    @Transactional
    public boolean refreshRuntimeToken(
        UUID groupId,
        String tokenDigest,
        Instant expiresAt,
        Instant now
    ) {
        return (
            jdbcTemplate.update(
                """
                update modeling_operational_run_dispatch
                   set runtime_token_digest = ?,
                       runtime_token_expires_at = ?,
                       last_modified_at = ?
                 where id = ?
                   and status in ('CLAIMED', 'UNKNOWN')
                   and runtime_consumed_at is null
                   and project_bundle_checksum is not null
                """,
                tokenDigest,
                Timestamp.from(expiresAt),
                Timestamp.from(now),
                groupId
            ) ==
            1
        );
    }

    @Transactional
    public boolean markSubmitted(UUID groupId, Instant now) {
        int updated = jdbcTemplate.update(
            """
            update modeling_operational_run_dispatch
               set status = 'SUBMITTED', last_error_code = null,
                   last_modified_at = ?
             where id = ? and status in ('CLAIMED', 'UNKNOWN')
            """,
            Timestamp.from(now),
            groupId
        );
        if (updated == 1) {
            jdbcTemplate.update(
                """
                update modeling_pipeline_run
                   set status = 'RUNNING',
                       started_date = coalesce(started_date, ?),
                       last_modified_date = ?
                 where pipeline_run_group_id = ?
                   and run_purpose = 'OPERATIONAL_RUN'
                   and status = 'QUEUED'
                """,
                Timestamp.from(now),
                Timestamp.from(now),
                groupId
            );
        }
        return updated == 1;
    }

    @Transactional
    public boolean markUnknown(
        UUID groupId,
        String errorCode,
        Instant now
    ) {
        return (
            jdbcTemplate.update(
                """
                update modeling_operational_run_dispatch
                   set status = 'UNKNOWN', last_error_code = ?,
                       next_attempt_at = ?, last_modified_at = ?
                 where id = ? and status in (
                     'PENDING', 'CLAIMED', 'SUBMITTED', 'UNKNOWN'
                 )
                """,
                required(errorCode, "errorCode"),
                Timestamp.from(now.plusSeconds(30)),
                Timestamp.from(now),
                groupId
            ) ==
            1
        );
    }

    @Transactional
    public Optional<RuntimeRecord> lockRuntimeSpec(String tokenDigest) {
        return jdbcTemplate
            .query(
                """
                with locked_dispatch as (
                    select *
                      from modeling_operational_run_dispatch
                     where runtime_token_digest = ?
                       and status in (
                           'CLAIMED', 'SUBMITTED', 'UNKNOWN'
                       )
                     for update
                )
                select d.id, d.tenant_id, d.binding_id,
                       d.binding_version, d.execution_target_key,
                       d.target_name, d.airflow_dag_id,
                       d.airflow_run_id, d.project_bundle_checksum,
                       d.runtime_token_digest,
                       d.runtime_token_expires_at,
                       d.profile_lease_id, d.runtime_consumed_at,
                       b.environment,
                       string_agg(
                           pr.dbt_selector,
                           ' ' order by pr.model_spec_id
                       ) as selector,
                       (
                           array_agg(
                               pr.id order by pr.model_spec_id, pr.id
                           )
                       )[1] as pipeline_run_id
                  from locked_dispatch d
                  join modeling_plan_execution_binding b
                    on b.id = d.binding_id
                   and b.tenant_id = d.tenant_id
                  join modeling_pipeline_run pr
                    on pr.tenant_id = d.tenant_id
                   and pr.pipeline_run_group_id = d.id
                   and pr.run_purpose = 'OPERATIONAL_RUN'
                 group by
                       d.id, d.tenant_id, d.binding_id,
                       d.binding_version, d.execution_target_key,
                       d.target_name, d.airflow_dag_id,
                       d.airflow_run_id, d.project_bundle_checksum,
                       d.runtime_token_digest,
                       d.runtime_token_expires_at,
                       d.profile_lease_id, d.runtime_consumed_at,
                       b.environment
                """,
                (row, rowNumber) ->
                    new RuntimeRecord(
                        row.getObject("id", UUID.class),
                        row.getString("tenant_id"),
                        row.getObject("binding_id", UUID.class),
                        row.getInt("binding_version"),
                        row.getString("execution_target_key"),
                        row.getString("target_name"),
                        row.getString("airflow_dag_id"),
                        row.getString("airflow_run_id"),
                        row.getString("project_bundle_checksum"),
                        row.getString("runtime_token_digest"),
                        instant(
                            row.getTimestamp(
                                "runtime_token_expires_at"
                            )
                        ),
                        row.getObject("profile_lease_id", UUID.class),
                        instant(
                            row.getTimestamp("runtime_consumed_at")
                        ),
                        row.getString("environment"),
                        row.getString("selector"),
                        row.getObject("pipeline_run_id", UUID.class)
                    ),
                tokenDigest
            )
            .stream()
            .findFirst();
    }

    @Transactional(readOnly = true)
    public EvidenceScope loadEvidenceScope(UUID groupId) {
        List<EvidenceEntry> entries = jdbcTemplate.query(
            """
            select pr.id as pipeline_run_id, pr.model_spec_id,
                   pr.model_revision, pr.model_checksum,
                   pr.implementation_revision,
                   pr.implementation_checksum,
                   i.ownership as implementation_mode,
                   i.dbt_unique_id, e.target_identifier,
                   coalesce((
                       select string_agg(
                           field.value ->> 'name',
                           chr(31) order by field.ordinality
                       )
                         from jsonb_array_elements(
                             coalesce(
                                 sr.snapshot_json -> 'fields',
                                 '[]'::jsonb
                             )
                         ) with ordinality field(value, ordinality)
                   ), '') as expected_columns
              from modeling_pipeline_run pr
              join modeling_operational_run_dispatch d
                on d.tenant_id = pr.tenant_id
               and d.id = pr.pipeline_run_group_id
              join modeling_plan_execution_binding_entry e
                on e.tenant_id = pr.tenant_id
               and e.binding_id = d.binding_id
               and e.model_spec_id = pr.model_spec_id
              join modeling_model_implementation i
                on i.tenant_id = pr.tenant_id
               and i.model_spec_id = pr.model_spec_id
               and i.model_revision = pr.model_revision
               and i.implementation_revision =
                   pr.implementation_revision
               and i.current_implementation_checksum =
                   pr.implementation_checksum
              join modeling_model_spec_revision sr
                on sr.tenant_id = pr.tenant_id
               and sr.model_spec_id = pr.model_spec_id
               and sr.revision = pr.model_revision
               and sr.content_checksum = pr.model_checksum
             where pr.pipeline_run_group_id = ?
               and pr.run_purpose = 'OPERATIONAL_RUN'
             order by pr.model_spec_id, pr.id
            """,
            (row, rowNumber) ->
                new EvidenceEntry(
                    row.getObject("pipeline_run_id", UUID.class),
                    row.getObject("model_spec_id", UUID.class),
                    row.getInt("model_revision"),
                    row.getString("model_checksum"),
                    row.getInt("implementation_revision"),
                    row.getString("implementation_checksum"),
                    row.getString("implementation_mode"),
                    row.getString("dbt_unique_id"),
                    row.getString("target_identifier"),
                    expectedColumns(row.getString("expected_columns"))
                ),
            groupId
        );
        if (entries.isEmpty()) {
            throw failure(
                "MODEL_OPERATIONAL_RUN_NOT_FOUND",
                "Operational run evidence scope does not exist",
                Kind.NOT_FOUND
            );
        }
        List<EvidenceHeader> headers = jdbcTemplate.query(
            """
            select d.id, d.tenant_id, d.binding_id,
                   d.binding_version, d.execution_target_key,
                   d.project_bundle_checksum, d.profile_lease_id,
                   l.credential_version_ref
              from modeling_operational_run_dispatch d
              join modeling_dbt_runtime_profile_lease l
                on l.id = d.profile_lease_id
               and l.tenant_id = d.tenant_id
             where d.id = ?
               and d.status in (
                   'SUBMITTED', 'UNKNOWN', 'FAILED', 'COMPLETED'
               )
            """,
            (row, rowNumber) ->
                new EvidenceHeader(
                    row.getObject("id", UUID.class),
                    row.getString("tenant_id"),
                    row.getObject("binding_id", UUID.class),
                    row.getInt("binding_version"),
                    row.getString("execution_target_key"),
                    row.getString("project_bundle_checksum"),
                    row.getObject("profile_lease_id", UUID.class),
                    row.getString("credential_version_ref")
                ),
            groupId
        );
        if (headers.isEmpty()) {
            throw failure(
                "MODEL_OPERATIONAL_RUN_RUNTIME_INCOMPLETE",
                "Operational run profile evidence is unavailable",
                Kind.CONFLICT
            );
        }
        EvidenceHeader header = headers.getFirst();
        return new EvidenceScope(
            header.groupId(),
            header.tenantId(),
            header.bindingId(),
            header.bindingVersion(),
            header.executionTargetKey(),
            header.projectBundleChecksum(),
            header.profileLeaseId(),
            header.credentialVersionRef(),
            entries
        );
    }

    @Transactional
    public int markDbtSucceeded(
        UUID groupId,
        UUID invocationId,
        Instant now
    ) {
        return jdbcTemplate.update(
            """
            update modeling_pipeline_run
               set status = 'DBT_SUCCEEDED',
                   dbt_invocation_id = ?,
                   started_date = coalesce(started_date, ?),
                   last_modified_date = ?
             where pipeline_run_group_id = ?
               and run_purpose = 'OPERATIONAL_RUN'
               and status in ('RUNNING', 'QUEUED')
            """,
            invocationId,
            Timestamp.from(now),
            Timestamp.from(now),
            groupId
        );
    }

    @Transactional
    public int markRelationsVerified(UUID groupId, Instant now) {
        return jdbcTemplate.update(
            """
            update modeling_pipeline_run
               set status = 'BUILT', last_modified_date = ?
             where pipeline_run_group_id = ?
               and run_purpose = 'OPERATIONAL_RUN'
               and status = 'DBT_SUCCEEDED'
            """,
            Timestamp.from(now),
            groupId
        );
    }

    @Transactional
    public int finalizeSucceeded(UUID groupId, Instant now) {
        Integer expected = jdbcTemplate.queryForObject(
            """
            select count(*)
              from modeling_pipeline_run
             where pipeline_run_group_id = ?
               and run_purpose = 'OPERATIONAL_RUN'
            """,
            Integer.class,
            groupId
        );
        int updated = jdbcTemplate.update(
            """
            update modeling_pipeline_run
               set status = 'SUCCEEDED', finished_date = ?,
                   operational_active_claim_key = null,
                   last_modified_date = ?
             where pipeline_run_group_id = ?
               and run_purpose = 'OPERATIONAL_RUN'
               and status = 'BUILT'
            """,
            Timestamp.from(now),
            Timestamp.from(now),
            groupId
        );
        if (expected == null || expected < 1 || updated != expected) {
            throw failure(
                "MODEL_DBT_FINALIZE_PRECONDITION_FAILED",
                "Operational run cannot be finalized as successful",
                Kind.CONFLICT
            );
        }
        jdbcTemplate.update(
            """
            update modeling_operational_run_dispatch
               set status = 'COMPLETED', last_error_code = null,
                   last_modified_at = ?
             where id = ? and status in ('SUBMITTED', 'UNKNOWN')
            """,
            Timestamp.from(now),
            groupId
        );
        return updated;
    }

    @Transactional
    public int finalizeFailed(
        UUID groupId,
        String errorCode,
        Instant now
    ) {
        int updated = jdbcTemplate.update(
            """
            update modeling_pipeline_run
               set status = 'FAILED', finished_date = ?,
                   message = ?, operational_active_claim_key = null,
                   last_modified_date = ?
             where pipeline_run_group_id = ?
               and run_purpose = 'OPERATIONAL_RUN'
               and status not in ('SUCCEEDED', 'FAILED')
            """,
            Timestamp.from(now),
            required(errorCode, "errorCode"),
            Timestamp.from(now),
            groupId
        );
        jdbcTemplate.update(
            """
            update modeling_operational_run_dispatch
               set status = 'FAILED', last_error_code = ?,
                   last_modified_at = ?
             where id = ? and status <> 'COMPLETED'
            """,
            required(errorCode, "errorCode"),
            Timestamp.from(now),
            groupId
        );
        return updated;
    }

    @Transactional
    public boolean attachRuntimeLease(
        UUID groupId,
        UUID leaseId,
        Instant now
    ) {
        return (
            jdbcTemplate.update(
                """
                update modeling_operational_run_dispatch
                   set profile_lease_id = ?, runtime_consumed_at = ?,
                       last_modified_at = ?
                 where id = ?
                   and status in ('CLAIMED', 'SUBMITTED', 'UNKNOWN')
                   and profile_lease_id is null
                   and runtime_consumed_at is null
                """,
                leaseId,
                Timestamp.from(now),
                Timestamp.from(now),
                groupId
            ) ==
            1
        );
    }

    private BindingScope lockBinding(
        String tenantId,
        UUID planId,
        UUID bindingId
    ) {
        List<BindingScope> rows = jdbcTemplate.query(
            """
            select id, tenant_id, plan_id, environment,
                   execution_target_key, version, dag_id,
                   deployment_status, desired_scope_checksum,
                   desired_deployment_checksum, deployed_checksum
              from modeling_plan_execution_binding
             where tenant_id = ? and id = ?
               and (?::uuid is null or plan_id = ?::uuid)
             for update
            """,
            (row, rowNumber) ->
                new BindingScope(
                    row.getObject("id", UUID.class),
                    row.getString("tenant_id"),
                    row.getObject("plan_id", UUID.class),
                    row.getString("environment"),
                    row.getString("execution_target_key"),
                    row.getInt("version"),
                    row.getString("dag_id"),
                    row.getString("deployment_status"),
                    row.getString("desired_scope_checksum"),
                    row.getString("desired_deployment_checksum"),
                    row.getString("deployed_checksum")
                ),
            tenantId,
            bindingId,
            planId,
            planId
        );
        if (rows.isEmpty()) {
            throw failure(
                "MODEL_PLAN_BINDING_NOT_FOUND",
                "Plan execution binding does not exist",
                Kind.NOT_FOUND
            );
        }
        return rows.getFirst();
    }

    private static String operationalDagId(UUID bindingId) {
        return "dts_plan_" + bindingId.toString().replace("-", "");
    }

    private List<ScopeEntry> loadEntries(BindingScope binding) {
        List<ScopeEntry> entries = jdbcTemplate.query(
            """
            select e.model_spec_id, e.model_revision,
                   s.current_checksum as model_checksum,
                   (event.details_json ->> 'implementationRevision')::int
                       as implementation_revision,
                   event.details_json ->> 'implementationChecksum'
                       as implementation_checksum,
                   e.dbt_unique_id
              from modeling_plan_execution_binding_entry e
              join modeling_model_spec s
                on s.tenant_id = e.tenant_id
               and s.id = e.model_spec_id
               and s.plan_id = ?
               and s.revision = e.model_revision
               and s.status = 'PUBLISHED'
              join modeling_model_lifecycle_event event
                on event.id = e.published_release_id
               and event.model_spec_id = e.model_spec_id
               and event.model_revision = e.model_revision
               and event.status = 'PUBLISHED'
             where e.tenant_id = ? and e.binding_id = ?
             order by e.model_spec_id
            """,
            (row, rowNumber) ->
                new ScopeEntry(
                    row.getObject("model_spec_id", UUID.class),
                    row.getInt("model_revision"),
                    row.getString("model_checksum"),
                    row.getInt("implementation_revision"),
                    row.getString("implementation_checksum"),
                    row.getString("dbt_unique_id")
                ),
            binding.planId(),
            binding.tenantId(),
            binding.id()
        );
        if (
            entries
                .stream()
                .anyMatch(entry ->
                    entry.modelSpecId() == null ||
                    entry.modelRevision() < 1 ||
                    entry.modelChecksum() == null ||
                    !entry.modelChecksum().matches("^[0-9a-f]{64}$") ||
                    entry.implementationRevision() < 1 ||
                    entry.implementationChecksum() == null ||
                    !entry
                        .implementationChecksum()
                        .matches("^[0-9a-f]{64}$") ||
                    entry.dbtUniqueId() == null ||
                    entry.dbtUniqueId().isBlank()
                )
        ) {
            throw failure(
                "MODEL_PLAN_BINDING_SCOPE_INVALID",
                "Published plan scope is incomplete",
                Kind.CONFLICT
            );
        }
        return List.copyOf(entries);
    }

    private Optional<OpenedRun> findOpened(
        String tenantId,
        UUID bindingId,
        String dagRunId
    ) {
        return jdbcTemplate
            .query(
                """
                select d.id, d.tenant_id, b.plan_id, d.binding_id,
                       d.binding_version, d.trigger_type,
                       d.airflow_dag_id, d.airflow_run_id,
                       d.scope_checksum, d.project_bundle_checksum,
                       d.status
                  from modeling_operational_run_dispatch d
                  join modeling_plan_execution_binding b
                    on b.id = d.binding_id
                   and b.tenant_id = d.tenant_id
                 where d.tenant_id = ? and d.binding_id = ?
                   and d.airflow_run_id = ?
                """,
                (row, rowNumber) ->
                    new OpenedRun(
                        row.getObject("id", UUID.class),
                        row.getString("tenant_id"),
                        row.getObject("plan_id", UUID.class),
                        row.getObject("binding_id", UUID.class),
                        row.getInt("binding_version"),
                        row.getString("trigger_type"),
                        row.getString("airflow_dag_id"),
                        row.getString("airflow_run_id"),
                        row.getString("scope_checksum"),
                        row.getString("project_bundle_checksum"),
                        row.getString("status"),
                        false
                    ),
                tenantId,
                bindingId,
                dagRunId
            )
            .stream()
            .findFirst();
    }

    private Optional<OpenedRun> findOpened(UUID groupId) {
        return jdbcTemplate
            .query(
                """
                select d.id, d.tenant_id, b.plan_id, d.binding_id,
                       d.binding_version, d.trigger_type,
                       d.airflow_dag_id, d.airflow_run_id,
                       d.scope_checksum, d.project_bundle_checksum,
                       d.status
                  from modeling_operational_run_dispatch d
                  join modeling_plan_execution_binding b
                    on b.id = d.binding_id
                   and b.tenant_id = d.tenant_id
                 where d.id = ?
                """,
                (row, rowNumber) ->
                    new OpenedRun(
                        row.getObject("id", UUID.class),
                        row.getString("tenant_id"),
                        row.getObject("plan_id", UUID.class),
                        row.getObject("binding_id", UUID.class),
                        row.getInt("binding_version"),
                        row.getString("trigger_type"),
                        row.getString("airflow_dag_id"),
                        row.getString("airflow_run_id"),
                        row.getString("scope_checksum"),
                        row.getString("project_bundle_checksum"),
                        row.getString("status"),
                        true
                    ),
                groupId
            )
            .stream()
            .findFirst();
    }

    private boolean hasActiveRun(String tenantId, UUID bindingId) {
        Boolean active = jdbcTemplate.queryForObject(
            """
            select exists (
                select 1
                  from modeling_pipeline_run
                 where tenant_id = ?
                   and execution_binding_id = ?
                   and run_purpose = 'OPERATIONAL_RUN'
                   and operational_active_claim_key is not null
            )
            """,
            Boolean.class,
            tenantId,
            bindingId
        );
        return Boolean.TRUE.equals(active);
    }

    private static String selector(String dbtUniqueId) {
        String value = required(dbtUniqueId, "dbtUniqueId");
        int index = value.lastIndexOf('.');
        return index < 0 ? value : value.substring(index + 1);
    }

    private static UUID stableUuid(String value) {
        return UUID.nameUUIDFromBytes(
            value.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String digest(String value) {
        try {
            return HexFormat.of()
                .formatHex(
                    MessageDigest.getInstance("SHA-256")
                        .digest(value.getBytes(StandardCharsets.UTF_8))
                );
        } catch (Exception impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String required(String value, String name) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return text;
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static List<String> expectedColumns(String value) {
        if (value == null || value.isBlank()) return List.of();
        List<String> columns = List.of(value.split("\u001f", -1));
        if (
            columns.size() > 1000 ||
            columns.stream().anyMatch(String::isBlank) ||
            columns.stream().distinct().count() != columns.size()
        ) {
            throw failure(
                "MODEL_OPERATIONAL_RUN_SCOPE_INVALID",
                "Pinned model field contract is invalid",
                Kind.CONFLICT
            );
        }
        return columns;
    }

    private static PlanExecutionException failure(
        String code,
        String message,
        Kind kind
    ) {
        return new PlanExecutionException(code, message, kind);
    }

    private record BindingScope(
        UUID id,
        String tenantId,
        UUID planId,
        String environment,
        String executionTargetKey,
        int version,
        String dagId,
        String deploymentStatus,
        String desiredScopeChecksum,
        String desiredDeploymentChecksum,
        String deployedChecksum
    ) {}

    private record ScopeEntry(
        UUID modelSpecId,
        int modelRevision,
        String modelChecksum,
        int implementationRevision,
        String implementationChecksum,
        String dbtUniqueId
    ) {}

    private record ScopeArtifactRow(
        UUID groupId,
        String tenantId,
        UUID bindingId,
        int bindingVersion,
        String executionTargetKey,
        String targetName,
        String airflowDagId,
        String airflowRunId,
        String scopeChecksum,
        String deploymentChecksum,
        String projectBundleChecksum,
        UUID pipelineRunId,
        UUID modelSpecId,
        int modelRevision,
        String modelChecksum,
        int implementationRevision,
        String implementationChecksum,
        String dbtSelector,
        String dbtUniqueId,
        String path,
        String contentChecksum,
        String content
    ) {}

    public record OpenedRun(
        UUID pipelineRunGroupId,
        String tenantId,
        UUID planId,
        UUID bindingId,
        int bindingVersion,
        String triggerType,
        String airflowDagId,
        String airflowRunId,
        String scopeChecksum,
        String projectBundleChecksum,
        String status,
        boolean replayed
    ) {
        OpenedRun asReplay() {
            return new OpenedRun(
                pipelineRunGroupId,
                tenantId,
                planId,
                bindingId,
                bindingVersion,
                triggerType,
                airflowDagId,
                airflowRunId,
                scopeChecksum,
                projectBundleChecksum,
                status,
                true
            );
        }
    }

    public record OperationalScope(
        UUID pipelineRunGroupId,
        String tenantId,
        UUID bindingId,
        int bindingVersion,
        String executionTargetKey,
        String targetName,
        String airflowDagId,
        String airflowRunId,
        String scopeChecksum,
        String deploymentChecksum,
        String projectBundleChecksum,
        List<CandidateArtifactEntry> entries
    ) {}

    public record RuntimeRecord(
        UUID groupId,
        String tenantId,
        UUID bindingId,
        int bindingVersion,
        String executionTargetKey,
        String targetName,
        String airflowDagId,
        String airflowRunId,
        String projectBundleChecksum,
        String runtimeTokenDigest,
        Instant runtimeTokenExpiresAt,
        UUID profileLeaseId,
        Instant runtimeConsumedAt,
        String environment,
        String selector,
        UUID pipelineRunId
    ) {}

    public record PreparedDispatch(
        UUID groupId,
        String projectBundleChecksum,
        String runtimeTokenDigest,
        Instant runtimeTokenExpiresAt,
        String status
    ) {}

    private record EvidenceHeader(
        UUID groupId,
        String tenantId,
        UUID bindingId,
        int bindingVersion,
        String executionTargetKey,
        String projectBundleChecksum,
        UUID profileLeaseId,
        String credentialVersionRef
    ) {}

    public record EvidenceEntry(
        UUID pipelineRunId,
        UUID modelSpecId,
        int modelRevision,
        String modelChecksum,
        int implementationRevision,
        String implementationChecksum,
        String implementationMode,
        String dbtUniqueId,
        String targetIdentifier,
        List<String> expectedColumns
    ) {}

    public record EvidenceScope(
        UUID pipelineRunGroupId,
        String tenantId,
        UUID bindingId,
        int bindingVersion,
        String executionTargetKey,
        String projectBundleChecksum,
        UUID profileLeaseId,
        String credentialVersionRef,
        List<EvidenceEntry> entries
    ) {}
}
