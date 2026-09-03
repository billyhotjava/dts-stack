package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Catalog-owned schema and identity reader for planning consumers. */
@Component
@Transactional(readOnly = true)
public class JpaCatalogSourceReferenceReadAdapter implements CatalogSourceReferenceReadPort {

    private final CatalogTableSchemaRepository tables;
    private final CatalogColumnSchemaRepository columns;
    private final CatalogDatasetRepository datasets;
    private final AccessChecker accessChecker;

    public JpaCatalogSourceReferenceReadAdapter(
        CatalogTableSchemaRepository tables,
        CatalogColumnSchemaRepository columns,
        CatalogDatasetRepository datasets,
        AccessChecker accessChecker
    ) {
        this.tables = tables;
        this.columns = columns;
        this.datasets = datasets;
        this.accessChecker = accessChecker;
    }

    @Override
    public SourceSnapshot resolveTable(UUID tableId, String actorDepartmentId) {
        if (tableId == null) {
            return SourceSnapshot.providerError();
        }
        CatalogTableSchema table = tables.findById(tableId).orElse(null);
        if (table == null) {
            return SourceSnapshot.missing();
        }
        return resolveVisibleTable(table, actorDepartmentId);
    }

    @Override
    public SourceSnapshot resolveTableForExecution(UUID tableId) {
        if (tableId == null) {
            return SourceSnapshot.providerError();
        }
        CatalogTableSchema table = tables.findById(tableId).orElse(null);
        if (table == null) {
            return SourceSnapshot.missing();
        }
        return resolveExecutionTable(table);
    }

    @Override
    public SourceSnapshot resolveConnectionTable(
        UUID sourceId,
        String namespace,
        String objectName,
        String actorDepartmentId
    ) {
        if (sourceId == null || isBlank(namespace) || isBlank(objectName)) {
            return SourceSnapshot.providerError();
        }
        CatalogDataset dataset = datasets
            .findFirstBySourceIdAndHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(sourceId, namespace, objectName)
            .orElse(null);
        if (dataset == null) {
            return SourceSnapshot.providerError();
        }
        if (!canRead(dataset, actorDepartmentId)) {
            return SourceSnapshot.forbidden();
        }
        List<CatalogTableSchema> catalogTables = tables.findByDataset(dataset);
        CatalogTableSchema table = catalogTables
            .stream()
            .filter(candidate -> objectName.equalsIgnoreCase(candidate.getName()))
            .findFirst()
            .orElseGet(() -> catalogTables.size() == 1 ? catalogTables.getFirst() : null);
        if (table == null) {
            return SourceSnapshot.providerError();
        }
        return available(dataset, table);
    }

    @Override
    public SourceSnapshot resolveConnectionTableForExecution(
        UUID sourceId,
        String namespace,
        String objectName
    ) {
        if (sourceId == null || isBlank(namespace) || isBlank(objectName)) {
            return SourceSnapshot.providerError();
        }
        CatalogDataset dataset = datasets
            .findFirstBySourceIdAndHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(sourceId, namespace, objectName)
            .orElse(null);
        if (dataset == null) {
            return SourceSnapshot.providerError();
        }
        if (!isExecutionAvailable(dataset)) {
            return SourceSnapshot.missing();
        }
        List<CatalogTableSchema> catalogTables = tables.findByDataset(dataset);
        CatalogTableSchema table = catalogTables
            .stream()
            .filter(candidate -> objectName.equalsIgnoreCase(candidate.getName()))
            .findFirst()
            .orElseGet(() -> catalogTables.size() == 1 ? catalogTables.getFirst() : null);
        if (table == null) {
            return SourceSnapshot.providerError();
        }
        return available(dataset, table);
    }

    @Override
    public Optional<String> findDatasetAssetKey(UUID datasetId) {
        if (datasetId == null) {
            return Optional.empty();
        }
        return datasets.findById(datasetId).map(CatalogAssetKey::dataset);
    }

    @Override
    public Optional<String> findDatasetAssetKeyByTableId(UUID tableId) {
        if (tableId == null) {
            return Optional.empty();
        }
        return tables.findById(tableId).map(CatalogTableSchema::getDataset).map(CatalogAssetKey::dataset);
    }

    @Override
    public Optional<String> findDatasetAssetKey(UUID sourceId, String namespace, String objectName) {
        if (sourceId == null || isBlank(namespace) || isBlank(objectName)) {
            return Optional.empty();
        }
        return datasets
            .findFirstBySourceIdAndHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(sourceId, namespace, objectName)
            .map(CatalogAssetKey::dataset);
    }

    private SourceSnapshot resolveVisibleTable(CatalogTableSchema table, String actorDepartmentId) {
        CatalogDataset dataset = table.getDataset();
        if (!canRead(dataset, actorDepartmentId)) {
            return SourceSnapshot.forbidden();
        }
        return available(dataset, table);
    }

    private SourceSnapshot resolveExecutionTable(CatalogTableSchema table) {
        CatalogDataset dataset = table.getDataset();
        if (!isExecutionAvailable(dataset)) {
            return SourceSnapshot.missing();
        }
        return available(dataset, table);
    }

    private boolean isExecutionAvailable(CatalogDataset dataset) {
        return dataset != null && !Boolean.FALSE.equals(dataset.getEnabled());
    }

    private SourceSnapshot available(CatalogDataset dataset, CatalogTableSchema table) {
        if (dataset.getSourceId() != null) {
            if ("STALE".equalsIgnoreCase(dataset.getHarvestStatus())) {
                return SourceSnapshot.missing();
            }
            if (!"SYNCED".equalsIgnoreCase(dataset.getHarvestStatus())) {
                return SourceSnapshot.providerError();
            }
        }
        return SourceSnapshot.available(
            CatalogAssetType.DATASET,
            CatalogAssetKey.dataset(dataset),
            table.getName(),
            schemaFingerprint(table)
        );
    }

    private boolean canRead(CatalogDataset dataset, String actorDepartmentId) {
        return dataset != null &&
        accessChecker.canRead(dataset) &&
        accessChecker.departmentAllowedExact(dataset, actorDepartmentId);
    }

    private String schemaFingerprint(CatalogTableSchema table) {
        List<CatalogColumnSchema> orderedColumns = columns
            .findByTable(table)
            .stream()
            .sorted(Comparator.comparing(CatalogColumnSchema::getName, String.CASE_INSENSITIVE_ORDER))
            .toList();
        if (orderedColumns.isEmpty()) {
            throw new IllegalStateException("Catalog schema has no columns");
        }
        StringBuilder canonical = new StringBuilder(safe(table.getName()));
        for (CatalogColumnSchema column : orderedColumns) {
            canonical
                .append('\u0000')
                .append(safe(column.getName()))
                .append('\u0000')
                .append(safe(column.getDataType()))
                .append('\u0000')
                .append(Boolean.TRUE.equals(column.getNullable()))
                .append('\u0000')
                .append(safe(column.getStatus()));
        }
        String apiEvidence = apiFingerprintEvidence(table);
        if (apiEvidence != null) {
            canonical.append('\u0000').append(apiEvidence);
        }
        return sha256(canonical.toString());
    }

    private String apiFingerprintEvidence(CatalogTableSchema table) {
        if (table == null) {
            return null;
        }
        Map<String, String> tableTags = tagValues(table.getTags());
        Map<String, String> datasetTags = table.getDataset() == null ? Map.of() : tagValues(table.getDataset().getTags());
        String origin = consistentEvidenceValue("origin", tableTags, datasetTags);
        if (!"API".equalsIgnoreCase(origin)) {
            return null;
        }
        CatalogDataset dataset = table.getDataset();
        if (dataset == null || !Boolean.TRUE.equals(dataset.getEnabled()) || "STALE".equalsIgnoreCase(dataset.getLifecycleStatus())) {
            throw new IllegalStateException("API landing catalog asset is not current");
        }
        String connectionId = requiredApiEvidence("connectionId", tableTags, datasetTags);
        String taskId = requiredApiEvidence("taskId", tableTags, datasetTags);
        String taskRevision = requiredApiEvidence("taskRevision", tableTags, datasetTags);
        String resourceId = requiredApiEvidence("resourceId", tableTags, datasetTags);
        String executionSequence = requiredApiEvidence("executionSequence", tableTags, datasetTags);
        String executionId = requiredApiEvidence("executionId", tableTags, datasetTags);
        String executionStatus = requiredApiEvidence("executionStatus", tableTags, datasetTags);
        String landingTruth = requiredApiEvidence("landingTruth", tableTags, datasetTags);
        String landingStatus = requiredApiEvidence("landingStatus", tableTags, datasetTags);
        String configChecksum = requiredApiEvidence("configChecksum", tableTags, datasetTags);
        String fieldSnapshotChecksum = requiredApiEvidence("fieldSnapshotChecksum", tableTags, datasetTags);
        UUID evidenceConnectionId;
        try {
            evidenceConnectionId = UUID.fromString(connectionId);
            Instant.parse(taskRevision);
            if (Long.parseLong(executionSequence) < 0) {
                throw new IllegalArgumentException("negative sequence");
            }
        } catch (RuntimeException exception) {
            throw new IllegalStateException("API landing evidence has invalid task or execution ordering", exception);
        }
        if (
            (dataset.getSourceId() != null && !dataset.getSourceId().equals(evidenceConnectionId)) ||
            !"SUCCESS".equalsIgnoreCase(executionStatus) ||
            !"VERIFIED".equalsIgnoreCase(landingTruth) ||
            !"SUCCESS".equalsIgnoreCase(landingStatus)
        ) {
            throw new IllegalStateException("API landing evidence is not verified successful");
        }
        return String.join(
            "\u0000",
            "api-connection=" + connectionId,
            "api-task=" + taskId,
            "api-task-revision=" + taskRevision,
            "api-resource=" + resourceId,
            "api-execution-sequence=" + executionSequence,
            "api-execution=" + executionId,
            "api-execution-status=SUCCESS",
            "api-landing-status=SUCCESS",
            "api-config=" + configChecksum,
            "api-field-snapshot=" + fieldSnapshotChecksum
        );
    }

    private String requiredApiEvidence(String key, Map<String, String> tableTags, Map<String, String> datasetTags) {
        String value = consistentEvidenceValue(key, tableTags, datasetTags);
        if (isBlank(value)) {
            throw new IllegalStateException("API landing evidence is missing " + key);
        }
        return value;
    }

    private String consistentEvidenceValue(String key, Map<String, String> tableTags, Map<String, String> datasetTags) {
        String tableValue = tableTags.get(key);
        String datasetValue = datasetTags.get(key);
        if (!isBlank(tableValue) && !isBlank(datasetValue) && !tableValue.equals(datasetValue)) {
            throw new IllegalStateException("API landing evidence conflicts for " + key);
        }
        return firstNonBlank(tableValue, datasetValue);
    }

    private Map<String, String> tagValues(String tags) {
        if (isBlank(tags)) {
            return Map.of();
        }
        Map<String, String> values = new java.util.LinkedHashMap<>();
        for (String entry : tags.split(";")) {
            int separator = entry.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = entry.substring(0, separator).trim();
            String value = entry.substring(separator + 1).trim();
            if (!key.isEmpty() && !value.isEmpty()) {
                values.put(key, value);
            }
        }
        return values;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
