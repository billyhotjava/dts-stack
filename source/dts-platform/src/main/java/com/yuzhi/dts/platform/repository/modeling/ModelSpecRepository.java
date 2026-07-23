package com.yuzhi.dts.platform.repository.modeling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import com.yuzhi.dts.platform.service.modeling.ModelSpecException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
                idempotency_request_hash, idempotency_response_snapshot
            ) values (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?,
                2, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), cast(? as jsonb),
                cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), cast(? as jsonb),
                cast(? as jsonb), ?, ?, ?, cast(? as jsonb)
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
            responseSnapshot
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
            ) values (?, ?, ?, ?, ?, ?, ?, ?, 2, cast(? as jsonb), ?)
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
            actorId
        );
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
}
