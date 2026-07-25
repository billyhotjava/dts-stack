package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.imports.checksum.ModelPackageChecksum;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * F3-only persistence boundary for sanitized dbt package artifacts.
 *
 * <p>Imported evidence is intentionally stored as {@code IMPORTED}; the existing compile and test
 * gates continue to require independently produced {@code COMPILED} evidence. Every row is pinned
 * to the current canonical model and append-only implementation revision.
 */
@Service
public class ModelingDbtArtifactImportService {

    private final JdbcTemplate jdbcTemplate;

    public ModelingDbtArtifactImportService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public ImportResult importArtifacts(ImportCommand command) {
        requirePinnedDbtImplementation(command);
        List<String> nodeIds = command
            .artifacts()
            .stream()
            .map(ImportedArtifact::dbtUniqueId)
            .distinct()
            .sorted()
            .toList();
        for (String nodeId : nodeIds) {
            lockNode(command.tenantId(), command.projectKey(), nodeId);
            requireNodeAvailable(command, nodeId);
        }

        List<ImportedArtifact> artifacts = command
            .artifacts()
            .stream()
            .sorted(
                Comparator
                    .comparing(ImportedArtifact::dbtUniqueId)
                    .thenComparing(item -> item.artifactType().name())
            )
            .toList();
        Instant now = Instant.now();
        try {
            for (ImportedArtifact artifact : artifacts) {
                persistArtifact(command, artifact, now);
            }
        } catch (DataIntegrityViolationException conflict) {
            throw conflict(
                "MODEL_ARTIFACT_REVISION_CONFLICT",
                "Imported dbt artifact conflicts with the pinned model or implementation revision"
            );
        }
        return new ImportResult(
            command.modelSpecId(),
            command.planId(),
            command.modelRevision(),
            command.modelChecksum(),
            command.implementationId(),
            command.implementationRevision(),
            command.implementationChecksum(),
            artifacts.size()
        );
    }

    private void requirePinnedDbtImplementation(ImportCommand command) {
        List<UUID> pinned = jdbcTemplate.queryForList(
            """
            select i.id
              from modeling_model_spec s
              join modeling_model_implementation i
                on i.tenant_id = s.tenant_id and i.model_spec_id = s.id
              join modeling_model_implementation_revision ir
                on ir.tenant_id = i.tenant_id
               and ir.implementation_id = i.id
               and ir.revision = i.implementation_revision
             where s.tenant_id = ? and s.id = ? and s.plan_id = ?
               and s.revision = ? and s.current_checksum = ? and s.status = ?
               and i.id = ? and i.plan_id = s.plan_id
               and i.model_revision = s.revision and i.model_checksum = s.current_checksum
               and i.status = 'ACTIVE' and i.ownership = 'DBT_MANAGED'
               and i.project_key = ? and i.dbt_unique_id = ?
               and i.implementation_revision = ? and i.current_implementation_checksum = ?
               and ir.content_checksum = i.current_implementation_checksum
               and i.input_mode = 'GENERATED'
               and jsonb_array_length(i.inputs_json) = 1
               and i.inputs_json -> 0 ->> 'generatorType' = 'DBT'
               and i.inputs_json -> 0 -> 'config' ->> 'projectKey' = i.project_key
               and i.inputs_json -> 0 -> 'config' ->> 'dbtUniqueId' = i.dbt_unique_id
             for share of s, i, ir
            """,
            UUID.class,
            command.tenantId(),
            command.modelSpecId(),
            command.planId(),
            command.modelRevision(),
            command.modelChecksum(),
            command.expectedModelStatus().name(),
            command.implementationId(),
            command.projectKey(),
            command.modelDbtUniqueId(),
            command.implementationRevision(),
            command.implementationChecksum()
        );
        if (pinned.size() != 1) {
            throw conflict(
                "MODEL_IMPORT_ARTIFACT_PIN_CONFLICT",
                "The DBT artifact import no longer matches the current model and implementation pins"
            );
        }
    }

    private void lockNode(String tenantId, String projectKey, String dbtUniqueId) {
        jdbcTemplate.query(
            "select pg_advisory_xact_lock(hashtextextended(?, 0))",
            (org.springframework.jdbc.core.ResultSetExtractor<Void>) resultSet -> {
                resultSet.next();
                return null;
            },
            "dbt-artifact:" +
            tenantId.length() + ":" + tenantId +
            projectKey.length() + ":" + projectKey +
            dbtUniqueId.length() + ":" + dbtUniqueId
        );
    }

    private void requireNodeAvailable(ImportCommand command, String dbtUniqueId) {
        Boolean ownedElsewhere = jdbcTemplate.queryForObject(
            """
            select exists (
                select 1
                  from modeling_model_implementation i
                 where i.tenant_id = ? and i.project_key = ? and i.dbt_unique_id = ?
                   and i.model_spec_id <> ?
                union all
                select 1
                  from modeling_dbt_artifact a
                  join modeling_model_spec s on s.id = a.model_spec_id
                 where s.tenant_id = ? and a.ownership = 'DBT_MANAGED'
                   and a.project_key = ? and a.dbt_unique_id = ?
                   and a.model_spec_id <> ?
            )
            """,
            Boolean.class,
            command.tenantId(),
            command.projectKey(),
            dbtUniqueId,
            command.modelSpecId(),
            command.tenantId(),
            command.projectKey(),
            dbtUniqueId,
            command.modelSpecId()
        );
        if (Boolean.TRUE.equals(ownedElsewhere)) {
            throw conflict(
                "MODEL_IMPLEMENTATION_DBT_CONFLICT",
                "The dbt node is already owned by another ModelSpec"
            );
        }
    }

    private void persistArtifact(ImportCommand command, ImportedArtifact artifact, Instant now) {
        String artifactKey = artifact.artifactType().name() + ":" + artifact.dbtUniqueId();
        jdbcTemplate.update(
            """
            insert into modeling_dbt_artifact (
                id, model_spec_id, plan_id, project_key, dbt_unique_id, artifact_key,
                artifact_type, path, content_checksum, content, status, revision,
                model_checksum, ownership, idempotency_key, implementation_revision,
                node_kind, materialization, physical_asset_ref, created_date, last_modified_date
            )
            select ?, s.id, s.plan_id, i.project_key, ?, ?, ?, ?, ?, ?, 'IMPORTED',
                   s.revision, s.current_checksum, 'DBT_MANAGED', ?, i.implementation_revision,
                   ?, ?, null, ?, ?
              from modeling_model_spec s
              join modeling_model_implementation i
                on i.tenant_id = s.tenant_id and i.model_spec_id = s.id
              join modeling_model_implementation_revision ir
                on ir.tenant_id = i.tenant_id
               and ir.implementation_id = i.id
               and ir.revision = i.implementation_revision
             where s.tenant_id = ? and s.id = ? and s.plan_id = ?
               and s.revision = ? and s.current_checksum = ? and s.status = ?
               and i.id = ? and i.ownership = 'DBT_MANAGED' and i.status = 'ACTIVE'
               and i.project_key = ? and i.dbt_unique_id = ?
               and i.model_revision = s.revision and i.model_checksum = s.current_checksum
               and i.implementation_revision = ? and i.current_implementation_checksum = ?
               and ir.content_checksum = i.current_implementation_checksum
            on conflict do nothing
            """,
            UUID.randomUUID(),
            artifact.dbtUniqueId(),
            artifactKey,
            artifact.artifactType().name(),
            artifact.path(),
            artifact.checksum(),
            artifact.content(),
            command.idempotencyKey(),
            artifact.nodeKind().name(),
            artifact.materialization(),
            Timestamp.from(now),
            Timestamp.from(now),
            command.tenantId(),
            command.modelSpecId(),
            command.planId(),
            command.modelRevision(),
            command.modelChecksum(),
            command.expectedModelStatus().name(),
            command.implementationId(),
            command.projectKey(),
            command.modelDbtUniqueId(),
            command.implementationRevision(),
            command.implementationChecksum()
        );

        Integer exact = jdbcTemplate.queryForObject(
            """
            select count(*)
              from modeling_dbt_artifact a
              join modeling_model_spec s
                on s.id = a.model_spec_id and s.tenant_id = ?
              join modeling_model_implementation i
                on i.tenant_id = s.tenant_id and i.model_spec_id = s.id
              join modeling_model_implementation_revision ir
                on ir.tenant_id = i.tenant_id
               and ir.implementation_id = i.id
               and ir.revision = a.implementation_revision
             where a.model_spec_id = ? and a.plan_id = ?
               and a.revision = ? and a.model_checksum = ?
               and s.status = ? and s.current_checksum = a.model_checksum
               and i.id = ? and i.implementation_revision = ?
               and ir.content_checksum = ?
               and a.project_key = ? and a.dbt_unique_id = ?
               and a.artifact_key = ? and a.artifact_type = ?
               and a.path = ? and a.content_checksum = ? and a.content = ?
               and a.status = 'IMPORTED' and a.ownership = 'DBT_MANAGED'
               and a.node_kind = ? and a.materialization = ?
               and a.physical_asset_ref is null
            """,
            Integer.class,
            command.tenantId(),
            command.modelSpecId(),
            command.planId(),
            command.modelRevision(),
            command.modelChecksum(),
            command.expectedModelStatus().name(),
            command.implementationId(),
            command.implementationRevision(),
            command.implementationChecksum(),
            command.projectKey(),
            artifact.dbtUniqueId(),
            artifactKey,
            artifact.artifactType().name(),
            artifact.path(),
            artifact.checksum(),
            artifact.content(),
            artifact.nodeKind().name(),
            artifact.materialization()
        );
        if (exact == null || exact != 1) {
            throw conflict(
                "MODEL_ARTIFACT_REVISION_CONFLICT",
                "The artifact slot already contains different imported content"
            );
        }
    }

    public enum NodeKind {
        MODEL,
        STG,
        EPHEMERAL,
    }

    public enum ArtifactType {
        SQL,
        SCHEMA,
        CONFIG,
        DEPENDENCY,
    }

    public record ImportedArtifact(
        String dbtUniqueId,
        NodeKind nodeKind,
        ArtifactType artifactType,
        String path,
        String checksum,
        String content,
        String materialization
    ) {
        public ImportedArtifact {
            dbtUniqueId = requiredText(dbtUniqueId, "dbtUniqueId", 256);
            Objects.requireNonNull(nodeKind, "nodeKind is required");
            Objects.requireNonNull(artifactType, "artifactType is required");
            path = requiredText(path, "path", Integer.MAX_VALUE);
            checksum = requiredChecksum(checksum, "checksum");
            content = requiredContent(content);
            materialization = requiredText(materialization, "materialization", 64).toLowerCase();
            if (!checksum.equals(ModelPackageChecksum.sha256Text(content))) {
                throw new IllegalArgumentException("checksum does not match content");
            }
            if (nodeKind == NodeKind.EPHEMERAL && !"ephemeral".equals(materialization)) {
                throw new IllegalArgumentException("EPHEMERAL artifacts require ephemeral materialization");
            }
        }
    }

    public record ImportCommand(
        String tenantId,
        UUID modelSpecId,
        UUID planId,
        int modelRevision,
        String modelChecksum,
        ModelStatus expectedModelStatus,
        UUID implementationId,
        int implementationRevision,
        String implementationChecksum,
        String projectKey,
        String modelDbtUniqueId,
        String idempotencyKey,
        List<ImportedArtifact> artifacts
    ) {
        public ImportCommand {
            tenantId = requiredText(tenantId, "tenantId", 128);
            Objects.requireNonNull(modelSpecId, "modelSpecId is required");
            Objects.requireNonNull(planId, "planId is required");
            if (modelRevision < 1) throw new IllegalArgumentException("modelRevision must be positive");
            modelChecksum = requiredChecksum(modelChecksum, "modelChecksum");
            Objects.requireNonNull(expectedModelStatus, "expectedModelStatus is required");
            Objects.requireNonNull(implementationId, "implementationId is required");
            if (implementationRevision < 1) {
                throw new IllegalArgumentException("implementationRevision must be positive");
            }
            implementationChecksum = requiredChecksum(implementationChecksum, "implementationChecksum");
            projectKey = requiredText(projectKey, "projectKey", 128);
            modelDbtUniqueId = requiredText(modelDbtUniqueId, "modelDbtUniqueId", 256);
            idempotencyKey = requiredText(idempotencyKey, "idempotencyKey", 256);
            artifacts = List.copyOf(Objects.requireNonNull(artifacts, "artifacts are required"));
            if (artifacts.isEmpty() || artifacts.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("artifacts cannot be empty or contain null");
            }
            Set<String> slots = new HashSet<>();
            boolean hasModel = false;
            for (ImportedArtifact artifact : artifacts) {
                if (!slots.add(artifact.dbtUniqueId() + "\u0000" + artifact.artifactType().name())) {
                    throw new IllegalArgumentException("artifact node/type slots must be unique");
                }
                if (artifact.nodeKind() == NodeKind.MODEL) {
                    hasModel = true;
                    if (!modelDbtUniqueId.equals(artifact.dbtUniqueId())) {
                        throw new IllegalArgumentException("MODEL artifacts must use modelDbtUniqueId");
                    }
                } else if (modelDbtUniqueId.equals(artifact.dbtUniqueId())) {
                    throw new IllegalArgumentException("technical artifacts cannot reuse modelDbtUniqueId");
                }
            }
            if (!hasModel) throw new IllegalArgumentException("at least one MODEL artifact is required");
        }
    }

    public record ImportResult(
        UUID modelSpecId,
        UUID planId,
        int modelRevision,
        String modelChecksum,
        UUID implementationId,
        int implementationRevision,
        String implementationChecksum,
        int artifactCount
    ) {}

    private static String requiredText(String value, String name, int maxLength) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        String normalized = value.trim();
        if (normalized.length() > maxLength) throw new IllegalArgumentException(name + " is too long");
        return normalized;
    }

    private static String requiredChecksum(String value, String name) {
        String checksum = requiredText(value, name, 64).toLowerCase();
        if (!checksum.matches("^[0-9a-f]{64}$")) throw new IllegalArgumentException(name + " must be SHA-256");
        return checksum;
    }

    private static String requiredContent(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("content is required");
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static ModelSpecException conflict(String code, String message) {
        return new ModelSpecException(code, message, ModelSpecException.Kind.CONFLICT);
    }
}
