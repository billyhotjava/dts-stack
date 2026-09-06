package com.yuzhi.dts.platform.repository.modeling;

import com.yuzhi.dts.platform.service.modeling.PlanExecutionException;
import com.yuzhi.dts.platform.service.modeling.PlanExecutionException.Kind;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reads the immutable implementation-input snapshots used by a release materialization. */
@Repository
public class ModelMaterializationSourceSnapshotRepository {

    private static final String CURRENT_INPUTS_SELECT = """
        select c.tenant_id, c.plan_id, e.model_spec_id, e.revision as model_revision,
               e.checksum as model_checksum, r.input_mode, r.inputs_json::text as inputs_json,
               model_revision.snapshot_json -> 'sourceRefs' as source_refs_json
          from modeling_model_release_candidate c
          join modeling_model_release_candidate_entry e
            on e.tenant_id = c.tenant_id and e.candidate_id = c.id and e.status = c.status
          left join modeling_model_spec_revision model_revision
            on model_revision.tenant_id = e.tenant_id
           and model_revision.model_spec_id = e.model_spec_id
           and model_revision.revision = e.revision
           and model_revision.content_checksum = e.checksum
           and model_revision.contract_version = 2
          left join modeling_model_implementation i
            on i.tenant_id = e.tenant_id and i.plan_id = e.plan_id
           and i.model_spec_id = e.model_spec_id and i.model_revision = e.revision
           and i.model_checksum = e.checksum and i.ownership = e.implementation_mode
           and i.status = 'ACTIVE'
          left join modeling_model_implementation_revision r
            on r.tenant_id = i.tenant_id and r.implementation_id = i.id
           and r.revision = i.implementation_revision
           and r.content_checksum = i.current_implementation_checksum
        """;

    private static final String LOCKED_INPUTS_SELECT = """
        select c.tenant_id, c.plan_id, e.model_spec_id, e.revision as model_revision,
               e.checksum as model_checksum, r.input_mode, r.inputs_json::text as inputs_json,
               model_revision.snapshot_json -> 'sourceRefs' as source_refs_json
          from modeling_model_release_candidate c
          join modeling_model_release_candidate_entry e
            on e.tenant_id = c.tenant_id and e.candidate_id = c.id and e.status = c.status
          left join modeling_model_spec_revision model_revision
            on model_revision.tenant_id = e.tenant_id
           and model_revision.model_spec_id = e.model_spec_id
           and model_revision.revision = e.revision
           and model_revision.content_checksum = e.checksum
           and model_revision.contract_version = 2
          left join modeling_model_implementation_revision r
            on r.tenant_id = e.tenant_id
           and r.implementation_id = e.implementation_id
           and r.revision = e.implementation_revision
           and r.content_checksum = e.implementation_checksum
        """;

    private static final String OPERATIONAL_SCOPE_COUNT = """
        select count(*)
          from modeling_operational_run_dispatch dispatch
          join modeling_pipeline_run pipeline_run
            on pipeline_run.pipeline_run_group_id = dispatch.id
           and pipeline_run.run_purpose = 'OPERATIONAL_RUN'
         where dispatch.id = ?
        """;

    private static final String OPERATIONAL_INPUTS_SELECT = """
        select pipeline_run.tenant_id, pipeline_run.plan_id,
               pipeline_run.model_spec_id,
               pipeline_run.model_revision,
               pipeline_run.model_checksum,
               implementation_revision.input_mode,
               implementation_revision.inputs_json::text as inputs_json,
               model_revision.snapshot_json -> 'sourceRefs' as source_refs_json
          from modeling_operational_run_dispatch dispatch
          join modeling_plan_execution_binding binding
            on binding.id = dispatch.binding_id
           and binding.tenant_id = dispatch.tenant_id
           and binding.version = dispatch.binding_version
           and binding.execution_target_key = dispatch.execution_target_key
          join modeling_pipeline_run pipeline_run
            on pipeline_run.pipeline_run_group_id = dispatch.id
           and pipeline_run.run_purpose = 'OPERATIONAL_RUN'
           and pipeline_run.tenant_id = dispatch.tenant_id
           and pipeline_run.execution_binding_id = dispatch.binding_id
           and pipeline_run.binding_version = dispatch.binding_version
           and pipeline_run.plan_id = binding.plan_id
           and pipeline_run.environment = binding.environment
           and pipeline_run.target = dispatch.target_name
          join modeling_plan_execution_binding_entry binding_entry
            on binding_entry.tenant_id = pipeline_run.tenant_id
           and binding_entry.binding_id = dispatch.binding_id
           and binding_entry.model_spec_id = pipeline_run.model_spec_id
           and binding_entry.model_revision = pipeline_run.model_revision
          join modeling_model_lifecycle_event release_event
            on release_event.id = binding_entry.published_release_id
           and release_event.tenant_id = pipeline_run.tenant_id
           and release_event.plan_id = pipeline_run.plan_id
           and release_event.model_spec_id = pipeline_run.model_spec_id
           and release_event.model_revision = pipeline_run.model_revision
           and release_event.model_checksum = pipeline_run.model_checksum
           and release_event.event_type = 'RELEASE'
           and release_event.status = 'PUBLISHED'
           and release_event.details_json ->> 'executionTargetKey' = binding.execution_target_key
           and release_event.details_json ->> 'environment' = binding.environment
           and release_event.details_json ->> 'targetIdentifier' = binding_entry.target_identifier
           and release_event.details_json ->> 'dbtUniqueId' = binding_entry.dbt_unique_id
           and release_event.details_json ->> 'candidateVersion' ~ '^[1-9][0-9]*$'
           and release_event.details_json ->> 'implementationRevision' =
               pipeline_run.implementation_revision::text
           and release_event.details_json ->> 'implementationChecksum' =
               pipeline_run.implementation_checksum
          join modeling_model_release_candidate candidate
            on candidate.tenant_id = release_event.tenant_id
           and candidate.id = case
                 when release_event.details_json ->> 'candidateId' ~
                     '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
                 then (release_event.details_json ->> 'candidateId')::uuid
               end
           and candidate.plan_id = pipeline_run.plan_id
          join modeling_model_release_candidate_entry candidate_entry
            on candidate_entry.tenant_id = pipeline_run.tenant_id
           and candidate_entry.candidate_id = candidate.id
           and candidate_entry.plan_id = pipeline_run.plan_id
           and candidate_entry.model_spec_id = pipeline_run.model_spec_id
           and candidate_entry.revision = pipeline_run.model_revision
           and candidate_entry.checksum = pipeline_run.model_checksum
           and candidate_entry.implementation_revision = pipeline_run.implementation_revision
           and candidate_entry.implementation_checksum = pipeline_run.implementation_checksum
          join modeling_model_implementation_revision implementation_revision
            on implementation_revision.tenant_id = candidate_entry.tenant_id
           and implementation_revision.implementation_id = candidate_entry.implementation_id
           and implementation_revision.revision = pipeline_run.implementation_revision
           and implementation_revision.content_checksum = pipeline_run.implementation_checksum
           and implementation_revision.ownership = candidate_entry.implementation_mode
          join modeling_model_spec_revision model_revision
            on model_revision.tenant_id = pipeline_run.tenant_id
           and model_revision.model_spec_id = pipeline_run.model_spec_id
           and model_revision.revision = pipeline_run.model_revision
           and model_revision.content_checksum = pipeline_run.model_checksum
           and model_revision.contract_version = 2
        """;

    private final JdbcTemplate jdbcTemplate;

    public ModelMaterializationSourceSnapshotRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<InputSnapshot> findCandidateInputs(String tenantId, UUID candidateId, int candidateVersion) {
        return jdbcTemplate.query(
            CURRENT_INPUTS_SELECT + " where c.tenant_id = ? and c.id = ? and c.version = ? order by e.sort_order, e.id",
            ModelMaterializationSourceSnapshotRepository::map,
            tenantId,
            candidateId,
            candidateVersion
        );
    }

    public List<InputSnapshot> findDispatchInputs(UUID dispatchId) {
        // candidate_version is the dispatch creation CAS. The Candidate head advances while the
        // entry's implementation identity remains the immutable snapshot for this materialization.
        return jdbcTemplate.query(
            LOCKED_INPUTS_SELECT +
                " join modeling_materialization_dispatch d " +
                "on d.tenant_id = c.tenant_id and d.candidate_id = c.id " +
                "where d.id = ? order by e.sort_order, e.id",
            ModelMaterializationSourceSnapshotRepository::map,
            dispatchId
        );
    }

    /**
     * Returns every operational-run root only when each pipeline row still proves the exact
     * published implementation and source snapshot captured when the run was opened.
     */
    public List<InputSnapshot> findOperationalInputs(UUID groupId) {
        if (groupId == null) throw new IllegalArgumentException("groupId is required");
        Integer expectedScope = jdbcTemplate.queryForObject(
            OPERATIONAL_SCOPE_COUNT,
            Integer.class,
            groupId
        );
        if (expectedScope == null || expectedScope < 1) {
            throw operationalInputMissing();
        }
        List<InputSnapshot> snapshots = jdbcTemplate.query(
            OPERATIONAL_INPUTS_SELECT + " where dispatch.id = ? order by pipeline_run.model_spec_id, pipeline_run.id",
            ModelMaterializationSourceSnapshotRepository::map,
            groupId
        );
        if (snapshots.size() != expectedScope) {
            throw operationalInputMismatch();
        }
        return snapshots;
    }

    public List<InputSnapshot> findPublishedInput(
        String tenantId,
        UUID modelSpecId,
        int modelRevision,
        String modelChecksum
    ) {
        return jdbcTemplate.query(
            """
            select s.tenant_id, s.plan_id, s.id as model_spec_id, s.revision as model_revision,
                   s.current_checksum as model_checksum, r.input_mode, r.inputs_json::text as inputs_json,
                   model_revision.snapshot_json -> 'sourceRefs' as source_refs_json
              from modeling_model_spec s
              join modeling_model_spec_revision model_revision
                on model_revision.tenant_id = s.tenant_id
               and model_revision.model_spec_id = s.id
               and model_revision.revision = s.revision
               and model_revision.content_checksum = s.current_checksum
               and model_revision.contract_version = 2
              join modeling_model_implementation i
                on i.tenant_id = s.tenant_id and i.plan_id = s.plan_id
               and i.model_spec_id = s.id and i.model_revision = s.revision
               and i.model_checksum = s.current_checksum and i.status = 'ACTIVE'
              join modeling_model_implementation_revision r
                on r.tenant_id = i.tenant_id and r.implementation_id = i.id
               and r.revision = i.implementation_revision
               and r.content_checksum = i.current_implementation_checksum
             where s.tenant_id = ? and s.id = ? and s.revision = ?
               and s.current_checksum = ? and s.status = 'PUBLISHED' and s.contract_version = 2
            """,
            ModelMaterializationSourceSnapshotRepository::map,
            tenantId,
            modelSpecId,
            modelRevision,
            modelChecksum
        );
    }

    public java.util.Optional<SourceBindingDescriptor> findSourceBindingDescriptor(
        String tenantId,
        UUID planId,
        UUID sourceBindingId,
        String resolvedVersion
    ) {
        return jdbcTemplate
            .query(
                """
                select id, source_type, source_id, locator_json::text as locator_json,
                       source_version
                  from modeling_warehouse_plan_source
                 where tenant_id = ? and plan_id = ? and id = ?
                   and confirmation_status = 'CONFIRMED'
                   and source_version = ?
                """,
                (row, rowNumber) ->
                    new SourceBindingDescriptor(
                        row.getObject("id", UUID.class),
                        row.getString("source_type"),
                        row.getString("source_id"),
                        row.getString("locator_json"),
                        row.getString("source_version")
                    ),
                tenantId,
                planId,
                sourceBindingId,
                resolvedVersion
            )
            .stream()
            .findFirst();
    }

    private static InputSnapshot map(java.sql.ResultSet row, int rowNumber) throws java.sql.SQLException {
        return new InputSnapshot(
            row.getString("tenant_id"),
            row.getObject("plan_id", UUID.class),
            row.getObject("model_spec_id", UUID.class),
            row.getInt("model_revision"),
            row.getString("model_checksum"),
            row.getString("input_mode"),
            row.getString("inputs_json"),
            row.getString("source_refs_json")
        );
    }

    private static PlanExecutionException operationalInputMissing() {
        return new PlanExecutionException(
            "MODEL_OPERATIONAL_SOURCE_SNAPSHOT_MISSING",
            "Operational materialization inputs are missing",
            Kind.CONFLICT
        );
    }

    private static PlanExecutionException operationalInputMismatch() {
        return new PlanExecutionException(
            "MODEL_OPERATIONAL_SOURCE_SNAPSHOT_MISMATCH",
            "Operational materialization inputs cannot prove their published snapshot",
            Kind.CONFLICT
        );
    }

    public record InputSnapshot(
        String tenantId,
        UUID planId,
        UUID modelSpecId,
        int modelRevision,
        String modelChecksum,
        String inputMode,
        String inputsJson,
        String sourceRefsJson
    ) {}

    public record SourceBindingDescriptor(
        UUID sourceBindingId,
        String sourceType,
        String sourceId,
        String locatorJson,
        String resolvedVersion
    ) {}
}
