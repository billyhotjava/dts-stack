package com.yuzhi.dts.platform.repository.modeling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ArtifactView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ArtifactWrite;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.EventType;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.LifecycleEventView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.RegistrationStep;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.RegistrationStepView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL store for revision-bound implementation and lifecycle evidence. */
@Repository
public class ModelLifecycleRepository {

    private static final TypeReference<Map<String, Object>> DETAILS_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ModelLifecycleRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<ImplementationView> findImplementation(String tenantId, UUID modelSpecId) {
        return jdbcTemplate
            .query(
                """
                select id, model_spec_id, plan_id, model_revision, model_checksum,
                       ownership, project_key, dbt_unique_id, status
                  from modeling_model_implementation
                 where tenant_id = ? and model_spec_id = ?
                """,
                (row, number) ->
                    new ImplementationView(
                        row.getObject("id", UUID.class),
                        row.getObject("model_spec_id", UUID.class),
                        row.getObject("plan_id", UUID.class),
                        row.getInt("model_revision"),
                        row.getString("model_checksum"),
                        ImplementationMode.valueOf(row.getString("ownership")),
                        row.getString("project_key"),
                        row.getString("dbt_unique_id"),
                        row.getString("status")
                    ),
                tenantId,
                modelSpecId
            )
            .stream()
            .findFirst();
    }

    public int claimImplementation(
        String tenantId,
        String actorId,
        ModelSpecView model,
        ImplementationMode ownership,
        String projectKey,
        String dbtUniqueId,
        String idempotencyKey,
        Instant now
    ) {
        return jdbcTemplate.update(
            """
            insert into modeling_model_implementation (
                id, tenant_id, model_spec_id, plan_id, model_revision, model_checksum,
                ownership, project_key, dbt_unique_id, status, idempotency_key,
                created_by, created_date, last_modified_date
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?)
            on conflict (tenant_id, model_spec_id) do update
               set plan_id = excluded.plan_id,
                   model_revision = excluded.model_revision,
                   model_checksum = excluded.model_checksum,
                   status = 'ACTIVE',
                   idempotency_key = excluded.idempotency_key,
                   last_modified_date = excluded.last_modified_date
             where modeling_model_implementation.ownership = excluded.ownership
               and modeling_model_implementation.project_key = excluded.project_key
               and modeling_model_implementation.dbt_unique_id = excluded.dbt_unique_id
            """,
            UUID.randomUUID(),
            tenantId,
            model.id(),
            model.planId(),
            model.revision(),
            model.checksum(),
            ownership.name(),
            projectKey,
            dbtUniqueId,
            idempotencyKey,
            actorId,
            Timestamp.from(now),
            Timestamp.from(now)
        );
    }

    public void saveArtifacts(
        String tenantId,
        ModelSpecView model,
        ImplementationView implementation,
        String idempotencyKey,
        List<ArtifactWrite> artifacts,
        Instant now
    ) {
        for (ArtifactWrite artifact : artifacts) {
            String uniqueId = implementation.dbtUniqueId() + "." + artifact.artifactType().toLowerCase();
            int changed = jdbcTemplate.update(
                """
                insert into modeling_dbt_artifact (
                    id, model_spec_id, plan_id, project_key, dbt_unique_id, artifact_type,
                    path, content_checksum, content, status, revision, model_checksum,
                    ownership, idempotency_key, created_date, last_modified_date
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'COMPILED', ?, ?, ?, ?, ?, ?)
                on conflict (model_spec_id, revision, dbt_unique_id) do update
                   set path = excluded.path,
                       content_checksum = excluded.content_checksum,
                       content = excluded.content,
                       status = excluded.status,
                       model_checksum = excluded.model_checksum,
                       ownership = excluded.ownership,
                       idempotency_key = excluded.idempotency_key,
                       last_modified_date = excluded.last_modified_date
                 where modeling_dbt_artifact.model_checksum = excluded.model_checksum
                   and modeling_dbt_artifact.ownership = excluded.ownership
                """,
                UUID.randomUUID(),
                model.id(),
                model.planId(),
                implementation.projectKey(),
                uniqueId,
                artifact.artifactType(),
                artifact.path(),
                artifact.checksum(),
                artifact.content(),
                model.revision(),
                model.checksum(),
                implementation.ownership().name(),
                idempotencyKey,
                Timestamp.from(now),
                Timestamp.from(now)
            );
            if (changed == 0) {
                throw new com.yuzhi.dts.platform.service.modeling.ModelSpecException(
                    "MODEL_ARTIFACT_REVISION_CONFLICT",
                    "An artifact already exists for another ModelSpec checksum or implementation owner",
                    com.yuzhi.dts.platform.service.modeling.ModelSpecException.Kind.CONFLICT
                );
            }
        }
    }

    public List<ArtifactView> listArtifacts(String tenantId, UUID modelSpecId, Integer revision) {
        String revisionClause = revision == null ? "" : " and a.revision = ?";
        Object[] arguments = revision == null
            ? new Object[] { tenantId, modelSpecId }
            : new Object[] { tenantId, modelSpecId, revision };
        return jdbcTemplate.query(
            """
            select a.id, a.model_spec_id, a.plan_id, a.revision, a.model_checksum,
                   a.ownership, a.artifact_type, a.path, a.content_checksum, a.status
              from modeling_dbt_artifact a
              join modeling_model_spec s on s.id = a.model_spec_id
             where s.tenant_id = ? and a.model_spec_id = ?
            """ + revisionClause + " order by a.revision desc, a.artifact_type, a.path",
            (row, number) ->
                new ArtifactView(
                    row.getObject("id", UUID.class),
                    row.getObject("model_spec_id", UUID.class),
                    row.getObject("plan_id", UUID.class),
                    row.getInt("revision"),
                    row.getString("model_checksum"),
                    ImplementationMode.valueOf(row.getString("ownership")),
                    row.getString("artifact_type"),
                    row.getString("path"),
                    row.getString("content_checksum"),
                    row.getString("status")
                ),
            arguments
        );
    }

    public LifecycleEventView recordEvent(
        String tenantId,
        String actorId,
        ModelSpecView model,
        EventType eventType,
        String status,
        String idempotencyKey,
        String comment,
        String externalRef,
        Map<String, Object> details,
        Instant now
    ) {
        jdbcTemplate.update(
            """
            insert into modeling_model_lifecycle_event (
                id, tenant_id, model_spec_id, plan_id, model_revision, model_checksum,
                event_type, status, idempotency_key, actor_id, comment_text,
                external_ref, details_json, created_date
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?)
            on conflict (tenant_id, model_spec_id, event_type, idempotency_key) do nothing
            """,
            UUID.randomUUID(),
            tenantId,
            model.id(),
            model.planId(),
            model.revision(),
            model.checksum(),
            eventType.name(),
            status,
            idempotencyKey,
            actorId,
            comment,
            externalRef,
            json(details),
            Timestamp.from(now)
        );
        return findEvent(tenantId, model.id(), eventType, idempotencyKey).orElseThrow();
    }

    public Optional<LifecycleEventView> findEvent(
        String tenantId,
        UUID modelSpecId,
        EventType eventType,
        String idempotencyKey
    ) {
        return queryEvents(
            " where tenant_id = ? and model_spec_id = ? and event_type = ? and idempotency_key = ?",
            tenantId,
            modelSpecId,
            eventType.name(),
            idempotencyKey
        ).stream().findFirst();
    }

    public Optional<LifecycleEventView> findEvent(UUID eventId) {
        return queryEvents(" where id = ?", eventId).stream().findFirst();
    }

    public Optional<LifecycleEventView> findEvent(String tenantId, UUID eventId) {
        return queryEvents(" where tenant_id = ? and id = ?", tenantId, eventId).stream().findFirst();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateEventStatus(UUID eventId, String status, Map<String, Object> details) {
        jdbcTemplate.update(
            "update modeling_model_lifecycle_event set status = ?, details_json = cast(? as jsonb) where id = ?",
            status,
            json(details),
            eventId
        );
    }

    public Optional<LifecycleEventView> findLatestEvent(
        String tenantId,
        UUID modelSpecId,
        int revision,
        EventType eventType,
        String status
    ) {
        return queryEvents(
            " where tenant_id = ? and model_spec_id = ? and model_revision = ? and event_type = ? and status = ? order by created_date desc limit 1",
            tenantId,
            modelSpecId,
            revision,
            eventType.name(),
            status
        ).stream().findFirst();
    }

    public boolean hasPassedEvidence(String tenantId, UUID modelSpecId, int revision, EventType eventType) {
        Integer count = jdbcTemplate.queryForObject(
            """
            select count(*) from modeling_model_lifecycle_event
             where tenant_id = ? and model_spec_id = ? and model_revision = ?
               and event_type = ? and status = 'PASSED'
            """,
            Integer.class,
            tenantId,
            modelSpecId,
            revision,
            eventType.name()
        );
        return count != null && count > 0;
    }

    public List<LifecycleEventView> listEvents(String tenantId, UUID modelSpecId) {
        return queryEvents(
            " where tenant_id = ? and model_spec_id = ? order by created_date desc, id",
            tenantId,
            modelSpecId
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RegistrationStepView startRegistration(
        UUID releaseEventId,
        RegistrationStep step,
        Instant now
    ) {
        jdbcTemplate.update(
            """
            insert into modeling_model_registration_step (
                id, release_event_id, step_code, status, external_ref,
                attempt_count, error_message, last_attempt_at
            ) values (?, ?, ?, 'ATTEMPTING', null, 1, null, ?)
            on conflict (release_event_id, step_code) do update
               set status = 'ATTEMPTING',
                   attempt_count = modeling_model_registration_step.attempt_count + 1,
                   error_message = null,
                   last_attempt_at = excluded.last_attempt_at
            """,
            UUID.randomUUID(),
            releaseEventId,
            step.name(),
            Timestamp.from(now)
        );
        return listRegistrations(releaseEventId).stream().filter(item -> item.step() == step).findFirst().orElseThrow();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RegistrationStepView completeRegistration(
        UUID releaseEventId,
        RegistrationStep step,
        String status,
        String externalRef,
        String errorMessage,
        Instant now
    ) {
        int changed = jdbcTemplate.update(
            """
            update modeling_model_registration_step
               set status = ?, external_ref = coalesce(?, external_ref), error_message = ?, last_attempt_at = ?
             where release_event_id = ? and step_code = ?
            """,
            status,
            externalRef,
            errorMessage,
            Timestamp.from(now),
            releaseEventId,
            step.name()
        );
        if (changed == 0) throw new IllegalStateException("Registration attempt was not started");
        return listRegistrations(releaseEventId).stream().filter(item -> item.step() == step).findFirst().orElseThrow();
    }

    public List<RegistrationStepView> listRegistrations(UUID releaseEventId) {
        return jdbcTemplate.query(
            """
            select id, release_event_id, step_code, status, external_ref,
                   attempt_count, error_message, last_attempt_at
              from modeling_model_registration_step
             where release_event_id = ? order by step_code
            """,
            (row, number) ->
                new RegistrationStepView(
                    row.getObject("id", UUID.class),
                    row.getObject("release_event_id", UUID.class),
                    RegistrationStep.valueOf(row.getString("step_code")),
                    row.getString("status"),
                    row.getString("external_ref"),
                    row.getInt("attempt_count"),
                    row.getString("error_message"),
                    row.getTimestamp("last_attempt_at").toInstant()
                ),
            releaseEventId
        );
    }

    private List<LifecycleEventView> queryEvents(String suffix, Object... arguments) {
        return jdbcTemplate.query(
            """
            select id, model_spec_id, plan_id, model_revision, model_checksum,
                   event_type, status, idempotency_key, actor_id, comment_text,
                   external_ref, details_json::text, created_date
              from modeling_model_lifecycle_event
            """ + suffix,
            (row, number) ->
                new LifecycleEventView(
                    row.getObject("id", UUID.class),
                    row.getObject("model_spec_id", UUID.class),
                    row.getObject("plan_id", UUID.class),
                    row.getInt("model_revision"),
                    row.getString("model_checksum"),
                    EventType.valueOf(row.getString("event_type")),
                    row.getString("status"),
                    row.getString("idempotency_key"),
                    row.getString("actor_id"),
                    row.getString("comment_text"),
                    row.getString("external_ref"),
                    readDetails(row.getString("details_json")),
                    row.getTimestamp("created_date").toInstant()
                ),
            arguments
        );
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Lifecycle details cannot be serialized", exception);
        }
    }

    private Map<String, Object> readDetails(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(value, DETAILS_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Lifecycle details cannot be read", exception);
        }
    }
}
