package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.AssetRole;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.EvidenceChannel;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.EvidenceStatus;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.ObservationCommand;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.ProducerKind;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.RelationType;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/** Reversible SOURCE/DIM normalization command lane. Legacy warehouse-layer values are never rewritten. */
@Service
public class CatalogAssetNormalizationMigrationService {

    private static final int MAX_BATCH_SIZE = 500;
    private final JdbcTemplate jdbc;
    private final CatalogAssetRegistrationService registrations;

    public CatalogAssetNormalizationMigrationService(JdbcTemplate jdbc, CatalogAssetRegistrationService registrations) {
        this.jdbc = jdbc;
        this.registrations = registrations;
    }

    @Transactional(readOnly = true)
    public Preview preview(int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, MAX_BATCH_SIZE));
        List<PreviewRow> rows = jdbc.query(
            """
            SELECT dataset.id, dataset.name, dataset.domain_id, dataset.source_id,
                   dataset.hive_database, dataset.hive_table, upper(btrim(dataset.warehouse_layer)) AS legacy_layer,
                   EXISTS (
                       SELECT 1 FROM catalog_asset_semantic_projection projection
                        WHERE projection.asset_type = 'DATASET' AND projection.resource_id = dataset.id
                   ) AS projection_exists
              FROM catalog_dataset dataset
             WHERE upper(btrim(dataset.warehouse_layer)) IN ('SOURCE', 'DIM')
               AND EXISTS (
                   SELECT 1 FROM catalog_asset_normalization_issue issue
                    WHERE issue.resource_id = dataset.id AND issue.status = 'PENDING'
               )
             ORDER BY dataset.id
             LIMIT ?
            """,
            (rs, rowNum) -> previewRow(rs),
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
            if (plan == null) continue;
            assertProjectionAbsent(row.resourceId());
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
                null,
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
            String actionCode = "DIM".equals(row.legacyLayerCode())
                ? "DIM_TO_DWD_DIMENSION_TABLE"
                : "SOURCE_TO_PRODUCER_REF";
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
                row.legacyLayerCode(),
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
        return new ApplyResult(batchId, preview.previewHash(), preview.rows().size(), applied, preview.rows().size() - applied, now);
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
            Long currentVersion = jdbc.queryForObject(
                "SELECT projection_version FROM catalog_asset_semantic_projection WHERE asset_type = 'DATASET' AND asset_key = ?",
                Long.class,
                item.assetKey()
            );
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
        return new RollbackResult(batchId, removed, now);
    }

    private PreviewRow previewRow(ResultSet rs) throws SQLException {
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
        boolean projectionExists = rs.getBoolean("projection_exists");
        boolean automatic = "SOURCE".equals(legacyLayer) && sourceId != null && assetKey != null && !projectionExists;
        if (projectionExists) {
            issueCode = "SEMANTIC_PROJECTION_ALREADY_EXISTS";
            automatic = false;
        } else if (issueCode == null && "SOURCE".equals(legacyLayer) && sourceId == null) {
            issueCode = "SOURCE_PRODUCER_UNRESOLVED";
        } else if (issueCode == null && "DIM".equals(legacyLayer)) {
            issueCode = "DIMENSION_CONFIRMATION_REQUIRED";
        }
        return new PreviewRow(
            resourceId,
            rs.getString("name"),
            rs.getObject("domain_id", UUID.class),
            sourceId,
            legacyLayer,
            assetKey,
            automatic,
            issueCode
        );
    }

    private ResolutionPlan resolutionPlan(PreviewRow row, Resolution resolution) {
        if (row.assetKey() == null || "SEMANTIC_PROJECTION_ALREADY_EXISTS".equals(row.issueCode())) return null;
        RelationType relationType = resolution == null || resolution.relationType() == null
            ? RelationType.TABLE
            : resolution.relationType();
        if ("SOURCE".equals(row.legacyLayerCode())) {
            String producerId = resolution == null ? value(row.sourceId()) : trimToNull(resolution.producerId());
            ProducerKind kind = resolution == null ? ProducerKind.SOURCE_SYSTEM : resolution.producerKind();
            if (kind != ProducerKind.SOURCE_SYSTEM || producerId == null) return null;
            return new ResolutionPlan(kind, producerId, resolution == null ? null : trimToNull(resolution.producerVersion()), relationType);
        }
        if (
            resolution == null ||
            !resolution.confirmDimension() ||
            resolution.producerKind() == null ||
            !StringUtils.hasText(resolution.producerId())
        ) {
            return null;
        }
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

    private void assertProjectionAbsent(UUID resourceId) {
        Long count = jdbc.queryForObject(
            "SELECT count(*) FROM catalog_asset_semantic_projection WHERE asset_type = 'DATASET' AND resource_id = ?",
            Long.class,
            resourceId
        );
        if (count != null && count > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CATALOG_ASSET_NORMALIZATION_PROJECTION_DRIFT");
        }
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
            "SELECT count(DISTINCT resource_id) FROM catalog_asset_normalization_issue WHERE status = 'PENDING'",
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
            .append(row.automatic()).append('|')
            .append(row.issueCode()).append('\n'));
        return DigestUtils.sha256Hex(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String itemChecksum(PreviewRow row, ResolutionPlan plan, long projectionVersion) {
        String value = String.join(
            "|",
            row.resourceId().toString(),
            row.assetKey(),
            row.legacyLayerCode(),
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
        boolean automatic,
        String issueCode
    ) {}

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
        int unresolved,
        Instant appliedAt
    ) {}

    public record RollbackResult(UUID batchId, int removed, Instant rolledBackAt) {}

    private record ResolutionPlan(
        ProducerKind producerKind,
        String producerId,
        String producerVersion,
        RelationType relationType
    ) {}

    private record AppliedItem(UUID resourceId, String assetKey, long projectionVersion) {}

    private record Batch(UUID id, String status) {}
}
