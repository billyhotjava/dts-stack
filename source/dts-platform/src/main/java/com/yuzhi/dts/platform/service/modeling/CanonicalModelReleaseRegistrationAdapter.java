package com.yuzhi.dts.platform.service.modeling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.security.SecurityLevelCatalog;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ArtifactView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.RegistrationStep;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelRevisionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Registers one deterministic external representation per ModelSpec release revision. */
@Component
public class CanonicalModelReleaseRegistrationAdapter implements ModelReleaseRegistrationPort {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public CanonicalModelReleaseRegistrationAdapter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RegistrationResult register(
        RegistrationStep step,
        String tenantId,
        String actorId,
        UUID releaseEventId,
        ModelSpecView model,
        List<ArtifactView> artifacts,
        String previousExternalRef
    ) {
        return switch (step) {
            case CATALOG_ASSET -> registerCatalog(tenantId, actorId, model);
            case BI_DATASET -> registerBiDataset(tenantId, actorId, model);
            case LINEAGE -> registerLineage(model);
        };
    }

    private RegistrationResult registerCatalog(String tenantId, String actorId, ModelSpecView model) {
        UUID id = deterministicId("catalog", model);
        Instant now = Instant.now();
        String table = "model_" + model.id().toString().replace("-", "");
        String tags = json(Map.of(
            "tenantId", tenantId,
            "modelSpecId", model.id(),
            "revision", model.revision(),
            "modelChecksum", model.checksum()
        ));
        jdbcTemplate.update(
            """
            insert into catalog_dataset (
                id, name, domain_id, type, classification, owner, hive_database, hive_table,
                tags, description, warehouse_layer, enabled, exposed_by, lifecycle_status,
                created_by, created_date, last_modified_by, last_modified_date
            ) values (?, ?, ?, 'jdbc', ?, ?, 'dts_modeling', ?, ?, ?, ?, true, 'VIEW', 'PUBLISHED', ?, ?, ?, ?)
            on conflict (id) do update
               set name = excluded.name, domain_id = excluded.domain_id, classification = excluded.classification,
                   owner = excluded.owner, tags = excluded.tags, description = excluded.description,
                   warehouse_layer = excluded.warehouse_layer, enabled = true, lifecycle_status = 'PUBLISHED',
                   last_modified_by = excluded.last_modified_by, last_modified_date = excluded.last_modified_date
            """,
            id,
            model.name(),
            model.domainId(),
            SecurityLevelCatalog.DEFAULT_DATA_SECURITY_LEVEL.code(),
            actor(actorId),
            table,
            tags,
            model.description(),
            model.layer().name(),
            actor(actorId),
            Timestamp.from(now),
            actor(actorId),
            Timestamp.from(now)
        );
        return new RegistrationResult("catalog-dataset:" + id);
    }

    private RegistrationResult registerBiDataset(String tenantId, String actorId, ModelSpecView model) {
        UUID id = deterministicId("bi", model);
        String sql = sqlArtifact(model);
        Instant now = Instant.now();
        String parameters = json(Map.of(
            "tenantId", tenantId,
            "modelSpecId", model.id(),
            "revision", model.revision(),
            "modelChecksum", model.checksum()
        ));
        jdbcTemplate.update(
            """
            insert into query_dataset_asset (
                id, name, description, status, refresh_strategy, sql_text, parameters_json,
                published_version, enabled, created_by, created_date, last_modified_by, last_modified_date
            ) values (?, ?, ?, 'PUBLISHED', 'MANUAL', ?, ?, ?, true, ?, ?, ?, ?)
            on conflict (id) do update
               set name = excluded.name, description = excluded.description, status = 'PUBLISHED',
                   sql_text = excluded.sql_text, parameters_json = excluded.parameters_json,
                   published_version = excluded.published_version, enabled = true,
                   last_modified_by = excluded.last_modified_by, last_modified_date = excluded.last_modified_date
            """,
            id,
            model.name(),
            model.description(),
            sql,
            parameters,
            model.revision(),
            actor(actorId),
            Timestamp.from(now),
            actor(actorId),
            Timestamp.from(now)
        );
        return new RegistrationResult("bi-dataset:" + id);
    }

    private RegistrationResult registerLineage(ModelSpecView model) {
        Instant now = Instant.now();
        int count = 0;
        jdbcTemplate.update(
            "delete from modeling_lineage_edge where to_model_spec_id = ? and edge_type = 'MODEL_DEPENDENCY'",
            model.id()
        );
        for (ModelRevisionRef dependency : model.dependsOn()) {
            jdbcTemplate.update(
                """
                insert into modeling_lineage_edge (
                    id, from_model_spec_id, to_model_spec_id, edge_type, created_date, last_modified_date
                ) values (?, ?, ?, 'MODEL_DEPENDENCY', ?, ?)
                on conflict (from_model_spec_id, to_model_spec_id, edge_type) do update
                   set last_modified_date = excluded.last_modified_date
                """,
                UUID.randomUUID(),
                dependency.modelSpecId(),
                model.id(),
                Timestamp.from(now),
                Timestamp.from(now)
            );
            count += 1;
        }
        return new RegistrationResult("model-lineage:" + model.id() + "@r" + model.revision() + ":" + count);
    }

    private String sqlArtifact(ModelSpecView model) {
        List<String> values = jdbcTemplate.query(
            """
            select content from modeling_dbt_artifact
             where model_spec_id = ? and revision = ? and model_checksum = ?
               and artifact_type = 'SQL' and status = 'COMPILED'
             order by path limit 1
            """,
            (row, number) -> row.getString("content"),
            model.id(),
            model.revision(),
            model.checksum()
        );
        if (values.isEmpty() || values.getFirst() == null || values.getFirst().isBlank()) {
            throw new ModelSpecException(
                "MODEL_RELEASE_SQL_ARTIFACT_REQUIRED",
                "A current compiled SQL artifact is required for BI registration",
                ModelSpecException.Kind.CONFLICT
            );
        }
        return values.getFirst();
    }

    private static UUID deterministicId(String namespace, ModelSpecView model) {
        String value = "model-release:" + namespace + ":" + model.id() + ":" + model.revision();
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private String json(Map<String, ?> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Release registration metadata cannot be serialized", exception);
        }
    }

    private static String actor(String value) {
        String actor = value == null || value.isBlank() ? "model-lifecycle" : value.trim();
        return actor.length() <= 50 ? actor : actor.substring(0, 50);
    }
}
