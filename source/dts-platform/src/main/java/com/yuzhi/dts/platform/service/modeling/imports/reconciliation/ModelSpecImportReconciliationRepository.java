package com.yuzhi.dts.platform.service.modeling.imports.reconciliation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.FieldMapping;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.GeneratedInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.InputMode;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.PhysicalAssetInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.SaveImplementationCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.UpstreamModelInput;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.RevisionPins;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Private read adapter for forward-undo eligibility and historical implementation payloads. */
@Repository
public class ModelSpecImportReconciliationRepository {

    private static final TypeReference<List<FieldMapping>> FIELD_MAPPINGS = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> SETTINGS = new TypeReference<>() {};

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ModelSpecImportReconciliationRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public UndoEligibility evaluateUndoEligibility(
        String tenantId,
        UUID modelSpecId,
        RevisionPins expectedCurrent,
        RevisionPins restorePins
    ) {
        List<UUID> lockedPlans = jdbcTemplate.query(
            """
            select plan.id
              from modeling_warehouse_plan plan
              join modeling_model_spec spec
                on spec.tenant_id = plan.tenant_id and spec.plan_id = plan.id
             where spec.tenant_id = ? and spec.id = ? and spec.contract_version = 2
             for update of plan
            """,
            (row, rowNumber) -> row.getObject("id", UUID.class),
            tenantId,
            modelSpecId
        );
        if (lockedPlans.size() != 1) {
            return UndoEligibility.blocked("CURRENT_MODEL_OR_IMPLEMENTATION_MISSING");
        }

        LockedUndoHead lockedHead = jdbcTemplate
            .query(
                """
                select spec.status, spec.revision, spec.current_checksum,
                       implementation.implementation_revision,
                       implementation.current_implementation_checksum
                  from modeling_model_spec spec
                  join modeling_model_implementation implementation
                    on implementation.tenant_id = spec.tenant_id
                   and implementation.model_spec_id = spec.id
                 where spec.tenant_id = ? and spec.id = ? and spec.contract_version = 2
                   and implementation.ownership = 'DBT_MANAGED'
                 for update of spec, implementation
                """,
                (row, rowNumber) -> new LockedUndoHead(
                    row.getString("status"),
                    row.getInt("revision"),
                    row.getString("current_checksum"),
                    row.getInt("implementation_revision"),
                    row.getString("current_implementation_checksum")
                ),
                tenantId,
                modelSpecId
            )
            .stream()
            .findFirst()
            .orElse(null);
        if (lockedHead == null) {
            return UndoEligibility.blocked("CURRENT_MODEL_OR_IMPLEMENTATION_MISSING");
        }
        if (
            lockedHead.modelRevision() != expectedCurrent.modelRevision() ||
            !expectedCurrent.modelChecksum().equals(lockedHead.modelChecksum()) ||
            lockedHead.implementationRevision() != expectedCurrent.implementationRevision() ||
            !expectedCurrent.implementationChecksum().equals(lockedHead.implementationChecksum())
        ) {
            return UndoEligibility.blocked("CURRENT_PINS_CHANGED");
        }
        if (!"DRAFT".equals(lockedHead.status())) {
            return UndoEligibility.blocked("MODEL_NOT_DRAFT");
        }

        // The plan, ModelSpec and implementation locks above are intentionally acquired before
        // this predicate read. The enclosing restore boundary is SERIALIZABLE, so a concurrent
        // release/materialization/downstream pin that races this read cannot commit together with
        // the restore as though both had observed the other operation absent.
        return jdbcTemplate
            .query(
                """
                select exists (
                           select 1 from modeling_model_spec_revision prior_spec
                            where prior_spec.tenant_id = spec.tenant_id
                              and prior_spec.model_spec_id = spec.id
                              and prior_spec.revision = ?
                              and prior_spec.content_checksum = ?
                              and prior_spec.contract_version = 2
                       ) as prior_model_exists,
                       exists (
                           select 1 from modeling_model_implementation_revision prior_implementation
                            where prior_implementation.tenant_id = implementation.tenant_id
                              and prior_implementation.implementation_id = implementation.id
                              and prior_implementation.revision = ?
                              and prior_implementation.content_checksum = ?
                              and prior_implementation.ownership = 'DBT_MANAGED'
                       ) as prior_implementation_exists,
                       exists (
                           select 1 from modeling_model_release_candidate_entry entry
                            where entry.tenant_id = spec.tenant_id and entry.model_spec_id = spec.id
                       ) as release_candidate_exists,
                       exists (
                           select 1
                             from modeling_model_release_candidate_entry entry
                             join modeling_materialization_dispatch dispatch
                               on dispatch.tenant_id = entry.tenant_id and dispatch.candidate_id = entry.candidate_id
                            where entry.tenant_id = spec.tenant_id and entry.model_spec_id = spec.id
                       ) as materialization_exists,
                       exists (
                           select 1
                             from modeling_model_spec downstream
                            where downstream.tenant_id = spec.tenant_id
                              and downstream.id <> spec.id
                              and downstream.status <> 'ARCHIVED'
                              and (
                                  exists (
                                      select 1 from jsonb_array_elements(coalesce(downstream.depends_on, '[]'::jsonb)) pin
                                       where pin ->> 'modelSpecId' = spec.id::text
                                         and pin ->> 'revision' = ?
                                  )
                                  or exists (
                                      select 1 from jsonb_array_elements(coalesce(downstream.dimension_refs, '[]'::jsonb)) pin
                                       where pin ->> 'modelSpecId' = spec.id::text
                                         and pin ->> 'revision' = ?
                                  )
                              )
                       ) as downstream_model_pin_exists,
                       exists (
                           select 1
                             from modeling_model_implementation downstream
                             cross join lateral jsonb_array_elements(coalesce(downstream.inputs_json, '[]'::jsonb)) pin
                            where downstream.tenant_id = spec.tenant_id
                              and downstream.model_spec_id <> spec.id
                              and pin ->> 'modelSpecId' = spec.id::text
                              and pin ->> 'revision' = ?
                              and pin ->> 'checksum' = ?
                              and pin ->> 'implementationRevision' = ?
                              and pin ->> 'implementationChecksum' = ?
                       ) as downstream_implementation_pin_exists
                  from modeling_model_spec spec
                  join modeling_model_implementation implementation
                    on implementation.tenant_id = spec.tenant_id and implementation.model_spec_id = spec.id
                 where spec.tenant_id = ? and spec.id = ?
                   and spec.contract_version = 2
                   and implementation.ownership = 'DBT_MANAGED'
                """,
                (row, rowNumber) -> {
                    if (row.getBoolean("materialization_exists")) return UndoEligibility.blocked("MATERIALIZATION_EXISTS");
                    if (row.getBoolean("release_candidate_exists")) return UndoEligibility.blocked("RELEASE_CANDIDATE_EXISTS");
                    if (row.getBoolean("downstream_model_pin_exists")) return UndoEligibility.blocked("DOWNSTREAM_MODEL_PIN");
                    if (row.getBoolean("downstream_implementation_pin_exists")) {
                        return UndoEligibility.blocked("DOWNSTREAM_IMPLEMENTATION_PIN");
                    }
                    if (!row.getBoolean("prior_model_exists")) return UndoEligibility.blocked("PREVIOUS_MODEL_REVISION_MISSING");
                    if (!row.getBoolean("prior_implementation_exists")) {
                        return UndoEligibility.blocked("PREVIOUS_IMPLEMENTATION_REVISION_MISSING");
                    }
                    return UndoEligibility.eligible();
                },
                restorePins.modelRevision(),
                restorePins.modelChecksum(),
                restorePins.implementationRevision(),
                restorePins.implementationChecksum(),
                Integer.toString(expectedCurrent.modelRevision()),
                Integer.toString(expectedCurrent.modelRevision()),
                Integer.toString(expectedCurrent.modelRevision()),
                expectedCurrent.modelChecksum(),
                Integer.toString(expectedCurrent.implementationRevision()),
                expectedCurrent.implementationChecksum(),
                tenantId,
                modelSpecId
            )
            .stream()
            .findFirst()
            .orElse(UndoEligibility.blocked("CURRENT_MODEL_OR_IMPLEMENTATION_MISSING"));
    }

    private record LockedUndoHead(
        String status,
        int modelRevision,
        String modelChecksum,
        int implementationRevision,
        String implementationChecksum
    ) {}

    @Transactional(readOnly = true)
    public Optional<HistoricalImplementation> findHistoricalImplementation(
        String tenantId,
        UUID modelSpecId,
        RevisionPins pins,
        String idempotencyKey
    ) {
        return jdbcTemplate
            .query(
                """
                select implementation.project_key, implementation.dbt_unique_id,
                       revision.input_mode, revision.inputs_json::text as inputs_json,
                       revision.field_mappings_json::text as field_mappings_json,
                       revision.settings_json::text as settings_json,
                       revision.ownership, revision.materialization
                  from modeling_model_implementation implementation
                  join modeling_model_implementation_revision revision
                    on revision.tenant_id = implementation.tenant_id
                   and revision.implementation_id = implementation.id
                 where implementation.tenant_id = ?
                   and implementation.model_spec_id = ?
                   and revision.revision = ?
                   and revision.content_checksum = ?
                   and implementation.ownership = 'DBT_MANAGED'
                   and revision.ownership = 'DBT_MANAGED'
                """,
                (row, rowNumber) -> {
                    InputMode mode = InputMode.valueOf(row.getString("input_mode"));
                    SaveImplementationCommand command = new SaveImplementationCommand(
                        mode,
                        readInputs(mode, row.getString("inputs_json")),
                        read(row.getString("field_mappings_json"), FIELD_MAPPINGS),
                        read(row.getString("settings_json"), SETTINGS),
                        ImplementationMode.valueOf(row.getString("ownership")),
                        row.getString("materialization"),
                        idempotencyKey
                    );
                    return new HistoricalImplementation(
                        row.getString("project_key"),
                        row.getString("dbt_unique_id"),
                        command
                    );
                },
                tenantId,
                modelSpecId,
                pins.implementationRevision(),
                pins.implementationChecksum()
            )
            .stream()
            .findFirst();
    }

    private List<ImplementationInput> readInputs(InputMode inputMode, String json) {
        try {
            return switch (inputMode) {
                case PHYSICAL_ASSET -> objectMapper
                    .readValue(json, new TypeReference<List<PhysicalAssetInput>>() {})
                    .stream().map(value -> (ImplementationInput) value).toList();
                case UPSTREAM_MODEL -> objectMapper
                    .readValue(json, new TypeReference<List<UpstreamModelInput>>() {})
                    .stream().map(value -> (ImplementationInput) value).toList();
                case GENERATED -> objectMapper
                    .readValue(json, new TypeReference<List<GeneratedInput>>() {})
                    .stream().map(value -> (ImplementationInput) value).toList();
            };
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalStateException("Stored implementation input payload is invalid", exception);
        }
    }

    private <T> T read(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored implementation payload is invalid", exception);
        }
    }

    public record UndoEligibility(boolean allowed, String reasonCode) {
        static UndoEligibility eligible() {
            return new UndoEligibility(true, null);
        }

        static UndoEligibility blocked(String reasonCode) {
            return new UndoEligibility(false, reasonCode);
        }
    }

    public record HistoricalImplementation(
        String projectKey,
        String dbtUniqueId,
        SaveImplementationCommand command
    ) {}
}
