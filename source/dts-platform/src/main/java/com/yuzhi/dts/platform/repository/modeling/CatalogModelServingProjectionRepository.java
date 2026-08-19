package com.yuzhi.dts.platform.repository.modeling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetType;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.ExpectedRelationType;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.PhysicalColumn;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingContract.ModelServingProjection;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingContract.PublishedRef;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingContract.ServingRef;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingContract.SuccessfulPublicationCommand;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.RelationEvidence;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL CAS projection for Catalog latest-published and serving pointers. */
@Repository
public class CatalogModelServingProjectionRepository {

    private static final TypeReference<List<PhysicalColumn>> PHYSICAL_COLUMNS = new TypeReference<>() {};

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public CatalogModelServingProjectionRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /** Convenience for the canonical path; internally preserves the two independent transitions. */
    @Transactional(propagation = Propagation.MANDATORY)
    public ProjectionMutation projectSuccessfulPublication(SuccessfulPublicationCommand command) {
        ProjectionMutation published = projectLatestPublished(command);
        ProjectionMutation serving = promoteServing(command);
        return new ProjectionMutation(
            published.latestPublishedChanged(),
            serving.servingChanged(),
            serving.version(),
            serving.outcomeCode()
        );
    }

    /** Advances governance publication only. It never reads materialization evidence or serving. */
    @Transactional(propagation = Propagation.MANDATORY)
    public ProjectionMutation projectLatestPublished(SuccessfulPublicationCommand command) {
        Objects.requireNonNull(command, "command is required");
        PublishedRef published = new PublishedRef(
            command.modelSpecId(),
            command.modelRevision(),
            command.modelChecksum(),
            command.implementationRevision(),
            command.implementationChecksum(),
            command.candidateId(),
            command.candidateVersion(),
            null,
            command.physicalAssetId(),
            command.publishedAt()
        );
        Optional<ModelServingProjection> current = lock(command.tenantId(), command.modelSpecId());
        if (current.isEmpty()) {
            jdbcTemplate.update(
                """
                insert into modeling_catalog_model_serving_projection (
                    tenant_id, model_spec_id, catalog_asset_type, catalog_asset_key,
                    latest_published_ref, serving_ref, version, sync_status,
                    sync_attempts, created_at, updated_at
                ) values (?, ?, ?, ?, cast(? as jsonb), null, 1,
                          'SYNC_PENDING', 0, ?, ?)
                on conflict (tenant_id, model_spec_id) do nothing
                """,
                command.tenantId(),
                command.modelSpecId(),
                command.catalogAssetType().name(),
                command.catalogAssetKey(),
                json(published),
                Timestamp.from(command.publishedAt()),
                Timestamp.from(command.publishedAt())
            );
            current = lock(command.tenantId(), command.modelSpecId());
            if (current.isEmpty()) {
                throw new IllegalStateException("CATALOG_MODEL_SERVING_PROJECTION_NOT_CREATED");
            }
            ModelServingProjection inserted = current.orElseThrow();
            requireStableIdentity(inserted, command);
            if (samePublished(inserted.latestPublishedRef(), published)) {
                return new ProjectionMutation(true, false, inserted.version(), "LATEST_PUBLISHED");
            }
        }

        ModelServingProjection projection = current.orElseThrow();
        requireStableIdentity(projection, command);
        boolean latestChanged = false;
        long version = projection.version();
        PublishedRef latest = projection.latestPublishedRef();
        if (latest != null && latest.modelRevision() > published.modelRevision()) {
            return new ProjectionMutation(false, false, version, "NO_CHANGE");
        }
        if (latest != null && latest.modelRevision() == published.modelRevision()) {
            if (!samePublishedRevision(latest, published)) {
                throw new IllegalStateException("CATALOG_MODEL_LATEST_PUBLISHED_CONFLICT");
            }
            if (
                Objects.equals(latest.candidateId(), published.candidateId()) &&
                latest.candidateVersion() > published.candidateVersion()
            ) {
                return new ProjectionMutation(false, false, version, "NO_CHANGE");
            }
            if (samePublishedGovernance(latest, published)) {
                published = withEvidenceCandidateVersion(
                    published,
                    latest.evidenceCandidateVersion()
                );
            }
        }
        if (!samePublished(latest, published)) {
            int updated = jdbcTemplate.update(
                """
                update modeling_catalog_model_serving_projection
                   set latest_published_ref = cast(? as jsonb), version = version + 1,
                       sync_status = 'SYNC_PENDING', sync_attempts = 0,
                       last_sync_error = null, next_sync_at = null, updated_at = ?
                 where tenant_id = ? and model_spec_id = ? and version = ?
                """,
                json(published),
                Timestamp.from(command.publishedAt()),
                command.tenantId(),
                command.modelSpecId(),
                version
            );
            requireCas(updated);
            version++;
            latestChanged = true;
        }

        return new ProjectionMutation(
            latestChanged,
            false,
            version,
            latestChanged ? "LATEST_PUBLISHED" : "NO_CHANGE"
        );
    }

    /** Promotes serving only after current build, dispatch, relation and Candidate pins all match. */
    @Transactional(propagation = Propagation.MANDATORY)
    public ProjectionMutation promoteServing(SuccessfulPublicationCommand command) {
        Objects.requireNonNull(command, "command is required");
        ModelServingProjection projection = lock(command.tenantId(), command.modelSpecId())
            .orElseThrow(() -> new IllegalStateException("CATALOG_MODEL_LATEST_PUBLISHED_REQUIRED"));
        requireStableIdentity(projection, command);
        PublishedRef latest = projection.latestPublishedRef();
        if (
            latest == null ||
            latest.modelRevision() != command.modelRevision() ||
            !Objects.equals(latest.modelChecksum(), command.modelChecksum()) ||
            latest.implementationRevision() != command.implementationRevision() ||
            !Objects.equals(latest.implementationChecksum(), command.implementationChecksum()) ||
            !Objects.equals(latest.candidateId(), command.candidateId()) ||
            latest.candidateVersion() != command.candidateVersion()
        ) {
            throw new IllegalStateException("CATALOG_MODEL_LATEST_PUBLISHED_NOT_CURRENT");
        }
        long version = projection.version();
        Optional<PromotionEvidence> promotion = findCurrentSuccessfulEvidence(command);
        if (promotion.isEmpty()) {
            return new ProjectionMutation(false, false, version, "SERVING_NOT_READY");
        }
        PromotionEvidence successfulEvidence = promotion.orElseThrow();
        ServingRef serving = toServingRef(command, successfulEvidence);
        PublishedRef evidencePinnedLatest = withEvidenceCandidateVersion(
            latest,
            successfulEvidence.relation().candidateVersion()
        );
        ServingRef currentServing = projection.servingRef();
        if (
            latest.evidenceCandidateVersion() != null &&
            latest.evidenceCandidateVersion() > serving.candidateVersion()
        ) {
            return new ProjectionMutation(false, false, version, "SERVING_STALE_REJECTED");
        }
        if (
            currentServing != null &&
            (currentServing.modelRevision() > serving.modelRevision() ||
                (currentServing.modelRevision() == serving.modelRevision() &&
                    Objects.equals(currentServing.candidateId(), serving.candidateId()) &&
                    currentServing.candidateVersion() > serving.candidateVersion()))
        ) {
            return new ProjectionMutation(false, false, version, "SERVING_STALE_REJECTED");
        }
        if (sameServing(currentServing, serving) && samePublished(latest, evidencePinnedLatest)) {
            return new ProjectionMutation(false, false, version, "NO_CHANGE");
        }
        int promoted = jdbcTemplate.update(
            """
            update modeling_catalog_model_serving_projection
               set serving_ref = cast(? as jsonb), latest_published_ref = cast(? as jsonb),
                   version = version + 1,
                   sync_status = 'SYNC_PENDING', sync_attempts = 0,
                   last_sync_error = null, next_sync_at = null, updated_at = ?
             where tenant_id = ? and model_spec_id = ? and version = ?
               and (latest_published_ref ->> 'modelRevision')::int = ?
               and latest_published_ref ->> 'modelChecksum' = ?
               and latest_published_ref ->> 'candidateId' = ?
               and (latest_published_ref ->> 'candidateVersion')::int = ?
            """,
            json(serving),
            json(evidencePinnedLatest),
            Timestamp.from(command.publishedAt()),
            command.tenantId(),
            command.modelSpecId(),
            version,
            command.modelRevision(),
            command.modelChecksum(),
            command.candidateId().toString(),
            command.candidateVersion()
        );
        requireCas(promoted);
        return new ProjectionMutation(false, true, version + 1, "SERVING_PROMOTED");
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Optional<ModelServingProjection> findProjectionForPreview(String tenantId, UUID modelSpecId) {
        return jdbcTemplate
            .query(
                """
                select tenant_id, model_spec_id, catalog_asset_type, catalog_asset_key,
                       latest_published_ref::text, serving_ref::text, version,
                       sync_status, updated_at
                  from modeling_catalog_model_serving_projection
                 where tenant_id = ? and model_spec_id = ?
                 for share
                """,
                (row, rowNumber) -> mapProjection(row),
                tenantId,
                modelSpecId
            )
            .stream()
            .findFirst();
    }

    @Transactional(readOnly = true)
    public Optional<ModelServingProjection> findProjection(String tenantId, UUID modelSpecId) {
        return jdbcTemplate
            .query(
                """
                select tenant_id, model_spec_id, catalog_asset_type, catalog_asset_key,
                       latest_published_ref::text, serving_ref::text, version,
                       sync_status, updated_at
                  from modeling_catalog_model_serving_projection
                 where tenant_id = ? and model_spec_id = ?
                """,
                (row, rowNumber) -> mapProjection(row),
                tenantId,
                modelSpecId
            )
            .stream()
            .findFirst();
    }

    /** Reads the durable delivery state without widening the published/serving projection contract. */
    @Transactional(readOnly = true)
    public Optional<ServingSyncState> findSyncState(String tenantId, UUID modelSpecId) {
        return jdbcTemplate
            .query(
                """
                select tenant_id, model_spec_id, catalog_asset_type, catalog_asset_key,
                       latest_published_ref::text, serving_ref::text, version,
                       sync_status, sync_attempts, last_sync_error, next_sync_at, updated_at
                  from modeling_catalog_model_serving_projection
                 where tenant_id = ? and model_spec_id = ?
                """,
                (row, rowNumber) -> new ServingSyncState(
                    mapProjection(row),
                    row.getInt("sync_attempts"),
                    row.getString("last_sync_error"),
                    row.getTimestamp("next_sync_at") == null
                        ? null
                        : row.getTimestamp("next_sync_at").toInstant()
                ),
                tenantId,
                modelSpecId
            )
            .stream()
            .findFirst();
    }

    /** Reads serving delivery evidence for a bounded asset page in one query. Existing CAS write paths are untouched. */
    @Transactional(readOnly = true)
    public Map<String, List<ServingSyncState>> findSyncStatesByAssetKeys(
        CatalogAssetType assetType,
        Collection<String> assetKeys
    ) {
        if (assetType == null || assetKeys == null || assetKeys.isEmpty()) {
            return Map.of();
        }
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (String assetKey : assetKeys) {
            if (assetKey != null && !assetKey.isBlank()) {
                keys.add(assetKey.trim());
            }
        }
        if (keys.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(keys.size(), "?"));
        List<Object> arguments = new java.util.ArrayList<>(keys.size() + 1);
        String sql;
        if (CatalogAssetType.DATASET == assetType) {
            sql =
                """
                select semantics.asset_key as requested_asset_key,
                       projection.tenant_id, projection.model_spec_id,
                       projection.catalog_asset_type, projection.catalog_asset_key,
                       projection.latest_published_ref::text,
                       projection.serving_ref::text, projection.version,
                       projection.sync_status, projection.sync_attempts,
                       projection.last_sync_error, projection.next_sync_at,
                       projection.updated_at
                  from modeling_catalog_model_serving_projection projection
                  join catalog_asset_semantic_projection semantics
                    on semantics.asset_type = 'DATASET'
                   and semantics.resource_id = case
                         when projection.serving_ref ->> 'physicalAssetId'
                              ~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
                         then (projection.serving_ref ->> 'physicalAssetId')::uuid
                         else null
                       end
                 where projection.serving_ref is not null
                   and semantics.asset_key in (
                """ + placeholders + ") order by semantics.asset_key, projection.updated_at desc, projection.model_spec_id";
            arguments.addAll(keys);
        } else {
            sql =
                """
                select catalog_asset_key as requested_asset_key,
                       tenant_id, model_spec_id, catalog_asset_type, catalog_asset_key,
                       latest_published_ref::text, serving_ref::text, version,
                       sync_status, sync_attempts, last_sync_error, next_sync_at, updated_at
                  from modeling_catalog_model_serving_projection
                 where catalog_asset_type = ? and catalog_asset_key in (
                """ + placeholders + ") order by catalog_asset_key, updated_at desc, model_spec_id";
            arguments.add(assetType.name());
            arguments.addAll(keys);
        }
        Map<String, List<ServingSyncState>> states = new LinkedHashMap<>();
        jdbcTemplate.query(
            sql,
            row -> {
                ServingSyncState state = new ServingSyncState(
                    mapProjection(row),
                    row.getInt("sync_attempts"),
                    row.getString("last_sync_error"),
                    row.getTimestamp("next_sync_at") == null ? null : row.getTimestamp("next_sync_at").toInstant()
                );
                states.computeIfAbsent(row.getString("requested_asset_key"), ignored -> new java.util.ArrayList<>()).add(state);
            },
            arguments.toArray()
        );
        states.replaceAll((key, value) -> List.copyOf(value));
        return Map.copyOf(states);
    }

    @Transactional(readOnly = true)
    public Optional<RelationEvidence> findRelationEvidence(String tenantId, UUID evidenceId) {
        return jdbcTemplate
            .query(
                evidenceSelect() + " where o.tenant_id = ? and o.id = ?",
                (row, rowNumber) -> mapEvidence(row),
                tenantId,
                evidenceId
            )
            .stream()
            .findFirst();
    }

    /** Latest complete relation evidence for the exact current candidate pins; never manufactures a preview ref. */
    @Transactional(readOnly = true)
    public Optional<RelationEvidence> findLatestSuccessfulCandidateEvidence(
        String tenantId,
        UUID modelSpecId,
        int modelRevision,
        String modelChecksum,
        int implementationRevision,
        String implementationChecksum
    ) {
        return jdbcTemplate
            .query(
                evidenceSelect() +
                """
                 where o.tenant_id = ? and o.model_spec_id = ?
                   and o.model_revision = ? and o.model_checksum = ?
                   and o.implementation_revision = ? and o.implementation_checksum = ?
                   and o.verified and o.relation_exists
                   and c.status in ('BUILT', 'QUALITY_RUNNING', 'QUALITY_PASSED', 'REVIEW_PENDING',
                                    'APPROVED', 'PUBLISHING', 'PARTIAL', 'PUBLISHED')
                   and d.status = 'COMPLETED' and p.status = 'BUILT'
                   and d.id = (
                       select d2.id
                         from modeling_materialization_dispatch d2
                        where d2.tenant_id = d.tenant_id
                          and d2.candidate_id = d.candidate_id
                          and d2.status = 'COMPLETED'
                        order by d2.candidate_version desc, d2.attempt desc,
                                 d2.last_modified_at desc, d2.id desc
                        limit 1
                   )
                   and o.observation_attempt = (
                       select max(o2.observation_attempt)
                         from modeling_physical_relation_observation o2
                        where o2.pipeline_run_id = o.pipeline_run_id
                          and o2.model_spec_id = o.model_spec_id
                          and o2.implementation_revision = o.implementation_revision
                   )
                 order by o.release_candidate_version desc, d.attempt desc,
                          o.observation_attempt desc, o.observed_at desc
                 limit 1
                """,
                (row, rowNumber) -> mapEvidence(row),
                tenantId,
                modelSpecId,
                modelRevision,
                modelChecksum,
                implementationRevision,
                implementationChecksum
            )
            .stream()
            .findFirst();
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Optional<RelationEvidence> findRelationEvidenceForPreview(String tenantId, UUID evidenceId) {
        return jdbcTemplate
            .query(
                evidenceSelect() + " where o.tenant_id = ? and o.id = ? for share of o, c, e, p, d",
                (row, rowNumber) -> mapEvidence(row),
                tenantId,
                evidenceId
            )
            .stream()
            .findFirst();
    }

    /**
     * Atomically leases due serving projections without holding a database transaction across
     * the downstream Analytics call. A crashed worker becomes claimable again after the lease.
     */
    @Transactional
    public List<SyncCandidate> claimSyncCandidates(int requestedLimit, Instant now, Duration leaseDuration) {
        Objects.requireNonNull(now, "now is required");
        Objects.requireNonNull(leaseDuration, "leaseDuration is required");
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        Instant leaseUntil = now.plus(leaseDuration);
        return jdbcTemplate.query(
            """
            with claimable as (
                select projection.tenant_id, projection.model_spec_id
                  from modeling_catalog_model_serving_projection projection
                 where projection.serving_ref is not null
                   and projection.sync_attempts < 6
                   and (
                       (projection.sync_status = 'SYNC_PENDING' and (projection.next_sync_at is null or projection.next_sync_at <= ?))
                       or
                       (projection.sync_status = 'SYNC_FAILED' and projection.next_sync_at is not null and projection.next_sync_at <= ?)
                       or
                       (
                           projection.sync_status = 'SYNCED'
                           and exists (
                               select 1
                                 from catalog_dataset dataset
                                where dataset.id = case
                                      when projection.serving_ref ->> 'physicalAssetId'
                                           ~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
                                      then (projection.serving_ref ->> 'physicalAssetId')::uuid
                                      else null
                                  end
                                  and dataset.enabled = true
                                  and upper(dataset.warehouse_layer) in ('DWS', 'ADS')
                           )
                           and not exists (
                               select 1
                                 from query_dataset_asset query_dataset
                                where query_dataset.source_model_spec_id = projection.model_spec_id
                           )
                       )
                   )
                 order by projection.updated_at, projection.model_spec_id
                 for update skip locked
                 limit ?
            )
            update modeling_catalog_model_serving_projection projection
               set sync_status = case
                       when projection.sync_status = 'SYNCED' then 'SYNC_PENDING'
                       else projection.sync_status
                   end,
                   next_sync_at = ?, updated_at = current_timestamp
              from claimable
             where projection.tenant_id = claimable.tenant_id
               and projection.model_spec_id = claimable.model_spec_id
            returning projection.tenant_id, projection.model_spec_id,
                      projection.catalog_asset_type, projection.catalog_asset_key,
                      projection.latest_published_ref::text,
                      projection.serving_ref::text, projection.version,
                      projection.sync_status, projection.sync_attempts,
                      projection.updated_at
            """,
            (row, rowNumber) -> new SyncCandidate(mapProjection(row), row.getInt("sync_attempts")),
            Timestamp.from(now),
            Timestamp.from(now),
            limit,
            Timestamp.from(leaseUntil)
        );
    }

    @Transactional
    public boolean markSyncFailed(String tenantId, UUID modelSpecId, long expectedVersion, String errorCode, Instant nextAttemptAt) {
        return jdbcTemplate.update(
            """
            update modeling_catalog_model_serving_projection
               set sync_status = 'SYNC_FAILED', sync_attempts = sync_attempts + 1,
                   last_sync_error = ?, next_sync_at = ?, updated_at = current_timestamp
             where tenant_id = ? and model_spec_id = ? and version = ?
            """,
            safeError(errorCode),
            nextAttemptAt == null ? null : Timestamp.from(nextAttemptAt),
            tenantId,
            modelSpecId,
            expectedVersion
        ) == 1;
    }

    @Transactional
    public boolean markSyncSucceeded(String tenantId, UUID modelSpecId, long expectedVersion) {
        return jdbcTemplate.update(
            """
            update modeling_catalog_model_serving_projection
               set sync_status = 'SYNCED', last_sync_error = null,
                   next_sync_at = null, updated_at = current_timestamp
             where tenant_id = ? and model_spec_id = ? and version = ?
            """,
            tenantId,
            modelSpecId,
            expectedVersion
        ) == 1;
    }

    /**
     * Starts a new delivery cycle for a failed row. Incrementing the projection version makes any
     * receipt held by an older worker stale while preserving both canonical pointer documents.
     */
    @Transactional
    public boolean requestSyncRetry(String tenantId, UUID modelSpecId, long expectedVersion) {
        return jdbcTemplate.update(
            """
            update modeling_catalog_model_serving_projection
               set sync_status = 'SYNC_PENDING', sync_attempts = 0,
                   last_sync_error = null, next_sync_at = null,
                   version = version + 1, updated_at = current_timestamp
             where tenant_id = ? and model_spec_id = ? and version = ?
               and sync_status = 'SYNC_FAILED' and serving_ref is not null
            """,
            tenantId,
            modelSpecId,
            expectedVersion
        ) == 1;
    }

    private Optional<ModelServingProjection> lock(String tenantId, UUID modelSpecId) {
        return jdbcTemplate
            .query(
                """
                select tenant_id, model_spec_id, catalog_asset_type, catalog_asset_key,
                       latest_published_ref::text, serving_ref::text, version,
                       sync_status, updated_at
                  from modeling_catalog_model_serving_projection
                 where tenant_id = ? and model_spec_id = ?
                 for update
                """,
                (row, rowNumber) -> mapProjection(row),
                tenantId,
                modelSpecId
            )
            .stream()
            .findFirst();
    }

    private Optional<PromotionEvidence> findCurrentSuccessfulEvidence(SuccessfulPublicationCommand command) {
        List<PromotionEvidence> rows = jdbcTemplate.query(
            "select ev.*, cd.id physical_asset_id, cd.source_id physical_source_id from (" +
            evidenceSelect() +
            """
             where o.tenant_id = ?
               and o.release_candidate_id = ?
               and c.version = ?
               and o.model_spec_id = ?
               and o.model_revision = ?
               and o.model_checksum = ?
               and o.implementation_revision = ?
               and o.implementation_checksum = ?
               and o.verified and o.relation_exists
               and c.status in ('PUBLISHING', 'PARTIAL')
               and e.status in ('PUBLISHING', 'PARTIAL')
               and d.status = 'COMPLETED'
               and d.id = (
                   select d2.id
                     from modeling_materialization_dispatch d2
                    where d2.tenant_id = d.tenant_id
                      and d2.candidate_id = d.candidate_id
                      and d2.status = 'COMPLETED'
                    order by d2.candidate_version desc, d2.attempt desc,
                             d2.last_modified_at desc, d2.id desc
                    limit 1
               )
               and p.status = 'BUILT'
               and o.observation_attempt = (
                   select max(o2.observation_attempt)
                     from modeling_physical_relation_observation o2
                    where o2.pipeline_run_id = o.pipeline_run_id
                      and o2.model_spec_id = o.model_spec_id
                      and o2.implementation_revision = o.implementation_revision
               )
            ) ev
            join catalog_dataset cd
              on cd.source_id = ? and cd.enabled
             and lower(cd.hive_database) = lower(ev.schema_name)
             and lower(cd.hive_table) = lower(ev.identifier)
            """,
            (row, rowNumber) -> new PromotionEvidence(
                mapEvidence(row),
                row.getObject("physical_asset_id", UUID.class),
                row.getObject("physical_source_id", UUID.class)
            ),
            command.tenantId(),
            command.candidateId(),
            command.candidateVersion(),
            command.modelSpecId(),
            command.modelRevision(),
            command.modelChecksum(),
            command.implementationRevision(),
            command.implementationChecksum(),
            command.sourceId()
        );
        if (rows.size() > 1) {
            throw new IllegalStateException("CATALOG_MODEL_SERVING_EVIDENCE_AMBIGUOUS");
        }
        return rows.stream().findFirst();
    }

    private static String evidenceSelect() {
        return """
            select o.tenant_id, o.id relation_evidence_id, o.model_spec_id,
                   o.model_revision, o.model_checksum, o.implementation_revision,
                   o.implementation_checksum, o.release_candidate_id candidate_id,
                   o.release_candidate_version candidate_version, c.status candidate_status,
                   d.attempt, d.status dispatch_status, o.pipeline_run_id,
                   p.status pipeline_status, o.observation_attempt, o.adapter,
                   o.credential_version_ref, o.database_name, o.schema_name, o.identifier,
                   o.actual_type, o.relation_exists, o.verified,
                   o.actual_columns::text actual_columns, o.metadata_checksum evidence_checksum,
                   o.observed_at
              from modeling_physical_relation_observation o
              join modeling_model_release_candidate c
                on c.tenant_id = o.tenant_id and c.id = o.release_candidate_id
              join modeling_model_release_candidate_entry e
                on e.tenant_id = o.tenant_id and e.candidate_id = o.release_candidate_id
               and e.model_spec_id = o.model_spec_id and e.revision = o.model_revision
               and e.checksum = o.model_checksum
               and e.implementation_revision = o.implementation_revision
               and e.implementation_checksum = o.implementation_checksum
               and e.status = c.status
              join modeling_pipeline_run p
                on p.id = o.pipeline_run_id and p.pipeline_run_group_id = o.pipeline_run_group_id
               and p.run_purpose = 'RELEASE_BUILD'
              join modeling_materialization_dispatch d
                on d.id = o.pipeline_run_group_id and d.tenant_id = o.tenant_id
               and d.candidate_id = o.release_candidate_id
               and d.candidate_version = o.release_candidate_version
            """;
    }

    private ModelServingProjection mapProjection(java.sql.ResultSet row) throws java.sql.SQLException {
        return new ModelServingProjection(
            row.getString("tenant_id"),
            row.getObject("model_spec_id", UUID.class),
            CatalogAssetType.valueOf(row.getString("catalog_asset_type")),
            row.getString("catalog_asset_key"),
            read(row.getString("latest_published_ref"), PublishedRef.class),
            read(row.getString("serving_ref"), ServingRef.class),
            row.getLong("version"),
            row.getString("sync_status"),
            row.getTimestamp("updated_at").toInstant()
        );
    }

    private RelationEvidence mapEvidence(java.sql.ResultSet row) throws java.sql.SQLException {
        return new RelationEvidence(
            row.getString("tenant_id"),
            row.getObject("relation_evidence_id", UUID.class),
            row.getObject("model_spec_id", UUID.class),
            row.getInt("model_revision"),
            row.getString("model_checksum"),
            row.getInt("implementation_revision"),
            row.getString("implementation_checksum"),
            row.getObject("candidate_id", UUID.class),
            row.getInt("candidate_version"),
            row.getString("candidate_status"),
            row.getInt("attempt"),
            row.getString("dispatch_status"),
            row.getObject("pipeline_run_id", UUID.class),
            row.getString("pipeline_status"),
            row.getInt("observation_attempt"),
            row.getString("adapter"),
            row.getString("credential_version_ref"),
            row.getString("database_name"),
            row.getString("schema_name"),
            row.getString("identifier"),
            row.getString("actual_type") == null
                ? null
                : ExpectedRelationType.valueOf(row.getString("actual_type")),
            row.getBoolean("relation_exists"),
            row.getBoolean("verified"),
            readColumns(row.getString("actual_columns")),
            row.getString("evidence_checksum"),
            row.getTimestamp("observed_at").toInstant()
        );
    }

    private ServingRef toServingRef(SuccessfulPublicationCommand command, PromotionEvidence promotion) {
        RelationEvidence evidence = promotion.relation();
        return new ServingRef(
            command.modelSpecId(),
            command.modelRevision(),
            command.modelChecksum(),
            command.implementationRevision(),
            command.implementationChecksum(),
            command.candidateId(),
            evidence.candidateVersion(),
            evidence.attempt(),
            evidence.pipelineRunId(),
            evidence.observationAttempt(),
            evidence.relationEvidenceId(),
            evidence.evidenceChecksum(),
            promotion.physicalAssetId(),
            promotion.sourceId(),
            evidence.adapter(),
            evidence.databaseName(),
            evidence.schemaName(),
            evidence.identifier(),
            evidence.observedAt()
        );
    }

    private static PublishedRef withEvidenceCandidateVersion(PublishedRef published, Integer evidenceCandidateVersion) {
        return new PublishedRef(
            published.modelSpecId(),
            published.modelRevision(),
            published.modelChecksum(),
            published.implementationRevision(),
            published.implementationChecksum(),
            published.candidateId(),
            published.candidateVersion(),
            evidenceCandidateVersion,
            published.physicalAssetId(),
            published.publishedAt()
        );
    }

    private void requireStableIdentity(ModelServingProjection current, SuccessfulPublicationCommand command) {
        if (
            current.catalogAssetType() != command.catalogAssetType() ||
            !Objects.equals(current.catalogAssetKey(), command.catalogAssetKey())
        ) {
            throw new IllegalStateException("CATALOG_MODEL_STABLE_IDENTITY_CONFLICT");
        }
    }

    private static boolean samePublished(PublishedRef left, PublishedRef right) {
        if (left == null || right == null) return left == right;
        return Objects.equals(left.modelSpecId(), right.modelSpecId()) &&
            left.modelRevision() == right.modelRevision() &&
            Objects.equals(left.modelChecksum(), right.modelChecksum()) &&
            left.implementationRevision() == right.implementationRevision() &&
            Objects.equals(left.implementationChecksum(), right.implementationChecksum()) &&
            Objects.equals(left.candidateId(), right.candidateId()) &&
            left.candidateVersion() == right.candidateVersion() &&
            Objects.equals(left.evidenceCandidateVersion(), right.evidenceCandidateVersion()) &&
            Objects.equals(left.physicalAssetId(), right.physicalAssetId());
    }

    private static boolean samePublishedGovernance(PublishedRef left, PublishedRef right) {
        if (left == null || right == null) return left == right;
        return Objects.equals(left.modelSpecId(), right.modelSpecId()) &&
            left.modelRevision() == right.modelRevision() &&
            Objects.equals(left.modelChecksum(), right.modelChecksum()) &&
            left.implementationRevision() == right.implementationRevision() &&
            Objects.equals(left.implementationChecksum(), right.implementationChecksum()) &&
            Objects.equals(left.candidateId(), right.candidateId()) &&
            left.candidateVersion() == right.candidateVersion() &&
            Objects.equals(left.physicalAssetId(), right.physicalAssetId());
    }

    private static boolean samePublishedRevision(PublishedRef left, PublishedRef right) {
        return Objects.equals(left.modelSpecId(), right.modelSpecId()) &&
            left.modelRevision() == right.modelRevision() &&
            Objects.equals(left.modelChecksum(), right.modelChecksum()) &&
            left.implementationRevision() == right.implementationRevision() &&
            Objects.equals(left.implementationChecksum(), right.implementationChecksum());
    }

    private static boolean sameServing(ServingRef left, ServingRef right) {
        return Objects.equals(left, right);
    }

    private static void requireCas(int updated) {
        if (updated != 1) {
            throw new IllegalStateException("CATALOG_MODEL_SERVING_CAS_CONFLICT");
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("CATALOG_MODEL_SERVING_JSON_FAILED", failure);
        }
    }

    private <T> T read(String value, Class<T> type) {
        if (value == null) return null;
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("CATALOG_MODEL_SERVING_JSON_INVALID", failure);
        }
    }

    private List<PhysicalColumn> readColumns(String value) {
        if (value == null) return List.of();
        try {
            return objectMapper.readValue(value, PHYSICAL_COLUMNS);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("PHYSICAL_RELATION_EVIDENCE_COLUMNS_INVALID", failure);
        }
    }

    private static String safeError(String errorCode) {
        if (errorCode == null || errorCode.isBlank()) return "CATALOG_SYNC_FAILED";
        String normalized = errorCode.trim();
        return normalized.length() <= 128 ? normalized : normalized.substring(0, 128);
    }

    private record PromotionEvidence(RelationEvidence relation, UUID physicalAssetId, UUID sourceId) {}

    public record SyncCandidate(ModelServingProjection projection, int syncAttempts) {}

    public record ServingSyncState(
        ModelServingProjection projection,
        int syncAttempts,
        String lastSyncError,
        Instant nextSyncAt
    ) {}

    public record ProjectionMutation(
        boolean latestPublishedChanged,
        boolean servingChanged,
        long version,
        String outcomeCode
    ) {
        public ProjectionMutation(boolean latestPublishedChanged, boolean servingChanged, long version) {
            this(
                latestPublishedChanged,
                servingChanged,
                version,
                latestPublishedChanged
                    ? "LATEST_PUBLISHED"
                    : servingChanged ? "SERVING_PROMOTED" : "NO_CHANGE"
            );
        }

        public boolean servingNotReady() {
            return "SERVING_NOT_READY".equals(outcomeCode);
        }
    }
}
