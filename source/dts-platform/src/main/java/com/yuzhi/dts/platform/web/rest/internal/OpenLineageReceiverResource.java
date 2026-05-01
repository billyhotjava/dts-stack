package com.yuzhi.dts.platform.web.rest.internal;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogDatasetLineage;
import com.yuzhi.dts.platform.domain.catalog.CatalogLineageJob;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetLineageRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogLineageJobRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/lineage")
@Transactional
public class OpenLineageReceiverResource {

    private static final String RELATION_AIRFLOW = "AIRFLOW";
    private static final String CATALOG_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).CATALOG_MAINTAINERS)";

    private final CatalogDatasetRepository datasetRepository;
    private final CatalogDatasetLineageRepository lineageRepository;
    private final CatalogLineageJobRepository lineageJobRepository;

    public OpenLineageReceiverResource(
        CatalogDatasetRepository datasetRepository,
        CatalogDatasetLineageRepository lineageRepository,
        CatalogLineageJobRepository lineageJobRepository
    ) {
        this.datasetRepository = datasetRepository;
        this.lineageRepository = lineageRepository;
        this.lineageJobRepository = lineageJobRepository;
    }

    @PostMapping("/openlineage")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ResponseEntity<Map<String, Object>> receive(@RequestBody Map<String, Object> payload) {
        Map<String, Object> jobPayload = payloadMap(payload.get("job"));
        Map<String, Object> runPayload = payloadMap(payload.get("run"));
        String jobName = firstNonBlank(stringValue(jobPayload.get("name")), stringValue(payload.get("jobName")), "openlineage-job");
        String namespace = firstNonBlank(stringValue(jobPayload.get("namespace")), stringValue(payload.get("producer")), "openlineage");
        String eventType = firstNonBlank(stringValue(payload.get("eventType")), stringValue(payload.get("event_type")), "UNKNOWN");
        String runId = firstNonBlank(stringValue(runPayload.get("runId")), stringValue(runPayload.get("run_id")), null);
        Instant observedAt = parseInstant(firstNonBlank(stringValue(payload.get("eventTime")), stringValue(payload.get("event_time")), null));

        CatalogLineageJob job = upsertJob(namespace, jobName, runId, eventType, observedAt, payload);
        List<CatalogDataset> inputs = resolveDatasets(listOfMaps(payload.get("inputs")), "UPSTREAM");
        List<CatalogDataset> outputs = resolveDatasets(listOfMaps(payload.get("outputs")), "DOWNSTREAM");

        int created = 0;
        int updated = 0;
        for (CatalogDataset input : inputs) {
            for (CatalogDataset output : outputs) {
                if (input.getId() == null || output.getId() == null || input.getId().equals(output.getId())) {
                    continue;
                }
                CatalogDatasetLineage lineage = lineageRepository
                    .findFirstByUpstreamDatasetIdAndDownstreamDatasetIdAndRelationTypeIgnoreCaseAndValidToIsNull(
                        input.getId(),
                        output.getId(),
                        RELATION_AIRFLOW
                    )
                    .orElseGet(CatalogDatasetLineage::new);
                boolean isNew = lineage.getId() == null;
                lineage.setUpstreamDatasetId(input.getId());
                lineage.setDownstreamDatasetId(output.getId());
                lineage.setRelationType(RELATION_AIRFLOW);
                lineage.setUpstreamAssetType("DATASET");
                lineage.setDownstreamAssetType("DATASET");
                lineage.setDirection("UPSTREAM_TO_DOWNSTREAM");
                lineage.setProjectName(jobName);
                lineage.setLineageJobId(job.getId());
                lineage.setVerificationStatus(verificationStatus(eventType));
                lineage.setLastExecutionId(runId);
                lineage.setLastExecutionStatus(eventType.toLowerCase(Locale.ROOT));
                lineage.setLastObservedAt(observedAt);
                if (lineage.getValidFrom() == null) {
                    lineage.setValidFrom(observedAt != null ? observedAt : Instant.now());
                }
                if ("VERIFIED".equals(lineage.getVerificationStatus())) {
                    lineage.setLastVerifiedAt(observedAt);
                }
                lineage.setNotes("openlineage:" + namespace + "/" + jobName);
                lineageRepository.save(lineage);
                if (isNew) {
                    created++;
                } else {
                    updated++;
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jobId", job.getId() != null ? job.getId().toString() : null);
        result.put("inputs", inputs.size());
        result.put("outputs", outputs.size());
        result.put("created", created);
        result.put("updated", updated);
        result.put("eventType", eventType);
        result.put("runId", runId);
        return ResponseEntity.ok(result);
    }

    private CatalogLineageJob upsertJob(String namespace, String jobName, String runId, String eventType, Instant observedAt, Map<String, Object> payload) {
        String jobKey = "OPENLINEAGE:" + safeKey(namespace) + ":" + safeKey(jobName);
        CatalogLineageJob job = lineageJobRepository.findByJobKey(jobKey).orElseGet(CatalogLineageJob::new);
        job.setJobKey(jobKey);
        job.setName(truncate(jobName, 256));
        job.setJobType("AIRFLOW_DAG");
        job.setEngine("OPENLINEAGE");
        job.setRelationType(RELATION_AIRFLOW);
        job.setProjectName(jobName);
        job.setExternalId(runId);
        job.setStatus(verificationStatus(eventType).toLowerCase(Locale.ROOT));
        job.setLastExecutionId(runId);
        job.setLastExecutionStatus(eventType.toLowerCase(Locale.ROOT));
        job.setLastObservedAt(observedAt);
        if ("VERIFIED".equals(verificationStatus(eventType))) {
            job.setLastVerifiedAt(observedAt);
        }
        job.setDetailPayload(truncate(String.valueOf(payload), 4000));
        return lineageJobRepository.save(job);
    }

    private List<CatalogDataset> resolveDatasets(List<Map<String, Object>> datasets, String role) {
        List<CatalogDataset> result = new ArrayList<>();
        for (Map<String, Object> payload : datasets) {
            DatasetName name = datasetName(payload);
            if (!StringUtils.hasText(name.table())) {
                continue;
            }
            CatalogDataset dataset = datasetRepository
                .findFirstByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(name.schema(), name.table())
                .orElseGet(() -> createDataset(name, role));
            result.add(dataset);
        }
        return result;
    }

    private CatalogDataset createDataset(DatasetName name, String role) {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setName(truncate(name.table(), 128));
        dataset.setType("DATASET");
        dataset.setHiveDatabase(truncate(name.schema(), 128));
        dataset.setHiveTable(truncate(name.table(), 128));
        dataset.setWarehouseLayer(inferLayer(name.table(), role));
        dataset.setDescription(truncate("Discovered from OpenLineage namespace=" + name.namespace(), 2048));
        dataset.setSnapshotTime(Instant.now());
        return datasetRepository.save(dataset);
    }

    private DatasetName datasetName(Map<String, Object> payload) {
        String rawName = firstNonBlank(stringValue(payload.get("name")), stringValue(payload.get("qualifiedName")), null);
        String namespace = firstNonBlank(stringValue(payload.get("namespace")), "openlineage");
        String schema = firstNonBlank(schemaFacet(payload), namespace);
        String table = rawName;
        if (StringUtils.hasText(rawName) && rawName.contains(".")) {
            String[] parts = rawName.split("\\.");
            table = parts[parts.length - 1];
            schema = parts.length > 1 ? parts[parts.length - 2] : schema;
        }
        return new DatasetName(cleanName(namespace), cleanName(schema), cleanName(table));
    }

    private String schemaFacet(Map<String, Object> payload) {
        Map<String, Object> facets = payloadMap(payload.get("facets"));
        Map<String, Object> schema = payloadMap(facets.get("schema"));
        return stringValue(schema.get("schemaName"));
    }

    private String verificationStatus(String eventType) {
        String normalized = StringUtils.hasText(eventType) ? eventType.trim().toUpperCase(Locale.ROOT) : "";
        if ("COMPLETE".equals(normalized) || "SUCCESS".equals(normalized)) {
            return "VERIFIED";
        }
        if ("FAIL".equals(normalized) || "FAILED".equals(normalized) || "ABORT".equals(normalized)) {
            return "KNOWN_UNVERIFIED";
        }
        return "DECLARED";
    }

    private String inferLayer(String table, String role) {
        String normalized = StringUtils.hasText(table) ? table.trim().toLowerCase(Locale.ROOT) : "";
        for (String layer : List.of("ods", "stg", "dwd", "dws", "ads", "dim")) {
            if (normalized.startsWith(layer + "_") || normalized.startsWith(layer + ".")) {
                return layer.toUpperCase(Locale.ROOT);
            }
        }
        return "UPSTREAM".equals(role) ? "SOURCE" : null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> payloadMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(Objects::nonNull).filter(Map.class::isInstance).map(item -> (Map<String, Object>) item).toList();
    }

    private Instant parseInstant(String value) {
        if (!StringUtils.hasText(value)) {
            return Instant.now();
        }
        try {
            return Instant.parse(value.trim());
        } catch (Exception ignored) {
            return Instant.now();
        }
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

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String cleanName(String value) {
        if (!StringUtils.hasText(value)) {
            return "openlineage";
        }
        return value.trim().replaceAll("[^A-Za-z0-9_\\-.]", "_");
    }

    private String safeKey(String value) {
        return cleanName(value).replaceAll("[^A-Za-z0-9_.:-]", "_");
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength));
    }

    private record DatasetName(String namespace, String schema, String table) {}
}
