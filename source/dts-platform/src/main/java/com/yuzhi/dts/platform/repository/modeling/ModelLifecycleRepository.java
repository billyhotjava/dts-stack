package com.yuzhi.dts.platform.repository.modeling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ArtifactView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ArtifactWrite;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.EventType;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.FieldMapping;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.GeneratedInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.InputMode;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.LifecycleEventView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.PhysicalAssetInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.SaveImplementationCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.UpstreamModelInput;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationChecksumCodec;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationExecutionPlanner;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL store for revision-bound implementation and lifecycle evidence. */
@Repository
public class ModelLifecycleRepository {

    private static final TypeReference<Map<String, Object>> DETAILS_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<FieldMapping>> FIELD_MAPPINGS_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> SETTINGS_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ModelImplementationChecksumCodec implementationChecksumCodec;

    public ModelLifecycleRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.implementationChecksumCodec = new ModelImplementationChecksumCodec(objectMapper);
    }

    public Optional<ImplementationView> findImplementation(String tenantId, UUID modelSpecId) {
        return jdbcTemplate
            .query(
                """
                select id, model_spec_id, plan_id, model_revision, model_checksum,
                       ownership, project_key, dbt_unique_id, status, implementation_revision,
                       current_implementation_checksum, input_mode, inputs_json::text,
                       field_mappings_json::text, settings_json::text, materialization
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
                        row.getString("status"),
                        row.getInt("implementation_revision"),
                        row.getString("current_implementation_checksum"),
                        InputMode.valueOf(row.getString("input_mode")),
                        readInputs(InputMode.valueOf(row.getString("input_mode")), row.getString("inputs_json")),
                        readFieldMappings(row.getString("field_mappings_json")),
                        readSettings(row.getString("settings_json")),
                        row.getString("materialization")
                    ),
                tenantId,
                modelSpecId
            )
            .stream()
            .findFirst();
    }

    /** Holds a shared row lock until the caller's transaction commits and verifies the exact implementation head. */
    public boolean lockImplementation(String tenantId, UUID modelSpecId, ImplementationView expected) {
        if (expected == null) return false;
        return !jdbcTemplate
            .queryForList(
                """
                select id
                  from modeling_model_implementation
                 where tenant_id = ? and model_spec_id = ? and id = ?
                   and model_revision = ? and model_checksum = ?
                   and implementation_revision = ? and current_implementation_checksum = ?
                   and ownership = ? and project_key = ? and dbt_unique_id = ?
                 for share
                """,
                UUID.class,
                tenantId,
                modelSpecId,
                expected.id(),
                expected.revision(),
                expected.modelChecksum(),
                expected.implementationRevision(),
                expected.implementationChecksum(),
                expected.ownership().name(),
                expected.projectKey(),
                expected.dbtUniqueId()
            )
            .isEmpty();
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
        return claimImplementation(tenantId, actorId, model, ownership, projectKey, dbtUniqueId, idempotencyKey, -1, null, now);
    }

    public int claimImplementation(
        String tenantId,
        String actorId,
        ModelSpecView model,
        ImplementationMode ownership,
        String projectKey,
        String dbtUniqueId,
        String idempotencyKey,
        int expectedImplementationRevision,
        String expectedImplementationChecksum,
        Instant now
    ) {
        return saveImplementation(
            tenantId,
            actorId,
            model,
            projectKey,
            dbtUniqueId,
            new SaveImplementationCommand(
                InputMode.GENERATED,
                List.of(new GeneratedInput("DBT_MANAGED_CLAIM", Map.of())),
                List.of(),
                Map.of(),
                ownership,
                model.materialization() == null || model.materialization().isBlank() ? "table" : model.materialization(),
                idempotencyKey
            ),
            expectedImplementationRevision,
            expectedImplementationChecksum,
            now
        );
    }

    /** Saves a current implementation head and appends a revision only when its input payload changes. */
    public int saveImplementation(
        String tenantId,
        String actorId,
        ModelSpecView model,
        String projectKey,
        String dbtUniqueId,
        SaveImplementationCommand command,
        Instant now
    ) {
        return saveImplementation(tenantId, actorId, model, projectKey, dbtUniqueId, command, -1, null, now);
    }

    /** A non-negative expected revision is a compare-and-swap precondition from the lifecycle API. */
    public int saveImplementation(
        String tenantId,
        String actorId,
        ModelSpecView model,
        String projectKey,
        String dbtUniqueId,
        SaveImplementationCommand command,
        int expectedImplementationRevision,
        String expectedImplementationChecksum,
        Instant now
    ) {
        String inputs = implementationJson(command.inputs());
        String mappings = implementationJson(command.fieldMappings());
        String settings = implementationJson(command.settings());
        String checksum = implementationChecksum(command);
        Integer changed = jdbcTemplate.queryForObject(
            """
            with saved_head as (
            insert into modeling_model_implementation (
                id, tenant_id, model_spec_id, plan_id, model_revision, model_checksum,
                ownership, project_key, dbt_unique_id, status, idempotency_key, implementation_revision,
                current_implementation_checksum, input_mode, inputs_json, field_mappings_json, settings_json, materialization,
                created_by, created_date, last_modified_date
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, 1, ?, ?, cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), ?, ?, ?, ?)
            on conflict (tenant_id, model_spec_id) do update
               set plan_id = excluded.plan_id,
                   model_revision = excluded.model_revision,
                   model_checksum = excluded.model_checksum,
                   status = 'ACTIVE',
                   idempotency_key = excluded.idempotency_key,
                   implementation_revision = case
                       when modeling_model_implementation.current_implementation_checksum = excluded.current_implementation_checksum
                       then modeling_model_implementation.implementation_revision
                       else modeling_model_implementation.implementation_revision + 1
                   end,
                   current_implementation_checksum = excluded.current_implementation_checksum,
                   input_mode = excluded.input_mode,
                   inputs_json = excluded.inputs_json,
                   field_mappings_json = excluded.field_mappings_json,
                   settings_json = excluded.settings_json,
                   materialization = excluded.materialization,
                   last_modified_date = excluded.last_modified_date
             where modeling_model_implementation.ownership = excluded.ownership
               and modeling_model_implementation.project_key = excluded.project_key
               and modeling_model_implementation.dbt_unique_id = excluded.dbt_unique_id
               and (? < 0 or (
                    modeling_model_implementation.implementation_revision = ?
                    and modeling_model_implementation.current_implementation_checksum = ?
               ))
            returning tenant_id, id, implementation_revision, current_implementation_checksum,
                      input_mode, inputs_json, field_mappings_json, settings_json, ownership, materialization
            ), inserted_revision as (
                insert into modeling_model_implementation_revision (
                    id, tenant_id, implementation_id, revision, content_checksum, input_mode,
                    inputs_json, field_mappings_json, settings_json, ownership, materialization, created_by, created_date
                )
                select ?, tenant_id, id, implementation_revision, current_implementation_checksum, input_mode,
                       inputs_json, field_mappings_json, settings_json, ownership, materialization, ?, ?
                  from saved_head
                on conflict (tenant_id, implementation_id, revision) do nothing
                returning 1
            )
            select count(*)::int from saved_head
            """,
            Integer.class,
            UUID.randomUUID(),
            tenantId,
            model.id(),
            model.planId(),
            model.revision(),
            model.checksum(),
            command.ownership().name(),
            projectKey,
            dbtUniqueId,
            command.idempotencyKey(),
            checksum,
            command.inputMode().name(),
            inputs,
            mappings,
            settings,
            command.materialization(),
            actorId,
            Timestamp.from(now),
            Timestamp.from(now),
            expectedImplementationRevision,
            expectedImplementationRevision,
            expectedImplementationChecksum,
            UUID.randomUUID(),
            actorId,
            Timestamp.from(now)
        );
        return changed == null ? 0 : changed;
    }

    /**
     * F3 import-only implementation writer.
     *
     * <p>The canonical ModelSpec row and, when present, the implementation head are locked at the
     * exact caller-provided pins before the existing append-only persistence primitive is invoked.
     * The ordinary implementation writer remains unchanged and is not an alternate DBT import
     * path.
     */
    @Transactional
    public Optional<ImplementationView> saveImportedDbtImplementation(
        String tenantId,
        String actorId,
        ModelSpecView model,
        ModelStatus expectedModelStatus,
        String projectKey,
        String dbtUniqueId,
        SaveImplementationCommand command,
        int expectedImplementationRevision,
        String expectedImplementationChecksum,
        Instant now
    ) {
        requireImportedDbtCommand(model, expectedModelStatus, projectKey, dbtUniqueId, command);
        if (
            expectedImplementationRevision < 0 ||
            (expectedImplementationRevision == 0 && expectedImplementationChecksum != null) ||
            (
                expectedImplementationRevision > 0 &&
                (expectedImplementationChecksum == null || !expectedImplementationChecksum.matches("^[0-9a-f]{64}$"))
            )
        ) {
            throw new IllegalArgumentException("Invalid imported DBT implementation precondition");
        }

        boolean modelPinned = !jdbcTemplate
            .queryForList(
                """
                select id
                  from modeling_model_spec
                 where tenant_id = ? and id = ? and plan_id = ?
                   and revision = ? and current_checksum = ? and status = ?
                 for share
                """,
                UUID.class,
                tenantId,
                model.id(),
                model.planId(),
                model.revision(),
                model.checksum(),
                expectedModelStatus.name()
            )
            .isEmpty();
        if (!modelPinned) return Optional.empty();

        List<UUID> currentHeads = jdbcTemplate.queryForList(
            """
            select id
              from modeling_model_implementation
             where tenant_id = ? and model_spec_id = ?
            """ +
            (
                expectedImplementationRevision == 0
                    ? ""
                    : """
                       and status = 'ACTIVE' and ownership = 'DBT_MANAGED'
                       and project_key = ? and dbt_unique_id = ?
                       and implementation_revision = ? and current_implementation_checksum = ?
                      """
            ) +
            " for update",
            UUID.class,
            expectedImplementationRevision == 0
                ? new Object[] { tenantId, model.id() }
                : new Object[] {
                    tenantId,
                    model.id(),
                    projectKey.trim(),
                    dbtUniqueId.trim(),
                    expectedImplementationRevision,
                    expectedImplementationChecksum,
                }
        );
        if (
            (expectedImplementationRevision == 0 && !currentHeads.isEmpty()) ||
            (expectedImplementationRevision > 0 && currentHeads.size() != 1)
        ) {
            return Optional.empty();
        }

        int saved = saveImplementation(
            tenantId,
            actorId,
            model,
            projectKey.trim(),
            dbtUniqueId.trim(),
            command,
            expectedImplementationRevision,
            expectedImplementationChecksum,
            now
        );
        if (saved == 0) return Optional.empty();
        return findImplementation(tenantId, model.id()).filter(current ->
            current.revision() == model.revision() &&
            java.util.Objects.equals(current.modelChecksum(), model.checksum()) &&
            current.ownership() == ImplementationMode.DBT_MANAGED &&
            "ACTIVE".equals(current.status()) &&
            java.util.Objects.equals(current.projectKey(), projectKey.trim()) &&
            java.util.Objects.equals(current.dbtUniqueId(), dbtUniqueId.trim())
        );
    }

    /**
     * Forward-undo-only DBT writer.
     *
     * <p>The current implementation is compared against the post-import ModelSpec and
     * implementation pins, while the replacement head is attached to the newly appended restored
     * ModelSpec revision. Existing save/import writers intentionally retain their original CAS
     * semantics.
     */
    @Transactional
    public int restoreImportedDbtImplementation(
        String tenantId,
        String actorId,
        ModelSpecView restoredModel,
        String projectKey,
        String dbtUniqueId,
        SaveImplementationCommand restoredCommand,
        int expectedCurrentModelRevision,
        String expectedCurrentModelChecksum,
        int expectedCurrentImplementationRevision,
        String expectedCurrentImplementationChecksum,
        String expectedRestoredImplementationChecksum,
        Instant now
    ) {
        requireImportedDbtCommand(restoredModel, ModelStatus.DRAFT, projectKey, dbtUniqueId, restoredCommand);
        String restoredImplementationChecksum = implementationChecksum(restoredCommand);
        if (
            expectedCurrentModelRevision < 1 ||
            expectedCurrentImplementationRevision < 1 ||
            expectedCurrentModelChecksum == null ||
            !expectedCurrentModelChecksum.matches("^[0-9a-f]{64}$") ||
            expectedCurrentImplementationChecksum == null ||
            !expectedCurrentImplementationChecksum.matches("^[0-9a-f]{64}$") ||
            expectedRestoredImplementationChecksum == null ||
            !expectedRestoredImplementationChecksum.matches("^[0-9a-f]{64}$") ||
            !expectedRestoredImplementationChecksum.equals(restoredImplementationChecksum)
        ) {
            throw new IllegalArgumentException("Invalid imported DBT forward-undo pins");
        }

        String inputs = implementationJson(restoredCommand.inputs());
        String mappings = implementationJson(restoredCommand.fieldMappings());
        String settings = implementationJson(restoredCommand.settings());
        Integer changed = jdbcTemplate.queryForObject(
            """
            with locked_model as (
                select spec.id
                  from modeling_model_spec spec
                 where spec.tenant_id = ? and spec.id = ? and spec.plan_id = ?
                   and spec.revision = ? and spec.current_checksum = ? and spec.status = 'DRAFT'
                 for share
            ), locked_head as (
                select implementation.id, implementation.implementation_revision
                  from modeling_model_implementation implementation
                  join locked_model on locked_model.id = implementation.model_spec_id
                 where implementation.tenant_id = ?
                   and implementation.ownership = 'DBT_MANAGED'
                   and implementation.status = 'ACTIVE'
                   and implementation.project_key = ? and implementation.dbt_unique_id = ?
                   and implementation.model_revision = ? and implementation.model_checksum = ?
                   and implementation.implementation_revision = ?
                   and implementation.current_implementation_checksum = ?
                 for update
            ), saved_head as (
                update modeling_model_implementation implementation
                   set plan_id = ?, model_revision = ?, model_checksum = ?, status = 'ACTIVE',
                       idempotency_key = ?,
                       implementation_revision = locked_head.implementation_revision + 1,
                       current_implementation_checksum = ?, input_mode = ?,
                       inputs_json = cast(? as jsonb), field_mappings_json = cast(? as jsonb),
                       settings_json = cast(? as jsonb), materialization = ?, last_modified_date = ?
                  from locked_head
                 where implementation.tenant_id = ? and implementation.id = locked_head.id
                returning implementation.tenant_id, implementation.id,
                          implementation.implementation_revision,
                          implementation.current_implementation_checksum,
                          implementation.input_mode, implementation.inputs_json,
                          implementation.field_mappings_json, implementation.settings_json,
                          implementation.ownership, implementation.materialization
            ), inserted_revision as (
                insert into modeling_model_implementation_revision (
                    id, tenant_id, implementation_id, revision, content_checksum, input_mode,
                    inputs_json, field_mappings_json, settings_json, ownership, materialization,
                    created_by, created_date
                )
                select ?, tenant_id, id, implementation_revision,
                       current_implementation_checksum, input_mode, inputs_json,
                       field_mappings_json, settings_json, ownership, materialization, ?, ?
                  from saved_head
                returning 1
            )
            select count(*)::int from saved_head
            """,
            Integer.class,
            tenantId,
            restoredModel.id(),
            restoredModel.planId(),
            restoredModel.revision(),
            restoredModel.checksum(),
            tenantId,
            projectKey.trim(),
            dbtUniqueId.trim(),
            expectedCurrentModelRevision,
            expectedCurrentModelChecksum,
            expectedCurrentImplementationRevision,
            expectedCurrentImplementationChecksum,
            restoredModel.planId(),
            restoredModel.revision(),
            restoredModel.checksum(),
            restoredCommand.idempotencyKey(),
            restoredImplementationChecksum,
            restoredCommand.inputMode().name(),
            inputs,
            mappings,
            settings,
            restoredCommand.materialization(),
            Timestamp.from(now),
            tenantId,
            UUID.randomUUID(),
            actorId,
            Timestamp.from(now)
        );
        return changed == null ? 0 : changed;
    }

    /**
     * Explicitly replaces a DBT-managed implementation head with a designer-owned revision.
     * Ordinary saves intentionally keep their ownership equality check and cannot use this path.
     */
    public int convertImplementationOwnership(
        String tenantId,
        String actorId,
        ModelSpecView model,
        String projectKey,
        String dbtUniqueId,
        SaveImplementationCommand command,
        Instant now
    ) {
        return convertImplementationOwnership(tenantId, actorId, model, projectKey, dbtUniqueId, command, -1, null, now);
    }

    public int convertImplementationOwnership(
        String tenantId,
        String actorId,
        ModelSpecView model,
        String projectKey,
        String dbtUniqueId,
        SaveImplementationCommand command,
        int expectedImplementationRevision,
        String expectedImplementationChecksum,
        Instant now
    ) {
        if (command.ownership() != ImplementationMode.DESIGNER_GENERATED) {
            throw new IllegalArgumentException("Only designer-generated implementations may replace DBT ownership");
        }
        String inputs = implementationJson(command.inputs());
        String mappings = implementationJson(command.fieldMappings());
        String settings = implementationJson(command.settings());
        String checksum = implementationChecksum(command);
        Integer changed = jdbcTemplate.queryForObject(
            """
            with locked_head as (
                select id
                  from modeling_model_implementation
                 where tenant_id = ? and model_spec_id = ? and ownership = 'DBT_MANAGED'
                 for update
            ), saved_head as (
                update modeling_model_implementation implementation
                   set plan_id = ?, model_revision = ?, model_checksum = ?, ownership = 'DESIGNER_GENERATED',
                       project_key = ?, dbt_unique_id = ?, status = 'ACTIVE', idempotency_key = ?,
                       implementation_revision = implementation_revision + 1,
                       current_implementation_checksum = ?, input_mode = ?, inputs_json = cast(? as jsonb),
                       field_mappings_json = cast(? as jsonb), settings_json = cast(? as jsonb), materialization = ?,
                       last_modified_date = ?
                 from locked_head
                 where implementation.id = locked_head.id
                   and (? < 0 or (
                       implementation.implementation_revision = ?
                       and implementation.current_implementation_checksum = ?
                   ))
                returning implementation.tenant_id, implementation.id, implementation.implementation_revision,
                          implementation.current_implementation_checksum, implementation.input_mode, implementation.inputs_json,
                          implementation.field_mappings_json, implementation.settings_json, implementation.ownership,
                          implementation.materialization
            ), inserted_revision as (
                insert into modeling_model_implementation_revision (
                    id, tenant_id, implementation_id, revision, content_checksum, input_mode,
                    inputs_json, field_mappings_json, settings_json, ownership, materialization, created_by, created_date
                )
                select ?, tenant_id, id, implementation_revision, current_implementation_checksum, input_mode,
                       inputs_json, field_mappings_json, settings_json, ownership, materialization, ?, ?
                  from saved_head
                returning 1
            )
            select count(*)::int from saved_head
            """,
            Integer.class,
            tenantId,
            model.id(),
            model.planId(),
            model.revision(),
            model.checksum(),
            projectKey,
            dbtUniqueId,
            command.idempotencyKey(),
            checksum,
            command.inputMode().name(),
            inputs,
            mappings,
            settings,
            command.materialization(),
            Timestamp.from(now),
            expectedImplementationRevision,
            expectedImplementationRevision,
            expectedImplementationChecksum,
            UUID.randomUUID(),
            actorId,
            Timestamp.from(now)
        );
        return changed == null ? 0 : changed;
    }

    /** Dedicated forward CAS for the irreversible designer-to-dbt hand-over; ordinary writers stay unchanged. */
    public int transitionDesignerImplementationToDbtManaged(
        String tenantId, String actorId, ModelSpecView model, String projectKey, String dbtUniqueId,
        SaveImplementationCommand command, int expectedModelRevision, String expectedModelChecksum,
        int expectedImplementationRevision, String expectedImplementationChecksum, Instant now
    ) {
        if (command.ownership() != ImplementationMode.DBT_MANAGED) throw new IllegalArgumentException("Only DBT-managed ownership is allowed");
        String inputs = implementationJson(command.inputs()); String mappings = implementationJson(command.fieldMappings());
        String settings = implementationJson(command.settings()); String checksum = implementationChecksum(command);
        Integer changed = jdbcTemplate.queryForObject("""
            with locked_head as (select id from modeling_model_implementation where tenant_id = ? and model_spec_id = ? and model_revision = ? and model_checksum = ? and ownership = 'DESIGNER_GENERATED' and status = 'ACTIVE' and implementation_revision = ? and current_implementation_checksum = ? for update),
            saved_head as (update modeling_model_implementation i set plan_id=?, model_revision=?, model_checksum=?, ownership='DBT_MANAGED', project_key=?, dbt_unique_id=?, status='ACTIVE', idempotency_key=?, implementation_revision=i.implementation_revision+1, current_implementation_checksum=?, input_mode=?, inputs_json=cast(? as jsonb), field_mappings_json=cast(? as jsonb), settings_json=cast(? as jsonb), materialization=?, last_modified_date=? from locked_head where i.id=locked_head.id returning i.tenant_id,i.id,i.implementation_revision,i.current_implementation_checksum,i.input_mode,i.inputs_json,i.field_mappings_json,i.settings_json,i.ownership,i.materialization),
            inserted_revision as (insert into modeling_model_implementation_revision (id,tenant_id,implementation_id,revision,content_checksum,input_mode,inputs_json,field_mappings_json,settings_json,ownership,materialization,created_by,created_date) select ?,tenant_id,id,implementation_revision,current_implementation_checksum,input_mode,inputs_json,field_mappings_json,settings_json,ownership,materialization,?,? from saved_head returning 1)
            select count(*)::int from saved_head
            """, Integer.class, tenantId, model.id(), expectedModelRevision, expectedModelChecksum, expectedImplementationRevision, expectedImplementationChecksum,
            model.planId(), model.revision(), model.checksum(), projectKey, dbtUniqueId, command.idempotencyKey(), checksum,
            command.inputMode().name(), inputs, mappings, settings, command.materialization(), Timestamp.from(now), UUID.randomUUID(), actorId, Timestamp.from(now));
        return changed == null ? 0 : changed;
    }

    public void saveArtifacts(
        String tenantId,
        ModelSpecView model,
        ImplementationView implementation,
        String idempotencyKey,
        List<ArtifactWrite> artifacts,
        Instant now
    ) {
        if (implementation.ownership() == ImplementationMode.DBT_MANAGED && !artifacts.isEmpty()) {
            throw new com.yuzhi.dts.platform.service.modeling.ModelSpecException(
                "MODEL_ARTIFACT_DBT_WRITE_PATH_REQUIRED",
                "DBT-managed artifacts may only be updated by the dedicated DBT import path",
                com.yuzhi.dts.platform.service.modeling.ModelSpecException.Kind.CONFLICT
            );
        }
        saveArtifacts(tenantId, model, implementation, idempotencyKey, artifacts, now, false);
    }

    /** Dedicated DBT ingestion path; it may only replace artifacts pinned to the same implementation revision. */
    @Transactional
    public void saveDbtManagedArtifacts(
        String tenantId,
        ModelSpecView model,
        ImplementationView implementation,
        String idempotencyKey,
        List<ArtifactWrite> artifacts,
        Instant now
    ) {
        if (implementation.ownership() != ImplementationMode.DBT_MANAGED) {
            throw new IllegalArgumentException("DBT artifact writer requires DBT-managed implementation ownership");
        }
        saveArtifacts(tenantId, model, implementation, idempotencyKey, artifacts, now, true);
    }

    private void saveArtifacts(
        String tenantId,
        ModelSpecView model,
        ImplementationView implementation,
        String idempotencyKey,
        List<ArtifactWrite> artifacts,
        Instant now,
        boolean dbtWritePath
    ) {
        String conflictClause = dbtWritePath
            ? """
                on conflict (
                    model_spec_id, revision, implementation_revision,
                    project_key, dbt_unique_id, node_kind, artifact_type
                )
                    where ownership = 'DBT_MANAGED'
                do update
                   set path = excluded.path,
                       dbt_unique_id = excluded.dbt_unique_id,
                       content_checksum = excluded.content_checksum,
                       content = excluded.content,
                       status = excluded.status,
                       model_checksum = excluded.model_checksum,
                       ownership = excluded.ownership,
                       idempotency_key = excluded.idempotency_key,
                       materialization = excluded.materialization,
                       physical_asset_ref = excluded.physical_asset_ref,
                       last_modified_date = excluded.last_modified_date
                 where modeling_dbt_artifact.model_checksum = excluded.model_checksum
                   and modeling_dbt_artifact.ownership = excluded.ownership
                   and modeling_dbt_artifact.implementation_revision = excluded.implementation_revision
                   and modeling_dbt_artifact.project_key = excluded.project_key
                   and modeling_dbt_artifact.dbt_unique_id = excluded.dbt_unique_id
                   and modeling_dbt_artifact.path = excluded.path
                   and modeling_dbt_artifact.content_checksum = excluded.content_checksum
                   and modeling_dbt_artifact.content = excluded.content
                   and modeling_dbt_artifact.node_kind = excluded.node_kind
                   and modeling_dbt_artifact.materialization = excluded.materialization
                   and modeling_dbt_artifact.physical_asset_ref is not distinct from excluded.physical_asset_ref
                """
            : """
                on conflict (model_spec_id, revision, implementation_revision, artifact_key) do update
                   set path = excluded.path,
                       dbt_unique_id = excluded.dbt_unique_id,
                       content_checksum = excluded.content_checksum,
                       content = excluded.content,
                       status = excluded.status,
                       model_checksum = excluded.model_checksum,
                       ownership = excluded.ownership,
                       idempotency_key = excluded.idempotency_key,
                       node_kind = excluded.node_kind,
                       materialization = excluded.materialization,
                       physical_asset_ref = excluded.physical_asset_ref,
                       last_modified_date = excluded.last_modified_date
                 where modeling_dbt_artifact.model_checksum = excluded.model_checksum
                   and modeling_dbt_artifact.ownership = excluded.ownership
                   and modeling_dbt_artifact.ownership <> 'DBT_MANAGED'
                   and modeling_dbt_artifact.implementation_revision = excluded.implementation_revision
                   and modeling_dbt_artifact.project_key is not distinct from excluded.project_key
                   and modeling_dbt_artifact.dbt_unique_id is not distinct from excluded.dbt_unique_id
                   and modeling_dbt_artifact.path is not distinct from excluded.path
                   and modeling_dbt_artifact.content_checksum is not distinct from excluded.content_checksum
                   and modeling_dbt_artifact.content is not distinct from excluded.content
                   and modeling_dbt_artifact.node_kind is not distinct from excluded.node_kind
                   and modeling_dbt_artifact.materialization is not distinct from excluded.materialization
                   and modeling_dbt_artifact.physical_asset_ref is not distinct from excluded.physical_asset_ref
                """;
        for (ArtifactWrite artifact : artifacts) {
            if (artifact.physicalAssetRef() != null && !physicalAssetBelongsToTenant(tenantId, model.planId(), artifact.physicalAssetRef())) {
                throw new com.yuzhi.dts.platform.service.modeling.ModelSpecException(
                    "MODEL_ARTIFACT_PHYSICAL_ASSET_FORBIDDEN",
                    "Artifact physical asset must belong to the current tenant and warehouse plan",
                    com.yuzhi.dts.platform.service.modeling.ModelSpecException.Kind.FORBIDDEN
                );
            }
            String uniqueId = "STG".equals(artifact.nodeKind())
                ? stagingDbtUniqueId(implementation.dbtUniqueId())
                : implementation.dbtUniqueId();
            String artifactKey = artifact.artifactType() + ":" + artifact.path();
            int changed = jdbcTemplate.update(
                """
                insert into modeling_dbt_artifact (
                    id, model_spec_id, plan_id, project_key, dbt_unique_id, artifact_key, artifact_type,
                    path, content_checksum, content, status, revision, model_checksum,
                    ownership, idempotency_key, implementation_revision, node_kind, materialization,
                    physical_asset_ref, created_date, last_modified_date
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'COMPILED', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """ + conflictClause,
                UUID.randomUUID(),
                model.id(),
                model.planId(),
                implementation.projectKey(),
                uniqueId,
                artifactKey,
                artifact.artifactType(),
                artifact.path(),
                artifact.checksum(),
                artifact.content(),
                model.revision(),
                model.checksum(),
                implementation.ownership().name(),
                idempotencyKey,
                implementation.implementationRevision(),
                artifact.nodeKind() == null || artifact.nodeKind().isBlank() ? artifact.artifactType() : artifact.nodeKind(),
                artifact.materialization() == null || artifact.materialization().isBlank()
                    ? implementation.materialization()
                    : artifact.materialization(),
                artifact.physicalAssetRef(),
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

    private static String stagingDbtUniqueId(String dbtUniqueId) {
        int separator = dbtUniqueId == null ? -1 : dbtUniqueId.lastIndexOf('.');
        if (separator < 0 || separator == dbtUniqueId.length() - 1) {
            throw new IllegalArgumentException("DBT unique id must identify a model node");
        }
        return dbtUniqueId.substring(0, separator + 1) + "stg_" + dbtUniqueId.substring(separator + 1);
    }

    private boolean physicalAssetBelongsToTenant(String tenantId, UUID planId, UUID physicalAssetRef) {
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from modeling_warehouse_plan_source where tenant_id = ? and plan_id = ? and id = ?",
            Integer.class,
            tenantId,
            planId,
            physicalAssetRef
        );
        return count != null && count == 1;
    }

    public List<ArtifactView> listArtifacts(String tenantId, UUID modelSpecId, Integer revision) {
        String revisionClause = revision == null ? "" : " and a.revision = ?";
        Object[] arguments = revision == null
            ? new Object[] { tenantId, modelSpecId }
            : new Object[] { tenantId, modelSpecId, revision };
        return jdbcTemplate.query(
            """
            select a.id, a.model_spec_id, a.plan_id, a.revision, a.model_checksum,
                   a.ownership, a.artifact_type, a.path, a.content_checksum, a.status,
                   a.implementation_revision, a.node_kind, a.materialization, a.physical_asset_ref
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
                    row.getString("status"),
                    row.getInt("implementation_revision"),
                    row.getString("node_kind"),
                    row.getString("materialization"),
                    row.getObject("physical_asset_ref", UUID.class)
                ),
            arguments
        );
    }

    public java.util.Set<String> currentArtifactTypes(
        String tenantId,
        UUID modelSpecId,
        ImplementationView implementation
    ) {
        if (implementation == null) return java.util.Set.of();
        return java.util.Set.copyOf(
            jdbcTemplate.queryForList(
                """
                select distinct a.artifact_type
                  from modeling_dbt_artifact a
                  join modeling_model_spec s
                    on s.id = a.model_spec_id and s.tenant_id = ?
                  join modeling_model_implementation_revision ir
                    on ir.tenant_id = s.tenant_id
                   and ir.implementation_id = ?
                   and ir.revision = a.implementation_revision
                 where a.model_spec_id = ?
                   and a.revision = ?
                   and a.model_checksum = ?
                   and a.ownership = ?
                   and a.project_key = ?
                   and a.dbt_unique_id = ?
                   and a.implementation_revision = ?
                   and ir.content_checksum = ?
                   and a.status in ('IMPORTED', 'COMPILED')
                """,
                String.class,
                tenantId,
                implementation.id(),
                modelSpecId,
                implementation.revision(),
                implementation.modelChecksum(),
                implementation.ownership().name(),
                implementation.projectKey(),
                implementation.dbtUniqueId(),
                implementation.implementationRevision(),
                implementation.implementationChecksum()
            )
        );
    }

    public int promoteImportedArtifactsToCompiled(
        String tenantId,
        UUID modelSpecId,
        ImplementationView implementation,
        Instant now
    ) {
        if (implementation == null || implementation.ownership() != ImplementationMode.DBT_MANAGED) return 0;
        return jdbcTemplate.update(
            """
            update modeling_dbt_artifact a
               set status = 'COMPILED', last_modified_date = ?
              from modeling_model_spec s, modeling_model_implementation_revision ir
             where s.id = a.model_spec_id and s.tenant_id = ?
               and ir.tenant_id = s.tenant_id
               and ir.implementation_id = ?
               and ir.revision = a.implementation_revision
               and a.model_spec_id = ?
               and a.revision = ?
               and a.model_checksum = ?
               and a.ownership = ?
               and a.project_key = ?
               and a.dbt_unique_id = ?
               and a.implementation_revision = ?
               and ir.content_checksum = ?
               and a.status = 'IMPORTED'
            """,
            Timestamp.from(now),
            tenantId,
            implementation.id(),
            modelSpecId,
            implementation.revision(),
            implementation.modelChecksum(),
            implementation.ownership().name(),
            implementation.projectKey(),
            implementation.dbtUniqueId(),
            implementation.implementationRevision(),
            implementation.implementationChecksum()
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
        return recordEvent(
            tenantId,
            actorId,
            model,
            eventType,
            status,
            idempotencyKey,
            comment,
            externalRef,
            details,
            findImplementation(tenantId, model.id()).orElse(null),
            now
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
        ImplementationView implementation,
        Instant now
    ) {
        Map<String, Object> pinnedDetails = new LinkedHashMap<>(details == null ? Map.of() : details);
        if (implementation != null) {
            pinnedDetails.put("implementationRevision", implementation.implementationRevision());
            pinnedDetails.put("implementationChecksum", implementation.implementationChecksum());
            pinnedDetails.put("implementationOwnership", implementation.ownership().name());
            pinnedDetails.put("dbtUniqueId", implementation.dbtUniqueId());
        }
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
            json(pinnedDetails),
            Timestamp.from(now)
        );
        LifecycleEventView persisted = findEvent(tenantId, model.id(), eventType, idempotencyKey).orElseThrow();
        if (implementation != null && !matchesImplementation(persisted, implementation)) {
            throw new com.yuzhi.dts.platform.service.modeling.ModelSpecException(
                "MODEL_LIFECYCLE_IDEMPOTENCY_CONFLICT",
                "The idempotency key belongs to another implementation revision",
                com.yuzhi.dts.platform.service.modeling.ModelSpecException.Kind.CONFLICT
            );
        }
        return persisted;
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

    public static boolean matchesImplementation(LifecycleEventView event, ImplementationView implementation) {
        if (event == null || implementation == null || event.details() == null) return false;
        Object revision = event.details().get("implementationRevision");
        Object checksum = event.details().get("implementationChecksum");
        int pinnedRevision;
        try {
            pinnedRevision = revision instanceof Number number
                ? number.intValue()
                : Integer.parseInt(String.valueOf(revision));
        } catch (NumberFormatException invalid) {
            return false;
        }
        return (
            pinnedRevision == implementation.implementationRevision() &&
            java.util.Objects.equals(String.valueOf(checksum), implementation.implementationChecksum())
        );
    }

    public Optional<LifecycleEventView> findEvent(UUID eventId) {
        return queryEvents(" where id = ?", eventId).stream().findFirst();
    }

    public Optional<LifecycleEventView> findEvent(String tenantId, UUID eventId) {
        return queryEvents(" where tenant_id = ? and id = ?", tenantId, eventId).stream().findFirst();
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

    public Optional<LifecycleEventView> findLatestEvent(
        String tenantId,
        UUID modelSpecId,
        int revision,
        int implementationRevision,
        String implementationChecksum,
        EventType eventType,
        String status
    ) {
        return queryEvents(
            """
             where tenant_id = ? and model_spec_id = ? and model_revision = ?
               and details_json ->> 'implementationRevision' = ?
               and details_json ->> 'implementationChecksum' = ?
               and event_type = ? and status = ?
             order by created_date desc limit 1
            """,
            tenantId,
            modelSpecId,
            revision,
            Integer.toString(implementationRevision),
            implementationChecksum,
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

    /** Evidence is current only when it was emitted by the same append-only implementation revision. */
    public boolean hasPassedEvidence(
        String tenantId,
        UUID modelSpecId,
        int revision,
        int implementationRevision,
        String implementationChecksum,
        EventType eventType
    ) {
        Integer count = jdbcTemplate.queryForObject(
            """
            select count(*) from modeling_model_lifecycle_event
             where tenant_id = ? and model_spec_id = ? and model_revision = ?
               and event_type = ? and status = 'PASSED'
               and details_json ->> 'implementationRevision' = ?
               and details_json ->> 'implementationChecksum' = ?
            """,
            Integer.class,
            tenantId,
            modelSpecId,
            revision,
            eventType.name(),
            Integer.toString(implementationRevision),
            implementationChecksum
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
            throw new IllegalArgumentException("Stored implementation input payload is invalid", exception);
        }
    }

    private List<FieldMapping> readFieldMappings(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            return objectMapper.readValue(value, FIELD_MAPPINGS_TYPE);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("Stored implementation field mapping payload is invalid", exception);
        }
    }

    private Map<String, Object> readSettings(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(value, SETTINGS_TYPE);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("Stored implementation settings payload is invalid", exception);
        }
    }

    private String implementationJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Implementation persistence payload cannot be serialized", exception);
        }
    }

    private String implementationChecksum(SaveImplementationCommand command) {
        return implementationChecksumCodec.contentChecksum(command);
    }

    private static void requireImportedDbtCommand(
        ModelSpecView model,
        ModelStatus expectedModelStatus,
        String projectKey,
        String dbtUniqueId,
        SaveImplementationCommand command
    ) {
        boolean executionSettingsValid = command != null && (
            command.settings().isEmpty() ||
            ModelImplementationExecutionPlanner.plan(model, command, dbtUniqueId).valid()
        );
        if (
            model == null ||
            expectedModelStatus == null ||
            model.status() != expectedModelStatus ||
            model.implementationMode() != ImplementationMode.DBT_MANAGED ||
            projectKey == null ||
            projectKey.isBlank() ||
            dbtUniqueId == null ||
            dbtUniqueId.isBlank() ||
            command == null ||
            command.ownership() != ImplementationMode.DBT_MANAGED ||
            command.inputMode() != InputMode.GENERATED ||
            command.inputs().size() != 1 ||
            !(command.inputs().get(0) instanceof GeneratedInput input) ||
            !"DBT".equals(input.generatorType()) ||
            !command.fieldMappings().isEmpty() ||
            !executionSettingsValid ||
            !java.util.Objects.equals(input.config().get("projectKey"), projectKey.trim()) ||
            !java.util.Objects.equals(input.config().get("dbtUniqueId"), dbtUniqueId.trim())
        ) {
            throw new IllegalArgumentException("Imported DBT implementation payload is invalid");
        }
    }
}
