package com.yuzhi.dts.platform.repository.modeling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.ExpectedRelationType;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.PhysicalColumn;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Append-only PostgreSQL adapter for revision-bound physical relation observations. */
@Repository
public class PhysicalRelationObservationRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PhysicalRelationObservationRepository(
        JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<PersistedObservation> appendAll(
        List<ObservationWrite> observations
    ) {
        if (observations == null || observations.isEmpty()) {
            throw new IllegalArgumentException(
                "observations are required"
            );
        }
        List<PersistedObservation> persisted =
            new ArrayList<>(observations.size());
        for (ObservationWrite observation : observations) {
            persisted.add(append(observation));
        }
        return List.copyOf(persisted);
    }

    @Transactional(readOnly = true)
    public List<PersistedObservation> findCurrent(
        UUID pipelineRunGroupId
    ) {
        if (pipelineRunGroupId == null) {
            throw new IllegalArgumentException(
                "pipelineRunGroupId is required"
            );
        }
        return jdbcTemplate.query(
            """
            select distinct on (pipeline_run_id)
                   id, pipeline_run_id, model_spec_id,
                   observation_attempt, verified, relation_exists,
                   adapter, database_name, schema_name, identifier,
                   expected_type, actual_type, column_count,
                   observed_at, error_code, metadata_checksum
              from modeling_physical_relation_observation
             where pipeline_run_group_id = ?
             order by pipeline_run_id,
                      observation_attempt desc,
                      created_date desc,
                      id desc
            """,
            (row, rowNumber) ->
                new PersistedObservation(
                    row.getObject("id", UUID.class),
                    row.getObject(
                        "pipeline_run_id",
                        UUID.class
                    ),
                    row.getObject(
                        "model_spec_id",
                        UUID.class
                    ),
                    row.getInt("observation_attempt"),
                    row.getBoolean("verified"),
                    row.getBoolean("relation_exists"),
                    row.getString("adapter"),
                    row.getString("database_name"),
                    row.getString("schema_name"),
                    row.getString("identifier"),
                    ExpectedRelationType.valueOf(
                        row.getString("expected_type")
                    ),
                    row.getString("actual_type") == null
                        ? null
                        : ExpectedRelationType.valueOf(
                            row.getString("actual_type")
                        ),
                    row.getInt("column_count"),
                    row
                        .getTimestamp("observed_at")
                        .toInstant(),
                    row.getString("error_code"),
                    row.getString("metadata_checksum")
                ),
            pipelineRunGroupId
        );
    }

    private PersistedObservation append(
        ObservationWrite observation
    ) {
        Objects.requireNonNull(
            observation,
            "observation is required"
        );
        PipelineIdentity pipeline = lockPipeline(
            observation.pipelineRunId()
        );
        requireBound(observation, pipeline);
        Integer nextAttempt = jdbcTemplate.queryForObject(
            """
            select coalesce(max(observation_attempt), 0) + 1
              from modeling_physical_relation_observation
             where pipeline_run_id = ?
               and model_spec_id = ?
               and implementation_revision = ?
            """,
            Integer.class,
            observation.pipelineRunId(),
            observation.modelSpecId(),
            observation.implementationRevision()
        );
        int attempt = nextAttempt == null ? 1 : nextAttempt;
        UUID id = UUID.randomUUID();
        String actualColumns = observation.relationExists()
            ? json(observation.actualColumns())
            : null;
        int inserted = jdbcTemplate.update(
            """
            insert into modeling_physical_relation_observation (
                id, tenant_id, release_candidate_id,
                release_candidate_version, pipeline_run_group_id,
                pipeline_run_id, model_spec_id, model_revision,
                model_checksum, implementation_revision,
                implementation_checksum, dbt_invocation_id,
                scoped_bundle_checksum, observation_attempt,
                adapter, credential_version_ref, database_name,
                schema_name, identifier, expected_type, actual_type,
                relation_exists, verified, column_count,
                expected_columns_checksum, columns_checksum,
                actual_columns, metadata_checksum, error_code,
                observed_at, created_date
            ) values (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?,
                ?, ?
            )
            """,
            id,
            observation.tenantId(),
            observation.candidateId(),
            observation.candidateVersion(),
            observation.pipelineRunGroupId(),
            observation.pipelineRunId(),
            observation.modelSpecId(),
            observation.modelRevision(),
            observation.modelChecksum(),
            observation.implementationRevision(),
            observation.implementationChecksum(),
            observation.dbtInvocationId(),
            observation.scopedBundleChecksum(),
            attempt,
            observation.adapter(),
            observation.credentialVersionRef(),
            observation.databaseName(),
            observation.schemaName(),
            observation.identifier(),
            observation.expectedType().name(),
            observation.actualType() == null
                ? null
                : observation.actualType().name(),
            observation.relationExists(),
            observation.verified(),
            observation.actualColumns().size(),
            observation.expectedColumnsChecksum(),
            observation.columnsChecksum(),
            actualColumns,
            observation.metadataChecksum(),
            observation.errorCode(),
            Timestamp.from(observation.observedAt()),
            Timestamp.from(observation.createdAt())
        );
        if (inserted != 1) {
            throw new IllegalStateException(
                "Physical relation observation was not appended"
            );
        }
        return new PersistedObservation(
            id,
            observation.pipelineRunId(),
            observation.modelSpecId(),
            attempt,
            observation.verified(),
            observation.relationExists(),
            observation.adapter(),
            observation.databaseName(),
            observation.schemaName(),
            observation.identifier(),
            observation.expectedType(),
            observation.actualType(),
            observation.actualColumns().size(),
            observation.observedAt(),
            observation.errorCode(),
            observation.metadataChecksum()
        );
    }

    private PipelineIdentity lockPipeline(UUID pipelineRunId) {
        if (pipelineRunId == null) {
            throw new IllegalArgumentException(
                "pipelineRunId is required"
            );
        }
        return jdbcTemplate
            .query(
                """
                select tenant_id, release_candidate_id,
                       release_candidate_version,
                       pipeline_run_group_id, model_spec_id,
                       model_revision, model_checksum,
                       implementation_revision,
                       implementation_checksum,
                       dbt_invocation_id, scoped_bundle_checksum,
                       status, started_date
                  from modeling_pipeline_run
                 where id = ?
                   and run_purpose = 'RELEASE_BUILD'
                 for update
                """,
                (row, rowNumber) ->
                    new PipelineIdentity(
                        row.getString("tenant_id"),
                        row.getObject(
                            "release_candidate_id",
                            UUID.class
                        ),
                        row.getInt(
                            "release_candidate_version"
                        ),
                        row.getObject(
                            "pipeline_run_group_id",
                            UUID.class
                        ),
                        row.getObject(
                            "model_spec_id",
                            UUID.class
                        ),
                        row.getInt("model_revision"),
                        row.getString("model_checksum"),
                        row.getInt(
                            "implementation_revision"
                        ),
                        row.getString(
                            "implementation_checksum"
                        ),
                        row.getObject(
                            "dbt_invocation_id",
                            UUID.class
                        ),
                        row.getString(
                            "scoped_bundle_checksum"
                        ),
                        row.getString("status"),
                        row.getTimestamp("started_date") == null
                            ? null
                            : row
                                .getTimestamp("started_date")
                                .toInstant()
                    ),
                pipelineRunId
            )
            .stream()
            .findFirst()
            .orElseThrow(() ->
                new IllegalStateException(
                    "Physical relation observation has no pipeline run"
                )
            );
    }

    private static void requireBound(
        ObservationWrite observation,
        PipelineIdentity pipeline
    ) {
        if (
            !Objects.equals(
                observation.tenantId(),
                pipeline.tenantId()
            ) ||
            !Objects.equals(
                observation.candidateId(),
                pipeline.candidateId()
            ) ||
            observation.candidateVersion() !=
            pipeline.candidateVersion() ||
            !Objects.equals(
                observation.pipelineRunGroupId(),
                pipeline.pipelineRunGroupId()
            ) ||
            !Objects.equals(
                observation.modelSpecId(),
                pipeline.modelSpecId()
            ) ||
            observation.modelRevision() !=
            pipeline.modelRevision() ||
            !Objects.equals(
                observation.modelChecksum(),
                pipeline.modelChecksum()
            ) ||
            observation.implementationRevision() !=
            pipeline.implementationRevision() ||
            !Objects.equals(
                observation.implementationChecksum(),
                pipeline.implementationChecksum()
            ) ||
            !Objects.equals(
                observation.dbtInvocationId(),
                pipeline.dbtInvocationId()
            ) ||
            !Objects.equals(
                observation.scopedBundleChecksum(),
                pipeline.scopedBundleChecksum()
            ) ||
            !java.util.Set.of(
                "DBT_SUCCEEDED",
                "BUILT"
            ).contains(pipeline.status()) ||
            pipeline.startedAt() == null ||
            observation
                .observedAt()
                .isBefore(pipeline.startedAt())
        ) {
            throw new IllegalStateException(
                "Physical relation observation is stale or not revision-bound"
            );
        }
    }

    private String json(List<PhysicalColumn> columns) {
        try {
            return objectMapper.writeValueAsString(columns);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException(
                "Physical relation columns cannot be serialized",
                failure
            );
        }
    }

    private record PipelineIdentity(
        String tenantId,
        UUID candidateId,
        int candidateVersion,
        UUID pipelineRunGroupId,
        UUID modelSpecId,
        int modelRevision,
        String modelChecksum,
        int implementationRevision,
        String implementationChecksum,
        UUID dbtInvocationId,
        String scopedBundleChecksum,
        String status,
        Instant startedAt
    ) {}

    public record ObservationWrite(
        String tenantId,
        UUID candidateId,
        int candidateVersion,
        UUID pipelineRunGroupId,
        UUID pipelineRunId,
        UUID modelSpecId,
        int modelRevision,
        String modelChecksum,
        int implementationRevision,
        String implementationChecksum,
        UUID dbtInvocationId,
        String scopedBundleChecksum,
        String adapter,
        String credentialVersionRef,
        String databaseName,
        String schemaName,
        String identifier,
        ExpectedRelationType expectedType,
        ExpectedRelationType actualType,
        boolean relationExists,
        boolean verified,
        List<PhysicalColumn> actualColumns,
        String expectedColumnsChecksum,
        String columnsChecksum,
        String metadataChecksum,
        String errorCode,
        Instant observedAt,
        Instant createdAt
    ) {
        public ObservationWrite {
            actualColumns = actualColumns == null
                ? List.of()
                : List.copyOf(actualColumns);
        }
    }

    public record PersistedObservation(
        UUID id,
        UUID pipelineRunId,
        UUID modelSpecId,
        int observationAttempt,
        boolean verified,
        boolean relationExists,
        String adapter,
        String databaseName,
        String schemaName,
        String identifier,
        ExpectedRelationType expectedType,
        ExpectedRelationType actualType,
        int columnCount,
        Instant observedAt,
        String errorCode,
        String metadataChecksum
    ) {}
}
