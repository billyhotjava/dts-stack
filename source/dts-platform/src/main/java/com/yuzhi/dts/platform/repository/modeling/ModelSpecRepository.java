package com.yuzhi.dts.platform.repository.modeling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelRevisionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.SourceKind;
import com.yuzhi.dts.platform.service.modeling.ModelSpecException;
import com.yuzhi.dts.platform.service.modeling.ModelingVNextContract;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** PostgreSQL persistence adapter for the canonical and legacy ModelSpec ledger. */
@Repository
public class ModelSpecRepository {

    private static final String CURRENT_COLUMNS = """
        select true as current_head, s.contract_version, s.tenant_id, s.id, s.plan_id, s.domain_id, s.status, s.revision,
               s.current_checksum as current_checksum, r.content_checksum as revision_checksum,
               r.snapshot_json::text as current_snapshot, r.spec_json as legacy_spec_json,
               s.idempotency_key, s.idempotency_request_hash,
               s.idempotency_response_snapshot::text as idempotency_response_snapshot,
               s.created_date, s.last_modified_date
          from modeling_model_spec s
          left join modeling_model_spec_revision r
            on r.model_spec_id = s.id and r.revision = s.revision
        """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ModelSpecRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<StoredModelSpec> findCurrent(String tenantId, UUID modelSpecId) {
        return jdbcTemplate
            .query(
                CURRENT_COLUMNS + " where s.tenant_id = ? and s.id = ?",
                ModelSpecRepository::mapStored,
                tenantId,
                modelSpecId
            )
            .stream()
            .findFirst();
    }

    public boolean hasActiveModelReferences(String tenantId, UUID modelSpecId) {
        Boolean referenced = jdbcTemplate.queryForObject(
            """
            select exists (
                select 1
                  from modeling_model_spec candidate
                 where candidate.tenant_id = ?
                   and candidate.id <> ?
                   and candidate.contract_version = 2
                   and candidate.status <> 'ARCHIVED'
                   and (
                       exists (
                           select 1
                             from jsonb_array_elements(coalesce(candidate.depends_on, '[]'::jsonb)) dependency
                            where dependency ->> 'modelSpecId' = ?
                       )
                       or exists (
                           select 1
                             from jsonb_array_elements(coalesce(candidate.dimension_refs, '[]'::jsonb)) dimension_ref
                            where dimension_ref ->> 'modelSpecId' = ?
                       )
                   )
            )
            """,
            Boolean.class,
            tenantId,
            modelSpecId,
            modelSpecId.toString(),
            modelSpecId.toString()
        );
        return Boolean.TRUE.equals(referenced);
    }

    public boolean hasReclassificationEvidence(String tenantId, UUID modelSpecId) {
        Boolean exists = jdbcTemplate.queryForObject(
            """
            select exists (
                select 1
                  from modeling_model_implementation
                 where tenant_id = ? and model_spec_id = ?
                union all
                select 1
                  from modeling_model_lifecycle_event
                 where tenant_id = ? and model_spec_id = ?
                union all
                select 1
                  from modeling_model_release_candidate_entry
                 where tenant_id = ? and model_spec_id = ?
            )
            """,
            Boolean.class,
            tenantId,
            modelSpecId,
            tenantId,
            modelSpecId,
            tenantId,
            modelSpecId
        );
        return Boolean.TRUE.equals(exists);
    }

    public Optional<ReclassificationReplay> findReclassificationReplay(
        String tenantId,
        UUID modelSpecId,
        String idempotencyKey
    ) {
        return jdbcTemplate
            .query(
                """
                select result_revision, result_checksum
                  from modeling_model_reclassification_command
                 where tenant_id = ? and model_spec_id = ? and idempotency_key = ?
                """,
                (row, rowNumber) -> new ReclassificationReplay(
                    row.getInt("result_revision"),
                    row.getString("result_checksum")
                ),
                tenantId,
                modelSpecId,
                idempotencyKey
            )
            .stream()
            .findFirst();
    }

    public void insertReclassificationCommand(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        String idempotencyKey,
        ModelType fromType,
        ModelType toType,
        int resultRevision,
        String resultChecksum,
        Instant createdAt
    ) {
        jdbcTemplate.update(
            """
            insert into modeling_model_reclassification_command (
                id, tenant_id, model_spec_id, idempotency_key, from_type, to_type,
                result_revision, result_checksum, created_by, created_date
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            UUID.randomUUID(),
            tenantId,
            modelSpecId,
            idempotencyKey,
            fromType.name(),
            toType.name(),
            resultRevision,
            resultChecksum,
            actorId,
            Timestamp.from(createdAt)
        );
    }

    public Optional<StoredModelSpec> findByIdempotencyKey(String tenantId, String idempotencyKey) {
        return jdbcTemplate
            .query(
                CURRENT_COLUMNS + " where s.tenant_id = ? and s.contract_version = 2 and s.idempotency_key = ?",
                ModelSpecRepository::mapStored,
                tenantId,
                idempotencyKey
            )
            .stream()
            .findFirst();
    }

    public List<StoredModelSpec> listCurrent(String tenantId, ListFilter filter) {
        StringBuilder sql = new StringBuilder(CURRENT_COLUMNS).append(" where s.tenant_id = ?");
        List<Object> arguments = new ArrayList<>();
        arguments.add(tenantId);
        ListFilter effective = filter == null ? new ListFilter(null, null, null, null) : filter;
        if (effective.planId() != null) {
            sql.append(" and s.plan_id = ?");
            arguments.add(effective.planId());
        }
        if (effective.domainId() != null) {
            sql.append(" and s.domain_id = ?");
            arguments.add(effective.domainId());
        }
        if (effective.modelType() != null) {
            sql.append(" and s.model_type = ?");
            arguments.add(effective.modelType().name());
        }
        if (effective.layer() != null) {
            sql.append(" and s.layer = ?");
            arguments.add(effective.layer().name());
        }
        sql.append(" order by s.name, s.id");
        return jdbcTemplate.query(sql.toString(), ModelSpecRepository::mapStored, arguments.toArray());
    }

    /**
     * Bounded, tenant-scoped read used only by the relationship graph projection.
     *
     * <p>Domain authorization is pushed into the query so the projection does not perform one
     * permission lookup per ModelSpec.
     */
    public List<StoredModelSpec> listCurrentForRelationshipGraph(
        String tenantId,
        UUID planId,
        Set<UUID> visibleDomainIds,
        boolean canonicalReadEnabled,
        int limit
    ) {
        return listCurrentForRelationshipGraph(
            tenantId,
            planId,
            visibleDomainIds,
            canonicalReadEnabled,
            null,
            limit
        );
    }

    /**
     * Stable UUID-keyset window for continuing a relationship graph projection.
     *
     * <p>The cursor is scoped by the same tenant, plan, contract and domain predicates as the
     * initial window; it is never interpreted as a globally readable ModelSpec identifier.
     */
    public List<StoredModelSpec> listCurrentForRelationshipGraph(
        String tenantId,
        UUID planId,
        Set<UUID> visibleDomainIds,
        boolean canonicalReadEnabled,
        UUID afterId,
        int limit
    ) {
        if (planId == null || limit < 1) {
            return List.of();
        }
        Set<UUID> effectiveVisibleDomainIds = visibleDomainIds == null ? Set.of() : visibleDomainIds;
        List<UUID> orderedDomainIds = effectiveVisibleDomainIds.stream().filter(java.util.Objects::nonNull).sorted().toList();
        StringBuilder sql = new StringBuilder(CURRENT_COLUMNS)
            .append(" where s.tenant_id = ? and s.plan_id = ?");
        List<Object> arguments = new ArrayList<>();
        arguments.add(tenantId);
        arguments.add(planId);
        if (!canonicalReadEnabled) {
            sql.append(" and s.contract_version <> ?");
            arguments.add(ModelSpecContract.CONTRACT_VERSION);
        }
        if (afterId != null) {
            sql.append(" and s.id > ?");
            arguments.add(afterId);
        }
        sql.append(" and (");
        if (orderedDomainIds.isEmpty()) {
            sql.append("s.domain_id is null and s.contract_version = ?");
        } else {
            sql
                .append("s.domain_id in (")
                .append(String.join(", ", java.util.Collections.nCopies(orderedDomainIds.size(), "?")))
                .append(") or (s.domain_id is null and s.contract_version = ?)");
            arguments.addAll(orderedDomainIds);
        }
        arguments.add(ModelingVNextContract.CONTRACT_VERSION);
        sql.append(") order by s.id limit ?");
        arguments.add(limit);
        return jdbcTemplate.query(sql.toString(), ModelSpecRepository::mapStored, arguments.toArray());
    }

    public Optional<StoredModelSpec> findRevision(String tenantId, UUID modelSpecId, int revision) {
        return jdbcTemplate
            .query(
                """
                select false as current_head, r.contract_version, r.tenant_id, s.id, s.plan_id, s.domain_id, r.status, r.revision,
                       null as current_checksum, r.content_checksum as revision_checksum,
                       r.snapshot_json::text as current_snapshot, r.spec_json as legacy_spec_json,
                       s.idempotency_key, s.idempotency_request_hash,
                       s.idempotency_response_snapshot::text as idempotency_response_snapshot,
                       s.created_date, coalesce(r.last_modified_date, r.created_date) as last_modified_date
                  from modeling_model_spec_revision r
                  join modeling_model_spec s on s.id = r.model_spec_id and s.tenant_id = r.tenant_id
                 where r.tenant_id = ? and r.model_spec_id = ? and r.revision = ?
                """,
                ModelSpecRepository::mapStored,
                tenantId,
                modelSpecId,
                revision
            )
            .stream()
            .findFirst();
    }

    /**
     * Bounded exact-revision projection used only by the relationship graph.
     *
     * <p>Every requested pair is matched in SQL and domain visibility is applied before snapshot
     * decoding, so historical references never drift to the mutable current head.
     */
    public List<StoredModelSpec> listRevisionsForRelationshipGraph(
        String tenantId,
        List<ModelRevisionRef> references,
        Set<UUID> visibleDomainIds,
        boolean canonicalReadEnabled,
        int limit
    ) {
        if (references == null || references.isEmpty() || limit < 1) return List.of();
        int boundedLimit = Math.min(limit, 500);
        Set<ModelRevisionRef> unique = new java.util.LinkedHashSet<>();
        int inspected = 0;
        for (ModelRevisionRef reference : references) {
            if (++inspected > boundedLimit || unique.size() >= boundedLimit) break;
            if (reference != null && reference.modelSpecId() != null && reference.revision() > 0) {
                unique.add(reference);
            }
        }
        List<ModelRevisionRef> boundedReferences = unique
            .stream()
            .sorted(
                java.util.Comparator
                    .comparing(ModelRevisionRef::modelSpecId)
                    .thenComparingInt(ModelRevisionRef::revision)
            )
            .toList();
        if (boundedReferences.isEmpty()) return List.of();
        List<UUID> orderedDomainIds = (visibleDomainIds == null ? Set.<UUID>of() : visibleDomainIds)
            .stream()
            .filter(Objects::nonNull)
            .sorted()
            .toList();
        StringBuilder sql = new StringBuilder(
            """
            select false as current_head, r.contract_version, r.tenant_id, s.id, s.plan_id, s.domain_id, r.status, r.revision,
                   null as current_checksum, r.content_checksum as revision_checksum,
                   r.snapshot_json::text as current_snapshot, r.spec_json as legacy_spec_json,
                   s.idempotency_key, s.idempotency_request_hash,
                   s.idempotency_response_snapshot::text as idempotency_response_snapshot,
                   s.created_date, coalesce(r.last_modified_date, r.created_date) as last_modified_date
              from modeling_model_spec_revision r
              join modeling_model_spec s on s.id = r.model_spec_id and s.tenant_id = r.tenant_id
             where r.tenant_id = ?
            """
        );
        List<Object> arguments = new ArrayList<>();
        arguments.add(tenantId);
        if (!canonicalReadEnabled) {
            sql.append(" and r.contract_version <> ?");
            arguments.add(ModelSpecContract.CONTRACT_VERSION);
        }
        sql.append(" and (");
        if (orderedDomainIds.isEmpty()) {
            sql.append("s.domain_id is null and r.contract_version = ?");
        } else {
            sql
                .append("s.domain_id in (")
                .append(String.join(", ", java.util.Collections.nCopies(orderedDomainIds.size(), "?")))
                .append(") or (s.domain_id is null and r.contract_version = ?)");
            arguments.addAll(orderedDomainIds);
        }
        arguments.add(ModelingVNextContract.CONTRACT_VERSION);
        sql.append(") and (");
        for (int index = 0; index < boundedReferences.size(); index++) {
            if (index > 0) sql.append(" or ");
            sql.append("(r.model_spec_id = ? and r.revision = ?)");
            ModelRevisionRef reference = boundedReferences.get(index);
            arguments.add(reference.modelSpecId());
            arguments.add(reference.revision());
        }
        sql.append(") order by s.name, s.id, r.revision limit ?");
        arguments.add(boundedLimit);
        return jdbcTemplate.query(sql.toString(), ModelSpecRepository::mapStored, arguments.toArray());
    }

    public Optional<PlanState> lockPlan(String tenantId, UUID planId) {
        return jdbcTemplate
            .query(
                "select id, lifecycle_status from modeling_warehouse_plan where tenant_id = ? and id = ? for share",
                (row, rowNumber) -> new PlanState(row.getObject("id", UUID.class), row.getString("lifecycle_status")),
                tenantId,
                planId
            )
            .stream()
            .findFirst();
    }

    public Optional<DomainBindingState> lockDomainBinding(String tenantId, UUID planId, UUID domainId) {
        return jdbcTemplate
            .query(
                """
                select domain_id, confirmation_status
                  from modeling_warehouse_plan_domain
                 where tenant_id = ? and plan_id = ? and domain_id = ?
                 for share
                """,
                (row, rowNumber) ->
                    new DomainBindingState(row.getObject("domain_id", UUID.class), row.getString("confirmation_status")),
                tenantId,
                planId,
                domainId
            )
            .stream()
            .findFirst();
    }

    public Optional<SourceBindingState> findSourceBinding(String tenantId, UUID planId, UUID sourceBindingId) {
        return querySourceBinding(tenantId, planId, sourceBindingId, false);
    }

    public Optional<SourceBindingState> lockSourceBinding(String tenantId, UUID planId, UUID sourceBindingId) {
        return querySourceBinding(tenantId, planId, sourceBindingId, true);
    }

    /**
     * Resolves the executable relation owned by a confirmed plan binding. Stable binding IDs stay
     * in ModelImplementation; compiler-specific names are always re-derived from authoritative
     * locator/catalog data instead of being copied from the browser.
     */
    public Optional<PhysicalSourceProjection> findCurrentPhysicalSource(
        String tenantId,
        UUID planId,
        UUID sourceBindingId,
        String resolvedVersion
    ) {
        return jdbcTemplate
            .query(
                """
                select s.source_type, s.source_version,
                       case
                           when s.source_type = 'CONNECTION_TABLE' then
                               concat_ws('.', nullif(s.locator_json ->> 'namespace', ''), nullif(s.locator_json ->> 'objectName', ''))
                           when s.source_type = 'CATALOG_TABLE' then
                               concat_ws('.', nullif(c.hive_database, ''), nullif(c.hive_table, ''))
                           when s.source_type = 'DBT_NODE' then
                               regexp_replace(coalesce(nullif(s.locator_json ->> 'uniqueId', ''), s.source_id), '^.*\\.', '')
                           else null
                       end as executable_ref,
                       case
                           when s.source_type = 'DBT_NODE' then 'STG'
                           when s.source_type = 'CATALOG_TABLE' then coalesce(nullif(upper(c.warehouse_layer), ''), 'ODS')
                           else 'ODS'
                       end as source_layer
                  from modeling_warehouse_plan_source s
                  left join catalog_dataset c
                    on s.source_type = 'CATALOG_TABLE'
                   and c.id::text = coalesce(nullif(s.locator_json ->> 'assetId', ''), s.source_id)
                 where s.tenant_id = ? and s.plan_id = ? and s.id = ?
                   and s.confirmation_status = 'CONFIRMED' and s.source_version = ?
                """,
                (row, rowNumber) -> {
                    String type = row.getString("source_type");
                    String ref = row.getString("executable_ref");
                    if (ref == null || ref.isBlank()) return null;
                    SourceKind kind = switch (type) {
                        case "CONNECTION_TABLE", "CATALOG_TABLE" -> SourceKind.TABLE;
                        case "DBT_NODE" -> SourceKind.DBT_MODEL;
                        default -> null;
                    };
                    if (kind == null) return null;
                    Layer layer;
                    try {
                        layer = Layer.valueOf(row.getString("source_layer"));
                    } catch (IllegalArgumentException | NullPointerException invalidLayer) {
                        return null;
                    }
                    return new PhysicalSourceProjection(kind, ref, layer, row.getString("source_version"));
                },
                tenantId,
                planId,
                sourceBindingId,
                resolvedVersion
            )
            .stream()
            .filter(java.util.Objects::nonNull)
            .findFirst();
    }

    private Optional<SourceBindingState> querySourceBinding(
        String tenantId,
        UUID planId,
        UUID sourceBindingId,
        boolean lock
    ) {
        return jdbcTemplate
            .query(
                """
                select s.id, s.source_type, s.source_id, s.source_version, s.confirmation_status,
                       s.locator_json::text as locator_json,
                       p.owner_id as plan_owner_id, p.owner_department_id as plan_owner_department_id
                  from modeling_warehouse_plan_source s
                  join modeling_warehouse_plan p on p.tenant_id = s.tenant_id and p.id = s.plan_id
                 where s.tenant_id = ? and s.plan_id = ? and s.id = ?
                """ + (lock ? " for share" : ""),
                (row, rowNumber) ->
                    new SourceBindingState(
                        row.getObject("id", UUID.class),
                        row.getString("source_type"),
                        row.getString("source_id"),
                        row.getString("source_version"),
                        row.getString("confirmation_status"),
                        row.getString("locator_json"),
                        row.getString("plan_owner_id"),
                        row.getString("plan_owner_department_id")
                    ),
                tenantId,
                planId,
                sourceBindingId
            )
            .stream()
            .findFirst();
    }

    public int insertV2(
        String tenantId,
        String actorId,
        com.yuzhi.dts.platform.service.modeling.ModelSpecContract.CreateModelSpecCommand command,
        ModelSpecView view,
        String requestHash,
        String responseSnapshot
    ) {
        return jdbcTemplate.update(
            """
            insert into modeling_model_spec (
                id, tenant_id, plan_id, layer, model_type, implementation_mode, name, grain_statement,
                materialization, status, revision, version, created_date, last_modified_date,
                contract_version, domain_id, business_activity_ref, description, consumption_scenario,
                fact_shape, grain_json, time_semantics, fields, source_refs, depends_on, dimension_refs,
                metric_refs, standard_bindings, generation_strategy, dimension_profile, current_checksum, idempotency_key,
                idempotency_request_hash, idempotency_response_snapshot, dimension_definition_id, dimension_definition_revision,
                data_mart_id, variant_code
            ) values (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?,
                2, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), cast(? as jsonb),
                cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), cast(? as jsonb),
                cast(? as jsonb), ?, ?, ?, cast(? as jsonb)
                , ?, ?, ?, ?
            )
            on conflict (tenant_id, idempotency_key)
            where contract_version = 2 and idempotency_key is not null
            do nothing
            """,
            view.id(),
            tenantId,
            view.planId(),
            view.layer().name(),
            view.modelType().name(),
            view.implementationMode().name(),
            view.name(),
            view.grain() == null ? null : view.grain().statement(),
            view.materialization(),
            view.status().name(),
            view.revision(),
            Timestamp.from(view.createdAt()),
            Timestamp.from(view.updatedAt()),
            view.domainId(),
            view.businessActivityRef(),
            view.description(),
            view.consumptionScenario(),
            enumName(view.factShape()),
            jsonOrNull(view.grain()),
            jsonOrNull(view.timeSemantics()),
            json(view.fields()),
            json(view.sourceRefs()),
            json(view.dependsOn()),
            json(view.dimensionRefs()),
            json(view.metricRefs()),
            json(view.standardBindings()),
            jsonOrNull(view.generationStrategy()),
            jsonOrNull(view.dimensionProfile()),
            view.checksum(),
            command.idempotencyKey(),
            requestHash,
            responseSnapshot,
            view.dimensionDefinitionRef() == null ? null : view.dimensionDefinitionRef().dimensionDefinitionId(),
            view.dimensionDefinitionRef() == null ? null : view.dimensionDefinitionRef().revision(),
            view.dataMartId(),
            view.variantCode()
        );
    }

    public int compareAndSetV2(
        String tenantId,
        String actorId,
        int expectedRevision,
        String expectedChecksum,
        ModelSpecView replacement,
        String snapshot
    ) {
        return jdbcTemplate.update(
            """
            update modeling_model_spec
               set layer = ?, model_type = ?, implementation_mode = ?, name = ?,
                   grain_statement = ?, materialization = ?, business_activity_ref = ?, description = ?,
                   consumption_scenario = ?, fact_shape = ?, grain_json = cast(? as jsonb),
                   time_semantics = cast(? as jsonb), fields = cast(? as jsonb), source_refs = cast(? as jsonb),
                   depends_on = cast(? as jsonb), dimension_refs = cast(? as jsonb), metric_refs = cast(? as jsonb),
                   standard_bindings = cast(? as jsonb), generation_strategy = cast(? as jsonb),
                   dimension_profile = cast(? as jsonb),
                   data_mart_id = ?, variant_code = ?,
                   current_checksum = ?, revision = ?, version = version + 1, last_modified_date = ?
             where tenant_id = ? and id = ? and contract_version = 2
               and revision = ? and current_checksum = ?
               and status = 'DRAFT'
            """,
            replacement.layer().name(),
            replacement.modelType().name(),
            replacement.implementationMode().name(),
            replacement.name(),
            replacement.grain() == null ? null : replacement.grain().statement(),
            replacement.materialization(),
            replacement.businessActivityRef(),
            replacement.description(),
            replacement.consumptionScenario(),
            enumName(replacement.factShape()),
            jsonOrNull(replacement.grain()),
            jsonOrNull(replacement.timeSemantics()),
            json(replacement.fields()),
            json(replacement.sourceRefs()),
            json(replacement.dependsOn()),
            json(replacement.dimensionRefs()),
            json(replacement.metricRefs()),
            json(replacement.standardBindings()),
            jsonOrNull(replacement.generationStrategy()),
            jsonOrNull(replacement.dimensionProfile()),
            replacement.dataMartId(),
            replacement.variantCode(),
            replacement.checksum(),
            replacement.revision(),
            Timestamp.from(replacement.updatedAt()),
            tenantId,
            replacement.id(),
            expectedRevision,
            expectedChecksum
        );
    }

    public int compareAndSetPublishedMetricRefs(
        String tenantId,
        String actorId,
        int expectedRevision,
        String expectedChecksum,
        ModelSpecView replacement,
        String snapshot
    ) {
        return jdbcTemplate.update(
            """
            update modeling_model_spec
               set metric_refs = cast(? as jsonb), current_checksum = ?, revision = ?, version = version + 1,
                   last_modified_date = ?
             where tenant_id = ? and id = ? and contract_version = 2
               and revision = ? and current_checksum = ? and status = 'PUBLISHED'
            """,
            json(replacement.metricRefs()),
            replacement.checksum(),
            replacement.revision(),
            Timestamp.from(replacement.updatedAt()),
            tenantId,
            replacement.id(),
            expectedRevision,
            expectedChecksum
        );
    }

    public int compareAndSetLifecycle(
        String tenantId,
        String actorId,
        int expectedRevision,
        String expectedChecksum,
        ModelStatus expectedStatus,
        ModelSpecView replacement
    ) {
        return jdbcTemplate.update(
            """
            update modeling_model_spec
               set status = ?, revision = ?, current_checksum = ?, version = version + 1,
                   last_modified_date = ?
             where tenant_id = ? and id = ? and contract_version = 2
               and revision = ? and current_checksum = ? and status = ?
            """,
            replacement.status().name(),
            replacement.revision(),
            replacement.checksum(),
            Timestamp.from(replacement.updatedAt()),
            tenantId,
            replacement.id(),
            expectedRevision,
            expectedChecksum,
            expectedStatus.name()
        );
    }

    public int updateV2RevisionLifecycle(
        String tenantId,
        String actorId,
        ModelStatus expectedStatus,
        ModelSpecView replacement,
        String snapshot
    ) {
        return jdbcTemplate.update(
            """
            update modeling_model_spec_revision
               set status = ?, snapshot_json = cast(? as jsonb), last_modified_date = ?, created_by = coalesce(created_by, ?)
             where tenant_id = ? and model_spec_id = ? and contract_version = 2
               and revision = ? and content_checksum = ? and status = ?
            """,
            replacement.status().name(),
            snapshot,
            Timestamp.from(replacement.updatedAt()),
            actorId,
            tenantId,
            replacement.id(),
            replacement.revision(),
            replacement.checksum(),
            expectedStatus.name()
        );
    }

    public boolean hasCompiledArtifact(String tenantId, UUID modelSpecId, int revision, String artifactType) {
        return hasCompiledArtifact(tenantId, modelSpecId, revision, null, artifactType);
    }

    public boolean hasCompiledArtifact(
        String tenantId,
        UUID modelSpecId,
        int revision,
        String modelChecksum,
        String artifactType
    ) {
        Integer count = jdbcTemplate.queryForObject(
            """
            select count(*)
              from modeling_dbt_artifact a
              join modeling_model_spec s on s.id = a.model_spec_id
             where s.tenant_id = ? and a.model_spec_id = ? and a.revision = ?
               and (? is null or a.model_checksum = ?)
               and a.artifact_type = ? and a.status = 'COMPILED'
            """,
            Integer.class,
            tenantId,
            modelSpecId,
            revision,
            modelChecksum,
            modelChecksum,
            artifactType
        );
        return count != null && count > 0;
    }

    public void insertV2Revision(String tenantId, String actorId, ModelSpecView view, String snapshot) {
        jdbcTemplate.update(
            """
            insert into modeling_model_spec_revision (
                id, model_spec_id, revision, status, content_checksum, created_date, last_modified_date,
                tenant_id, contract_version, snapshot_json, created_by
                , dimension_definition_id, dimension_definition_revision, data_mart_id, variant_code
            ) values (?, ?, ?, ?, ?, ?, ?, ?, 2, cast(? as jsonb), ?, ?, ?, ?, ?)
            """,
            UUID.randomUUID(),
            view.id(),
            view.revision(),
            view.status().name(),
            view.checksum(),
            Timestamp.from(view.updatedAt()),
            Timestamp.from(view.updatedAt()),
            tenantId,
            snapshot,
            actorId,
            view.dimensionDefinitionRef() == null ? null : view.dimensionDefinitionRef().dimensionDefinitionId(),
            view.dimensionDefinitionRef() == null ? null : view.dimensionDefinitionRef().revision(),
            view.dataMartId(),
            view.variantCode()
        );
    }

    public boolean planHasCurrentDataMart(String tenantId, UUID planId, UUID dataMartId, UUID domainId) {
        Boolean exists = jdbcTemplate.queryForObject(
            """
            select exists (
                select 1
                  from modeling_warehouse_plan_data_mart p
                  join modeling_data_mart m
                    on m.tenant_id = p.tenant_id and m.id = p.data_mart_id
                  join modeling_data_mart_domain d
                    on d.tenant_id = m.tenant_id and d.data_mart_id = m.id
                 where p.tenant_id = ?
                   and p.plan_id = ?
                   and p.data_mart_id = ?
                   and m.status = 'CURRENT'
                   and d.domain_id = ?
            )
            """,
            Boolean.class,
            tenantId,
            planId,
            dataMartId,
            domainId
        );
        return Boolean.TRUE.equals(exists);
    }

    public Optional<UUID> findActiveDimensionVariant(
        String tenantId,
        UUID planId,
        UUID dimensionDefinitionId,
        UUID dataMartId,
        String variantCode,
        UUID excludingModelSpecId
    ) {
        return jdbcTemplate
            .query(
                """
                select id
                  from modeling_model_spec
                 where tenant_id = ?
                   and plan_id = ?
                   and contract_version = 2
                   and model_type = 'DIMENSION'
                   and dimension_definition_id = ?
                   and coalesce(data_mart_id, '00000000-0000-0000-0000-000000000000'::uuid)
                       = coalesce(?::uuid, '00000000-0000-0000-0000-000000000000'::uuid)
                   and coalesce(variant_code, 'DEFAULT') = coalesce(?, 'DEFAULT')
                   and status <> 'ARCHIVED'
                   and (?::uuid is null or id <> ?::uuid)
                 order by created_date, id
                 limit 1
                """,
                (row, rowNumber) -> row.getObject("id", UUID.class),
                tenantId,
                planId,
                dimensionDefinitionId,
                dataMartId,
                variantCode,
                excludingModelSpecId,
                excludingModelSpecId
            )
            .stream()
            .findFirst();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("ModelSpec persistence payload cannot be serialized", exception);
        }
    }

    private String jsonOrNull(Object value) {
        return value == null ? null : json(value);
    }

    private static StoredModelSpec mapStored(ResultSet row, int rowNumber) throws SQLException {
        return new StoredModelSpec(
            row.getBoolean("current_head"),
            row.getInt("contract_version"),
            row.getString("tenant_id"),
            row.getObject("id", UUID.class),
            row.getObject("plan_id", UUID.class),
            row.getObject("domain_id", UUID.class),
            parseStatus(row.getString("status")),
            row.getInt("revision"),
            row.getString("current_checksum"),
            row.getString("revision_checksum"),
            row.getString("current_snapshot"),
            row.getString("legacy_spec_json"),
            row.getString("idempotency_key"),
            row.getString("idempotency_request_hash"),
            row.getString("idempotency_response_snapshot"),
            instant(row.getTimestamp("created_date")),
            instant(row.getTimestamp("last_modified_date"))
        );
    }

    private static ModelStatus parseStatus(String status) {
        try {
            return ModelStatus.valueOf(status);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ModelSpecException(
                "MODEL_SPEC_STATUS_INVALID",
                "Stored ModelSpec status is unsupported",
                ModelSpecException.Kind.CONFLICT
            );
        }
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? Instant.EPOCH : timestamp.toInstant();
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    public record StoredModelSpec(
        boolean currentHead,
        int contractVersion,
        String tenantId,
        UUID id,
        UUID planId,
        UUID domainId,
        ModelStatus status,
        int revision,
        String currentChecksum,
        String revisionChecksum,
        String currentSnapshot,
        String legacySpecJson,
        String idempotencyKey,
        String idempotencyRequestHash,
        String idempotencyResponseSnapshot,
        Instant createdAt,
        Instant updatedAt
    ) {
        public StoredModelSpec(
            int contractVersion,
            String tenantId,
            UUID id,
            UUID planId,
            UUID domainId,
            ModelStatus status,
            int revision,
            String checksum,
            String currentSnapshot,
            String legacySpecJson,
            String idempotencyKey,
            String idempotencyRequestHash,
            String idempotencyResponseSnapshot,
            Instant createdAt,
            Instant updatedAt
        ) {
            this(
                true,
                contractVersion,
                tenantId,
                id,
                planId,
                domainId,
                status,
                revision,
                checksum,
                checksum,
                currentSnapshot,
                legacySpecJson,
                idempotencyKey,
                idempotencyRequestHash,
                idempotencyResponseSnapshot,
                createdAt,
                updatedAt
            );
        }

        public String checksum() {
            return revisionChecksum != null ? revisionChecksum : currentChecksum;
        }
    }

    public record ListFilter(UUID planId, UUID domainId, ModelType modelType, Layer layer) {}

    public record PlanState(UUID id, String lifecycleStatus) {}

    public record DomainBindingState(UUID domainId, String confirmationStatus) {}

    public record SourceBindingState(
        UUID id,
        String sourceType,
        String sourceId,
        String sourceVersion,
        String confirmationStatus,
        String locatorJson,
        String planOwnerId,
        String planOwnerDepartmentId
    ) {}

    public record ReclassificationReplay(int revision, String checksum) {}

    public record PhysicalSourceProjection(SourceKind kind, String ref, Layer layer, String resolvedVersion) {}
}
