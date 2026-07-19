package com.yuzhi.dts.platform.service.modeling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PostgreSQL application service for modeling vNext.
 *
 * <p>The service owns persistence and optimistic revision checks. The pure compiler,
 * validator and runtime gates remain side-effect free so they can be reused by tests
 * and by future Airflow adapters.</p>
 */
@Service
@Transactional
public class ModelingVNextApplicationService {

    private static final String DEFAULT_OWNER = "modeling-vnext";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ModelingRuntimeSubmissionService runtimeSubmissionService;
    private final ModelSpecApplicationService canonicalModelSpecService;

    public ModelingVNextApplicationService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this(jdbcTemplate, objectMapper, null, null);
    }

    public ModelingVNextApplicationService(
        JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper,
        ModelingRuntimeSubmissionService runtimeSubmissionService
    ) {
        this(jdbcTemplate, objectMapper, runtimeSubmissionService, null);
    }

    @Autowired
    public ModelingVNextApplicationService(
        JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper,
        ModelingRuntimeSubmissionService runtimeSubmissionService,
        ModelSpecApplicationService canonicalModelSpecService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.runtimeSubmissionService = runtimeSubmissionService;
        this.canonicalModelSpecService = canonicalModelSpecService;
    }

    public record WarehousePlan(
        String id,
        String planId,
        String domainId,
        String processId,
        String layer,
        String modelingMode,
        String targetGrain,
        String status,
        int revision
    ) {}

    public record Artifact(
        String modelSpecId,
        String planId,
        int revision,
        String modelChecksum,
        String ownership,
        String artifactType,
        String path,
        String checksum,
        String status
    ) {
        public Artifact(String artifactType, String path, String checksum, String status) {
            this(null, null, 0, null, null, artifactType, path, checksum, status);
        }
    }

    public record CompileResult(String modelSpecId, int revision, String status, List<Artifact> artifacts, List<String> issues) {}

    public record DriftView(String modelSpecId, String status, List<String> issues) {}

    public record ReleaseGateView(String modelSpecId, boolean publishable, String status, List<String> blockers) {}

    public record RunView(
        String id,
        String modelSpecId,
        int revision,
        String state,
        String sourceBatchId,
        String addaxTaskId,
        String airflowDagId,
        String airflowRunId,
        String dbtRunId,
        String dbtSelector,
        String targetTable,
        String message,
        String logUrl,
        String planId,
        String modelChecksum,
        String repairPath
    ) {
        public RunView(
            String id,
            String modelSpecId,
            int revision,
            String state,
            String sourceBatchId,
            String addaxTaskId,
            String airflowDagId,
            String airflowRunId,
            String dbtRunId,
            String dbtSelector,
            String targetTable,
            String message,
            String logUrl
        ) {
            this(
                id,
                modelSpecId,
                revision,
                state,
                sourceBatchId,
                addaxTaskId,
                airflowDagId,
                airflowRunId,
                dbtRunId,
                dbtSelector,
                targetTable,
                message,
                logUrl,
                null,
                null,
                null
            );
        }
    }

    private record ModelIdentity(
        UUID id,
        UUID planId,
        int revision,
        String modelChecksum,
        String implementationMode,
        String status
    ) {}

    public record LineageView(
        String modelSpecId,
        List<Map<String, String>> nodes,
        List<Map<String, String>> edges
    ) {}

    public record DomainIssue(String code, String message) {}

    public static class DomainException extends IllegalArgumentException {

        private final String code;

        public DomainException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    @Transactional(readOnly = true)
    public List<ModelingVNextContract.BusinessObject> listBusinessObjects(String tenantId, String processId, String status) {
        StringBuilder sql = new StringBuilder(
            "select id, code, name, description, object_kind, process_id, business_key, grain_statement, source_refs, status, implementation_mode from modeling_business_object where tenant_id = ?"
        );
        List<Object> args = new ArrayList<>();
        args.add(normalizeTenant(tenantId));
        if (notBlank(processId)) {
            sql.append(" and process_id = ?");
            args.add(processId);
        }
        if (notBlank(status)) {
            sql.append(" and status = ?");
            args.add(status);
        }
        sql.append(" order by code");
        return jdbcTemplate.query(sql.toString(), args.toArray(), businessObjectRowMapper());
    }

    public ModelingVNextContract.BusinessObject saveBusinessObject(
        String tenantId,
        ModelingVNextContract.BusinessObject object,
        int revision,
        String idempotencyKey
    ) {
        ModelingApiContract.requireValidWriteRequest(new ModelingApiContract.WriteEnvelope<>(object, revision, idempotencyKey));
        List<ModelingDomainValidator.Issue> issues = ModelingDomainValidator.validateBusinessObject(object, tenantId);
        if (!issues.isEmpty()) throw new DomainException(issues.getFirst().code(), issues.getFirst().message());
        String tenant = normalizeTenant(tenantId);
        UUID id = parseOrGenerate(object.id());
        int storedVersion = findVersion("modeling_business_object", tenant, id);
        if (storedVersion > 0 && storedVersion != revision) throw new DomainException("MODEL_REVISION_CONFLICT", "业务对象版本已变化，请刷新后重试");
        Instant now = Instant.now();
        String grain = writeJson(object.grain());
        String businessKey = writeJson(object.businessKey());
        String sourceRefs = writeJson(object.sourceRefs());
        if (storedVersion == 0) {
            jdbcTemplate.update(
                "insert into modeling_business_object (id, tenant_id, owner, code, name, description, object_kind, process_id, business_key, grain_statement, source_refs, implementation_mode, status, version, created_date, last_modified_date) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, tenant, DEFAULT_OWNER, object.code(), object.name(), object.description(), object.objectKind().name(), object.processId(), businessKey, grain, sourceRefs,
                enumName(object.implementationMode()), defaultIfBlank(object.status(), "DRAFT"), revision, Timestamp.from(now), Timestamp.from(now)
            );
        } else {
            jdbcTemplate.update(
                "update modeling_business_object set code = ?, name = ?, description = ?, object_kind = ?, process_id = ?, business_key = ?, grain_statement = ?, source_refs = ?, implementation_mode = ?, status = ?, version = ?, last_modified_date = ? where id = ? and tenant_id = ? and version = ?",
                object.code(), object.name(), object.description(), object.objectKind().name(), object.processId(), businessKey, grain, sourceRefs,
                enumName(object.implementationMode()), defaultIfBlank(object.status(), "DRAFT"), revision, Timestamp.from(now), id, tenant, revision
            );
        }
        return withId(object, id.toString());
    }

    @Transactional(readOnly = true)
    public List<WarehousePlan> listPlans(String tenantId, String processId, String layer) {
        StringBuilder sql = new StringBuilder(
            "select id, domain_id, process_id, layer, modeling_mode, target_grain, status, version from modeling_warehouse_plan where tenant_id = ?"
        );
        List<Object> args = new ArrayList<>();
        args.add(normalizeTenant(tenantId));
        if (notBlank(processId)) {
            sql.append(" and process_id = ?");
            args.add(processId);
        }
        if (notBlank(layer)) {
            sql.append(" and layer = ?");
            args.add(layer);
        }
        sql.append(" order by process_id nulls last, layer nulls last, id");
        return jdbcTemplate.query(sql.toString(), args.toArray(), (rs, row) -> new WarehousePlan(
            rs.getString("id"), rs.getString("id"), rs.getString("domain_id"), rs.getString("process_id"),
            rs.getString("layer"), rs.getString("modeling_mode"), rs.getString("target_grain"), rs.getString("status"),
            rs.getInt("version")
        ));
    }

    public WarehousePlan savePlan(String tenantId, WarehousePlan plan, int revision, String idempotencyKey) {
        ModelingApiContract.requireValidWriteRequest(new ModelingApiContract.WriteEnvelope<>(plan, revision, idempotencyKey));
        if (plan == null || !notBlank(plan.processId())) throw new DomainException("MODEL_PROCESS_REQUIRED", "规划必须绑定业务过程");
        if (!notBlank(plan.layer())) throw new DomainException("MODEL_LAYER_INVALID", "规划层级不能为空");
        String tenant = normalizeTenant(tenantId);
        UUID id = parseOrGenerate(plan.id());
        int storedVersion = findVersion("modeling_warehouse_plan", tenant, id);
        if (storedVersion > 0 && storedVersion != revision) throw new DomainException("MODEL_REVISION_CONFLICT", "规划版本已变化，请刷新后重试");
        String modelingMode = defaultIfBlank(plan.modelingMode(), "DESIGNER_GENERATED");
        String status = defaultIfBlank(plan.status(), "DRAFT");
        Timestamp now = Timestamp.from(Instant.now());
        if (storedVersion == 0) {
            jdbcTemplate.update(
                """
                insert into modeling_warehouse_plan (
                    id, tenant_id, owner, code, name, objective, scope, owner_id, onboarding_mode, lifecycle_status,
                    domain_id, process_id, layer, modeling_mode, target_grain, status, version,
                    business_scope_version, sources_version, source_mappings_version, policy_version,
                    created_date, last_modified_date
                ) values (?, ?, ?, ?, ?, ?, ?, ?, 'BUSINESS_FIRST', 'DRAFT', ?, ?, ?, ?, ?, ?, ?, 1, 1, 1, 1, ?, ?)
                """,
                id,
                tenant,
                DEFAULT_OWNER,
                "legacy_vnext_" + id.toString().replace("-", ""),
                legacyPlanName(plan),
                "Migrated through the modeling vNext compatibility route",
                plan.targetGrain(),
                DEFAULT_OWNER,
                nullableUuid(plan.domainId()),
                plan.processId(),
                plan.layer(),
                modelingMode,
                plan.targetGrain(),
                status,
                revision,
                now,
                now
            );
        } else {
            jdbcTemplate.update(
                """
                update modeling_warehouse_plan
                   set domain_id = ?, process_id = ?, layer = ?, modeling_mode = ?, target_grain = ?, status = ?,
                       name = ?, scope = ?, version = ?, last_modified_date = ?
                 where id = ? and tenant_id = ? and version = ?
                """,
                nullableUuid(plan.domainId()),
                plan.processId(),
                plan.layer(),
                modelingMode,
                plan.targetGrain(),
                status,
                legacyPlanName(plan),
                plan.targetGrain(),
                revision,
                now,
                id,
                tenant,
                revision
            );
        }
        return new WarehousePlan(
            id.toString(),
            id.toString(),
            plan.domainId(),
            plan.processId(),
            plan.layer(),
            modelingMode,
            plan.targetGrain(),
            status,
            revision
        );
    }

    private static String legacyPlanName(WarehousePlan plan) {
        return plan.processId() + " / " + plan.layer();
    }

    @Transactional(readOnly = true)
    public List<ModelingVNextContract.ModelSpec> listModelSpecs(String tenantId, String objectId, String processId, String layer) {
        String tenant = normalizeTenant(tenantId);
        StringBuilder sql = new StringBuilder("select spec_json from modeling_model_spec_revision r join modeling_model_spec s on s.id = r.model_spec_id where s.tenant_id = ? and r.revision = s.revision and s.contract_version = 1");
        List<Object> args = new ArrayList<>();
        args.add(tenant);
        if (notBlank(objectId)) {
            sql.append(" and s.object_id = ?");
            args.add(externalUuid(objectId));
        }
        if (notBlank(processId)) {
            sql.append(" and s.process_id = ?");
            args.add(processId);
        }
        if (notBlank(layer)) {
            sql.append(" and s.layer = ?");
            args.add(layer);
        }
        sql.append(" order by s.name");
        List<ModelingVNextContract.ModelSpec> models = new ArrayList<>(
            jdbcTemplate.query(
                sql.toString(),
                args.toArray(),
                (rs, row) -> readJson(rs.getString("spec_json"), ModelingVNextContract.ModelSpec.class)
            )
        );
        if (
            canonicalModelSpecService != null &&
            canonicalModelSpecService.canonicalReadEnabled() &&
            !notBlank(objectId)
        ) {
            ModelSpecContract.Layer canonicalLayer = null;
            if (notBlank(layer)) {
                try {
                    canonicalLayer = ModelSpecContract.Layer.valueOf(layer);
                } catch (IllegalArgumentException ignored) {
                    return List.copyOf(models);
                }
            }
            canonicalModelSpecService
                .list(tenant, null, null, null, canonicalLayer)
                .stream()
                .filter(view -> view.contractVersion() == ModelSpecContract.CONTRACT_VERSION)
                .filter(view -> !notBlank(processId) || processId.equals(view.businessActivityRef()))
                .map(view -> projectCanonical(tenant, view))
                .forEach(models::add);
        }
        models.sort(Comparator.comparing(ModelingVNextContract.ModelSpec::name, Comparator.nullsLast(String::compareTo)));
        return List.copyOf(models);
    }

    public ModelingVNextContract.ModelSpec saveModelSpec(
        String tenantId,
        ModelingVNextContract.ModelSpec model,
        int revision,
        String idempotencyKey
    ) {
        ModelingApiContract.requireValidWriteRequest(new ModelingApiContract.WriteEnvelope<>(model, revision, idempotencyKey));
        ModelingVNextContract.BusinessObject object = findBusinessObject(tenantId, model == null ? null : model.objectId());
        if (model != null && object != null && !externalUuid(model.objectId()).equals(externalUuid(object.id()))) {
            throw new DomainException("OBJECT_MISMATCH", "模型只能关联当前业务对象");
        }
        ModelingVNextContract.BusinessObject validationObject = model != null && object != null ? withId(object, model.objectId()) : object;
        List<ModelingDomainValidator.Issue> issues = ModelingDomainValidator.validateModelSpec(model, validationObject);
        if (!issues.isEmpty()) throw new DomainException(issues.getFirst().code(), issues.getFirst().message());
        String tenant = normalizeTenant(tenantId);
        UUID id = parseOrGenerate(model.id());
        int storedVersion = findVersion("modeling_model_spec", tenant, id);
        if (storedVersion > 0 && storedVersion != revision) throw new DomainException("MODEL_REVISION_CONFLICT", "ModelSpec 版本已变化，请刷新后重试");
        Timestamp now = Timestamp.from(Instant.now());
        String specJson = writeJson(withId(model, id.toString()));
        if (storedVersion == 0) {
            jdbcTemplate.update(
                "insert into modeling_model_spec (id, tenant_id, object_id, process_id, layer, model_type, implementation_mode, name, grain_statement, dimensions, metrics, materialization, status, revision, legacy_ref, version, created_date, last_modified_date, contract_version) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)",
                id, tenant, externalUuid(model.objectId()), model.processId(), model.layer().name(), model.modelType().name(), model.implementationMode().name(), model.name(), writeJson(model.grain()), writeJson(model.dimensions()), writeJson(model.metrics()), model.materialization(), "DRAFT", revision, model.legacyRef(), revision, now, now
            );
        } else {
            int updated = jdbcTemplate.update(
                "update modeling_model_spec set process_id = ?, layer = ?, model_type = ?, implementation_mode = ?, name = ?, grain_statement = ?, dimensions = ?, metrics = ?, materialization = ?, revision = ?, legacy_ref = ?, version = ?, last_modified_date = ? where id = ? and tenant_id = ? and version = ? and contract_version = 1",
                model.processId(), model.layer().name(), model.modelType().name(), model.implementationMode().name(), model.name(), writeJson(model.grain()), writeJson(model.dimensions()), writeJson(model.metrics()), model.materialization(), revision, model.legacyRef(), revision, now, id, tenant, revision
            );
            if (updated == 0) {
                throw new DomainException("MODEL_SPEC_LEGACY_READONLY", "Canonical ModelSpec rows cannot be changed through the vNext writer");
            }
        }
        int revisionWritten = jdbcTemplate.update(
            "insert into modeling_model_spec_revision (id, model_spec_id, revision, tenant_id, contract_version, spec_json, status, content_checksum, created_date, last_modified_date) values (?, ?, ?, ?, 1, ?, ?, ?, ?, ?) on conflict (model_spec_id, revision) do update set spec_json = excluded.spec_json, last_modified_date = excluded.last_modified_date where modeling_model_spec_revision.contract_version = 1",
            UUID.randomUUID(), id, revision, tenant, specJson, "DRAFT", checksum(specJson), now, now
        );
        if (revisionWritten == 0) {
            throw new DomainException("MODEL_SPEC_LEGACY_READONLY", "Canonical ModelSpec revisions are append-only");
        }
        return withId(model, id.toString());
    }

    @Transactional(readOnly = true)
    public List<ModelingVNextContract.ModelSpec> dependencies(String tenantId, String id) {
        String tenant = normalizeTenant(tenantId);
        ModelSpecContract.ModelSpecView canonical = findCanonicalModelSpec(tenant, id);
        if (canonical != null) {
            return canonical
                .dependsOn()
                .stream()
                .map(reference -> canonicalRevision(tenant, reference))
                .map(view -> projectCanonical(tenant, view))
                .toList();
        }
        ModelingVNextContract.ModelSpec model = findModelSpec(tenantId, id);
        if (model == null || model.dependsOn() == null || model.dependsOn().isEmpty()) return List.of();
        return model.dependsOn().stream().map(dep -> findModelSpec(tenantId, dep)).filter(java.util.Objects::nonNull).toList();
    }

    public CompileResult compile(String tenantId, String id, int revision, String idempotencyKey) {
        ModelingApiContract.requireValidWriteRequest(new ModelingApiContract.WriteEnvelope<>(id, revision, idempotencyKey));
        ModelingVNextContract.ModelSpec model = findModelSpec(tenantId, id);
        if (model == null) throw new DomainException("MODEL_OBJECT_NOT_FOUND", "ModelSpec 不存在");
        if (model.revision() != revision) throw new DomainException("MODEL_REVISION_CONFLICT", "ModelSpec revision 不匹配");
        ModelIdentity identity = requireIdentity(tenantId, id, revision, null);
        try {
            ModelingDbtCompiler.CompiledArtifacts artifacts = ModelingDbtCompiler.compile(model);
            List<Artifact> persisted = new ArrayList<>();
            for (Map.Entry<String, String> entry : artifacts.files().entrySet()) {
                String type = artifactType(entry.getKey());
                String path = artifacts.outputDirectory() + "/" + entry.getKey();
                String content = entry.getValue();
                int changed = jdbcTemplate.update(
                    """
                    insert into modeling_dbt_artifact (
                        id, model_spec_id, plan_id, project_key, dbt_unique_id, artifact_type, path,
                        content_checksum, content, status, revision, model_checksum, ownership,
                        idempotency_key, created_date, last_modified_date
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'COMPILED', ?, ?, ?, ?, ?, ?)
                    on conflict (model_spec_id, revision, dbt_unique_id) do update
                       set path = excluded.path, content_checksum = excluded.content_checksum,
                           content = excluded.content, status = excluded.status,
                           model_checksum = excluded.model_checksum, ownership = excluded.ownership,
                           idempotency_key = excluded.idempotency_key,
                           last_modified_date = excluded.last_modified_date
                     where modeling_dbt_artifact.model_checksum = excluded.model_checksum
                    """,
                    UUID.randomUUID(), identity.id(), identity.planId(), "dts-modeling", "model." + model.id() + "." + entry.getKey(),
                    type, path, checksum(content), content, model.revision(), identity.modelChecksum(), identity.implementationMode(),
                    idempotencyKey, Timestamp.from(Instant.now()), Timestamp.from(Instant.now())
                );
                if (changed == 0) {
                    throw new DomainException("MODEL_ARTIFACT_REVISION_CONFLICT", "Artifact ownership or checksum changed");
                }
                persisted.add(new Artifact(
                    identity.id().toString(), nullableString(identity.planId()), identity.revision(), identity.modelChecksum(),
                    identity.implementationMode(), type, path, checksum(content), "COMPILED"
                ));
            }
            return new CompileResult(model.id(), model.revision(), "COMPILED", List.copyOf(persisted), List.of());
        } catch (ModelingDbtCompiler.CompileException exception) {
            return new CompileResult(model.id(), model.revision(), "FAILED", List.of(), List.of(exception.getMessage()));
        }
    }

    public DbtModelingContract.ImportResult importDbt(String tenantId, DbtModelingContract.ManifestImportRequest request) {
        ModelingDbtManifestImporter.ImportSummary summary = ModelingDbtManifestImporter.importModel(request);
        ModelingDbtManifestImporter.ImportedModel model = summary.models().getFirst();
        ModelIdentity identity = requireIdentity(tenantId, request.modelSpecId(), request.revision(), request.modelChecksum());
        if (!"DBT_MANAGED".equals(identity.implementationMode())) {
            throw new DomainException("MODEL_IMPLEMENTATION_OWNERSHIP_MISMATCH", "dbt import requires a DBT_MANAGED ModelSpec");
        }
        Boolean ownedElsewhere = jdbcTemplate.queryForObject(
            """
            select exists (
                select 1 from modeling_dbt_artifact
                 where project_key = ? and dbt_unique_id = ? and model_spec_id <> ?
            )
            """,
            Boolean.class,
            request.projectId(),
            model.uniqueId(),
            identity.id()
        );
        if (Boolean.TRUE.equals(ownedElsewhere)) {
            throw new DomainException("MODEL_IMPLEMENTATION_DBT_CONFLICT", "dbt node is already owned by another ModelSpec");
        }
        Instant now = Instant.now();
        int changed = jdbcTemplate.update(
            """
            insert into modeling_dbt_artifact (
                id, model_spec_id, plan_id, project_key, dbt_unique_id, artifact_type, path,
                content_checksum, content, status, revision, model_checksum, ownership,
                idempotency_key, created_date, last_modified_date
            ) values (?, ?, ?, ?, ?, 'SQL', ?, ?, ?, 'COMPILED', ?, ?, 'DBT_MANAGED', ?, ?, ?)
            on conflict (model_spec_id, revision, dbt_unique_id) do update
               set content_checksum = excluded.content_checksum, content = excluded.content,
                   status = excluded.status, model_checksum = excluded.model_checksum,
                   ownership = excluded.ownership, idempotency_key = excluded.idempotency_key,
                   last_modified_date = excluded.last_modified_date
             where modeling_dbt_artifact.model_checksum = excluded.model_checksum
               and modeling_dbt_artifact.ownership = 'DBT_MANAGED'
            """,
            UUID.randomUUID(), identity.id(), identity.planId(), request.projectId(), model.uniqueId(),
            model.uniqueId() + ".sql", model.contentChecksum(), model.sql(), identity.revision(), identity.modelChecksum(),
            request.idempotencyKey(), Timestamp.from(now), Timestamp.from(now)
        );
        if (changed == 0) {
            throw new DomainException("MODEL_ARTIFACT_REVISION_CONFLICT", "dbt artifact ownership or checksum changed");
        }
        return new DbtModelingContract.ImportResult(identity.id().toString(), model.uniqueId(), "COMPILED", 1);
    }

    @Transactional(readOnly = true)
    public List<Artifact> artifacts(String tenantId, String modelSpecId) {
        return jdbcTemplate.query(
            """
            select a.model_spec_id, a.plan_id, a.revision, a.model_checksum, a.ownership,
                   a.artifact_type, a.path, a.content_checksum, a.status
              from modeling_dbt_artifact a join modeling_model_spec s on s.id = a.model_spec_id
             where s.tenant_id = ? and a.model_spec_id = ?
             order by a.revision desc, a.artifact_type, a.path
            """,
            (rs, row) -> new Artifact(
                rs.getString("model_spec_id"), rs.getString("plan_id"), rs.getInt("revision"), rs.getString("model_checksum"),
                rs.getString("ownership"), rs.getString("artifact_type"), rs.getString("path"),
                rs.getString("content_checksum"), rs.getString("status")
            ),
            normalizeTenant(tenantId), externalUuid(modelSpecId)
        );
    }

    @Transactional(readOnly = true)
    public DriftView drift(String tenantId, String modelSpecId) {
        ModelingVNextContract.ModelSpec model = findModelSpec(tenantId, modelSpecId);
        if (model == null) throw new DomainException("MODEL_OBJECT_NOT_FOUND", "ModelSpec 不存在");
        ModelIdentity identity = requireIdentity(tenantId, modelSpecId, model.revision(), null);
        List<String> issues = new ArrayList<>();
        try {
            ModelingDbtCompiler.CompiledArtifacts compiled = ModelingDbtCompiler.compile(model);
            String expectedChecksum = checksum(compiled.files().get(model.name() + ".sql"));
            String actualChecksum = null;
            try {
                actualChecksum = jdbcTemplate.queryForObject(
                    """
                    select a.content_checksum from modeling_dbt_artifact a
                    join modeling_model_spec s on s.id = a.model_spec_id
                    where s.tenant_id = ? and a.model_spec_id = ? and a.revision = ?
                      and a.model_checksum = ? and a.artifact_type = 'SQL' and a.status = 'COMPILED'
                    """,
                    String.class,
                    normalizeTenant(tenantId),
                    identity.id(),
                    identity.revision(),
                    identity.modelChecksum()
                );
            } catch (EmptyResultDataAccessException ignored) {
                // A missing generated SQL artifact is drift, not a server error.
            }
            ModelingDriftGate.Snapshot expected = new ModelingDriftGate.Snapshot(
                model.dimensions(), model.grain().statement(), sourceRefs(model), standardElements(model), expectedChecksum, true, true, true
            );
            ModelingDriftGate.Snapshot actual = new ModelingDriftGate.Snapshot(
                model.dimensions(), model.grain().statement(), sourceRefs(model), standardElements(model), actualChecksum, true, true, true
            );
            ModelingDriftGate.compare(expected, actual).kinds().forEach(kind -> issues.add(kind.name()));
        } catch (ModelingDbtCompiler.CompileException exception) {
            issues.add("DBT_COMPILE_FAILED");
        }
        return new DriftView(modelSpecId, issues.isEmpty() ? "CLEAN" : "DRIFTED", List.copyOf(issues));
    }

    /**
     * Evaluates the controlled publication gate from persisted artifacts and drift evidence.
     * dbt parse/test are represented by the generated schema/test artifacts; an absent or
     * failed artifact is a blocker instead of being silently treated as success.
     */
    @Transactional(readOnly = true)
    public ReleaseGateView releaseGate(String tenantId, String modelSpecId) {
        ModelingVNextContract.ModelSpec model = findModelSpec(tenantId, modelSpecId);
        if (model == null) throw new DomainException("MODEL_OBJECT_NOT_FOUND", "ModelSpec 不存在");
        ModelIdentity identity = requireIdentity(tenantId, modelSpecId, model.revision(), null);
        List<ModelingDriftGate.DriftKind> driftKinds = drift(tenantId, modelSpecId).issues().stream()
            .map(this::driftKind)
            .filter(java.util.Objects::nonNull)
            .toList();
        boolean registered = isCanonicalModelSpec(tenantId, modelSpecId)
            ? canonicalRegistrationValid(tenantId, modelSpecId)
            : findBusinessObject(tenantId, model.objectId()) != null;
        boolean parsePassed = hasArtifact(tenantId, modelSpecId, identity.revision(), identity.modelChecksum(), "SCHEMA", "COMPILED");
        boolean testsPassed = hasArtifact(tenantId, modelSpecId, identity.revision(), identity.modelChecksum(), "TEST", "COMPILED");
        String checksum = null;
        try {
            checksum = checksum(ModelingDbtCompiler.compile(model).files().get(model.name() + ".sql"));
        } catch (ModelingDbtCompiler.CompileException ignored) {
            // The release gate will expose DBT_PARSE_FAILED/DBT_TEST_FAILED below.
        }
        ModelingDriftGate.Snapshot snapshot = new ModelingDriftGate.Snapshot(
            model.dimensions(), model.grain() == null ? null : model.grain().statement(), sourceRefs(model), standardElements(model), checksum,
            registered, parsePassed, testsPassed
        );
        ModelingDriftGate.ReleaseGateResult result = ModelingDriftGate.evaluate(snapshot, driftKinds);
        return new ReleaseGateView(modelSpecId, result.publishable(), result.status(), result.blockers());
    }

    private boolean isCanonicalModelSpec(String tenantId, String modelSpecId) {
        Boolean canonical = jdbcTemplate.queryForObject(
            "select contract_version = 2 from modeling_model_spec where tenant_id = ? and id = ?",
            Boolean.class,
            normalizeTenant(tenantId),
            externalUuid(modelSpecId)
        );
        return Boolean.TRUE.equals(canonical);
    }

    private boolean canonicalRegistrationValid(String tenantId, String modelSpecId) {
        Boolean registered = jdbcTemplate.queryForObject(
            """
            select exists (
                select 1
                  from modeling_model_spec s
                  join modeling_warehouse_plan p
                    on p.tenant_id = s.tenant_id and p.id = s.plan_id
                  join modeling_warehouse_plan_domain d
                    on d.tenant_id = s.tenant_id and d.plan_id = s.plan_id and d.domain_id = s.domain_id
                 where s.tenant_id = ? and s.id = ? and s.contract_version = 2
                   and s.plan_id is not null and s.domain_id is not null
                   and d.confirmation_status = 'CONFIRMED'
            )
            """,
            Boolean.class,
            normalizeTenant(tenantId),
            externalUuid(modelSpecId)
        );
        return Boolean.TRUE.equals(registered);
    }

    public RunView createRun(String tenantId, ModelingRunRequestContract.RunRequest request) {
        Set<String> knownSourceBatches = knownSourceBatches(request);
        List<ModelingRunRequestContract.Issue> issues = ModelingRunRequestContract.validate(request, knownSourceBatches);
        if (!issues.isEmpty()) throw new DomainException(issues.getFirst().code().name(), issues.getFirst().message());
        UUID modelSpecId = externalUuid(request.modelSpecId());
        ModelIdentity identity = requireIdentity(tenantId, request.modelSpecId(), request.revision(), null);
        if (!"PUBLISHED".equals(identity.status())) {
            throw new DomainException("MODEL_RUN_PUBLISHED_REQUIRED", "Only a published ModelSpec can run");
        }
        try {
            RunView replay = jdbcTemplate.queryForObject(
                runSelect() + " where tenant_id = ? and model_spec_id = ? and idempotency_key = ?",
                runRowMapper(), normalizeTenant(tenantId), modelSpecId, request.idempotencyKey()
            );
            if (replay != null && replay.revision() != request.revision()) {
                throw new DomainException("MODEL_RUN_IDEMPOTENCY_CONFLICT", "Run idempotency key belongs to another ModelSpec revision");
            }
            return replay;
        } catch (EmptyResultDataAccessException ignored) {
            UUID id = UUID.randomUUID();
            ModelingRunRequestContract.ExternalContext context = request.externalContext();
            boolean compiled = hasCompiledArtifacts(tenantId, modelSpecId, request.revision(), identity.modelChecksum());
            ModelingRuntimeSubmissionService.SubmissionResult submission = runtimeSubmissionService == null
                ? new ModelingRuntimeSubmissionService.SubmissionResult("QUEUED", context.addaxTaskId(), context.airflowRunId(), "RUNTIME_DISABLED")
                : runtimeSubmissionService.submit(
                    compiled ? ModelingAirflowSubmissionGate.CompileStatus.COMPILED : ModelingAirflowSubmissionGate.CompileStatus.FAILED,
                    request,
                    knownSourceBatches
                );
            if ("BLOCKED".equals(submission.state())) throw new DomainException("MODEL_RUN_BLOCKED", submission.message());
            String persistedState = "SUBMITTED".equals(submission.state())
                ? ModelingRunStateMachine.RunState.QUEUED.name()
                : submission.state();
            jdbcTemplate.update(
                """
                insert into modeling_pipeline_run (
                    id, tenant_id, model_spec_id, plan_id, model_revision, model_checksum,
                    repair_path, idempotency_key, status, source_batch_id, addax_task_id,
                    airflow_dag_id, airflow_run_id, dbt_run_id, dbt_selector, target,
                    message, version, created_date, last_modified_date
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)
                """,
                id, normalizeTenant(tenantId), modelSpecId, identity.planId(), identity.revision(), identity.modelChecksum(),
                repairPath(identity), request.idempotencyKey(), persistedState, context.sourceBatchId(), submission.addaxTaskId(),
                context.airflowDagId(), submission.airflowRunId(), context.dbtRunId(), context.dbtSelector(), context.targetTable(),
                submission.message(), Timestamp.from(Instant.now()), Timestamp.from(Instant.now())
            );
            return getRun(tenantId, id.toString());
        }
    }

    @Transactional(readOnly = true)
    public RunView getRun(String tenantId, String id) {
        try {
            return jdbcTemplate.queryForObject(
                runSelect() + " where tenant_id = ? and id = ?",
                runRowMapper(), normalizeTenant(tenantId), nullableUuid(id)
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new DomainException("MODEL_RUN_NOT_FOUND", "运行记录不存在");
        }
    }

    /**
     * Applies an external Addax/Airflow/dbt callback to one persisted run.
     * Same-state and late callbacks are idempotent through the shared state machine.
     */
    public RunView callbackRun(String tenantId, String id, ModelingRunCallbackContract.Callback callback) {
        final ModelingRunCallbackContract.Callback validated;
        try {
            validated = ModelingRunCallbackContract.validate(callback);
        } catch (IllegalArgumentException exception) {
            throw new DomainException("MODEL_CALLBACK_INVALID", exception.getMessage());
        }
        RunView current = getRun(tenantId, id);
        ModelingRunStateMachine.RunState currentState;
        try {
            currentState = ModelingRunStateMachine.RunState.valueOf(current.state());
        } catch (IllegalArgumentException exception) {
            throw new DomainException("MODEL_RUN_STATE_INVALID", "运行记录状态不可识别");
        }
        ModelingRunStateMachine.RunState next;
        try {
            next = ModelingRunStateMachine.applyCallback(currentState, validated.state());
        } catch (IllegalStateException exception) {
            throw new DomainException("MODEL_RUN_STATE_INVALID", exception.getMessage());
        }
        jdbcTemplate.update(
            "update modeling_pipeline_run set status = ?, addax_task_id = coalesce(?, addax_task_id), airflow_run_id = coalesce(?, airflow_run_id), dbt_run_id = coalesce(?, dbt_run_id), message = coalesce(?, message), version = version + 1, last_modified_date = ? where tenant_id = ? and id = ?",
            next.name(), callbackText(validated.addaxTaskId()), callbackText(validated.airflowRunId()), callbackText(validated.dbtRunId()), callbackText(validated.message()),
            Timestamp.from(Instant.now()), normalizeTenant(tenantId), nullableUuid(id)
        );
        return getRun(tenantId, id);
    }

    @Transactional(readOnly = true)
    public LineageView lineage(String tenantId, String modelSpecId) {
        ModelingVNextContract.ModelSpec model = findModelSpec(tenantId, modelSpecId);
        if (model == null) throw new DomainException("MODEL_OBJECT_NOT_FOUND", "ModelSpec 不存在");
        List<Map<String, String>> nodes = new ArrayList<>();
        nodes.add(Map.of("id", model.id(), "name", model.name(), "layer", model.layer().name(), "kind", "MODEL_SPEC"));
        if (model.sourceRefs() != null) {
            for (ModelingVNextContract.SourceRef source : model.sourceRefs()) {
                nodes.add(Map.of("id", source.ref(), "name", source.ref(), "layer", source.layer().name(), "kind", source.kind()));
            }
        }
        List<Map<String, String>> edges = model.sourceRefs() == null
            ? List.of()
            : model.sourceRefs().stream().map(source -> Map.of("from", source.ref(), "to", model.id(), "type", "SOURCE")).toList();
        return new LineageView(model.id(), List.copyOf(nodes), edges);
    }

    private ModelingVNextContract.BusinessObject findBusinessObject(String tenantId, String id) {
        if (!notBlank(id)) return null;
        try {
            return jdbcTemplate.queryForObject(
                "select id, code, name, description, object_kind, process_id, business_key, grain_statement, source_refs, status, implementation_mode from modeling_business_object where tenant_id = ? and id = ?",
                businessObjectRowMapper(), normalizeTenant(tenantId), externalUuid(id)
            );
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private ModelingVNextContract.ModelSpec findModelSpec(String tenantId, String id) {
        if (!notBlank(id)) return null;
        String tenant = normalizeTenant(tenantId);
        try {
            return jdbcTemplate.queryForObject(
                "select r.spec_json from modeling_model_spec_revision r join modeling_model_spec s on s.id = r.model_spec_id where s.tenant_id = ? and s.id = ? and r.revision = s.revision and s.contract_version = 1",
                (rs, row) -> readJson(rs.getString("spec_json"), ModelingVNextContract.ModelSpec.class), tenant, externalUuid(id)
            );
        } catch (EmptyResultDataAccessException exception) {
            ModelSpecContract.ModelSpecView canonical = findCanonicalModelSpec(tenant, id);
            return canonical == null ? null : projectCanonical(tenant, canonical);
        }
    }

    private ModelSpecContract.ModelSpecView findCanonicalModelSpec(String tenantId, String id) {
        if (
            canonicalModelSpecService == null ||
            !canonicalModelSpecService.canonicalReadEnabled() ||
            !notBlank(id)
        ) return null;
        try {
            ModelSpecContract.ModelSpecView view = canonicalModelSpecService.get(tenantId, externalUuid(id));
            return view.contractVersion() == ModelSpecContract.CONTRACT_VERSION ? view : null;
        } catch (ModelSpecException canonicalError) {
            if (canonicalError.kind() == ModelSpecException.Kind.NOT_FOUND) return null;
            throw new DomainException(canonicalError.code(), canonicalError.getMessage());
        }
    }

    private ModelSpecContract.ModelSpecView canonicalRevision(
        String tenantId,
        ModelSpecContract.ModelRevisionRef reference
    ) {
        try {
            return canonicalModelSpecService.revision(tenantId, reference);
        } catch (ModelSpecException canonicalError) {
            throw new DomainException(canonicalError.code(), canonicalError.getMessage());
        }
    }

    private ModelingVNextContract.ModelSpec projectCanonical(String tenantId, ModelSpecContract.ModelSpecView view) {
        try {
            return ModelSpecCompilerProjection.project(
                view,
                reference -> canonicalModelSpecService.revision(tenantId, reference)
            );
        } catch (ModelSpecException canonicalError) {
            throw new DomainException(canonicalError.code(), canonicalError.getMessage());
        }
    }

    private RowMapper<ModelingVNextContract.BusinessObject> businessObjectRowMapper() {
        return (rs, row) -> new ModelingVNextContract.BusinessObject(
            rs.getString("id"), rs.getString("code"), rs.getString("name"), rs.getString("description"),
            enumValue(ModelingVNextContract.ObjectKind.class, rs.getString("object_kind")), rs.getString("process_id"),
            readJson(rs.getString("business_key"), new TypeReference<List<String>>() {}), readJson(rs.getString("grain_statement"), ModelingVNextContract.Grain.class),
            readJson(rs.getString("source_refs"), new TypeReference<List<ModelingVNextContract.SourceRef>>() {}), rs.getString("status"),
            enumValue(ModelingVNextContract.ImplementationMode.class, rs.getString("implementation_mode"))
        );
    }

    private RowMapper<RunView> runRowMapper() {
        return (rs, row) -> new RunView(
            rs.getString("id"), rs.getString("model_spec_id"), rs.getInt("model_revision"), rs.getString("status"), rs.getString("source_batch_id"),
            rs.getString("addax_task_id"), rs.getString("airflow_dag_id"), rs.getString("airflow_run_id"), rs.getString("dbt_run_id"),
            rs.getString("dbt_selector"), rs.getString("target"), rs.getString("message"), null,
            rs.getString("plan_id"), rs.getString("model_checksum"), rs.getString("repair_path")
        );
    }

    private static String runSelect() {
        return """
            select id, model_spec_id, plan_id, model_revision, model_checksum, repair_path,
                   status, source_batch_id, addax_task_id, airflow_dag_id, airflow_run_id,
                   dbt_run_id, dbt_selector, target, message, version
              from modeling_pipeline_run
            """;
    }

    private boolean hasCompiledArtifacts(String tenantId, UUID modelSpecId, int revision, String modelChecksum) {
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from modeling_dbt_artifact a join modeling_model_spec s on s.id = a.model_spec_id where s.tenant_id = ? and a.model_spec_id = ? and a.revision = ? and a.model_checksum = ? and a.status = 'COMPILED'",
            Integer.class,
            normalizeTenant(tenantId),
            modelSpecId,
            revision,
            modelChecksum
        );
        return count != null && count > 0;
    }

    private boolean hasArtifact(
        String tenantId,
        String modelSpecId,
        int revision,
        String modelChecksum,
        String artifactType,
        String status
    ) {
        Integer count = jdbcTemplate.queryForObject(
            """
            select count(*) from modeling_dbt_artifact a
            join modeling_model_spec s on s.id = a.model_spec_id
            where s.tenant_id = ? and a.model_spec_id = ? and a.revision = ?
              and a.model_checksum = ? and a.artifact_type = ? and a.status = ?
            """,
            Integer.class,
            normalizeTenant(tenantId),
            externalUuid(modelSpecId),
            revision,
            modelChecksum,
            artifactType,
            status
        );
        return count != null && count > 0;
    }

    private ModelIdentity requireIdentity(String tenantId, String modelSpecId, int revision, String expectedChecksum) {
        ModelIdentity identity;
        try {
            identity = jdbcTemplate.queryForObject(
                """
                select s.id, s.plan_id, s.revision,
                       coalesce(s.current_checksum, r.content_checksum) as model_checksum,
                       s.implementation_mode, s.status
                  from modeling_model_spec s
                  join modeling_model_spec_revision r
                    on r.model_spec_id = s.id and r.revision = s.revision
                 where s.tenant_id = ? and s.id = ?
                """,
                (row, number) -> new ModelIdentity(
                    row.getObject("id", UUID.class),
                    row.getObject("plan_id", UUID.class),
                    row.getInt("revision"),
                    row.getString("model_checksum"),
                    row.getString("implementation_mode"),
                    row.getString("status")
                ),
                normalizeTenant(tenantId),
                externalUuid(modelSpecId)
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new DomainException("MODEL_OBJECT_NOT_FOUND", "ModelSpec 不存在");
        }
        if (identity == null || identity.revision() != revision) {
            throw new DomainException("MODEL_REVISION_CONFLICT", "ModelSpec revision 不匹配");
        }
        if (identity.modelChecksum() == null || identity.modelChecksum().isBlank()) {
            throw new DomainException("MODEL_CHECKSUM_REQUIRED", "ModelSpec checksum 不存在");
        }
        if (expectedChecksum != null && !expectedChecksum.equals(identity.modelChecksum())) {
            throw new DomainException("MODEL_CHECKSUM_CONFLICT", "ModelSpec checksum 不匹配");
        }
        return identity;
    }

    private static String repairPath(ModelIdentity identity) {
        String path = "/modeling/models/" + identity.id() + "?tab=design";
        return identity.planId() == null ? path : path + "&planId=" + identity.planId();
    }

    private static String nullableString(UUID value) {
        return value == null ? null : value.toString();
    }

    private ModelingDriftGate.DriftKind driftKind(String value) {
        try {
            return ModelingDriftGate.DriftKind.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return null;
        }
    }

    private static Set<String> knownSourceBatches(ModelingRunRequestContract.RunRequest request) {
        if (request == null || request.externalContext() == null) return Collections.emptySet();
        ModelingRunRequestContract.ExternalContext context = request.externalContext();
        if (notBlank(context.sourceBatchId()) && notBlank(context.addaxTaskId())) return Set.of(context.sourceBatchId());
        return Collections.emptySet();
    }

    private static List<String> sourceRefs(ModelingVNextContract.ModelSpec model) {
        if (model.sourceRefs() == null) return List.of();
        return model.sourceRefs().stream().filter(java.util.Objects::nonNull).map(ModelingVNextContract.SourceRef::ref).toList();
    }

    private static List<String> standardElements(ModelingVNextContract.ModelSpec model) {
        if (model.standardBindings() == null) return List.of();
        return model.standardBindings().stream()
            .filter(java.util.Objects::nonNull)
            .map(ModelingVNextContract.StandardBinding::standardElementId)
            .filter(ModelingVNextApplicationService::notBlank)
            .toList();
    }

    private int findVersion(String table, String tenant, UUID id) {
        try {
            Integer version = jdbcTemplate.queryForObject("select version from " + table + " where tenant_id = ? and id = ?", Integer.class, tenant, id);
            return version == null ? 0 : version;
        } catch (EmptyResultDataAccessException exception) {
            return 0;
        }
    }

    private <T> T readJson(String value, Class<T> type) {
        if (!notBlank(value)) return null;
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new DomainException("MODEL_METADATA_INVALID", "建模元数据 JSON 不可读");
        }
    }

    private <T> T readJson(String value, TypeReference<T> type) {
        if (!notBlank(value)) return null;
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new DomainException("MODEL_METADATA_INVALID", "建模元数据 JSON 不可读");
        }
    }

    private String writeJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new DomainException("MODEL_METADATA_INVALID", "建模元数据无法序列化");
        }
    }

    private static String checksum(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private static String artifactType(String file) {
        if (file.endsWith(".sql")) return "SQL";
        if (file.endsWith(".tests.yml")) return "TEST";
        if (file.endsWith(".yml")) return "SCHEMA";
        return "DOC";
    }

    private static UUID parseOrGenerate(String value) {
        return notBlank(value) ? externalUuid(value) : UUID.randomUUID();
    }

    /**
     * The UI contract deliberately permits stable semantic ids such as
     * {@code semantic-model-001}. PostgreSQL keeps UUID foreign keys, so map those
     * ids deterministically instead of rejecting a first-use ledger request.
     */
    private static UUID externalUuid(String value) {
        if (!notBlank(value)) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return UUID.nameUUIDFromBytes(("modeling:" + value).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static UUID nullableUuid(String value) {
        if (!notBlank(value)) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new DomainException("MODEL_ID_INVALID", "ID 必须是 UUID");
        }
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value) {
        return value == null ? null : Enum.valueOf(type, value.toUpperCase());
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static String normalizeTenant(String tenantId) {
        return defaultIfBlank(tenantId, "default");
    }

    private static String defaultIfBlank(String value, String fallback) {
        return notBlank(value) ? value : fallback;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String callbackText(String value) {
        return notBlank(value) ? value.trim() : null;
    }

    private static ModelingVNextContract.BusinessObject withId(ModelingVNextContract.BusinessObject value, String id) {
        return new ModelingVNextContract.BusinessObject(id, value.code(), value.name(), value.description(), value.objectKind(), value.processId(), value.businessKey(), value.grain(), value.sourceRefs(), value.status(), value.implementationMode());
    }

    private static ModelingVNextContract.ModelSpec withId(ModelingVNextContract.ModelSpec value, String id) {
        return new ModelingVNextContract.ModelSpec(id, value.objectId(), value.processId(), value.layer(), value.modelType(), value.implementationMode(), value.name(), value.grain(), value.standardBindings(), value.sourceRefs(), value.dimensions(), value.metrics(), value.materialization(), value.revision(), value.dependsOn(), value.legacyRef());
    }

}
