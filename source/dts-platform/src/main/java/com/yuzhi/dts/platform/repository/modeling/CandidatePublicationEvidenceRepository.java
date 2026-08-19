package com.yuzhi.dts.platform.repository.modeling;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException.Kind;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.ExpectedRelationType;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.PhysicalColumn;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the one immutable, successfully materialized physical observation set that a Candidate may publish.
 */
@Repository
public class CandidatePublicationEvidenceRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public CandidatePublicationEvidenceRepository(
        JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<PublicationEntryEvidence> requireCurrent(CandidateView candidate, boolean lockRows) {
        if (candidate == null) throw new IllegalArgumentException("candidate is required");
        List<PublicationEntryEvidence> evidence = jdbcTemplate.query(
            """
            select e.id as entry_id, e.model_spec_id, e.revision as model_revision,
                   e.checksum as model_checksum, e.implementation_revision,
                   e.implementation_checksum, e.dbt_unique_id, e.target_identifier,
                   e.artifact_bundle_checksum, e.dependency_snapshot_checksum,
                   latest.pipeline_run_group_id, pr.id as pipeline_run_id, pr.dbt_invocation_id,
                   o.adapter, o.database_name, o.schema_name, o.identifier,
                   o.actual_type as relation_type, o.actual_columns,
                   o.metadata_checksum, o.observed_at
              from modeling_model_release_candidate c
              join modeling_model_release_candidate_entry e
                on e.tenant_id = c.tenant_id
               and e.candidate_id = c.id
               and e.status = c.status
              join lateral (
                    select pr.id as pipeline_run_id,
                           pr.pipeline_run_group_id
                      from modeling_pipeline_run pr
                      join modeling_materialization_dispatch d
                        on d.tenant_id = pr.tenant_id
                       and d.id = pr.pipeline_run_group_id
                       and d.candidate_id = pr.release_candidate_id
                       and d.status = 'COMPLETED'
                     where pr.tenant_id = c.tenant_id
                       and pr.release_candidate_id = c.id
                       and pr.release_candidate_entry_id = e.id
                       and pr.run_purpose = 'RELEASE_BUILD'
                       and pr.status = 'BUILT'
                       and pr.model_revision = e.revision
                       and pr.model_checksum = e.checksum
                       and pr.implementation_revision = e.implementation_revision
                       and pr.implementation_checksum = e.implementation_checksum
                       and pr.target = e.target_identifier
                     order by d.attempt desc,
                              d.last_modified_at desc,
                              d.id desc,
                              pr.id desc
                     limit 1
              ) latest on true
              join modeling_pipeline_run pr
                on pr.tenant_id = c.tenant_id
               and pr.id = latest.pipeline_run_id
              join lateral (
                    select observation.adapter, observation.database_name,
                           observation.schema_name, observation.identifier,
                           observation.actual_type, observation.actual_columns,
                           observation.metadata_checksum, observation.observed_at
                      from modeling_physical_relation_observation observation
                     where observation.tenant_id = c.tenant_id
                       and observation.release_candidate_id = c.id
                       and observation.pipeline_run_group_id = latest.pipeline_run_group_id
                       and observation.pipeline_run_id = pr.id
                       and observation.model_spec_id = e.model_spec_id
                       and observation.model_revision = e.revision
                       and observation.model_checksum = e.checksum
                       and observation.implementation_revision = e.implementation_revision
                       and observation.implementation_checksum = e.implementation_checksum
                       and observation.verified = true
                       and observation.relation_exists = true
                       and observation.metadata_checksum is not null
                     order by observation.observation_attempt desc,
                              observation.created_date desc,
                              observation.id desc
                     limit 1
              ) o on true
             where c.tenant_id = ?
               and c.id = ?
               and c.plan_id = ?
               and c.version = ?
               and c.status = ?
             order by e.sort_order, e.id
            %s
            """.formatted(lockRows ? "for share of c, e, pr" : ""),
            (row, rowNumber) ->
                new PublicationEntryEvidence(
                    row.getObject("entry_id", UUID.class),
                    row.getObject("model_spec_id", UUID.class),
                    row.getInt("model_revision"),
                    row.getString("model_checksum"),
                    row.getInt("implementation_revision"),
                    row.getString("implementation_checksum"),
                    row.getString("dbt_unique_id"),
                    row.getString("target_identifier"),
                    row.getString("artifact_bundle_checksum"),
                    row.getString("dependency_snapshot_checksum"),
                    row.getObject("pipeline_run_group_id", UUID.class),
                    row.getObject("pipeline_run_id", UUID.class),
                    row.getObject("dbt_invocation_id", UUID.class),
                    row.getString("adapter"),
                    row.getString("database_name"),
                    row.getString("schema_name"),
                    row.getString("identifier"),
                    ExpectedRelationType.valueOf(row.getString("relation_type")),
                    readColumns(row.getString("actual_columns")),
                    row.getString("metadata_checksum"),
                    row.getTimestamp("observed_at").toInstant()
                ),
            candidate.tenantId(),
            candidate.id(),
            candidate.planId(),
            candidate.version(),
            candidate.status().name()
        );
        Set<UUID> expectedModels = candidate
            .entries()
            .stream()
            .map(entry -> entry.modelSpecId())
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<UUID> observedModels = evidence
            .stream()
            .map(PublicationEntryEvidence::modelSpecId)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (
            evidence.size() != candidate.entries().size() ||
            evidence.isEmpty() ||
            !expectedModels.equals(observedModels) ||
            evidence.stream().anyMatch(item -> item.actualColumns().isEmpty())
        ) {
            throw new ModelReleaseCandidateException(
                "MODEL_RELEASE_CURRENT_PHYSICAL_OBSERVATION_REQUIRED",
                "Every Candidate entry requires one current verified physical relation observation",
                Kind.UNPROCESSABLE,
                Map.of(
                    "candidateId",
                    candidate.id(),
                    "expectedEntries",
                    candidate.entries().size(),
                    "verifiedEntries",
                    evidence.size()
                )
            );
        }
        return List.copyOf(evidence);
    }

    @Transactional(readOnly = true)
    public UUID requirePublishedAssetId(
        CandidateView candidate,
        PublicationEntryEvidence observation
    ) {
        if (candidate == null) throw new IllegalArgumentException("candidate is required");
        if (observation == null) throw new IllegalArgumentException("observation is required");
        List<String> values = jdbcTemplate.queryForList(
            """
            select event.details_json ->> 'physicalAssetId'
              from modeling_model_lifecycle_event event
             where event.tenant_id = ?
               and event.model_spec_id = ?
               and event.model_revision = ?
               and event.model_checksum = ?
               and event.event_type = 'RELEASE'
               and event.status = 'PUBLISHED'
               and event.details_json ->> 'candidateId' = ?
               and event.details_json ->> 'executionTargetKey' = ?
               and upper(event.details_json ->> 'environment') = upper(?)
               and nullif(
                       btrim(event.details_json ->> 'physicalAssetId'),
                       ''
                   ) is not null
             order by event.created_date desc, event.id desc
             limit 2
            """,
            String.class,
            candidate.tenantId(),
            observation.modelSpecId(),
            observation.modelRevision(),
            observation.modelChecksum(),
            candidate.id().toString(),
            candidate.executionTargetKey(),
            candidate.environment()
        );
        if (values.size() != 1) {
            throw publishedAssetConflict(
                candidate,
                observation,
                "Rollback requires one exact published physical asset reference"
            );
        }
        try {
            return UUID.fromString(values.getFirst());
        } catch (RuntimeException failure) {
            throw publishedAssetConflict(
                candidate,
                observation,
                "Published physical asset reference is invalid"
            );
        }
    }

    private List<PhysicalColumn> readColumns(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            List<PhysicalColumn> columns = objectMapper.readValue(
                value,
                new TypeReference<List<PhysicalColumn>>() {}
            );
            return columns == null ? List.of() : List.copyOf(columns);
        } catch (Exception failure) {
            throw new ModelReleaseCandidateException(
                "MODEL_RELEASE_PHYSICAL_COLUMNS_INVALID",
                "Current physical observation columns cannot be projected to Catalog",
                Kind.UNPROCESSABLE
            );
        }
    }

    private static ModelReleaseCandidateException publishedAssetConflict(
        CandidateView candidate,
        PublicationEntryEvidence observation,
        String message
    ) {
        return new ModelReleaseCandidateException(
            "MODEL_RELEASE_PUBLISHED_ASSET_REQUIRED",
            message,
            Kind.CONFLICT,
            Map.of(
                "candidateId",
                candidate.id(),
                "modelSpecId",
                observation.modelSpecId(),
                "modelRevision",
                observation.modelRevision()
            )
        );
    }

    public record PublicationEntryEvidence(
        UUID entryId,
        UUID modelSpecId,
        int modelRevision,
        String modelChecksum,
        int implementationRevision,
        String implementationChecksum,
        String dbtUniqueId,
        String targetIdentifier,
        String artifactChecksum,
        String dependencySnapshotChecksum,
        UUID pipelineRunGroupId,
        UUID pipelineRunId,
        UUID dbtInvocationId,
        String adapter,
        String databaseName,
        String schemaName,
        String identifier,
        ExpectedRelationType relationType,
        List<PhysicalColumn> actualColumns,
        String metadataChecksum,
        Instant observedAt
    ) {
        public PublicationEntryEvidence {
            actualColumns = actualColumns == null
                ? List.of()
                : List.copyOf(actualColumns);
        }

        public PublicationEntryEvidence(
            UUID entryId,
            UUID modelSpecId,
            int modelRevision,
            String modelChecksum,
            int implementationRevision,
            String implementationChecksum,
            String dbtUniqueId,
            String targetIdentifier,
            String artifactChecksum,
            String dependencySnapshotChecksum,
            UUID pipelineRunGroupId,
            UUID pipelineRunId,
            UUID dbtInvocationId,
            String adapter,
            String databaseName,
            String schemaName,
            String identifier,
            ExpectedRelationType relationType,
            String metadataChecksum,
            Instant observedAt
        ) {
            this(
                entryId,
                modelSpecId,
                modelRevision,
                modelChecksum,
                implementationRevision,
                implementationChecksum,
                dbtUniqueId,
                targetIdentifier,
                artifactChecksum,
                dependencySnapshotChecksum,
                pipelineRunGroupId,
                pipelineRunId,
                dbtInvocationId,
                adapter,
                databaseName,
                schemaName,
                identifier,
                relationType,
                List.of(),
                metadataChecksum,
                observedAt
            );
        }
    }
}
