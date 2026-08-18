package com.yuzhi.dts.platform.repository.modeling;

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
