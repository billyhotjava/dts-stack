package com.yuzhi.dts.platform.service.modeling.imports.preview;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyPayloadCodec;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyPlanContract.ApplyPlan;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.Action;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.ConversionMode;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.Kind;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.ModelSpecImportPreviewException;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.PreviewItem;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.PreviewSummary;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.RunStatus;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.LifecycleStatus;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.OnboardingMode;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.WarehousePlanHeader;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JDBC adapter for preview-only state and the read-only target facts used to build it.
 *
 * <p>Mutations are restricted to creating preview state and redacting expired preview payloads in
 * the two {@code modeling_model_spec_import_run*} control-plane tables.
 */
@Repository
public class ModelSpecImportPreviewRepository {

    private static final int MAX_ACTIVE_TENANT_RUNS = 200;
    private static final int MAX_ACTIVE_PLAN_RUNS = 50;
    private static final long MAX_ACTIVE_TENANT_BYTES = 512L * 1024L * 1024L;
    private static final long MAX_ACTIVE_PLAN_BYTES = 128L * 1024L * 1024L;

    private static final Set<String> SENSITIVE_PACKAGE_FIELDS = Set.of(
        "rawSql",
        "rawSqlChecksum",
        "compiledSql",
        "compiledSqlChecksum",
        "config"
    );

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ModelSpecImportApplyPayloadCodec applyPayloadCodec;

    public ModelSpecImportPreviewRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.applyPayloadCodec = new ModelSpecImportApplyPayloadCodec(objectMapper);
    }

    public Optional<PlanSnapshot> findPlan(String tenantId, UUID planId) {
        return jdbcTemplate
            .query(
                """
                select id, tenant_id, code, name, objective, scope, owner_id, owner_department_id,
                       onboarding_mode, lifecycle_status, version, business_scope_version, sources_version
                  from modeling_warehouse_plan
                 where tenant_id = ? and id = ?
                """,
                (row, rowNumber) ->
                    new PlanSnapshot(
                        row.getObject("id", UUID.class),
                        row.getString("tenant_id"),
                        row.getString("code"),
                        row.getString("name"),
                        row.getString("objective"),
                        row.getString("scope"),
                        row.getString("owner_id"),
                        row.getString("owner_department_id"),
                        OnboardingMode.valueOf(row.getString("onboarding_mode")),
                        LifecycleStatus.valueOf(row.getString("lifecycle_status")),
                        row.getInt("version"),
                        row.getInt("business_scope_version"),
                        row.getInt("sources_version")
                    ),
                tenantId,
                planId
            )
            .stream()
            .findFirst();
    }

    public List<DomainBindingSnapshot> findDomainBindings(String tenantId, UUID planId) {
        return jdbcTemplate.query(
            """
            select domain_id, confirmation_status, last_validated_at
              from modeling_warehouse_plan_domain
             where tenant_id = ? and plan_id = ?
             order by domain_id
            """,
            (row, rowNumber) ->
                new DomainBindingSnapshot(
                    row.getObject("domain_id", UUID.class),
                    row.getString("confirmation_status"),
                    instant(row.getTimestamp("last_validated_at"))
                ),
            tenantId,
            planId
        );
    }

    public List<SourceBindingSnapshot> findSourceBindings(String tenantId, UUID planId) {
        return jdbcTemplate.query(
            """
            select id, source_type, source_id, source_version, locator_json::text as locator_json,
                   confirmation_status, resolution_status, last_validated_at
              from modeling_warehouse_plan_source
             where tenant_id = ? and plan_id = ?
             order by source_type, source_id, id
            """,
            (row, rowNumber) ->
                new SourceBindingSnapshot(
                    row.getObject("id", UUID.class),
                    row.getString("source_type"),
                    row.getString("source_id"),
                    row.getString("source_version"),
                    row.getString("locator_json"),
                    row.getString("confirmation_status"),
                    row.getString("resolution_status"),
                    instant(row.getTimestamp("last_validated_at"))
                ),
            tenantId,
            planId
        );
    }

    public Optional<ModelOwnershipSnapshot> findOwnership(String tenantId, String projectKey, String dbtUniqueId) {
        return jdbcTemplate.query(
            """
            select i.id as implementation_id, i.model_spec_id, i.plan_id,
                   i.model_revision, i.model_checksum, i.implementation_revision, i.current_implementation_checksum,
                   i.ownership, i.project_key, i.dbt_unique_id,
                   s.revision as current_revision, s.current_checksum, s.model_type, s.status as model_status,
                   s.dimension_definition_id, s.dimension_definition_revision,
                   sql_artifact.content_checksum as effective_sql_checksum
              from modeling_model_implementation i
              join modeling_model_spec s
                on s.tenant_id = i.tenant_id and s.id = i.model_spec_id
              left join lateral (
                    select a.content_checksum
                      from modeling_dbt_artifact a
                     where a.model_spec_id = i.model_spec_id
                       and a.revision = s.revision
                       and a.model_checksum = s.current_checksum
                       and a.implementation_revision = i.implementation_revision
                       and a.ownership = i.ownership
                       and a.project_key = i.project_key
                       and a.dbt_unique_id = i.dbt_unique_id
                       and a.node_kind = 'MODEL'
                       and a.artifact_type = 'SQL'
                  and a.status in ('COMPILED', 'IMPORTED')
                     order by a.last_modified_date desc nulls last, a.id desc
                     limit 1
              ) sql_artifact on true
             where i.tenant_id = ? and i.project_key = ? and i.dbt_unique_id = ?
            """,
            (row, rowNumber) ->
                new ModelOwnershipSnapshot(
                    row.getObject("implementation_id", UUID.class),
                    row.getObject("model_spec_id", UUID.class),
                    row.getObject("plan_id", UUID.class),
                    row.getInt("model_revision"),
                    row.getString("model_checksum"),
                    row.getInt("implementation_revision"),
                    row.getString("current_implementation_checksum"),
                    row.getString("ownership"),
                    row.getString("project_key"),
                    row.getString("dbt_unique_id"),
                    row.getInt("current_revision"),
                    row.getString("current_checksum"),
                    row.getString("model_type"),
                    row.getString("model_status"),
                    row.getObject("dimension_definition_id", UUID.class),
                    (Integer) row.getObject("dimension_definition_revision"),
                    row.getString("effective_sql_checksum")
                ),
            tenantId,
            projectKey,
            dbtUniqueId
        )
            .stream()
            .findFirst();
    }

    @Transactional
    public void save(PersistedRun run, List<PersistedItem> items) {
        requireValidApplyPayload(run);
        requireValidApplyPlan(run);
        lockPreviewCapacity(run.tenantId(), run.planId());
        requirePreviewCapacity(run.tenantId(), run.planId(), estimatedPayloadBytes(run, items));
        jdbcTemplate.update(
            """
            insert into modeling_model_spec_import_run (
                id, tenant_id, plan_id, package_checksum, package_schema_version,
                package_json, request_json, context_snapshot_json, apply_payload_json, apply_payload_checksum,
                apply_plan_json, apply_plan_checksum, preview_hash, status,
                summary_json, expires_at, created_by, created_date, last_modified_by, last_modified_date
            ) values (
                ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), ?,
                cast(? as jsonb), ?, ?, ?,
                cast(? as jsonb), ?, ?, ?, ?, ?
            )
            """,
            run.id(),
            run.tenantId(),
            run.planId(),
            run.packageChecksum(),
            run.packageSchemaVersion(),
            sanitizePackagePayload(run.packageJson()),
            run.requestJson(),
            run.contextSnapshotJson(),
            run.applyPayloadJson(),
            run.applyPayloadChecksum(),
            run.applyPlanJson(),
            run.applyPlanChecksum(),
            run.previewHash(),
            run.status().name(),
            run.summaryJson(),
            Timestamp.from(run.expiresAt()),
            run.actorId(),
            Timestamp.from(run.createdAt()),
            run.actorId(),
            Timestamp.from(run.createdAt())
        );
        for (PersistedItem item : items) {
            jdbcTemplate.update(
                """
                insert into modeling_model_spec_import_run_item (
                    id, run_id, seq, dbt_unique_id, node_type, canonical, action, conversion_mode,
                    package_checksum, model_spec_checksum, current_model_spec_id, current_revision,
                    proposed_model_spec_json, proposed_implementation_json, dependency_json,
                    source_snapshot_json, issues_json, created_date, last_modified_date
                ) values (
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb),
                    cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), ?, ?
                )
                """,
                item.id(),
                run.id(),
                item.seq(),
                item.dbtUniqueId(),
                item.nodeType(),
                item.canonical(),
                item.action().name(),
                item.conversionMode().name(),
                item.packageChecksum(),
                item.modelSpecChecksum(),
                item.currentModelSpecId(),
                item.currentRevision(),
                item.proposedModelSpecJson(),
                item.proposedImplementationJson(),
                item.dependencyJson(),
                item.sourceSnapshotJson(),
                item.issuesJson(),
                Timestamp.from(run.createdAt()),
                Timestamp.from(run.createdAt())
            );
        }
    }

    private void lockPreviewCapacity(String tenantId, UUID planId) {
        lockPreviewCapacityKey("model-import-preview:tenant:" + tenantId);
        lockPreviewCapacityKey("model-import-preview:plan:" + tenantId + ":" + planId);
    }

    private void lockPreviewCapacityKey(String key) {
        jdbcTemplate.query(
            "select pg_advisory_xact_lock(hashtextextended(?, 0))",
            (org.springframework.jdbc.core.ResultSetExtractor<Void>) resultSet -> {
                resultSet.next();
                return null;
            },
            key
        );
    }

    @Transactional(readOnly = true)
    public void requirePreviewCapacity(String tenantId, UUID planId, long incomingBytes) {
        if (tenantId == null || planId == null || incomingBytes < 0L) {
            throw new IllegalArgumentException("Preview capacity scope is required");
        }
        if (incomingBytes > MAX_ACTIVE_PLAN_BYTES) {
            throw previewQuotaExceeded("singlePreviewBytes", incomingBytes, MAX_ACTIVE_PLAN_BYTES);
        }
        StorageUsage tenantUsage = storageUsage(
            """
            select count(*) as run_count,
                   coalesce(sum(
                       pg_column_size(run.package_json) +
                       pg_column_size(run.request_json) +
                       pg_column_size(run.context_snapshot_json) +
                       pg_column_size(run.apply_payload_json) +
                       pg_column_size(run.apply_plan_json) +
                       pg_column_size(run.summary_json) +
                       coalesce(items.item_bytes, 0)
                   ), 0) as payload_bytes
              from modeling_model_spec_import_run run
              left join lateral (
                    select coalesce(sum(
                        coalesce(pg_column_size(item.proposed_model_spec_json), 0) +
                        coalesce(pg_column_size(item.proposed_implementation_json), 0) +
                        pg_column_size(item.dependency_json) +
                        pg_column_size(item.source_snapshot_json) +
                        pg_column_size(item.issues_json)
                    ), 0) as item_bytes
                      from modeling_model_spec_import_run_item item
                     where item.run_id = run.id
              ) items on true
             where run.tenant_id = ?
               and run.expires_at > CURRENT_TIMESTAMP
               and run.payload_redacted_at is null
            """,
            tenantId
        );
        requireWithinQuota(
            "tenant",
            tenantUsage,
            incomingBytes,
            MAX_ACTIVE_TENANT_RUNS,
            MAX_ACTIVE_TENANT_BYTES
        );
        StorageUsage planUsage = storageUsage(
            """
            select count(*) as run_count,
                   coalesce(sum(
                       pg_column_size(run.package_json) +
                       pg_column_size(run.request_json) +
                       pg_column_size(run.context_snapshot_json) +
                       pg_column_size(run.apply_payload_json) +
                       pg_column_size(run.apply_plan_json) +
                       pg_column_size(run.summary_json) +
                       coalesce(items.item_bytes, 0)
                   ), 0) as payload_bytes
              from modeling_model_spec_import_run run
              left join lateral (
                    select coalesce(sum(
                        coalesce(pg_column_size(item.proposed_model_spec_json), 0) +
                        coalesce(pg_column_size(item.proposed_implementation_json), 0) +
                        pg_column_size(item.dependency_json) +
                        pg_column_size(item.source_snapshot_json) +
                        pg_column_size(item.issues_json)
                    ), 0) as item_bytes
                      from modeling_model_spec_import_run_item item
                     where item.run_id = run.id
              ) items on true
             where run.tenant_id = ?
               and run.plan_id = ?
               and run.expires_at > CURRENT_TIMESTAMP
               and run.payload_redacted_at is null
            """,
            tenantId,
            planId
        );
        requireWithinQuota("plan", planUsage, incomingBytes, MAX_ACTIVE_PLAN_RUNS, MAX_ACTIVE_PLAN_BYTES);
    }

    private StorageUsage storageUsage(String sql, Object... arguments) {
        StorageUsage usage = jdbcTemplate.queryForObject(
            sql,
            (row, rowNumber) -> new StorageUsage(row.getInt("run_count"), row.getLong("payload_bytes")),
            arguments
        );
        return usage == null ? StorageUsage.EMPTY : usage;
    }

    private static void requireWithinQuota(
        String scope,
        StorageUsage usage,
        long incomingBytes,
        int maxRuns,
        long maxBytes
    ) {
        if (usage.runCount() >= maxRuns) {
            throw previewQuotaExceeded(scope + "ActiveRuns", usage.runCount(), maxRuns);
        }
        if (usage.payloadBytes() + incomingBytes > maxBytes) {
            throw previewQuotaExceeded(scope + "PayloadBytes", usage.payloadBytes() + incomingBytes, maxBytes);
        }
    }

    private static ModelSpecImportPreviewException previewQuotaExceeded(String metric, long actual, long limit) {
        return new ModelSpecImportPreviewException(
            "MODEL_IMPORT_PREVIEW_QUOTA_EXCEEDED",
            "Active model import preview storage quota is exceeded",
            Kind.RATE_LIMITED,
            java.util.Map.of("metric", metric, "actual", actual, "limit", limit)
        );
    }

    private static long estimatedPayloadBytes(PersistedRun run, List<PersistedItem> items) {
        long total =
            utf8Bytes(run.packageJson()) +
            utf8Bytes(run.requestJson()) +
            utf8Bytes(run.contextSnapshotJson()) +
            utf8Bytes(run.applyPayloadJson()) +
            utf8Bytes(run.applyPlanJson()) +
            utf8Bytes(run.summaryJson());
        for (PersistedItem item : items) {
            total +=
                utf8Bytes(item.proposedModelSpecJson()) +
                utf8Bytes(item.proposedImplementationJson()) +
                utf8Bytes(item.dependencyJson()) +
                utf8Bytes(item.sourceSnapshotJson()) +
                utf8Bytes(item.issuesJson());
        }
        return total;
    }

    private static long utf8Bytes(String value) {
        return value == null ? 0L : value.getBytes(StandardCharsets.UTF_8).length;
    }

    /** Redacts expired payloads while retaining the run head and aggregate audit summary. */
    @Transactional
    public int redactExpiredPayloads(Instant now) {
        if (now == null) {
            return 0;
        }
        Timestamp timestamp = Timestamp.from(now);
        jdbcTemplate.update(
            """
            update modeling_model_spec_import_run_item item
               set proposed_model_spec_json = null,
                   proposed_implementation_json = null,
                   dependency_json = cast('{}' as jsonb),
                   source_snapshot_json = cast('[]' as jsonb),
                   issues_json = cast('[]' as jsonb),
                   last_modified_date = ?
             where exists (
                   select 1
                     from modeling_model_spec_import_run run
                    where run.id = item.run_id
                      and run.expires_at <= ?
                      and run.payload_redacted_at is null
             )
            """,
            timestamp,
            timestamp
        );
        return jdbcTemplate.update(
            """
            update modeling_model_spec_import_run
               set package_json = cast('{}' as jsonb),
                   request_json = cast('{}' as jsonb),
                   context_snapshot_json = cast('{}' as jsonb),
                   apply_payload_json = cast('{}' as jsonb),
                   apply_plan_json = cast('{}' as jsonb),
                   payload_redacted_at = ?,
                   last_modified_date = ?
             where expires_at <= ? and payload_redacted_at is null
            """,
            timestamp,
            timestamp,
            timestamp
        );
    }

    private String sanitizePackagePayload(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("Model import package payload must be a JSON object");
            }
            stripSensitivePackageFields(root);
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Model import package payload cannot be sanitized", exception);
        }
    }

    private void requireValidApplyPayload(PersistedRun run) {
        if (run == null || !applyPayloadCodec.isValid(run.applyPayloadJson(), run.applyPayloadChecksum())) {
            throw new IllegalArgumentException("Model import apply payload is missing, modified, or contains forbidden fields");
        }
    }

    private void requireValidApplyPlan(PersistedRun run) {
        try {
            JsonNode applyPlan = objectMapper.readTree(run.applyPlanJson());
            if (
                applyPlan == null ||
                !Objects.equals(run.applyPlanChecksum(), applyPayloadCodec.checksum(applyPlan))
            ) {
                throw new IllegalArgumentException("Model import apply plan checksum is invalid");
            }
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Model import apply plan cannot be validated", exception);
        }
    }

    private void stripSensitivePackageFields(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            SENSITIVE_PACKAGE_FIELDS.forEach(object::remove);
            object.elements().forEachRemaining(this::stripSensitivePackageFields);
        } else if (node.isArray()) {
            node.forEach(this::stripSensitivePackageFields);
        }
    }

    @Transactional(readOnly = true)
    public Optional<StoredRun> findRun(String tenantId, UUID runId) {
        Optional<StoredRunHead> head = jdbcTemplate
            .query(
                """
                select id, plan_id, preview_hash, apply_payload_checksum, status, summary_json::text as summary_json, expires_at
                  from modeling_model_spec_import_run
                 where tenant_id = ? and id = ?
                """,
                (row, rowNumber) ->
                    new StoredRunHead(
                        row.getObject("id", UUID.class),
                        row.getObject("plan_id", UUID.class),
                        row.getString("preview_hash"),
                        row.getString("apply_payload_checksum"),
                        RunStatus.valueOf(row.getString("status")),
                        row.getString("summary_json"),
                        instant(row.getTimestamp("expires_at"))
                    ),
                tenantId,
                runId
            )
            .stream()
            .findFirst();
        if (head.isEmpty()) {
            return Optional.empty();
        }
        List<PreviewItem> items = jdbcTemplate.query(
            """
            select dbt_unique_id, action, conversion_mode,
                   proposed_model_spec_json::text as proposed_model_spec_json,
                   proposed_implementation_json::text as proposed_implementation_json,
                   issues_json::text as issues_json
              from modeling_model_spec_import_run_item
             where run_id = ?
             order by seq, dbt_unique_id
            """,
            (row, rowNumber) ->
                new PreviewItem(
                    row.getString("dbt_unique_id"),
                    Action.valueOf(row.getString("action")),
                    ConversionMode.valueOf(row.getString("conversion_mode")),
                    readTree(row.getString("proposed_model_spec_json")),
                    readTree(row.getString("proposed_implementation_json")),
                    readIssues(row.getString("issues_json"))
                ),
            runId
        );
        StoredRunHead value = head.orElseThrow();
        return Optional.of(
            new StoredRun(
                value.id(),
                value.planId(),
                value.previewHash(),
                value.applyPayloadChecksum(),
                value.status(),
                value.expiresAt(),
                readSummary(value.summaryJson()),
                items
            )
        );
    }

    private PreviewSummary readSummary(String json) {
        try {
            return objectMapper.readValue(json, PreviewSummary.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored model import preview summary is invalid", exception);
        }
    }

    private List<ModelSpecImportPreviewContract.PreviewIssue> readIssues(String json) {
        try {
            return objectMapper.readerForListOf(ModelSpecImportPreviewContract.PreviewIssue.class).readValue(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored model import preview issues are invalid", exception);
        }
    }

    private com.fasterxml.jackson.databind.JsonNode readTree(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored model import preview projection is invalid", exception);
        }
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    /** Apply reads are unavailable after TTL redaction, even though the run audit head remains. */
    @Transactional(readOnly = true)
    public Optional<StoredApplyPlan> findApplyPlan(String tenantId, UUID runId) {
        return jdbcTemplate
            .query(
                """
                select id, plan_id, preview_hash, status, expires_at, package_checksum, apply_payload_checksum,
                       apply_plan_checksum,
                       request_json::text as request_json, context_snapshot_json::text as context_snapshot_json,
                       apply_payload_json::text as apply_payload_json, apply_plan_json::text as apply_plan_json
                  from modeling_model_spec_import_run
                 where tenant_id = ? and id = ? and payload_redacted_at is null and expires_at > CURRENT_TIMESTAMP
                """,
                (row, rowNumber) ->
                    new StoredApplyPlan(
                        row.getObject("id", UUID.class),
                        row.getObject("plan_id", UUID.class),
                        row.getString("preview_hash"),
                        RunStatus.valueOf(row.getString("status")),
                        instant(row.getTimestamp("expires_at")),
                        row.getString("package_checksum"),
                        row.getString("apply_payload_checksum"),
                        row.getString("apply_plan_checksum"),
                        row.getString("request_json"),
                        row.getString("context_snapshot_json"),
                        row.getString("apply_payload_json"),
                        readApplyPlan(row.getString("apply_plan_json"))
                    ),
                tenantId,
                runId
            )
            .stream()
            .filter(value -> applyPayloadCodec.isValid(value.applyPayloadJson(), value.applyPayloadChecksum()))
            .filter(value ->
                value.applyPlan() != null &&
                Objects.equals(value.runId(), value.applyPlan().runId()) &&
                Objects.equals(value.planId(), value.applyPlan().planId()) &&
                Objects.equals(value.previewHash(), value.applyPlan().previewHash()) &&
                Objects.equals(value.packageChecksum(), value.applyPlan().packageChecksum()) &&
                Objects.equals(value.applyPayloadChecksum(), value.applyPlan().applyPayloadChecksum()) &&
                Objects.equals(
                    value.applyPlanChecksum(),
                    applyPayloadCodec.checksum(objectMapper.valueToTree(value.applyPlan()))
                )
            )
            .findFirst();
    }

    private ApplyPlan readApplyPlan(String json) {
        try {
            return objectMapper.readValue(json, ApplyPlan.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored model import apply plan is invalid", exception);
        }
    }

    public record PlanSnapshot(
        UUID id,
        String tenantId,
        String code,
        String name,
        String objective,
        String scope,
        String ownerId,
        String ownerDepartmentId,
        OnboardingMode onboardingMode,
        LifecycleStatus lifecycleStatus,
        int version,
        int businessScopeVersion,
        int sourcesVersion
    ) {
        public WarehousePlanHeader header() {
            return new WarehousePlanHeader(
                id,
                tenantId,
                code,
                name,
                objective,
                scope,
                ownerId,
                ownerDepartmentId,
                onboardingMode,
                lifecycleStatus,
                version
            );
        }
    }

    public record DomainBindingSnapshot(UUID domainId, String confirmationStatus, Instant lastValidatedAt) {}

    public record SourceBindingSnapshot(
        UUID id,
        String sourceType,
        String sourceId,
        String sourceVersion,
        String locatorJson,
        String confirmationStatus,
        String savedResolutionStatus,
        Instant lastValidatedAt
    ) {}

    public record ModelOwnershipSnapshot(
        UUID implementationId,
        UUID modelSpecId,
        UUID planId,
        int implementationModelRevision,
        String implementationModelChecksum,
        int implementationRevision,
        String currentImplementationChecksum,
        String ownership,
        String projectKey,
        String dbtUniqueId,
        int currentRevision,
        String currentChecksum,
        String currentModelType,
        String currentModelStatus,
        UUID currentDimensionDefinitionId,
        Integer currentDimensionDefinitionRevision,
        String effectiveSqlChecksum
    ) {}

    public record PersistedRun(
        UUID id,
        String tenantId,
        UUID planId,
        String packageChecksum,
        String packageSchemaVersion,
        String packageJson,
        String requestJson,
        String contextSnapshotJson,
        String applyPayloadJson,
        String applyPayloadChecksum,
        String applyPlanJson,
        String applyPlanChecksum,
        String previewHash,
        RunStatus status,
        String summaryJson,
        Instant expiresAt,
        String actorId,
        Instant createdAt
    ) {}

    public record PersistedItem(
        UUID id,
        int seq,
        String dbtUniqueId,
        String nodeType,
        boolean canonical,
        Action action,
        ConversionMode conversionMode,
        String packageChecksum,
        String modelSpecChecksum,
        UUID currentModelSpecId,
        Integer currentRevision,
        String proposedModelSpecJson,
        String proposedImplementationJson,
        String dependencyJson,
        String sourceSnapshotJson,
        String issuesJson
    ) {}

    private record StoredRunHead(
        UUID id,
        UUID planId,
        String previewHash,
        String applyPayloadChecksum,
        RunStatus status,
        String summaryJson,
        Instant expiresAt
    ) {}

    public record StoredRun(
        UUID id,
        UUID planId,
        String previewHash,
        String applyPayloadChecksum,
        RunStatus status,
        Instant expiresAt,
        PreviewSummary summary,
        List<PreviewItem> items
    ) {}

    public record StoredApplyPlan(
        UUID runId,
        UUID planId,
        String previewHash,
        RunStatus status,
        Instant expiresAt,
        String packageChecksum,
        String applyPayloadChecksum,
        String applyPlanChecksum,
        String requestJson,
        String contextSnapshotJson,
        String applyPayloadJson,
        ApplyPlan applyPlan
    ) {}

    private record StorageUsage(int runCount, long payloadBytes) {

        private static final StorageUsage EMPTY = new StorageUsage(0, 0L);
    }
}
