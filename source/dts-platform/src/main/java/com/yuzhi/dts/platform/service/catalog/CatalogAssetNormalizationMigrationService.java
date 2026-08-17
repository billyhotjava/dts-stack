package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.AssetRole;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.EvidenceChannel;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.EvidenceStatus;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.DiscoveryState;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.GovernanceReadiness;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.LifecycleState;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.ObservationCommand;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.ProducerKind;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.PublicationState;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.RelationType;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.ServingHealth;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.StatusAxes;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/** Reversible semantic-projection backfill command lane. Legacy catalog values are never rewritten. */
@Service
public class CatalogAssetNormalizationMigrationService {

    private static final int MAX_BATCH_SIZE = 500;
    private final JdbcTemplate jdbc;
    private final CatalogAssetRegistrationService registrations;
    private final AuditService audit;

    public CatalogAssetNormalizationMigrationService(JdbcTemplate jdbc, CatalogAssetRegistrationService registrations) {
        this(jdbc, registrations, null);
    }

    @Autowired
    public CatalogAssetNormalizationMigrationService(
        JdbcTemplate jdbc,
        CatalogAssetRegistrationService registrations,
        AuditService audit
    ) {
        this.jdbc = jdbc;
        this.registrations = registrations;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Preview preview(int requestedLimit) {
        int limit = requireLimit(requestedLimit);
        Map<String, UUID> existingByAssetKey = existingProjectionResources();
        List<PreviewRow> rows = jdbc.query(
            """
            SELECT dataset.id, dataset.name, dataset.domain_id, dataset.source_id,
                   dataset.hive_database, dataset.hive_table, upper(btrim(dataset.warehouse_layer)) AS legacy_layer,
                   dataset.type, dataset.classification, dataset.owner, dataset.owner_dept,
                   dataset.lifecycle_status
              FROM catalog_dataset dataset
             WHERE NOT EXISTS (
                   SELECT 1 FROM catalog_asset_semantic_projection projection
                    WHERE projection.asset_type = 'DATASET' AND projection.resource_id = dataset.id
               )
             ORDER BY dataset.id
             LIMIT ?
            """,
            (rs, rowNum) -> previewRow(rs, existingByAssetKey),
            limit
        );
        long totalPending = countPending();
        return new Preview(hash(rows), rows, totalPending, rows.size() == limit && totalPending > rows.size());
    }

    @Transactional
    public ApplyResult apply(
        String expectedPreviewHash,
        int requestedLimit,
        List<Resolution> requestedResolutions,
        String correlationId
    ) {
        Preview preview = preview(requestedLimit);
        if (!StringUtils.hasText(expectedPreviewHash) || !expectedPreviewHash.equals(preview.previewHash())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CATALOG_ASSET_NORMALIZATION_PREVIEW_STALE");
        }
        Map<UUID, Resolution> resolutions = indexResolutions(requestedResolutions, preview.rows());
        UUID batchId = UUID.randomUUID();
        Instant now = Instant.now();
        String actor = SecurityUtils.getCurrentUserLogin().orElse("system");
        jdbc.update(
            """
            INSERT INTO catalog_asset_normalization_batch(
                id, preview_hash, status, previewed_count, applied_count, created_by,
                correlation_id, created_at, applied_at
            ) VALUES (?, ?, 'APPLIED', ?, 0, ?, ?, ?, ?)
            """,
            batchId,
            preview.previewHash(),
            preview.rows().size(),
            actor,
            trimToNull(correlationId),
            Timestamp.from(now),
            Timestamp.from(now)
        );

        int applied = 0;
        for (PreviewRow row : preview.rows()) {
            ResolutionPlan plan = resolutionPlan(row, resolutions.get(row.resourceId()));
            if (plan == null) {
                recordIssue(row);
                continue;
            }
            assertProjectionAbsent(row.resourceId(), row.assetKey());
            ObservationCommand command = new ObservationCommand(
                CatalogAssetType.DATASET,
                row.assetKey(),
                row.resourceId(),
                plan.relationType(),
                false,
                false,
                row.domainId(),
                row.legacyLayerCode(),
                "DIM".equals(row.legacyLayerCode()) ? AssetRole.DIMENSION_TABLE : AssetRole.RELATION,
                plan.producerKind(),
                plan.producerId(),
                plan.producerVersion(),
                EvidenceChannel.MANUAL,
                "migration:" + batchId + ":" + row.resourceId(),
                now,
                EvidenceStatus.ACTIVE,
                statusAxes(row),
                null,
                null
            );
            CatalogAssetRegistrationService.ObservationResult observed = registrations.observe(command);
            if (!observed.admitted() || observed.receipt() == null) {
                throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    observed.reasonCode() == null ? "CATALOG_ASSET_NORMALIZATION_REJECTED" : observed.reasonCode()
                );
            }
            long projectionVersion = observed.receipt().projectionVersion();
            if (!observed.receipt().created()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "CATALOG_ASSET_NORMALIZATION_PROJECTION_DRIFT");
            }
            String actionCode = actionCode(row);
            jdbc.update(
                """
                INSERT INTO catalog_asset_normalization_batch_item(
                    batch_id, resource_id, asset_key, legacy_layer_code, action_code,
                    producer_kind, producer_id, producer_version, relation_type,
                    projection_version, item_checksum, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                batchId,
                row.resourceId(),
                row.assetKey(),
                batchLayerCode(row.legacyLayerCode()),
                actionCode,
                plan.producerKind().name(),
                plan.producerId(),
                plan.producerVersion(),
                plan.relationType().name(),
                projectionVersion,
                itemChecksum(row, plan, projectionVersion),
                Timestamp.from(now)
            );
            jdbc.update(
                """
                UPDATE catalog_asset_normalization_issue
                   SET status = 'RESOLVED', resolved_by = ?, resolved_at = ?
                 WHERE resource_id = ? AND status = 'PENDING'
                """,
                "batch:" + batchId + ":" + actor,
                Timestamp.from(now),
                row.resourceId()
            );
            applied++;
        }
        jdbc.update(
            "UPDATE catalog_asset_normalization_batch SET applied_count = ? WHERE id = ?",
            applied,
            batchId
        );
        int skipped = preview.rows().size() - applied;
        audit(
            "CATALOG_ASSET_SEMANTIC_BACKFILL_APPLY",
            batchId,
            Map.of(
                "previewHash",
                preview.previewHash(),
                "previewed",
                preview.rows().size(),
                "applied",
                applied,
                "skipped",
                skipped,
                "issueCount",
                skipped,
                "correlationId",
                StringUtils.hasText(correlationId) ? correlationId.trim() : "-"
            )
        );
        return new ApplyResult(batchId, preview.previewHash(), preview.rows().size(), applied, skipped, skipped, now);
    }

    @Transactional
    public RollbackResult rollback(UUID batchId) {
        Batch batch = batch(batchId);
        if (!"APPLIED".equals(batch.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CATALOG_ASSET_NORMALIZATION_BATCH_NOT_APPLIED");
        }
        List<AppliedItem> items = jdbc.query(
            """
            SELECT resource_id, asset_key, projection_version
              FROM catalog_asset_normalization_batch_item
             WHERE batch_id = ?
             ORDER BY resource_id
            """,
            (rs, rowNum) ->
                new AppliedItem(
                    rs.getObject("resource_id", UUID.class),
                    rs.getString("asset_key"),
                    rs.getLong("projection_version")
                ),
            batchId
        );
        for (AppliedItem item : items) {
            Long currentVersion = currentProjectionVersion(item.assetKey());
            if (currentVersion == null || currentVersion != item.projectionVersion()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "CATALOG_ASSET_NORMALIZATION_ROLLBACK_DRIFT");
            }
        }
        int removed = 0;
        for (AppliedItem item : items) {
            removed += jdbc.update(
                "DELETE FROM catalog_asset_semantic_projection WHERE asset_type = 'DATASET' AND asset_key = ? AND projection_version = ?",
                item.assetKey(),
                item.projectionVersion()
            );
            jdbc.update(
                """
                UPDATE catalog_asset_normalization_issue
                   SET status = 'PENDING', resolved_by = NULL, resolved_at = NULL
                 WHERE resource_id = ? AND resolved_by LIKE ?
                """,
                item.resourceId(),
                "batch:" + batchId + ":%"
            );
        }
        registrations.reconcile();
        Instant now = Instant.now();
        jdbc.update(
            "UPDATE catalog_asset_normalization_batch SET status = 'ROLLED_BACK', rolled_back_at = ? WHERE id = ? AND status = 'APPLIED'",
            Timestamp.from(now),
            batchId
        );
        audit(
            "CATALOG_ASSET_SEMANTIC_BACKFILL_ROLLBACK",
            batchId,
            Map.of("removed", removed, "rolledBackAt", now)
        );
        return new RollbackResult(batchId, removed, now);
    }

    private void audit(String actionCode, UUID batchId, Map<String, ?> payload) {
        if (audit != null) {
            audit.auditActionStrict(actionCode, AuditStage.SUCCESS, batchId.toString(), payload);
        }
    }

    private PreviewRow previewRow(ResultSet rs, Map<String, UUID> existingByAssetKey) throws SQLException {
        UUID resourceId = rs.getObject("id", UUID.class);
        UUID sourceId = rs.getObject("source_id", UUID.class);
        String legacyLayer = rs.getString("legacy_layer");
        String assetKey = null;
        String issueCode = null;
        try {
            assetKey = CatalogAssetKey.dataset(
                sourceId,
                rs.getString("hive_database"),
                rs.getString("hive_database"),
                rs.getString("hive_table"),
                rs.getString("name")
            );
        } catch (IllegalArgumentException error) {
            issueCode = "CATALOG_ASSET_KEY_INVALID";
        }
        boolean assetKeyConflict = assetKey != null && existingByAssetKey.containsKey(assetKey);
        boolean automatic = "SOURCE".equals(legacyLayer) && sourceId != null && assetKey != null && !assetKeyConflict;
        if (assetKeyConflict) {
            issueCode = "ASSET_KEY_AMBIGUOUS";
            automatic = false;
        } else if (issueCode == null && "SOURCE".equals(legacyLayer) && sourceId == null) {
            issueCode = "SOURCE_PRODUCER_UNRESOLVED";
        } else if (issueCode == null && "DIM".equals(legacyLayer)) {
            issueCode = "DIMENSION_CONFIRMATION_REQUIRED";
        } else if (issueCode == null && !automatic) {
            issueCode = "PRODUCER_RESOLUTION_REQUIRED";
        }
        String currentGovernance = governanceReadiness(
            rs.getObject("domain_id", UUID.class),
            rs.getString("classification"),
            rs.getString("owner"),
            rs.getString("owner_dept"),
            legacyLayer
        ).name();
        String currentLifecycle = trimToNull(rs.getString("lifecycle_status"));
        String resolutionStatus = automatic
            ? "UNIQUE_RESOLUTION"
            : assetKeyConflict || "CATALOG_ASSET_KEY_INVALID".equals(issueCode)
                ? "CONFLICT"
                : "MANUAL_RESOLUTION_REQUIRED";
        return new PreviewRow(
            resourceId,
            rs.getString("name"),
            rs.getObject("domain_id", UUID.class),
            sourceId,
            legacyLayer,
            assetKey,
            inferRelationType(rs.getString("type")),
            automatic ? "SOURCE_SYSTEM:" + sourceId : "REQUIRED",
            automatic ? "MIGRATION_MANUAL" : "REQUIRED",
            currentGovernance,
            currentLifecycle == null ? "ACTIVE" : currentLifecycle,
            resolutionStatus,
            issueCode == null ? List.of() : List.of(issueCode),
            automatic,
            issueCode
        );
    }

    private ResolutionPlan resolutionPlan(PreviewRow row, Resolution resolution) {
        if (row.assetKey() == null || "CONFLICT".equals(row.resolutionStatus())) return null;
        RelationType relationType = resolution == null || resolution.relationType() == null
            ? row.relationType()
            : resolution.relationType();
        if ("SOURCE".equals(row.legacyLayerCode())) {
            String producerId = resolution == null ? value(row.sourceId()) : trimToNull(resolution.producerId());
            ProducerKind kind = resolution == null ? ProducerKind.SOURCE_SYSTEM : resolution.producerKind();
            if (kind != ProducerKind.SOURCE_SYSTEM || producerId == null) return null;
            return new ResolutionPlan(kind, producerId, resolution == null ? null : trimToNull(resolution.producerVersion()), relationType);
        }
        if (resolution == null || resolution.producerKind() == null || !StringUtils.hasText(resolution.producerId())) {
            return null;
        }
        if ("DIM".equals(row.legacyLayerCode()) && !resolution.confirmDimension()) return null;
        return new ResolutionPlan(
            resolution.producerKind(),
            resolution.producerId().trim(),
            trimToNull(resolution.producerVersion()),
            relationType
        );
    }

    private Map<UUID, Resolution> indexResolutions(List<Resolution> requested, List<PreviewRow> rows) {
        Map<UUID, PreviewRow> allowed = new LinkedHashMap<>();
        rows.forEach(row -> allowed.put(row.resourceId(), row));
        Map<UUID, Resolution> result = new LinkedHashMap<>();
        for (Resolution resolution : requested == null ? List.<Resolution>of() : requested) {
            if (resolution == null || resolution.resourceId() == null || !allowed.containsKey(resolution.resourceId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CATALOG_ASSET_NORMALIZATION_RESOLUTION_INVALID");
            }
            if (result.putIfAbsent(resolution.resourceId(), resolution) != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CATALOG_ASSET_NORMALIZATION_RESOLUTION_DUPLICATE");
            }
        }
        return result;
    }

    private void assertProjectionAbsent(UUID resourceId, String assetKey) {
        Long count = jdbc.queryForObject(
            "SELECT count(*) FROM catalog_asset_semantic_projection WHERE asset_type = 'DATASET' AND (resource_id = ? OR asset_key = ?)",
            Long.class,
            resourceId,
            assetKey
        );
        if (count != null && count > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CATALOG_ASSET_NORMALIZATION_PROJECTION_DRIFT");
        }
    }

    private Map<String, UUID> existingProjectionResources() {
        Map<String, UUID> result = new LinkedHashMap<>();
        List<ProjectionIdentity> identities = jdbc.query(
            "SELECT asset_key, resource_id FROM catalog_asset_semantic_projection WHERE asset_type = 'DATASET'",
            (rs, rowNum) -> new ProjectionIdentity(rs.getString("asset_key"), rs.getObject("resource_id", UUID.class))
        );
        identities.forEach(identity -> result.put(identity.assetKey(), identity.resourceId()));
        return result;
    }

    private int requireLimit(int requestedLimit) {
        if (requestedLimit < 1 || requestedLimit > MAX_BATCH_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CATALOG_ASSET_NORMALIZATION_LIMIT_INVALID");
        }
        return requestedLimit;
    }

    private void recordIssue(PreviewRow row) {
        String reasonCode = StringUtils.hasText(row.issueCode())
            ? row.issueCode()
            : "PRODUCER_RESOLUTION_REQUIRED";
        Long pending = jdbc.queryForObject(
            "SELECT count(*) FROM catalog_asset_normalization_issue WHERE resource_id = ? AND status = 'PENDING'",
            Long.class,
            row.resourceId()
        );
        if (pending != null && pending > 0) {
            jdbc.update(
                "UPDATE catalog_asset_normalization_issue SET asset_key = ?, legacy_layer_code = ?, reason_code = ? WHERE resource_id = ? AND status = 'PENDING'",
                row.assetKey(),
                row.legacyLayerCode(),
                reasonCode,
                row.resourceId()
            );
            return;
        }
        jdbc.update(
            """
            INSERT INTO catalog_asset_normalization_issue(
                id, resource_id, asset_type, asset_key, legacy_layer_code, reason_code, status, created_at
            ) VALUES (?, ?, 'DATASET', ?, ?, ?, 'PENDING', ?)
            """,
            UUID.randomUUID(),
            row.resourceId(),
            row.assetKey(),
            row.legacyLayerCode(),
            reasonCode,
            Timestamp.from(Instant.now())
        );
    }

    private StatusAxes statusAxes(PreviewRow row) {
        return new StatusAxes(
            DiscoveryState.DISCOVERED,
            GovernanceReadiness.valueOf(row.currentGovernance()),
            PublicationState.UNPUBLISHED,
            ServingHealth.UNKNOWN,
            lifecycleState(row.currentLifecycle())
        );
    }

    private static GovernanceReadiness governanceReadiness(
        UUID domainId,
        String classification,
        String owner,
        String ownerDept,
        String warehouseLayer
    ) {
        if (domainId == null) return GovernanceReadiness.UNASSIGNED;
        if (
            !StringUtils.hasText(classification) ||
            (!StringUtils.hasText(owner) && !StringUtils.hasText(ownerDept)) ||
            !StringUtils.hasText(warehouseLayer)
        ) {
            return GovernanceReadiness.INCOMPLETE;
        }
        return GovernanceReadiness.GOVERNED;
    }

    private static LifecycleState lifecycleState(String lifecycle) {
        if (!StringUtils.hasText(lifecycle)) return LifecycleState.ACTIVE;
        return switch (lifecycle.trim().toUpperCase(Locale.ROOT)) {
            case "DEPRECATED" -> LifecycleState.DEPRECATED;
            case "RETIRED" -> LifecycleState.RETIRED;
            default -> LifecycleState.ACTIVE;
        };
    }

    private static RelationType inferRelationType(String datasetType) {
        if (!StringUtils.hasText(datasetType)) return RelationType.TABLE;
        String normalized = datasetType.trim().toUpperCase(Locale.ROOT);
        if (normalized.contains("MATERIALIZED") && normalized.contains("VIEW")) {
            return RelationType.MATERIALIZED_VIEW;
        }
        return normalized.contains("VIEW") ? RelationType.VIEW : RelationType.TABLE;
    }

    private static String actionCode(PreviewRow row) {
        if ("DIM".equals(row.legacyLayerCode())) return "DIM_TO_DWD_DIMENSION_TABLE";
        if ("SOURCE".equals(row.legacyLayerCode())) return "SOURCE_TO_PRODUCER_REF";
        return "SEMANTIC_PROJECTION_BACKFILL";
    }

    private static String batchLayerCode(String legacyLayerCode) {
        return StringUtils.hasText(legacyLayerCode) ? legacyLayerCode : "__UNLAYERED__";
    }

    private Long currentProjectionVersion(String assetKey) {
        List<Long> versions = jdbc.query(
            "SELECT projection_version FROM catalog_asset_semantic_projection WHERE asset_type = 'DATASET' AND asset_key = ?",
            (rs, rowNum) -> rs.getLong("projection_version"),
            assetKey
        );
        return versions.size() == 1 ? versions.get(0) : null;
    }

    private Batch batch(UUID batchId) {
        List<Batch> rows = jdbc.query(
            "SELECT id, status FROM catalog_asset_normalization_batch WHERE id = ? FOR UPDATE",
            (rs, rowNum) -> new Batch(rs.getObject("id", UUID.class), rs.getString("status")),
            batchId
        );
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "CATALOG_ASSET_NORMALIZATION_BATCH_NOT_FOUND");
        }
        return rows.get(0);
    }

    private long countPending() {
        Long count = jdbc.queryForObject(
            """
            SELECT count(*)
              FROM catalog_dataset dataset
             WHERE NOT EXISTS (
                   SELECT 1 FROM catalog_asset_semantic_projection projection
                    WHERE projection.asset_type = 'DATASET' AND projection.resource_id = dataset.id
             )
            """,
            Long.class
        );
        return count == null ? 0 : count;
    }

    private String hash(List<PreviewRow> rows) {
        StringBuilder canonical = new StringBuilder();
        rows.forEach(row -> canonical
            .append(row.resourceId()).append('|')
            .append(row.legacyLayerCode()).append('|')
            .append(row.assetKey()).append('|')
            .append(row.sourceId()).append('|')
            .append(row.relationType()).append('|')
            .append(row.currentGovernance()).append('|')
            .append(row.currentLifecycle()).append('|')
            .append(row.resolutionStatus()).append('|')
            .append(row.automatic()).append('|')
            .append(row.issueCode()).append('\n'));
        return DigestUtils.sha256Hex(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String itemChecksum(PreviewRow row, ResolutionPlan plan, long projectionVersion) {
        String value = String.join(
            "|",
            row.resourceId().toString(),
            row.assetKey(),
            batchLayerCode(row.legacyLayerCode()),
            plan.producerKind().name(),
            plan.producerId(),
            String.valueOf(plan.producerVersion()),
            plan.relationType().name(),
            String.valueOf(projectionVersion)
        );
        return DigestUtils.sha256Hex(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String value(UUID id) {
        return id == null ? null : id.toString();
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public record Preview(String previewHash, List<PreviewRow> rows, long totalPending, boolean truncated) {
        public Preview {
            rows = rows == null ? List.of() : List.copyOf(rows);
        }
    }

    public record PreviewRow(
        UUID resourceId,
        String name,
        UUID domainId,
        UUID sourceId,
        String legacyLayerCode,
        String assetKey,
        RelationType relationType,
        String producerResolution,
        String evidenceResolution,
        String currentGovernance,
        String currentLifecycle,
        String resolutionStatus,
        List<String> reasonCodes,
        boolean automatic,
        String issueCode
    ) {
        public PreviewRow {
            reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
        }
    }

    public record Resolution(
        UUID resourceId,
        boolean confirmDimension,
        ProducerKind producerKind,
        String producerId,
        String producerVersion,
        RelationType relationType
    ) {}

    public record ApplyResult(
        UUID batchId,
        String previewHash,
        int previewed,
        int applied,
        int skipped,
        int issueCount,
        Instant appliedAt
    ) {
        public int unresolved() {
            return skipped;
        }
    }

    public record RollbackResult(UUID batchId, int removed, Instant rolledBackAt) {}

    private record ResolutionPlan(
        ProducerKind producerKind,
        String producerId,
        String producerVersion,
        RelationType relationType
    ) {}

    private record AppliedItem(UUID resourceId, String assetKey, long projectionVersion) {}

    private record Batch(UUID id, String status) {}

    private record ProjectionIdentity(String assetKey, UUID resourceId) {}
}
