package com.yuzhi.dts.platform.repository.modeling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencyReadPort;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencyReadPort.PlanFacts;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.DependencyFacts;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.ImplementationPin;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.ModelFact;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.PhysicalSourceFact;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.FieldMapping;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.GeneratedInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.InputMode;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.PhysicalAssetInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.UpstreamModelInput;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecException;
import com.yuzhi.dts.platform.service.modeling.ModelSpecSnapshotCodec;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL projection that loads a dependency graph in one recursive query plus one source query. */
@Repository
@Transactional(readOnly = true)
public class ModelImplementationDependencyReadAdapter implements ModelImplementationDependencyReadPort {

    private static final int MAX_GRAPH_NODES = 256;
    private static final TypeReference<List<FieldMapping>> FIELD_MAPPINGS_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> SETTINGS_TYPE = new TypeReference<>() {};
    private final JdbcTemplate jdbcTemplate;
    private final ModelSpecSnapshotCodec snapshots;
    private final ObjectMapper objectMapper;

    public ModelImplementationDependencyReadAdapter(
        JdbcTemplate jdbcTemplate,
        ModelSpecSnapshotCodec snapshots,
        ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.snapshots = snapshots;
        this.objectMapper = objectMapper;
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.yuzhi.dts.platform.service.modeling.ModelingSourceScopeGuard sourceScope;

    @Override
    public DependencyFacts readFacts(String tenantId, ModelSpecView owner) {
        if (tenantId == null || tenantId.isBlank() || owner == null || owner.id() == null || owner.planId() == null) {
            throw stale("Dependency graph identity is incomplete", null);
        }
        // The root may be an in-memory authoring candidate whose references have not been saved
        // yet. Traverse its supplied references, then immutable stored revisions for descendants.
        var rootReferences = objectMapper.createArrayNode();
        if (owner.dependsOn() != null) owner.dependsOn().forEach(ref -> rootReferences.add(objectMapper.valueToTree(ref)));
        if (owner.dimensionRefs() != null) owner.dimensionRefs().forEach(ref -> rootReferences.add(objectMapper.valueToTree(ref)));
        List<ModelRow> rows = jdbcTemplate.query(
            """
            with recursive requested(model_spec_id, revision) as (
                select cast(? as uuid), cast(? as integer)
                union
                select cast(edge.value ->> 'modelSpecId' as uuid),
                       cast(edge.value ->> 'revision' as integer)
                  from requested
                  join modeling_model_spec_revision parent
                    on parent.tenant_id = ?
                   and parent.model_spec_id = requested.model_spec_id
                   and parent.revision = requested.revision
                   and parent.contract_version = 2
                 cross join lateral jsonb_array_elements(
                       case when parent.model_spec_id = cast(? as uuid) and parent.revision = ?
                            then cast(? as jsonb)
                            else coalesce(parent.snapshot_json -> 'dependsOn', '[]'::jsonb) ||
                                 coalesce(parent.snapshot_json -> 'dimensionRefs', '[]'::jsonb)
                       end
                 ) edge(value)
            ), exact_requests as (
                select distinct model_spec_id, revision from requested
            )
            select revision.model_spec_id, revision.revision, revision.content_checksum,
                   revision.snapshot_json::text as snapshot_json,
                   implementation.model_revision as implementation_model_revision,
                   implementation.model_checksum as implementation_model_checksum,
                   implementation.implementation_revision,
                   implementation.current_implementation_checksum,
                   implementation.dbt_unique_id,
                   implementation.status as implementation_status,
                   implementation.id as implementation_id,
                   implementation.plan_id as implementation_plan_id,
                   implementation.ownership as implementation_ownership,
                   implementation.project_key,
                   implementation.input_mode,
                   implementation.inputs_json::text as inputs_json,
                   implementation.field_mappings_json::text as field_mappings_json,
                   implementation.settings_json::text as settings_json,
                   implementation.materialization
              from exact_requests requested
              join modeling_model_spec_revision revision
                on revision.tenant_id = ?
               and revision.model_spec_id = requested.model_spec_id
               and revision.revision = requested.revision
               and revision.contract_version = 2
              join modeling_model_spec spec
                on spec.tenant_id = revision.tenant_id
               and spec.id = revision.model_spec_id
               and (spec.plan_id = ? or spec.status = 'PUBLISHED')
               and spec.contract_version = 2
              left join modeling_model_implementation implementation
                on implementation.tenant_id = revision.tenant_id
               and implementation.model_spec_id = revision.model_spec_id
               and implementation.model_revision = revision.revision
               and implementation.model_checksum = revision.content_checksum
               and implementation.status = 'ACTIVE'
             order by revision.model_spec_id, revision.revision
             limit ?
            """,
            (resultSet, rowNumber) -> mapModel(resultSet),
            owner.id(),
            owner.revision(),
            tenantId,
            owner.id(),
            owner.revision(),
            rootReferences.toString(),
            tenantId,
            owner.planId(),
            MAX_GRAPH_NODES + 1
        );
        if (rows.size() > MAX_GRAPH_NODES) {
            throw stale("The dependency graph exceeds the supported bounded size", MAX_GRAPH_NODES);
        }
        List<ModelFact> models = rows.stream().map(this::modelFact).map(fact ->
            owner.id().equals(fact.model().id()) && owner.revision() == fact.model().revision()
                ? new ModelFact(owner, fact.implementation()) : fact
        ).toList();
        boolean ownerPresent = models
            .stream()
            .map(ModelFact::model)
            .anyMatch(model -> owner.id().equals(model.id()) && owner.revision() == model.revision());
        if (!ownerPresent) throw stale("The owning ModelSpec revision is unavailable", owner.id());
        sourceScope.requireModels(tenantId, owner.planId(), models.stream().map(fact -> fact.model().id()).toList());
        return new DependencyFacts(models, readPhysicalSources(tenantId, models.stream().map(ModelFact::model).toList()));
    }

    @Override
    public PlanFacts readPlanFacts(String tenantId, UUID planId, List<UUID> requestedModelSpecIds) {
        List<UUID> requested = requestedModelSpecIds == null
            ? List.of()
            : requestedModelSpecIds.stream().filter(Objects::nonNull).distinct().sorted().toList();
        if (tenantId == null || tenantId.isBlank() || planId == null || requested.isEmpty() || requested.size() > 64) {
            throw stale("Materialization plan roots are incomplete or exceed the supported size", requested.size());
        }
        String placeholders = String.join(", ", java.util.Collections.nCopies(requested.size(), "?"));
        String sql = """
            with recursive requested(model_spec_id, revision) as (
                select spec.id, spec.revision
                  from modeling_model_spec spec
                 where spec.tenant_id = ? and spec.plan_id = ? and spec.contract_version = 2
                   and spec.id in (%s)
                union
                select cast(edge.value ->> 'modelSpecId' as uuid),
                       cast(edge.value ->> 'revision' as integer)
                  from requested
                  join modeling_model_spec_revision parent
                    on parent.tenant_id = ?
                   and parent.model_spec_id = requested.model_spec_id
                   and parent.revision = requested.revision
                   and parent.contract_version = 2
                 cross join lateral jsonb_array_elements(
                       coalesce(parent.snapshot_json -> 'dependsOn', '[]'::jsonb) ||
                       coalesce(parent.snapshot_json -> 'dimensionRefs', '[]'::jsonb)
                 ) edge(value)
                 where not exists (
                       select 1 from modeling_model_implementation structure
                        where structure.tenant_id = parent.tenant_id
                          and structure.model_spec_id = parent.model_spec_id
                          and structure.model_revision = parent.revision
                          and structure.model_checksum = parent.content_checksum
                          and structure.status = 'ACTIVE' and structure.input_mode = 'GENERATED'
                          and jsonb_array_length(structure.inputs_json) = 1
                          and (
                              structure.inputs_json -> 0 ->> 'generatorType' = 'SCHEMA_ONLY'
                              or (structure.ownership = 'DBT_MANAGED'
                                  and structure.inputs_json -> 0 ->> 'generatorType' = 'DBT'
                                  and structure.inputs_json -> 0 -> 'config' ->> 'buildMode' = 'SCHEMA_ONLY')
                          )
                 )
            ), exact_requests as (
                select distinct model_spec_id, revision from requested
            )
            select revision.model_spec_id, revision.revision, revision.content_checksum,
                   revision.snapshot_json::text as snapshot_json,
                   implementation.model_revision as implementation_model_revision,
                   implementation.model_checksum as implementation_model_checksum,
                   implementation.implementation_revision,
                   implementation.current_implementation_checksum,
                   implementation.dbt_unique_id,
                   implementation.status as implementation_status,
                   implementation.id as implementation_id,
                   implementation.plan_id as implementation_plan_id,
                   implementation.ownership as implementation_ownership,
                   implementation.project_key,
                   implementation.input_mode,
                   implementation.inputs_json::text as inputs_json,
                   implementation.field_mappings_json::text as field_mappings_json,
                   implementation.settings_json::text as settings_json,
                   implementation.materialization
              from exact_requests requested
              join modeling_model_spec_revision revision
                on revision.tenant_id = ?
               and revision.model_spec_id = requested.model_spec_id
               and revision.revision = requested.revision
               and revision.contract_version = 2
              join modeling_model_spec spec
                on spec.tenant_id = revision.tenant_id
               and spec.id = revision.model_spec_id
               and (spec.plan_id = ? or spec.status = 'PUBLISHED')
               and spec.contract_version = 2
              left join modeling_model_implementation implementation
                on implementation.tenant_id = revision.tenant_id
               and implementation.model_spec_id = revision.model_spec_id
               and implementation.model_revision = revision.revision
               and implementation.model_checksum = revision.content_checksum
               and implementation.status = 'ACTIVE'
             order by revision.model_spec_id, revision.revision
             limit ?
            """.formatted(placeholders);
        List<Object> arguments = new ArrayList<>();
        arguments.add(tenantId.trim());
        arguments.add(planId);
        arguments.addAll(requested);
        arguments.add(tenantId.trim());
        arguments.add(tenantId.trim());
        arguments.add(planId);
        arguments.add(MAX_GRAPH_NODES + 1);
        List<ModelRow> rows = jdbcTemplate.query(sql, (resultSet, rowNumber) -> mapModel(resultSet), arguments.toArray());
        if (rows.size() > MAX_GRAPH_NODES) {
            throw stale("The dependency graph exceeds the supported bounded size", MAX_GRAPH_NODES);
        }
        List<ModelFact> models = rows.stream().map(this::modelFact).toList();
        sourceScope.requireModels(tenantId, planId, models.stream().map(fact -> fact.model().id()).toList());
        Set<UUID> foundRoots = models
            .stream()
            .map(ModelFact::model)
            .filter(model -> requested.contains(model.id()))
            .map(ModelSpecView::id)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (foundRoots.size() != requested.size()) {
            throw stale("One or more materialization plan roots are unavailable", requested);
        }
        java.util.LinkedHashMap<UUID, ImplementationView> implementations = new java.util.LinkedHashMap<>();
        for (ModelRow row : rows) {
            ImplementationView implementation = implementation(row);
            if (implementation != null) implementations.put(row.modelSpecId(), implementation);
        }
        List<ModelSpecView> modelViews = models.stream().map(ModelFact::model)
            .filter(model -> !com.yuzhi.dts.platform.service.modeling.ModelSchemaOnlySupport.isSchemaOnly(implementations.get(model.id())))
            .toList();
        return new PlanFacts(
            new DependencyFacts(models, readPhysicalSources(tenantId, modelViews)),
            implementations
        );
    }

    private ModelFact modelFact(ModelRow row) {
        ModelSpecView model = snapshots.readView(row.snapshotJson());
        if (
            !Objects.equals(model.id(), row.modelSpecId()) ||
            model.revision() != row.revision() ||
            !Objects.equals(model.checksum(), row.modelChecksum()) ||
            !snapshots.matchesStoredContentChecksum(row.snapshotJson(), model, row.modelChecksum())
        ) {
            throw stale("A stored ModelSpec revision failed checksum verification", row.modelSpecId());
        }
        ImplementationPin implementation = row.implementationRevision() == null
            ? null
            : new ImplementationPin(
                row.implementationModelRevision(),
                row.implementationModelChecksum(),
                row.implementationRevision(),
                row.implementationChecksum(),
                row.dbtUniqueId(),
                row.implementationStatus()
            );
        return new ModelFact(model, implementation);
    }

    private List<PhysicalSourceFact> readPhysicalSources(
        String tenantId,
        List<ModelSpecView> models
    ) {
        Set<UUID> sourceIds = new LinkedHashSet<>();
        models.forEach(model -> model.sourceRefs().forEach(source -> {
                if (source != null && source.sourceBindingId() != null) sourceIds.add(source.sourceBindingId());
            }));
        List<UUID> ordered = sourceIds.stream().sorted().toList();
        if (ordered.isEmpty()) return List.of();
        if (ordered.size() > 128) throw stale("The ModelSpec declares too many physical sources", ordered.size());

        String placeholders = String.join(", ", java.util.Collections.nCopies(ordered.size(), "?"));
        String sql = """
            select source.id, source.source_type, source.source_version,
                   source.confirmation_status, source.resolution_status,
                   case
                       when source.source_type = 'CONNECTION_TABLE' then
                           concat_ws('.', nullif(source.locator_json ->> 'namespace', ''),
                                          nullif(source.locator_json ->> 'objectName', ''))
                       when source.source_type = 'CATALOG_TABLE' then
                           concat_ws('.', nullif(dataset.hive_database, ''), nullif(dataset.hive_table, ''))
                       when source.source_type = 'DBT_NODE' then
                           coalesce(nullif(source.locator_json ->> 'uniqueId', ''), source.source_id)
                       else null
                   end as executable_ref
              from modeling_warehouse_plan_source source
              left join catalog_table_schema table_schema
                on source.source_type = 'CATALOG_TABLE'
               and table_schema.id::text = coalesce(nullif(source.locator_json ->> 'assetId', ''), source.source_id)
              left join catalog_dataset dataset
                on source.source_type = 'CATALOG_TABLE'
               and dataset.id = table_schema.dataset_id
             where source.tenant_id = ? and source.id in (
            """ + placeholders + ") order by source.id";
        List<Object> arguments = new ArrayList<>();
        arguments.add(tenantId);
        arguments.addAll(ordered);
        return jdbcTemplate.query(sql, ModelImplementationDependencyReadAdapter::mapPhysicalSource, arguments.toArray());
    }

    private static ModelRow mapModel(ResultSet row) throws SQLException {
        Integer implementationRevision = (Integer) row.getObject("implementation_revision");
        Integer implementationModelRevision = (Integer) row.getObject("implementation_model_revision");
        return new ModelRow(
            row.getObject("model_spec_id", UUID.class),
            row.getInt("revision"),
            row.getString("content_checksum"),
            row.getString("snapshot_json"),
            implementationModelRevision == null ? 0 : implementationModelRevision,
            row.getString("implementation_model_checksum"),
            implementationRevision,
            row.getString("current_implementation_checksum"),
            row.getString("dbt_unique_id"),
            row.getString("implementation_status"),
            row.getObject("implementation_id", UUID.class),
            row.getObject("implementation_plan_id", UUID.class),
            row.getString("implementation_ownership"),
            row.getString("project_key"),
            row.getString("input_mode"),
            row.getString("inputs_json"),
            row.getString("field_mappings_json"),
            row.getString("settings_json"),
            row.getString("materialization")
        );
    }

    private ImplementationView implementation(ModelRow row) {
        if (row.implementationRevision() == null) return null;
        InputMode inputMode = InputMode.valueOf(row.inputMode());
        return new ImplementationView(
            row.implementationId(),
            row.modelSpecId(),
            row.implementationPlanId(),
            row.implementationModelRevision(),
            row.implementationModelChecksum(),
            ImplementationMode.valueOf(row.implementationOwnership()),
            row.projectKey(),
            row.dbtUniqueId(),
            row.implementationStatus(),
            row.implementationRevision(),
            row.implementationChecksum(),
            inputMode,
            readInputs(inputMode, row.inputsJson()),
            readFieldMappings(row.fieldMappingsJson()),
            readSettings(row.settingsJson()),
            row.materialization()
        );
    }

    private List<ImplementationInput> readInputs(InputMode inputMode, String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            return switch (inputMode) {
                case PHYSICAL_ASSET -> objectMapper
                    .readValue(value, new TypeReference<List<PhysicalAssetInput>>() {})
                    .stream()
                    .map(input -> (ImplementationInput) input)
                    .toList();
                case UPSTREAM_MODEL -> objectMapper
                    .readValue(value, new TypeReference<List<UpstreamModelInput>>() {})
                    .stream()
                    .map(input -> (ImplementationInput) input)
                    .toList();
                case GENERATED -> objectMapper
                    .readValue(value, new TypeReference<List<GeneratedInput>>() {})
                    .stream()
                    .map(input -> (ImplementationInput) input)
                    .toList();
            };
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw stale("Stored implementation input payload is invalid", null);
        }
    }

    private List<FieldMapping> readFieldMappings(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            return objectMapper.readValue(value, FIELD_MAPPINGS_TYPE);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw stale("Stored implementation field mapping payload is invalid", null);
        }
    }

    private Map<String, Object> readSettings(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(value, SETTINGS_TYPE);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw stale("Stored implementation settings payload is invalid", null);
        }
    }

    private static PhysicalSourceFact mapPhysicalSource(ResultSet row, int rowNumber) throws SQLException {
        String version = row.getString("source_version");
        String executableRef = row.getString("executable_ref");
        boolean current =
            "CONFIRMED".equals(row.getString("confirmation_status")) &&
            "AVAILABLE".equals(row.getString("resolution_status")) &&
            version != null &&
            !version.isBlank() &&
            executableRef != null &&
            !executableRef.isBlank();
        return new PhysicalSourceFact(
            row.getObject("id", UUID.class),
            version,
            current,
            row.getString("source_type"),
            executableRef
        );
    }

    private static ModelSpecException stale(String message, Object dependency) {
        return new ModelSpecException(
            "MODEL_IMPLEMENTATION_DEPENDENCY_PIN_STALE",
            message,
            ModelSpecException.Kind.CONFLICT,
            dependency == null ? null : java.util.Map.of("dependency", dependency)
        );
    }

    private record ModelRow(
        UUID modelSpecId,
        int revision,
        String modelChecksum,
        String snapshotJson,
        int implementationModelRevision,
        String implementationModelChecksum,
        Integer implementationRevision,
        String implementationChecksum,
        String dbtUniqueId,
        String implementationStatus,
        UUID implementationId,
        UUID implementationPlanId,
        String implementationOwnership,
        String projectKey,
        String inputMode,
        String inputsJson,
        String fieldMappingsJson,
        String settingsJson,
        String materialization
    ) {}
}
