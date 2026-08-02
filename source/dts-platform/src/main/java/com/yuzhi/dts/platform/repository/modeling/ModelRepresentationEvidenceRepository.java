package com.yuzhi.dts.platform.repository.modeling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationEvidencePort.ArtifactEvidence;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationEvidencePort.ImplementationPin;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationEvidencePort.ImplementationSnapshot;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Read-only exact-pin projection over the canonical ModelSpec, implementation and dbt artifact ledgers. */
@Repository
@Transactional(readOnly = true)
public class ModelRepresentationEvidenceRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ModelRepresentationEvidenceRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<ImplementationPin> findCurrentPin(String tenantId, UUID modelSpecId) {
        return jdbcTemplate
            .query(
                """
                select implementation.id, implementation.implementation_revision,
                       implementation.current_implementation_checksum
                  from modeling_model_implementation implementation
                  join modeling_model_implementation_revision revision
                    on revision.tenant_id = implementation.tenant_id
                   and revision.implementation_id = implementation.id
                   and revision.revision = implementation.implementation_revision
                   and revision.content_checksum = implementation.current_implementation_checksum
                 where implementation.tenant_id = ? and implementation.model_spec_id = ?
                """,
                (row, rowNumber) ->
                    new ImplementationPin(
                        row.getObject("id", UUID.class),
                        row.getInt("implementation_revision"),
                        row.getString("current_implementation_checksum")
                    ),
                tenantId,
                modelSpecId
            )
            .stream()
            .findFirst();
    }

    public Optional<ImplementationSnapshot> findExactImplementation(
        String tenantId,
        UUID modelSpecId,
        int modelRevision,
        String modelChecksum,
        int implementationRevision
    ) {
        return jdbcTemplate
            .query(
                """
                select implementation.id, implementation.model_spec_id, spec.plan_id,
                       model_revision.revision as model_revision,
                       model_revision.content_checksum as model_checksum,
                       implementation_revision.revision as implementation_revision,
                       implementation_revision.content_checksum as implementation_checksum,
                       implementation_revision.ownership, implementation.project_key,
                       implementation.dbt_unique_id, implementation_revision.input_mode,
                       implementation_revision.inputs_json::text as inputs_json,
                       implementation_revision.field_mappings_json::text as field_mappings_json,
                       implementation_revision.settings_json::text as settings_json,
                       implementation_revision.materialization
                  from modeling_model_spec spec
                  join modeling_model_spec_revision model_revision
                    on model_revision.tenant_id = spec.tenant_id
                   and model_revision.model_spec_id = spec.id
                  join modeling_model_implementation implementation
                    on implementation.tenant_id = spec.tenant_id
                   and implementation.model_spec_id = spec.id
                  join modeling_model_implementation_revision implementation_revision
                    on implementation_revision.tenant_id = implementation.tenant_id
                   and implementation_revision.implementation_id = implementation.id
                 where spec.tenant_id = ? and spec.id = ? and spec.contract_version = 2
                   and model_revision.contract_version = 2
                   and model_revision.revision = ? and model_revision.content_checksum = ?
                   and implementation_revision.revision = ?
                   and exists (
                       select 1
                         from modeling_dbt_artifact artifact_binding
                        where artifact_binding.model_spec_id = spec.id
                          and artifact_binding.revision = model_revision.revision
                          and artifact_binding.model_checksum = model_revision.content_checksum
                          and artifact_binding.implementation_revision = implementation_revision.revision
                          and artifact_binding.ownership = implementation_revision.ownership
                          and artifact_binding.status in ('COMPILED', 'IMPORTED')
                   )
                """,
                (row, rowNumber) ->
                    new ImplementationSnapshot(
                        row.getObject("id", UUID.class),
                        row.getObject("model_spec_id", UUID.class),
                        row.getObject("plan_id", UUID.class),
                        row.getInt("model_revision"),
                        row.getString("model_checksum"),
                        row.getInt("implementation_revision"),
                        row.getString("implementation_checksum"),
                        ImplementationMode.valueOf(row.getString("ownership")),
                        row.getString("project_key"),
                        row.getString("dbt_unique_id"),
                        row.getString("input_mode"),
                        json(row.getString("inputs_json")),
                        json(row.getString("field_mappings_json")),
                        json(row.getString("settings_json")),
                        row.getString("materialization")
                    ),
                tenantId,
                modelSpecId,
                modelRevision,
                modelChecksum,
                implementationRevision
            )
            .stream()
            .findFirst();
    }

    public List<ArtifactEvidence> listExactArtifacts(
        String tenantId,
        UUID modelSpecId,
        int modelRevision,
        String modelChecksum,
        int implementationRevision,
        String implementationChecksum,
        boolean includeTechnicalContent
    ) {
        return jdbcTemplate.query(
            """
            select artifact.artifact_type, artifact.path, artifact.content_checksum,
                   artifact.status, artifact.content
              from modeling_dbt_artifact artifact
              join modeling_model_spec spec
                on spec.id = artifact.model_spec_id and spec.tenant_id = ?
              join modeling_model_implementation implementation
                on implementation.tenant_id = spec.tenant_id
               and implementation.model_spec_id = spec.id
              join modeling_model_implementation_revision implementation_revision
                on implementation_revision.tenant_id = implementation.tenant_id
               and implementation_revision.implementation_id = implementation.id
               and implementation_revision.revision = artifact.implementation_revision
             where artifact.model_spec_id = ? and artifact.revision = ?
               and artifact.model_checksum = ? and artifact.implementation_revision = ?
               and implementation_revision.content_checksum = ?
               and artifact.ownership = implementation_revision.ownership
               and artifact.status in ('COMPILED', 'IMPORTED')
             order by artifact.artifact_type, artifact.path, artifact.id
            """,
            (row, rowNumber) -> {
                String artifactType = row.getString("artifact_type");
                String content = row.getString("content");
                if (!includeTechnicalContent && "SQL".equalsIgnoreCase(artifactType)) content = null;
                return new ArtifactEvidence(
                    artifactType,
                    row.getString("path"),
                    row.getString("content_checksum"),
                    row.getString("status"),
                    content,
                    modelSpecId,
                    modelRevision,
                    modelChecksum,
                    implementationRevision,
                    implementationChecksum
                );
            },
            tenantId,
            modelSpecId,
            modelRevision,
            modelChecksum,
            implementationRevision,
            implementationChecksum
        );
    }

    private JsonNode json(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored model implementation evidence is invalid", exception);
        }
    }
}
