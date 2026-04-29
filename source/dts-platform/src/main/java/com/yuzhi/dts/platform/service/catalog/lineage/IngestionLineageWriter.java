package com.yuzhi.dts.platform.service.catalog.lineage;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogDatasetLineage;
import com.yuzhi.dts.platform.domain.infra.InfraOdsTableMapping;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetLineageRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.infra.InfraOdsTableMappingRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class IngestionLineageWriter {

    public static final String RELATION_ADDAX = "ADDAX";
    public static final String STATUS_DECLARED = "DECLARED";
    public static final String STATUS_VERIFIED = "VERIFIED";
    public static final String STATUS_KNOWN_UNVERIFIED = "KNOWN_UNVERIFIED";
    private static final String LAYER_SOURCE = "SOURCE";
    private static final String LAYER_ODS = "ODS";

    private final CatalogDatasetRepository datasetRepository;
    private final CatalogDatasetLineageRepository lineageRepository;
    private final InfraOdsTableMappingRepository mappingRepository;
    private final InfraDataSourceRepository dataSourceRepository;
    private final AuditService auditService;

    public IngestionLineageWriter(
        CatalogDatasetRepository datasetRepository,
        CatalogDatasetLineageRepository lineageRepository,
        InfraOdsTableMappingRepository mappingRepository,
        InfraDataSourceRepository dataSourceRepository,
        AuditService auditService
    ) {
        this.datasetRepository = datasetRepository;
        this.lineageRepository = lineageRepository;
        this.mappingRepository = mappingRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.auditService = auditService;
    }

    @Transactional
    public LineageWriteResult syncEnabledOdsMappings() {
        List<InfraOdsTableMapping> mappings = mappingRepository.findByEnabledTrueOrderByOdsSchemaAscOdsTableAsc();
        int created = 0;
        int updated = 0;
        int skipped = 0;
        int datasetsCreated = 0;
        for (InfraOdsTableMapping mapping : mappings) {
            LineageWriteResult result = writeAddaxLineage(mapping);
            created += result.created();
            updated += result.updated();
            skipped += result.skipped();
            datasetsCreated += result.datasetsCreated();
        }
        LineageWriteResult summary = new LineageWriteResult(created, updated, skipped, datasetsCreated, mappings.size(), "synced");
        auditService.auditAction(
            "LINEAGE_INGEST_WRITE",
            AuditStage.SUCCESS,
            "ADDAX",
            Map.of(
                "summary",
                "同步 Addax 入湖血缘",
                "mappings",
                summary.mappings(),
                "created",
                summary.created(),
                "updated",
                summary.updated(),
                "skipped",
                summary.skipped(),
                "datasetsCreated",
                summary.datasetsCreated()
            )
        );
        return summary;
    }

    @Transactional
    public LineageWriteResult writeAddaxLineage(InfraOdsTableMapping mapping) {
        return writeAddaxLineage(mapping, LineageObservation.declared());
    }

    @Transactional
    public LineageWriteResult writeAddaxLineage(InfraOdsTableMapping mapping, LineageObservation observation) {
        if (!isWritable(mapping)) {
            return LineageWriteResult.skipped("invalid-mapping");
        }
        LineageObservation effectiveObservation = observation == null ? LineageObservation.declared() : observation;
        InfraDataSource source = mapping.getConnectionId() == null
            ? null
            : dataSourceRepository.findById(mapping.getConnectionId()).orElse(null);
        DatasetResolveResult sourceDataset = ensureSourceDataset(mapping, source);
        DatasetResolveResult odsDataset = ensureOdsDataset(mapping, source);
        if (sourceDataset.dataset() == null || sourceDataset.dataset().getId() == null || odsDataset.dataset() == null || odsDataset.dataset().getId() == null) {
            return LineageWriteResult.skipped("dataset-unresolved");
        }
        if (sourceDataset.dataset().getId().equals(odsDataset.dataset().getId())) {
            return LineageWriteResult.skipped("same-dataset");
        }
        Optional<CatalogDatasetLineage> existing = lineageRepository.findFirstByUpstreamDatasetIdAndDownstreamDatasetId(
            sourceDataset.dataset().getId(),
            odsDataset.dataset().getId()
        );
        if (existing.isPresent()) {
            CatalogDatasetLineage link = existing.orElseThrow();
            if (!RELATION_ADDAX.equalsIgnoreCase(link.getRelationType())) {
                return new LineageWriteResult(0, 0, 1, sourceDataset.created() + odsDataset.created(), 1, "pair-owned-by-" + link.getRelationType());
            }
            link.setNotes(buildNotes(mapping, source, effectiveObservation));
            link.setUpstreamAssetType("EXTERNAL_TABLE");
            link.setDownstreamAssetType("DATASET");
            link.setDirection("UPSTREAM_TO_DOWNSTREAM");
            link.setProjectName(resolveTaskName(mapping));
            applyObservation(link, effectiveObservation);
            lineageRepository.save(link);
            return new LineageWriteResult(0, 1, 0, sourceDataset.created() + odsDataset.created(), 1, "updated");
        }

        CatalogDatasetLineage link = new CatalogDatasetLineage();
        link.setUpstreamDatasetId(sourceDataset.dataset().getId());
        link.setDownstreamDatasetId(odsDataset.dataset().getId());
        link.setRelationType(RELATION_ADDAX);
        link.setUpstreamAssetType("EXTERNAL_TABLE");
        link.setDownstreamAssetType("DATASET");
        link.setDirection("UPSTREAM_TO_DOWNSTREAM");
        link.setProjectName(resolveTaskName(mapping));
        link.setNotes(buildNotes(mapping, source, effectiveObservation));
        applyObservation(link, effectiveObservation);
        lineageRepository.save(link);
        return new LineageWriteResult(1, 0, 0, sourceDataset.created() + odsDataset.created(), 1, "created");
    }

    @Transactional
    public int removeAddaxLineage(InfraOdsTableMapping mapping) {
        if (!isWritable(mapping)) {
            return 0;
        }
        CatalogDataset sourceDataset = findSourceDataset(mapping).orElse(null);
        CatalogDataset odsDataset = findOdsDataset(mapping).orElse(null);
        if (sourceDataset == null || sourceDataset.getId() == null || odsDataset == null || odsDataset.getId() == null) {
            return 0;
        }
        Optional<CatalogDatasetLineage> existing = lineageRepository.findFirstByUpstreamDatasetIdAndDownstreamDatasetId(sourceDataset.getId(), odsDataset.getId());
        if (existing.isEmpty()) {
            return 0;
        }
        CatalogDatasetLineage link = existing.orElseThrow();
        if (!RELATION_ADDAX.equalsIgnoreCase(link.getRelationType())) {
            return 0;
        }
        lineageRepository.delete(link);
        return 1;
    }

    private DatasetResolveResult ensureSourceDataset(InfraOdsTableMapping mapping, InfraDataSource source) {
        Optional<CatalogDataset> existing = findSourceDataset(mapping);
        if (existing.isPresent()) {
            return new DatasetResolveResult(existing.orElseThrow(), 0);
        }
        CatalogDataset dataset = new CatalogDataset();
        dataset.setName(truncate(sourceLabel(mapping, source), 128));
        dataset.setType("EXTERNAL_TABLE");
        dataset.setSourceId(mapping.getConnectionId());
        dataset.setHiveDatabase(defaultIfBlank(mapping.getStreamNamespace(), "source"));
        dataset.setHiveTable(mapping.getStreamName());
        dataset.setWarehouseLayer(LAYER_SOURCE);
        dataset.setOwner(mapping.getOwner());
        dataset.setOwnerDept(firstNonBlank(mapping.getOwnerDept(), source != null ? source.getOwnerDept() : null));
        dataset.setDescription(truncate("External source table imported from Addax mapping: " + physicalName(mapping.getStreamNamespace(), mapping.getStreamName()), 2048));
        dataset.setSnapshotTime(Instant.now());
        return new DatasetResolveResult(datasetRepository.save(dataset), 1);
    }

    private DatasetResolveResult ensureOdsDataset(InfraOdsTableMapping mapping, InfraDataSource source) {
        Optional<CatalogDataset> existing = findOdsDataset(mapping);
        if (existing.isPresent()) {
            CatalogDataset dataset = existing.orElseThrow();
            boolean changed = false;
            if (!StringUtils.hasText(dataset.getWarehouseLayer())) {
                dataset.setWarehouseLayer(LAYER_ODS);
                changed = true;
            }
            if (dataset.getSourceId() == null && mapping.getConnectionId() != null) {
                dataset.setSourceId(mapping.getConnectionId());
                changed = true;
            }
            if (!StringUtils.hasText(dataset.getOwnerDept())) {
                dataset.setOwnerDept(firstNonBlank(mapping.getOwnerDept(), source != null ? source.getOwnerDept() : null));
                changed = true;
            }
            return new DatasetResolveResult(changed ? datasetRepository.save(dataset) : dataset, 0);
        }
        CatalogDataset dataset = new CatalogDataset();
        dataset.setName(truncate(mapping.getOdsTable(), 128));
        dataset.setType("DATASET");
        dataset.setSourceId(mapping.getConnectionId());
        dataset.setHiveDatabase(defaultIfBlank(mapping.getOdsSchema(), "ods"));
        dataset.setHiveTable(mapping.getOdsTable());
        dataset.setWarehouseLayer(LAYER_ODS);
        dataset.setOwner(mapping.getOwner());
        dataset.setOwnerDept(firstNonBlank(mapping.getOwnerDept(), source != null ? source.getOwnerDept() : null));
        dataset.setDescription(truncate("ODS table loaded by Addax mapping: " + physicalName(mapping.getOdsSchema(), mapping.getOdsTable()), 2048));
        dataset.setSnapshotTime(Instant.now());
        return new DatasetResolveResult(datasetRepository.save(dataset), 1);
    }

    private Optional<CatalogDataset> findSourceDataset(InfraOdsTableMapping mapping) {
        if (mapping == null || !StringUtils.hasText(mapping.getStreamName())) {
            return Optional.empty();
        }
        String schema = defaultIfBlank(mapping.getStreamNamespace(), "source");
        if (mapping.getConnectionId() != null) {
            Optional<CatalogDataset> bySource = datasetRepository.findFirstBySourceIdAndHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(
                mapping.getConnectionId(),
                schema,
                mapping.getStreamName()
            );
            if (bySource.isPresent()) {
                return bySource;
            }
        }
        return datasetRepository.findFirstByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(schema, mapping.getStreamName());
    }

    private Optional<CatalogDataset> findOdsDataset(InfraOdsTableMapping mapping) {
        if (mapping == null || !StringUtils.hasText(mapping.getOdsTable())) {
            return Optional.empty();
        }
        if (mapping.getDatasetId() != null) {
            Optional<CatalogDataset> byId = datasetRepository.findById(mapping.getDatasetId());
            if (byId.isPresent()) {
                return byId;
            }
        }
        String schema = defaultIfBlank(mapping.getOdsSchema(), "ods");
        if (mapping.getConnectionId() != null) {
            Optional<CatalogDataset> bySource = datasetRepository.findFirstBySourceIdAndHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(
                mapping.getConnectionId(),
                schema,
                mapping.getOdsTable()
            );
            if (bySource.isPresent()) {
                return bySource;
            }
        }
        return datasetRepository.findFirstByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(schema, mapping.getOdsTable());
    }

    private boolean isWritable(InfraOdsTableMapping mapping) {
        return mapping != null &&
            Boolean.TRUE.equals(mapping.getEnabled()) &&
            StringUtils.hasText(mapping.getStreamName()) &&
            StringUtils.hasText(mapping.getOdsTable());
    }

    private void applyObservation(CatalogDatasetLineage link, LineageObservation observation) {
        LineageObservation effective = observation == null ? LineageObservation.declared() : observation;
        Instant observedAt = effective.observedAt() == null ? Instant.now() : effective.observedAt();
        String status = normalizeStatus(effective.verificationStatus());
        if (STATUS_VERIFIED.equals(status)) {
            link.setVerificationStatus(STATUS_VERIFIED);
            link.setLastVerifiedAt(observedAt);
        } else if (STATUS_KNOWN_UNVERIFIED.equals(status)) {
            if (!STATUS_VERIFIED.equalsIgnoreCase(link.getVerificationStatus())) {
                link.setVerificationStatus(STATUS_KNOWN_UNVERIFIED);
            }
        } else if (!StringUtils.hasText(link.getVerificationStatus())) {
            link.setVerificationStatus(STATUS_DECLARED);
        }
        link.setLastObservedAt(observedAt);
        if (StringUtils.hasText(effective.executionId())) {
            link.setLastExecutionId(truncate(effective.executionId().trim(), 128));
        }
        if (StringUtils.hasText(effective.executionStatus())) {
            link.setLastExecutionStatus(truncate(effective.executionStatus().trim().toLowerCase(Locale.ROOT), 32));
        }
    }

    private String buildNotes(InfraOdsTableMapping mapping, InfraDataSource source, LineageObservation observation) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("engine", RELATION_ADDAX);
        data.put("mappingId", mapping.getId() != null ? mapping.getId().toString() : null);
        data.put("sourceDataSourceId", mapping.getConnectionId() != null ? mapping.getConnectionId().toString() : null);
        data.put("sourceName", source != null ? source.getName() : null);
        data.put("sourceTable", physicalName(mapping.getStreamNamespace(), mapping.getStreamName()));
        data.put("targetTable", physicalName(mapping.getOdsSchema(), mapping.getOdsTable()));
        data.put("task", resolveTaskName(mapping));
        LineageObservation effective = observation == null ? LineageObservation.declared() : observation;
        data.put("verificationStatus", normalizeStatus(effective.verificationStatus()));
        data.put("executionStatus", effective.executionStatus());
        data.put("executionId", effective.executionId());
        data.put("batchId", effective.batchId());
        data.values().removeIf(value -> value == null || !StringUtils.hasText(String.valueOf(value)));
        return data.toString();
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return STATUS_DECLARED;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (STATUS_VERIFIED.equals(normalized) || STATUS_KNOWN_UNVERIFIED.equals(normalized)) {
            return normalized;
        }
        return STATUS_DECLARED;
    }

    private String resolveTaskName(InfraOdsTableMapping mapping) {
        if (mapping == null || !StringUtils.hasText(mapping.getDescription())) {
            return null;
        }
        String description = mapping.getDescription().trim();
        int taskEnd = description.indexOf("] ");
        if (taskEnd >= 0) {
            description = description.substring(taskEnd + 2);
        }
        int colon = description.indexOf(": ");
        if (colon > 0) {
            return truncate(description.substring(0, colon), 128);
        }
        return null;
    }

    private String sourceLabel(InfraOdsTableMapping mapping, InfraDataSource source) {
        String sourceName = source != null ? source.getName() : null;
        String table = physicalName(mapping.getStreamNamespace(), mapping.getStreamName());
        return StringUtils.hasText(sourceName) ? sourceName + "/" + table : table;
    }

    private String physicalName(String schema, String table) {
        return StringUtils.hasText(schema) ? schema.trim() + "." + defaultIfBlank(table, "unknown") : defaultIfBlank(table, "unknown");
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength));
    }

    private record DatasetResolveResult(CatalogDataset dataset, int created) {}

    public record LineageObservation(
        String verificationStatus,
        String executionStatus,
        String executionId,
        String batchId,
        Instant observedAt
    ) {
        public static LineageObservation declared() {
            return new LineageObservation(STATUS_DECLARED, null, null, null, Instant.now());
        }

        public static LineageObservation fromExecution(String executionStatus, String executionId, String batchId, Instant observedAt) {
            String normalized = StringUtils.hasText(executionStatus) ? executionStatus.trim().toLowerCase(Locale.ROOT) : null;
            String verificationStatus = "success".equals(normalized) ? STATUS_VERIFIED : STATUS_KNOWN_UNVERIFIED;
            return new LineageObservation(
                verificationStatus,
                normalized,
                StringUtils.hasText(executionId) ? executionId.trim() : null,
                StringUtils.hasText(batchId) ? batchId.trim() : null,
                observedAt == null ? Instant.now() : observedAt
            );
        }
    }

    public record LineageWriteResult(int created, int updated, int skipped, int datasetsCreated, int mappings, String status) {
        public static LineageWriteResult skipped(String status) {
            return new LineageWriteResult(0, 0, 1, 0, 1, status);
        }
    }
}
