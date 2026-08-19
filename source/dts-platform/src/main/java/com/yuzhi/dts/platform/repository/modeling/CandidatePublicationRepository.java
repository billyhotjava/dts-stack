package com.yuzhi.dts.platform.repository.modeling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.security.SecurityLevelCatalog;
import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationEvidenceRepository.PublicationEntryEvidence;
import com.yuzhi.dts.platform.service.etl.DbtSqlProjectionParser;
import com.yuzhi.dts.platform.service.modeling.CandidatePublicationAssetFactory;
import com.yuzhi.dts.platform.service.modeling.ModelExecutionTargetCatalogResolver.ResolvedCatalogTarget;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.LifecycleEventView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException.Kind;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelField;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.SourceKind;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.SourceRef;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.PhysicalColumn;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Local, transaction-participating publication writes. No method opens a new transaction or invokes Airflow/dbt.
 */
@Repository
public class CandidatePublicationRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public CandidatePublicationRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public PublishedModelBinding registerModel(
        CandidateView candidate,
        ResolvedCatalogTarget target,
        PublicationEntryEvidence evidence,
        ModelSpecView model,
        LifecycleEventView release,
        String actorId,
        Instant now
    ) {
        Objects.requireNonNull(candidate, "candidate is required");
        Objects.requireNonNull(target, "catalog target is required");
        Objects.requireNonNull(evidence, "evidence is required");
        Objects.requireNonNull(model, "model is required");
        Objects.requireNonNull(release, "release is required");
        Objects.requireNonNull(now, "now is required");
        String actor = actor(actorId);
        String tags = json(
            Map.ofEntries(
                Map.entry("tenantId", candidate.tenantId()),
                Map.entry("sourceId", target.sourceId()),
                Map.entry("executionTargetKey", target.executionTargetKey()),
                Map.entry("modelSpecId", model.id()),
                Map.entry("revision", model.revision()),
                Map.entry("modelChecksum", model.checksum()),
                Map.entry("implementationRevision", evidence.implementationRevision()),
                Map.entry("implementationChecksum", evidence.implementationChecksum()),
                Map.entry("dbtUniqueId", evidence.dbtUniqueId()),
                Map.entry("targetIdentifier", evidence.targetIdentifier()),
                Map.entry("adapter", evidence.adapter()),
                Map.entry("database", evidence.databaseName()),
                Map.entry("schema", evidence.schemaName()),
                Map.entry("materializedTruth", true),
                Map.entry("physicalAssetVerified", true),
                Map.entry("artifactState", "CURRENT"),
                Map.entry("runStatus", "SUCCESS"),
                Map.entry("runInvocationId", evidence.dbtInvocationId())
            )
        );
        UUID assetId = registerCatalogDataset(
            candidate,
            target,
            evidence,
            model,
            tags,
            actor,
            now
        );
        String externalRef = "catalog-dataset:" + assetId;

        UUID tableId = registerCatalogTable(
            candidate,
            assetId,
            evidence,
            model,
            actor,
            now
        );
        replaceColumns(tableId, model, evidence, actor, now);
        replaceModelLineage(model, now);
        List<CatalogLineageInput> lineageInputs = replaceCatalogLineage(
            candidate,
            model,
            assetId,
            evidence,
            actor,
            now
        );
        replaceCatalogColumnLineage(
            candidate,
            model,
            assetId,
            tableId,
            evidence,
            lineageInputs,
            actor,
            now
        );

        int artifactWrites = jdbcTemplate.update(
            """
            update modeling_dbt_artifact artifact
               set physical_asset_ref = ?, last_modified_date = ?
              from modeling_model_spec spec
             where spec.id = artifact.model_spec_id
               and spec.tenant_id = ?
               and artifact.model_spec_id = ?
               and artifact.revision = ?
               and artifact.model_checksum = ?
               and artifact.implementation_revision = ?
               and artifact.status = 'COMPILED'
            """,
            assetId,
            Timestamp.from(now),
            candidate.tenantId(),
            model.id(),
            model.revision(),
            model.checksum(),
            evidence.implementationRevision()
        );
        if (artifactWrites < 1) {
            throw publicationConflict(candidate, "Current compiled artifacts were not bound to the physical asset");
        }

        Map<String, Object> releaseFacts = new LinkedHashMap<>();
        releaseFacts.put("candidateId", candidate.id());
        releaseFacts.put("candidateVersion", candidate.version());
        releaseFacts.put("executionTargetKey", candidate.executionTargetKey());
        releaseFacts.put("environment", candidate.environment());
        releaseFacts.put("dbtUniqueId", evidence.dbtUniqueId());
        releaseFacts.put("targetIdentifier", evidence.targetIdentifier());
        releaseFacts.put("artifactChecksum", evidence.artifactChecksum());
        releaseFacts.put("dependencySnapshotChecksum", evidence.dependencySnapshotChecksum());
        releaseFacts.put("physicalAssetId", assetId);
        releaseFacts.put("pipelineRunId", evidence.pipelineRunId());
        releaseFacts.put("physicalMetadataChecksum", evidence.metadataChecksum());
        int releaseWrites = jdbcTemplate.update(
            """
            update modeling_model_lifecycle_event
               set status = 'PUBLISHED',
                   external_ref = ?,
                   details_json = coalesce(details_json, '{}'::jsonb) || cast(? as jsonb)
             where id = ?
               and tenant_id = ?
               and model_spec_id = ?
               and model_revision = ?
               and model_checksum = ?
               and event_type = 'RELEASE'
               and status in ('REGISTERING', 'PUBLISHED')
            """,
            externalRef,
            json(releaseFacts),
            release.id(),
            candidate.tenantId(),
            model.id(),
            model.revision(),
            model.checksum()
        );
        if (releaseWrites != 1) throw publicationConflict(candidate, "Lifecycle release evidence was not finalized");
        return new PublishedModelBinding(
            model.id(),
            release.id(),
            model.revision(),
            evidence.dbtUniqueId(),
            evidence.targetIdentifier(),
            evidence.artifactChecksum(),
            evidence.dependencySnapshotChecksum(),
            assetId
        );
    }

    public void rebuildManualBinding(
        CandidateView candidate,
        List<PublishedModelBinding> committed,
        String actorId,
        Instant now
    ) {
        rebuildManualBinding(candidate, committed, false, actorId, now);
    }

    public void rebuildManualBindingAfterRollback(
        CandidateView candidate,
        String actorId,
        Instant now
    ) {
        rebuildManualBinding(candidate, List.of(), true, actorId, now);
    }

    public void rollbackModel(
        CandidateView candidate,
        PublicationEntryEvidence evidence,
        ModelSpecView model,
        LifecycleEventView rollback,
        String actorId,
        Instant now
    ) {
        Objects.requireNonNull(candidate, "candidate is required");
        Objects.requireNonNull(evidence, "evidence is required");
        Objects.requireNonNull(model, "model is required");
        Objects.requireNonNull(rollback, "rollback is required");
        UUID assetId = requireCandidatePublishedAssetId(
            candidate,
            model.id(),
            model.revision()
        );
        String actor = actor(actorId);
        int assetWrites = jdbcTemplate.update(
            """
            update catalog_dataset
               set enabled = false,
                   lifecycle_status = 'ARCHIVED',
                   last_modified_by = ?,
                   last_modified_date = ?
             where id = ?
               and enabled = true
               and lifecycle_status in ('ACTIVE', 'PUBLISHED', 'PENDING_GOVERNANCE')
            """,
            actor,
            Timestamp.from(now),
            assetId
        );
        if (assetWrites != 1) throw publicationConflict(candidate, "Published Catalog asset was not rolled back");
        jdbcTemplate.update(
            """
            update catalog_dataset_lineage
               set valid_to = ?, last_modified_by = ?, last_modified_date = ?
             where downstream_dataset_id = ?
               and valid_to is null
            """,
            Timestamp.from(now),
            actor,
            Timestamp.from(now),
            assetId
        );
        jdbcTemplate.update(
            """
            update catalog_column_lineage
               set valid_to = ?, last_modified_by = ?, last_modified_date = ?
             where downstream_dataset_id = ?
               and valid_to is null
            """,
            Timestamp.from(now),
            actor,
            Timestamp.from(now),
            assetId
        );
        int artifactWrites = jdbcTemplate.update(
            """
            update modeling_dbt_artifact artifact
               set physical_asset_ref = null, last_modified_date = ?
              from modeling_model_spec spec
             where spec.id = artifact.model_spec_id
               and spec.tenant_id = ?
               and artifact.model_spec_id = ?
               and artifact.revision = ?
               and artifact.model_checksum = ?
               and artifact.implementation_revision = ?
               and artifact.physical_asset_ref = ?
            """,
            Timestamp.from(now),
            candidate.tenantId(),
            model.id(),
            model.revision(),
            model.checksum(),
            evidence.implementationRevision(),
            assetId
        );
        if (artifactWrites < 1) {
            throw publicationConflict(candidate, "Published artifacts were not detached from the Catalog asset");
        }
        int releaseWrites = jdbcTemplate.update(
            """
            update modeling_model_lifecycle_event
               set status = 'ROLLED_BACK',
                   details_json = coalesce(details_json, '{}'::jsonb)
                       || jsonb_build_object('rollbackEventId', cast(? as text))
             where tenant_id = ?
               and model_spec_id = ?
               and model_revision = ?
               and model_checksum = ?
               and event_type = 'RELEASE'
               and status = 'PUBLISHED'
            """,
            rollback.id(),
            candidate.tenantId(),
            model.id(),
            model.revision(),
            model.checksum()
        );
        if (releaseWrites != 1) {
            throw publicationConflict(candidate, "Published lifecycle release was not rolled back");
        }
    }

    private void rebuildManualBinding(
        CandidateView candidate,
        List<PublishedModelBinding> committed,
        boolean allowEmpty,
        String actorId,
        Instant now
    ) {
        if (candidate == null || candidate.executionTargetKey() == null) {
            throw new IllegalArgumentException("Candidate execution target is required");
        }
        requireCompletePublishedReleaseFacts(candidate);
        List<PublishedModelBinding> scope = loadPublishedScope(candidate);
        List<UUID> committedModels = committed == null
            ? List.of()
            : committed.stream().map(PublishedModelBinding::modelSpecId).sorted().toList();
        List<UUID> scopeModels = scope.stream().map(PublishedModelBinding::modelSpecId).sorted().toList();
        if (!scopeModels.containsAll(committedModels) || (!allowEmpty && scope.isEmpty())) {
            throw publicationConflict(candidate, "Published plan scope could not be rebuilt");
        }
        String scopeChecksum = digest(
            scope
                .stream()
                .sorted(Comparator.comparing(PublishedModelBinding::modelSpecId))
                .flatMap(binding ->
                    List.of(
                        binding.modelSpecId().toString(),
                        binding.releaseId().toString(),
                        Integer.toString(binding.modelRevision()),
                        binding.dbtUniqueId(),
                        binding.targetIdentifier(),
                        binding.artifactChecksum(),
                        binding.dependencySnapshotChecksum()
                    )
                        .stream()
                )
                .toList()
        );
        String dagId = resolveExistingAirflowDag(candidate);
        String deploymentChecksum = digest(
            List.of(
                candidate.tenantId(),
                candidate.planId().toString(),
                candidate.environment(),
                candidate.executionTargetKey(),
                "MANUAL_ONLY",
                dagId,
                scopeChecksum
            )
        );
        UUID bindingId = stableUuid(
            "model-plan-binding:" +
            candidate.tenantId() +
            ":" +
            candidate.planId() +
            ":" +
            candidate.environment() +
            ":" +
            candidate.executionTargetKey()
        );
        String actor = actor(actorId);
        jdbcTemplate.update(
            """
            insert into modeling_plan_execution_binding (
                id, tenant_id, plan_id, environment, execution_target_key,
                version, schedule_mode, cron_expression, timezone,
                desired_scope_checksum, desired_deployment_checksum,
                dag_id, deployment_status, created_by, created_date,
                last_modified_by, last_modified_date
            ) values (
                ?, ?, ?, ?, ?, 1, 'MANUAL_ONLY', null, null, ?, ?, ?,
                'DEPLOYING', ?, ?, ?, ?
            )
            on conflict (tenant_id, plan_id, environment, execution_target_key)
            do update
               set version = case
                       when modeling_plan_execution_binding.desired_deployment_checksum
                            <> excluded.desired_deployment_checksum
                       then modeling_plan_execution_binding.version + 1
                       else modeling_plan_execution_binding.version
                   end,
                   desired_scope_checksum = excluded.desired_scope_checksum,
                   desired_deployment_checksum = excluded.desired_deployment_checksum,
                   dag_id = excluded.dag_id,
                   deployment_status = case
                       when modeling_plan_execution_binding.desired_deployment_checksum
                            <> excluded.desired_deployment_checksum
                       then 'DEPLOYING'
                       else modeling_plan_execution_binding.deployment_status
                   end,
                   last_error_code = null,
                   last_error_message = null,
                   last_modified_by = excluded.last_modified_by,
                   last_modified_date = excluded.last_modified_date
            """,
            bindingId,
            candidate.tenantId(),
            candidate.planId(),
            candidate.environment(),
            candidate.executionTargetKey(),
            scopeChecksum,
            deploymentChecksum,
            dagId,
            actor,
            Timestamp.from(now),
            actor,
            Timestamp.from(now)
        );
        UUID persistedBindingId = jdbcTemplate.queryForObject(
            """
            select id
              from modeling_plan_execution_binding
             where tenant_id = ? and plan_id = ? and environment = ?
               and execution_target_key = ?
             for update
            """,
            UUID.class,
            candidate.tenantId(),
            candidate.planId(),
            candidate.environment(),
            candidate.executionTargetKey()
        );
        jdbcTemplate.update(
            "delete from modeling_plan_execution_binding_entry where binding_id = ?",
            persistedBindingId
        );
        for (PublishedModelBinding entry : scope) {
            jdbcTemplate.update(
                """
                insert into modeling_plan_execution_binding_entry (
                    id, tenant_id, binding_id, model_spec_id,
                    published_release_id, model_revision, dbt_unique_id,
                    target_identifier, artifact_checksum,
                    dependency_snapshot_checksum, created_date
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                stableUuid("model-plan-binding-entry:" + persistedBindingId + ":" + entry.modelSpecId()),
                candidate.tenantId(),
                persistedBindingId,
                entry.modelSpecId(),
                entry.releaseId(),
                entry.modelRevision(),
                entry.dbtUniqueId(),
                entry.targetIdentifier(),
                entry.artifactChecksum(),
                entry.dependencySnapshotChecksum(),
                Timestamp.from(now)
            );
        }
    }

    private List<PublishedModelBinding> loadPublishedScope(CandidateView candidate) {
        return jdbcTemplate.query(
            """
            select s.id as model_spec_id, release.id as release_id,
                   s.revision as model_revision,
                   release.details_json ->> 'dbtUniqueId' as dbt_unique_id,
                   release.details_json ->> 'targetIdentifier' as target_identifier,
                   release.details_json ->> 'artifactChecksum' as artifact_checksum,
                   release.details_json ->> 'dependencySnapshotChecksum' as dependency_snapshot_checksum,
                   cast(release.details_json ->> 'physicalAssetId' as uuid) as physical_asset_id
              from modeling_model_spec s
              join lateral (
                    select event.id, event.details_json
                      from modeling_model_lifecycle_event event
                     where event.tenant_id = s.tenant_id
                       and event.model_spec_id = s.id
                       and event.model_revision = s.revision
                       and event.model_checksum = s.current_checksum
                       and event.event_type = 'RELEASE'
                       and event.status = 'PUBLISHED'
                       and event.details_json ->> 'executionTargetKey' = ?
                       and event.details_json ->> 'environment' = ?
                     order by event.created_date desc, event.id desc
                     limit 1
              ) release on true
             where s.tenant_id = ?
               and s.plan_id = ?
               and s.contract_version = 2
               and s.status = 'PUBLISHED'
             order by s.id
            """,
            (row, rowNumber) ->
                new PublishedModelBinding(
                    row.getObject("model_spec_id", UUID.class),
                    row.getObject("release_id", UUID.class),
                    row.getInt("model_revision"),
                    row.getString("dbt_unique_id"),
                    row.getString("target_identifier"),
                    row.getString("artifact_checksum"),
                    row.getString("dependency_snapshot_checksum"),
                    row.getObject("physical_asset_id", UUID.class)
                ),
            candidate.executionTargetKey(),
            candidate.environment(),
            candidate.tenantId(),
            candidate.planId()
        );
    }

    private void requireCompletePublishedReleaseFacts(CandidateView candidate) {
        List<UUID> incompleteModels = jdbcTemplate.query(
            """
            select s.id
              from modeling_model_spec s
              left join lateral (
                    select event.id, event.details_json
                      from modeling_model_lifecycle_event event
                     where event.tenant_id = s.tenant_id
                       and event.model_spec_id = s.id
                       and event.model_revision = s.revision
                       and event.model_checksum = s.current_checksum
                       and event.event_type = 'RELEASE'
                       and event.status = 'PUBLISHED'
                     order by event.created_date desc, event.id desc
                     limit 1
              ) release on true
             where s.tenant_id = ?
               and s.plan_id = ?
               and s.contract_version = 2
               and s.status = 'PUBLISHED'
               and (
                    release.id is null
                    or nullif(btrim(release.details_json ->> 'executionTargetKey'), '') is null
                    or nullif(btrim(release.details_json ->> 'environment'), '') is null
                    or nullif(btrim(release.details_json ->> 'dbtUniqueId'), '') is null
                    or nullif(btrim(release.details_json ->> 'targetIdentifier'), '') is null
                    or nullif(btrim(release.details_json ->> 'artifactChecksum'), '') is null
                    or nullif(btrim(release.details_json ->> 'dependencySnapshotChecksum'), '') is null
               )
             order by s.id
            """,
            (row, rowNumber) -> row.getObject("id", UUID.class),
            candidate.tenantId(),
            candidate.planId()
        );
        if (!incompleteModels.isEmpty()) {
            throw new ModelReleaseCandidateException(
                "MODEL_PLAN_BINDING_RELEASE_FACTS_REQUIRED",
                "Published models must be migrated to current release facts before rebuilding the plan binding",
                Kind.CONFLICT,
                Map.of(
                    "candidateId",
                    candidate.id(),
                    "planId",
                    candidate.planId(),
                    "modelSpecIds",
                    incompleteModels.stream().limit(20).toList(),
                    "remainingCount",
                    Math.max(0, incompleteModels.size() - 20)
                )
            );
        }
    }

    private UUID registerCatalogDataset(
        CandidateView candidate,
        ResolvedCatalogTarget target,
        PublicationEntryEvidence evidence,
        ModelSpecView model,
        String tags,
        String actor,
        Instant now
    ) {
        List<UUID> matching = lockCatalogAssetIds(
            target.sourceId(),
            evidence.schemaName(),
            evidence.identifier()
        );
        if (matching.size() > 1) {
            throw publicationConflict(
                candidate,
                "Catalog physical locator is ambiguous; merge the listed legacy asset before retry"
            );
        }
        UUID proposedId = CandidatePublicationAssetFactory.catalogDatasetId(
            target.sourceId(),
            evidence.schemaName(),
            evidence.identifier()
        );
        if (matching.isEmpty()) {
            jdbcTemplate.update(
                """
                insert into catalog_dataset (
                    id, name, domain_id, type, source_id,
                    classification, owner, hive_database, hive_table,
                    tags, description, warehouse_layer, enabled,
                    exposed_by, lifecycle_status, snapshot_time,
                    created_by, created_date,
                    last_modified_by, last_modified_date
                ) values (
                    ?, ?, ?, 'jdbc', ?, ?, ?, ?, ?, ?, ?, ?, true,
                    'VIEW', 'ACTIVE', ?, ?, ?, ?, ?
                )
                on conflict do nothing
                """,
                proposedId,
                model.name(),
                model.domainId(),
                target.sourceId(),
                SecurityLevelCatalog.DEFAULT_DATA_SECURITY_LEVEL.code(),
                actor,
                evidence.schemaName(),
                evidence.identifier(),
                tags,
                model.description(),
                model.layer().name(),
                Timestamp.from(evidence.observedAt()),
                actor,
                Timestamp.from(now),
                actor,
                Timestamp.from(now)
            );
            matching = lockCatalogAssetIds(
                target.sourceId(),
                evidence.schemaName(),
                evidence.identifier()
            );
        }
        if (matching.size() != 1) {
            throw publicationConflict(
                candidate,
                "Catalog physical locator could not be claimed uniquely"
            );
        }
        UUID assetId = matching.getFirst();
        int assetWrites = jdbcTemplate.update(
            """
            update catalog_dataset
               set name = ?,
                   domain_id = ?,
                   type = 'jdbc',
                   source_id = ?,
                   classification = ?,
                   owner = ?,
                   hive_database = ?,
                   hive_table = ?,
                   tags = ?,
                   description = ?,
                   warehouse_layer = ?,
                   enabled = true,
                   exposed_by = 'VIEW',
                   lifecycle_status = 'ACTIVE',
                   snapshot_time = ?,
                   last_modified_by = ?,
                   last_modified_date = ?
             where id = ?
            """,
            model.name(),
            model.domainId(),
            target.sourceId(),
            SecurityLevelCatalog.DEFAULT_DATA_SECURITY_LEVEL.code(),
            actor,
            evidence.schemaName(),
            evidence.identifier(),
            tags,
            model.description(),
            model.layer().name(),
            Timestamp.from(evidence.observedAt()),
            actor,
            Timestamp.from(now),
            assetId
        );
        if (assetWrites != 1) {
            throw publicationConflict(
                candidate,
                "Catalog dataset was not registered"
            );
        }
        return assetId;
    }

    private List<UUID> lockCatalogAssetIds(
        UUID sourceId,
        String schemaName,
        String identifier
    ) {
        return jdbcTemplate.queryForList(
            """
            select id
              from catalog_dataset
             where lower(btrim(hive_database)) = lower(btrim(?))
               and lower(btrim(hive_table)) = lower(btrim(?))
               and (source_id = ? or source_id is null)
             order by source_id nulls last, id
             for update
            """,
            UUID.class,
            schemaName,
            identifier,
            sourceId
        );
    }

    private UUID registerCatalogTable(
        CandidateView candidate,
        UUID assetId,
        PublicationEntryEvidence evidence,
        ModelSpecView model,
        String actor,
        Instant now
    ) {
        List<UUID> tableIds = lockCatalogTableIds(
            assetId,
            evidence.identifier()
        );
        if (tableIds.size() > 1) {
            throw publicationConflict(
                candidate,
                "Catalog table projection is ambiguous"
            );
        }
        UUID proposedId = stableUuid(
            "catalog-physical:table:" +
            assetId +
            ":" +
            evidence.identifier().trim().toLowerCase(Locale.ROOT)
        );
        if (tableIds.isEmpty()) {
            jdbcTemplate.update(
                """
                insert into catalog_table_schema (
                    id, dataset_id, name, owner, classification, tags,
                    created_by, created_date,
                    last_modified_by, last_modified_date
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict do nothing
                """,
                proposedId,
                assetId,
                evidence.identifier(),
                actor,
                SecurityLevelCatalog.DEFAULT_DATA_SECURITY_LEVEL.code(),
                json(
                    Map.of(
                        "modelSpecId",
                        model.id(),
                        "revision",
                        model.revision()
                    )
                ),
                actor,
                Timestamp.from(now),
                actor,
                Timestamp.from(now)
            );
            tableIds = lockCatalogTableIds(
                assetId,
                evidence.identifier()
            );
        }
        if (tableIds.size() != 1) {
            throw publicationConflict(
                candidate,
                "Catalog table projection could not be claimed uniquely"
            );
        }
        UUID tableId = tableIds.getFirst();
        int tableWrites = jdbcTemplate.update(
            """
            update catalog_table_schema
               set dataset_id = ?,
                   name = ?,
                   owner = ?,
                   classification = ?,
                   tags = ?,
                   last_modified_by = ?,
                   last_modified_date = ?
             where id = ?
            """,
            assetId,
            evidence.identifier(),
            actor,
            SecurityLevelCatalog.DEFAULT_DATA_SECURITY_LEVEL.code(),
            json(
                Map.of(
                    "modelSpecId",
                    model.id(),
                    "revision",
                    model.revision(),
                    "physicalMetadataChecksum",
                    evidence.metadataChecksum()
                )
            ),
            actor,
            Timestamp.from(now),
            tableId
        );
        if (tableWrites != 1) {
            throw publicationConflict(
                candidate,
                "Catalog table projection was not registered"
            );
        }
        return tableId;
    }

    private List<UUID> lockCatalogTableIds(
        UUID assetId,
        String identifier
    ) {
        return jdbcTemplate.queryForList(
            """
            select id
              from catalog_table_schema
             where dataset_id = ?
               and lower(btrim(name)) = lower(btrim(?))
             order by id
             for update
            """,
            UUID.class,
            assetId,
            identifier
        );
    }

    private String resolveExistingAirflowDag(CandidateView candidate) {
        List<String> dagIds = jdbcTemplate.queryForList(
            """
            select d.airflow_dag_id
              from modeling_materialization_dispatch d
             where d.tenant_id = ?
               and d.candidate_id = ?
               and d.status = 'COMPLETED'
             order by d.attempt desc, d.last_modified_at desc, d.id desc
             limit 1
            """,
            String.class,
            candidate.tenantId(),
            candidate.id()
        );
        if (dagIds.isEmpty() || dagIds.getFirst() == null || dagIds.getFirst().isBlank()) {
            throw publicationConflict(candidate, "Existing Airflow release-build DAG evidence is required");
        }
        return dagIds.getFirst().trim();
    }

    private void replaceColumns(
        UUID tableId,
        ModelSpecView model,
        PublicationEntryEvidence evidence,
        String actor,
        Instant now
    ) {
        Map<String, ModelField> designedFields = new LinkedHashMap<>();
        for (ModelField field : model.fields()) {
            designedFields.put(
                field.name().trim().toLowerCase(Locale.ROOT),
                field
            );
        }
        jdbcTemplate.update("delete from catalog_column_schema where table_id = ?", tableId);
        for (PhysicalColumn actual : evidence.actualColumns()) {
            ModelField field = designedFields.get(
                actual.name().trim().toLowerCase(Locale.ROOT)
            );
            Map<String, Object> columnTags = new LinkedHashMap<>();
            columnTags.put("physicalObservation", true);
            columnTags.put(
                "ordinalPosition",
                actual.ordinalPosition()
            );
            if (field != null && field.role() != null) {
                columnTags.put("fieldRole", field.role().name());
            }
            jdbcTemplate.update(
                """
                insert into catalog_column_schema (
                    id, table_id, name, data_type, nullable, tags,
                    sensitive_tags, comment, status,
                    created_by, created_date, last_modified_by, last_modified_date
                ) values (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?)
                """,
                stableUuid(
                    "catalog-physical:column:" +
                    tableId +
                    ":" +
                    actual.name().trim().toLowerCase(Locale.ROOT)
                ),
                tableId,
                actual.name(),
                actual.dataType(),
                actual.nullable(),
                json(columnTags),
                field == null ? null : field.securityLevel(),
                field == null ? null : field.displayName(),
                actor,
                Timestamp.from(now),
                actor,
                Timestamp.from(now)
            );
        }
    }

    private void replaceModelLineage(ModelSpecView model, Instant now) {
        jdbcTemplate.update(
            "delete from modeling_lineage_edge where to_model_spec_id = ? and edge_type = 'MODEL_DEPENDENCY'",
            model.id()
        );
        for (var dependency : model.dependsOn()) {
            jdbcTemplate.update(
                """
                insert into modeling_lineage_edge (
                    id, from_model_spec_id, to_model_spec_id,
                    edge_type, created_date, last_modified_date
                ) values (?, ?, ?, 'MODEL_DEPENDENCY', ?, ?)
                on conflict (from_model_spec_id, to_model_spec_id, edge_type)
                do update set last_modified_date = excluded.last_modified_date
                """,
                stableUuid("model-lineage:" + dependency.modelSpecId() + ":" + model.id()),
                dependency.modelSpecId(),
                model.id(),
                Timestamp.from(now),
                Timestamp.from(now)
            );
        }
    }

    private List<CatalogLineageInput> replaceCatalogLineage(
        CandidateView candidate,
        ModelSpecView model,
        UUID downstreamAssetId,
        PublicationEntryEvidence evidence,
        String actor,
        Instant now
    ) {
        jdbcTemplate.update(
            """
            update catalog_dataset_lineage
               set valid_to = ?, last_modified_by = ?, last_modified_date = ?
             where downstream_dataset_id = ?
               and relation_type = 'MODEL_DEPENDENCY'
               and valid_to is null
            """,
            Timestamp.from(now),
            actor,
            Timestamp.from(now),
            downstreamAssetId
        );
        Map<UUID, Boolean> upstreams = new LinkedHashMap<>();
        for (var dependency : model.dependsOn()) {
            UUID upstreamAssetId = requirePublishedAssetId(
                candidate,
                dependency.modelSpecId(),
                dependency.revision()
            );
            Integer exists = jdbcTemplate.queryForObject(
                "select count(*) from catalog_dataset where id = ? and enabled = true",
                Integer.class,
                upstreamAssetId
            );
            if (exists == null || exists != 1) {
                throw publicationConflict(candidate, "Published dependency Catalog asset is required");
            }
            upstreams.putIfAbsent(upstreamAssetId, false);
        }
        for (UUID sourceAssetId : resolveConfirmedOdsSourceAssets(candidate, model)) {
            upstreams.merge(
                sourceAssetId,
                true,
                (existing, physicalSource) -> existing || physicalSource
            );
        }
        List<CatalogLineageInput> inputs = new ArrayList<>();
        for (Map.Entry<UUID, Boolean> upstream : upstreams.entrySet()) {
            UUID lineageId = writeCatalogLineage(
                candidate,
                upstream.getKey(),
                downstreamAssetId,
                evidence,
                actor,
                now
            );
            inputs.add(
                new CatalogLineageInput(
                    upstream.getKey(),
                    lineageId,
                    upstream.getValue(),
                    loadCatalogRelationNames(upstream.getKey())
                )
            );
        }
        return List.copyOf(inputs);
    }

    private List<UUID> resolveConfirmedOdsSourceAssets(
        CandidateView candidate,
        ModelSpecView model
    ) {
        if (model.sourceRefs() == null || model.sourceRefs().isEmpty()) {
            return List.of();
        }
        List<UUID> assets = new ArrayList<>();
        for (SourceRef source : model.sourceRefs()) {
            if (
                source == null ||
                source.kind() != SourceKind.TABLE ||
                source.layer() != Layer.ODS
            ) {
                continue;
            }
            if (
                source.sourceBindingId() == null ||
                source.ref() == null ||
                source.resolvedVersion() == null
            ) {
                throw publicationConflict(
                    candidate,
                    "Confirmed ODS source identity is required for Catalog lineage"
                );
            }
            List<UUID> matches = jdbcTemplate.queryForList(
                """
                select dataset.id
                  from modeling_warehouse_plan_source binding
                  join catalog_table_schema table_schema
                    on table_schema.id::text = coalesce(
                           nullif(binding.locator_json ->> 'assetId', ''),
                           binding.source_id
                       )
                  join catalog_dataset dataset
                    on dataset.id = table_schema.dataset_id
                 where binding.tenant_id = ?
                   and binding.plan_id = ?
                   and binding.id = ?
                   and binding.source_type = 'CATALOG_TABLE'
                   and binding.source_id = ?
                   and binding.source_version = ?
                   and binding.confirmation_status = 'CONFIRMED'
                   and dataset.enabled = true
                   and upper(
                           coalesce(
                               nullif(dataset.warehouse_layer, ''),
                               'ODS'
                           )
                       ) = 'ODS'
                """,
                UUID.class,
                candidate.tenantId(),
                candidate.planId(),
                source.sourceBindingId(),
                source.ref(),
                source.resolvedVersion()
            );
            if (matches.size() != 1) {
                throw publicationConflict(
                    candidate,
                    "Confirmed ODS source must resolve to one enabled Catalog asset"
                );
            }
            assets.add(matches.getFirst());
        }
        return assets.stream().distinct().toList();
    }

    private UUID writeCatalogLineage(
        CandidateView candidate,
        UUID upstreamAssetId,
        UUID downstreamAssetId,
        PublicationEntryEvidence evidence,
        String actor,
        Instant now
    ) {
        UUID lineageId = stableUuid(
            "catalog-model-lineage:" +
            upstreamAssetId +
            ":" +
            downstreamAssetId +
            ":" +
            evidence.metadataChecksum()
        );
        jdbcTemplate.update(
            """
            insert into catalog_dataset_lineage (
                id, upstream_dataset_id, downstream_dataset_id,
                relation_type, notes, upstream_asset_type,
                downstream_asset_type, direction, project_name,
                verification_status, last_execution_id,
                last_execution_status, last_observed_at,
                last_verified_at, valid_from, valid_to,
                created_by, created_date, last_modified_by,
                last_modified_date
            ) values (
                ?, ?, ?, 'MODEL_DEPENDENCY', ?, 'DATASET', 'DATASET',
                'FORWARD', ?, 'VERIFIED', ?, 'SUCCESS', ?, ?, ?, null,
                ?, ?, ?, ?
            )
            on conflict (id) do update
               set upstream_dataset_id = excluded.upstream_dataset_id,
                   downstream_dataset_id = excluded.downstream_dataset_id,
                   notes = excluded.notes,
                   project_name = excluded.project_name,
                   verification_status = 'VERIFIED',
                   last_execution_id = excluded.last_execution_id,
                   last_execution_status = 'SUCCESS',
                   last_observed_at = excluded.last_observed_at,
                   last_verified_at = excluded.last_verified_at,
                   valid_to = null,
                   last_modified_by = excluded.last_modified_by,
                   last_modified_date = excluded.last_modified_date
            """,
            lineageId,
            upstreamAssetId,
            downstreamAssetId,
            "Candidate " + candidate.id(),
            candidate.planId().toString(),
            evidence.dbtInvocationId().toString(),
            Timestamp.from(evidence.observedAt()),
            Timestamp.from(evidence.observedAt()),
            Timestamp.from(now),
            actor,
            Timestamp.from(now),
            actor,
            Timestamp.from(now)
        );
        return lineageId;
    }

    private void replaceCatalogColumnLineage(
        CandidateView candidate,
        ModelSpecView model,
        UUID downstreamAssetId,
        UUID downstreamTableId,
        PublicationEntryEvidence evidence,
        List<CatalogLineageInput> lineageInputs,
        String actor,
        Instant now
    ) {
        jdbcTemplate.update(
            """
            update catalog_column_lineage
               set valid_to = ?, last_modified_by = ?, last_modified_date = ?
             where downstream_dataset_id = ?
               and relation_type = 'DBT'
               and valid_to is null
            """,
            Timestamp.from(now),
            actor,
            Timestamp.from(now),
            downstreamAssetId
        );
        if (lineageInputs == null || lineageInputs.isEmpty()) {
            return;
        }
        Map<String, CatalogColumn> downstreamColumns = loadColumnsForTable(
            downstreamTableId
        );
        if (downstreamColumns.isEmpty()) {
            return;
        }
        String rootSql = requireCurrentModelSql(candidate, model, evidence);
        Map<String, String> rootExpressions = DbtSqlProjectionParser.selectExpressionsByAlias(rootSql);
        Map<String, String> relationAliases = DbtSqlProjectionParser.relationAliases(rootSql);
        long physicalSourceCount = lineageInputs
            .stream()
            .filter(CatalogLineageInput::physicalSource)
            .count();
        Map<String, List<String>> stageExpressions = physicalSourceCount == 1
            ? loadCurrentStageExpressions(model, evidence)
            : Map.of();
        Map<UUID, Map<String, CatalogColumn>> upstreamColumnsByAsset = new LinkedHashMap<>();
        Map<String, Integer> columnOwnerCounts = new LinkedHashMap<>();
        for (CatalogLineageInput input : lineageInputs) {
            Map<String, CatalogColumn> columns = loadColumnsForDataset(input.upstreamAssetId());
            upstreamColumnsByAsset.put(input.upstreamAssetId(), columns);
            columns.keySet().forEach(name -> columnOwnerCounts.merge(name, 1, Integer::sum));
        }
        for (CatalogLineageInput input : lineageInputs) {
            Map<String, CatalogColumn> upstreamColumns = upstreamColumnsByAsset.getOrDefault(
                input.upstreamAssetId(),
                Map.of()
            );
            if (upstreamColumns.isEmpty()) {
                continue;
            }
            Set<String> allowedQualifiers = relationAliases
                .entrySet()
                .stream()
                .filter(alias -> input.relationNames().contains(alias.getValue()))
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
            for (CatalogColumn downstreamColumn : downstreamColumns.values()) {
                String downstreamKey = normalizeColumnName(
                    downstreamColumn.name()
                );
                String rootExpression = rootExpressions.get(downstreamKey);
                Map<String, ColumnMatch> matches;
                if (input.physicalSource() && physicalSourceCount == 1) {
                    matches = matchPhysicalSourceColumns(
                        rootExpression,
                        stageExpressions,
                        upstreamColumns,
                        allowedQualifiers,
                        columnOwnerCounts
                    );
                } else if (!input.physicalSource()) {
                    matches = matchDirectColumns(
                        rootExpression,
                        upstreamColumns,
                        allowedQualifiers,
                        columnOwnerCounts
                    );
                } else {
                    matches = Map.of();
                }
                if (
                    matches.isEmpty() &&
                    (!input.physicalSource() || physicalSourceCount <= 1)
                ) {
                    CatalogColumn sameName = upstreamColumns.get(downstreamKey);
                    boolean uniquelyOwned = columnOwnerCounts.getOrDefault(downstreamKey, 0) == 1;
                    if (
                        sameName != null &&
                        uniquelyOwned &&
                        (rootExpression == null || rootExpression.isBlank())
                    ) {
                        matches = Map.of(
                            downstreamKey,
                            new ColumnMatch(
                                sameName,
                                "SAME_NAME",
                                "same-name projection: " + sameName.name(),
                                "INFERRED"
                            )
                        );
                    }
                }
                for (ColumnMatch match : matches.values()) {
                    writeCatalogColumnLineage(
                        input,
                        downstreamAssetId,
                        match,
                        downstreamColumn,
                        evidence,
                        actor,
                        now
                    );
                }
            }
        }
    }

    private String requireCurrentModelSql(
        CandidateView candidate,
        ModelSpecView model,
        PublicationEntryEvidence evidence
    ) {
        List<String> sql = jdbcTemplate.queryForList(
            """
            select content
              from modeling_dbt_artifact
             where model_spec_id = ?
               and revision = ?
               and model_checksum = ?
               and implementation_revision = ?
               and dbt_unique_id = ?
               and artifact_type = 'SQL'
               and node_kind = 'MODEL'
               and status = 'COMPILED'
            """,
            String.class,
            model.id(),
            model.revision(),
            model.checksum(),
            evidence.implementationRevision(),
            evidence.dbtUniqueId()
        );
        if (sql.size() != 1 || sql.getFirst() == null || sql.getFirst().isBlank()) {
            throw publicationConflict(
                candidate,
                "Current compiled model SQL is required for Catalog column lineage"
            );
        }
        return sql.getFirst();
    }

    private Map<String, List<String>> loadCurrentStageExpressions(
        ModelSpecView model,
        PublicationEntryEvidence evidence
    ) {
        List<String> stageSql = jdbcTemplate.queryForList(
            """
            select content
              from modeling_dbt_artifact
             where model_spec_id = ?
               and revision = ?
               and model_checksum = ?
               and implementation_revision = ?
               and artifact_type = 'SQL'
               and node_kind = 'STG'
               and status = 'COMPILED'
             order by dbt_unique_id
            """,
            String.class,
            model.id(),
            model.revision(),
            model.checksum(),
            evidence.implementationRevision()
        );
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (String sql : stageSql) {
            for (
                Map.Entry<String, String> projection : DbtSqlProjectionParser
                    .selectExpressionsByAlias(sql)
                    .entrySet()
            ) {
                result
                    .computeIfAbsent(
                        projection.getKey(),
                        ignored -> new ArrayList<>()
                    )
                    .add(projection.getValue());
            }
        }
        return result;
    }

    private Map<String, ColumnMatch> matchDirectColumns(
        String rootExpression,
        Map<String, CatalogColumn> upstreamColumns,
        Set<String> allowedQualifiers,
        Map<String, Integer> columnOwnerCounts
    ) {
        if (rootExpression == null || rootExpression.isBlank()) {
            return Map.of();
        }
        Map<String, ColumnMatch> result = new LinkedHashMap<>();
        for (CatalogColumn column : upstreamColumns.values()) {
            String columnKey = normalizeColumnName(column.name());
            DbtSqlProjectionParser.ColumnReference reference = DbtSqlProjectionParser.columnReference(
                rootExpression,
                column.name()
            );
            boolean qualifiedForInput = reference.qualifiers().stream().anyMatch(allowedQualifiers::contains);
            boolean unqualifiedAndUnique = reference.unqualified() && columnOwnerCounts.getOrDefault(columnKey, 0) == 1;
            if (qualifiedForInput || unqualifiedAndUnique) {
                result.putIfAbsent(
                    columnKey,
                    new ColumnMatch(
                        column,
                        "SQL_EXPRESSION",
                        rootExpression,
                        "PARSED"
                    )
                );
            }
        }
        return result;
    }

    private Map<String, ColumnMatch> matchPhysicalSourceColumns(
        String rootExpression,
        Map<String, List<String>> stageExpressions,
        Map<String, CatalogColumn> upstreamColumns,
        Set<String> allowedQualifiers,
        Map<String, Integer> columnOwnerCounts
    ) {
        if (rootExpression == null || rootExpression.isBlank()) {
            return Map.of();
        }
        Map<String, ColumnMatch> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> stage : stageExpressions.entrySet()) {
            if (
                !DbtSqlProjectionParser.expressionReferencesColumn(
                    rootExpression,
                    stage.getKey()
                )
            ) {
                continue;
            }
            for (String stageExpression : stage.getValue()) {
                for (CatalogColumn column : upstreamColumns.values()) {
                    if (
                        DbtSqlProjectionParser.expressionReferencesColumn(
                            stageExpression,
                            column.name()
                        )
                    ) {
                        result.putIfAbsent(
                            normalizeColumnName(column.name()),
                            new ColumnMatch(
                                column,
                                "SQL_EXPRESSION",
                                rootExpression + " => " + stageExpression,
                                "PARSED"
                            )
                        );
                    }
                }
            }
        }
        for (
            Map.Entry<String, ColumnMatch> direct : matchDirectColumns(
                rootExpression,
                upstreamColumns,
                allowedQualifiers,
                columnOwnerCounts
            ).entrySet()
        ) {
            result.putIfAbsent(direct.getKey(), direct.getValue());
        }
        return result;
    }

    private Map<String, CatalogColumn> loadColumnsForDataset(UUID datasetId) {
        List<CatalogColumn> columns = jdbcTemplate.query(
            """
            select column_schema.id, column_schema.name
              from catalog_column_schema column_schema
              join catalog_table_schema table_schema
                on table_schema.id = column_schema.table_id
             where table_schema.dataset_id = ?
               and column_schema.status = 'ACTIVE'
             order by column_schema.name, column_schema.id
            """,
            (row, rowNumber) ->
                new CatalogColumn(
                    row.getObject("id", UUID.class),
                    row.getString("name")
                ),
            datasetId
        );
        return columnsByName(columns);
    }

    private Set<String> loadCatalogRelationNames(UUID datasetId) {
        return jdbcTemplate
            .query(
                "select name, hive_table from catalog_dataset where id = ?",
                (row, rowNumber) -> {
                    List<String> names = new ArrayList<>(2);
                    names.add(row.getString("name"));
                    names.add(row.getString("hive_table"));
                    return names;
                },
                datasetId
            )
            .stream()
            .flatMap(List::stream)
            .map(CandidatePublicationRepository::normalizeColumnName)
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private Map<String, CatalogColumn> loadColumnsForTable(UUID tableId) {
        List<CatalogColumn> columns = jdbcTemplate.query(
            """
            select id, name
              from catalog_column_schema
             where table_id = ?
               and status = 'ACTIVE'
             order by name, id
            """,
            (row, rowNumber) ->
                new CatalogColumn(
                    row.getObject("id", UUID.class),
                    row.getString("name")
                ),
            tableId
        );
        return columnsByName(columns);
    }

    private Map<String, CatalogColumn> columnsByName(
        List<CatalogColumn> columns
    ) {
        Map<String, CatalogColumn> result = new LinkedHashMap<>();
        for (CatalogColumn column : columns) {
            String key = normalizeColumnName(column.name());
            if (key != null) {
                result.putIfAbsent(key, column);
            }
        }
        return result;
    }

    private void writeCatalogColumnLineage(
        CatalogLineageInput input,
        UUID downstreamAssetId,
        ColumnMatch match,
        CatalogColumn downstreamColumn,
        PublicationEntryEvidence evidence,
        String actor,
        Instant now
    ) {
        CatalogColumn upstreamColumn = match.column();
        jdbcTemplate.update(
            """
            insert into catalog_column_lineage (
                id, dataset_lineage_id,
                upstream_dataset_id, downstream_dataset_id,
                upstream_column_id, downstream_column_id,
                upstream_column, downstream_column,
                relation_type, lineage_type, expression, confidence,
                project_name, lineage_job_id, last_observed_at,
                valid_from, valid_to,
                created_by, created_date, last_modified_by,
                last_modified_date
            ) values (
                ?, ?, ?, ?, ?, ?, ?, ?, 'DBT', ?, ?, ?, ?, null, ?, ?, null,
                ?, ?, ?, ?
            )
            on conflict (
                upstream_dataset_id, downstream_dataset_id,
                upstream_column, downstream_column, relation_type
            ) do update
               set dataset_lineage_id = excluded.dataset_lineage_id,
                   upstream_column_id = excluded.upstream_column_id,
                   downstream_column_id = excluded.downstream_column_id,
                   lineage_type = excluded.lineage_type,
                   expression = excluded.expression,
                   confidence = excluded.confidence,
                   project_name = excluded.project_name,
                   last_observed_at = excluded.last_observed_at,
                   valid_to = null,
                   last_modified_by = excluded.last_modified_by,
                   last_modified_date = excluded.last_modified_date
            """,
            stableUuid(
                "catalog-column-lineage:" +
                input.upstreamAssetId() +
                ":" +
                downstreamAssetId +
                ":" +
                normalizeColumnName(upstreamColumn.name()) +
                ":" +
                normalizeColumnName(downstreamColumn.name()) +
                ":DBT"
            ),
            input.lineageId(),
            input.upstreamAssetId(),
            downstreamAssetId,
            upstreamColumn.id(),
            downstreamColumn.id(),
            upstreamColumn.name(),
            downstreamColumn.name(),
            match.lineageType(),
            match.expression(),
            match.confidence(),
            dbtProjectName(evidence.dbtUniqueId()),
            Timestamp.from(evidence.observedAt()),
            Timestamp.from(now),
            actor,
            Timestamp.from(now),
            actor,
            Timestamp.from(now)
        );
    }

    private static String dbtProjectName(String dbtUniqueId) {
        if (dbtUniqueId == null || dbtUniqueId.isBlank()) {
            return null;
        }
        String[] parts = dbtUniqueId.split("\\.", 3);
        return parts.length >= 2 ? parts[1] : dbtUniqueId;
    }

    private static String normalizeColumnName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private UUID requirePublishedAssetId(
        CandidateView candidate,
        UUID modelSpecId,
        int modelRevision
    ) {
        return requirePublishedAssetId(candidate, modelSpecId, modelRevision, false);
    }

    private UUID requireCandidatePublishedAssetId(
        CandidateView candidate,
        UUID modelSpecId,
        int modelRevision
    ) {
        return requirePublishedAssetId(candidate, modelSpecId, modelRevision, true);
    }

    private UUID requirePublishedAssetId(
        CandidateView candidate,
        UUID modelSpecId,
        int modelRevision,
        boolean candidateOwned
    ) {
        String candidateClause = candidateOwned
            ? "and event.details_json ->> 'candidateId' = ?"
            : "";
        List<Object> arguments = new ArrayList<>(
            List.of(
                candidate.tenantId(),
                modelSpecId,
                modelRevision
            )
        );
        if (candidateOwned) {
            arguments.add(candidate.id().toString());
        }
        arguments.add(candidate.executionTargetKey());
        arguments.add(candidate.environment());
        List<String> values = jdbcTemplate.queryForList(
            """
            select event.details_json ->> 'physicalAssetId'
              from modeling_model_lifecycle_event event
             where event.tenant_id = ?
               and event.model_spec_id = ?
               and event.model_revision = ?
               and event.event_type = 'RELEASE'
               and event.status = 'PUBLISHED'
               %s
               and event.details_json ->> 'executionTargetKey' = ?
               and upper(event.details_json ->> 'environment') = upper(?)
               and nullif(
                       btrim(event.details_json ->> 'physicalAssetId'),
                       ''
                   ) is not null
             order by event.created_date desc, event.id desc
             limit 2
            """.formatted(candidateClause),
            String.class,
            arguments.toArray()
        );
        if (values.size() != 1) {
            throw publicationConflict(
                candidate,
                "Published dependency requires one exact physical asset reference"
            );
        }
        try {
            return UUID.fromString(values.getFirst());
        } catch (RuntimeException failure) {
            throw publicationConflict(
                candidate,
                "Published physical asset reference is invalid"
            );
        }
    }

    private String json(Map<String, ?> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Candidate publication metadata cannot be serialized", exception);
        }
    }

    private static String actor(String value) {
        String actor = value == null || value.isBlank() ? "candidate-publication" : value.trim();
        return actor.length() <= 50 ? actor : actor.substring(0, 50);
    }

    private static UUID stableUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String digest(List<String> values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : values) {
                digest.update(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static ModelReleaseCandidateException publicationConflict(
        CandidateView candidate,
        String message
    ) {
        return new ModelReleaseCandidateException(
            "MODEL_RELEASE_PUBLICATION_COMMIT_CONFLICT",
            message,
            Kind.CONFLICT,
            Map.of("candidateId", candidate.id(), "candidateVersion", candidate.version())
        );
    }

    private record CatalogLineageInput(
        UUID upstreamAssetId,
        UUID lineageId,
        boolean physicalSource,
        Set<String> relationNames
    ) {
        private CatalogLineageInput {
            relationNames = Set.copyOf(relationNames == null ? Set.of() : relationNames);
        }
    }

    private record CatalogColumn(UUID id, String name) {}

    private record ColumnMatch(
        CatalogColumn column,
        String lineageType,
        String expression,
        String confidence
    ) {}

    public record PublishedModelBinding(
        UUID modelSpecId,
        UUID releaseId,
        int modelRevision,
        String dbtUniqueId,
        String targetIdentifier,
        String artifactChecksum,
        String dependencySnapshotChecksum,
        UUID physicalAssetId
    ) {
        /** Compatibility constructor for tests and callers created before the physical asset receipt was exposed. */
        public PublishedModelBinding(
            UUID modelSpecId,
            UUID releaseId,
            int modelRevision,
            String dbtUniqueId,
            String targetIdentifier,
            String artifactChecksum,
            String dependencySnapshotChecksum
        ) {
            this(
                modelSpecId,
                releaseId,
                modelRevision,
                dbtUniqueId,
                targetIdentifier,
                artifactChecksum,
                dependencySnapshotChecksum,
                null
            );
        }
    }
}
