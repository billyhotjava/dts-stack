package com.yuzhi.dts.platform.web.rest.internal;

import com.yuzhi.dts.common.security.SecurityLevelCatalog;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogDatasetLineage;
import com.yuzhi.dts.platform.domain.catalog.CatalogLineageJob;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetLineageRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogLineageJobRepository;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetKey;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetGovernancePolicy;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationService;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationPropagationJobService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
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
    private final CatalogClassificationService classificationService;
    private final CatalogClassificationPropagationJobService propagationJobService;

    public OpenLineageReceiverResource(
        CatalogDatasetRepository datasetRepository,
        CatalogDatasetLineageRepository lineageRepository,
        CatalogLineageJobRepository lineageJobRepository,
        CatalogClassificationService classificationService,
        CatalogClassificationPropagationJobService propagationJobService
    ) {
        this.datasetRepository = datasetRepository;
        this.lineageRepository = lineageRepository;
        this.lineageJobRepository = lineageJobRepository;
        this.classificationService = classificationService;
        this.propagationJobService = propagationJobService;
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
        List<DatasetResolution> inputs = resolveDatasets(listOfMaps(payload.get("inputs")), "UPSTREAM", jobName, runId);
        List<DatasetResolution> outputs = resolveDatasets(listOfMaps(payload.get("outputs")), "DOWNSTREAM", jobName, runId);

        int created = 0;
        int updated = 0;
        int propagationEnqueued = 0;
        for (DatasetResolution inputResolution : inputs) {
            for (DatasetResolution outputResolution : outputs) {
                CatalogDataset input = inputResolution.dataset();
                CatalogDataset output = outputResolution.dataset();
                if (input == null || output == null || input.getId() == null || output.getId() == null || input.getId().equals(output.getId())) {
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
                lineage.setNotes(
                    "openlineage:" +
                    namespace +
                    "/" +
                    jobName +
                    " upstreamKey=" +
                    inputResolution.assetKey() +
                    " downstreamKey=" +
                    outputResolution.assetKey() +
                    " runId=" +
                    Objects.toString(runId, "")
                );
                lineageRepository.save(lineage);
                if (isNew) {
                    created++;
                } else {
                    updated++;
                }
            }
        }
        if ("VERIFIED".equals(verificationStatus(eventType))) {
            for (DatasetResolution output : outputs) {
                if (
                    output.dataset() != null &&
                    output.dataset().getId() != null &&
                    propagationJobService.enqueue(output.dataset().getId(), "OPENLINEAGE", runId)
                ) {
                    propagationEnqueued++;
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jobId", job.getId() != null ? job.getId().toString() : null);
        result.put("inputs", inputs.size());
        result.put("outputs", outputs.size());
        result.put("createdAssets", countCreated(inputs) + countCreated(outputs));
        result.put("assetEvidence", evidence(inputs, outputs));
        result.put("created", created);
        result.put("updated", updated);
        result.put("propagationEnqueued", propagationEnqueued);
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

    private List<DatasetResolution> resolveDatasets(List<Map<String, Object>> datasets, String role, String jobName, String runId) {
        List<DatasetResolution> result = new ArrayList<>();
        for (Map<String, Object> payload : datasets) {
            DatasetName name = datasetName(payload);
            if (!StringUtils.hasText(name.table())) {
                continue;
            }
            List<CatalogDataset> matches = datasetRepository.findByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(name.schema(), name.table());
            if (!matches.isEmpty()) {
                CatalogDataset dataset = matches.get(0);
                applyClassificationFacet(dataset, payload, role, runId);
                result.add(new DatasetResolution(dataset, role, name.namespace(), name.rawName(), safeDatasetKey(dataset), false, "schema_table"));
                continue;
            }
            CatalogDataset dataset = createDataset(name, payload, role, jobName, runId);
            applyClassificationFacet(dataset, payload, role, runId);
            result.add(new DatasetResolution(dataset, role, name.namespace(), name.rawName(), safeDatasetKey(dataset), true, "openlineage_discovery"));
        }
        return result;
    }

    private CatalogDataset createDataset(DatasetName name, Map<String, Object> payload, String role, String jobName, String runId) {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setName(truncate(name.table(), 128));
        dataset.setType("DATASET");
        dataset.setHiveDatabase(truncate(name.schema(), 128));
        dataset.setHiveTable(truncate(name.table(), 128));
        dataset.setWarehouseLayer(firstNonBlank(inferLayer(name.table(), role), "UNKNOWN"));
        dataset.setLifecycleStatus(CatalogAssetGovernancePolicy.lifecycleForDiscoveredAsset());
        dataset.setOwner(truncate(ownerFacet(payload), 64));
        dataset.setOwnerDept(truncate(ownerDeptFacet(payload), 64));
        dataset.setClassification(normalizedClassificationFacet(payload));
        dataset.setDescription(
            truncate(
                "Discovered from OpenLineage namespace=" +
                name.namespace() +
                ", rawName=" +
                name.rawName() +
                ", role=" +
                role +
                ", job=" +
                jobName +
                ", runId=" +
                Objects.toString(runId, ""),
                2048
            )
        );
        dataset.setSnapshotTime(Instant.now());
        return datasetRepository.save(dataset);
    }

    private void applyClassificationFacet(
        CatalogDataset dataset,
        Map<String, Object> payload,
        String role,
        String runId
    ) {
        String candidate = normalizedClassificationFacet(payload);
        if (!StringUtils.hasText(candidate) || dataset == null || dataset.getId() == null) {
            return;
        }
        String assetKey = safeDatasetKey(dataset);
        String evidence =
            "{\"source\":\"openlineage\",\"role\":\"" +
            role +
            "\",\"runId\":\"" +
            Objects.toString(runId, "") +
            "\"}";
        if (classificationService.resolve("ASSET", assetKey).isPresent()) {
            classificationService.addDetectedLevel(
                new CatalogClassificationService.LevelCommand(
                    "ASSET",
                    assetKey,
                    candidate,
                    runId,
                    evidence
                )
            );
            return;
        }
        classificationService.sealOrRaise(
            new CatalogClassificationService.SealCommand(
                "ASSET",
                assetKey,
                "DATASET",
                null,
                candidate,
                null,
                List.of(),
                "SENSITIVE_DETECTION",
                "openlineage:" + Objects.toString(runId, "unknown"),
                sha256(assetKey + ":" + candidate + ":" + Objects.toString(runId, "")),
                evidence
            )
        );
    }

    private String normalizedClassificationFacet(Map<String, Object> payload) {
        String raw = classificationFacet(payload);
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return SecurityLevelCatalog.requireDataLevel(raw).code();
        } catch (IllegalArgumentException ignored) {
            return null;
        }
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
        return new DatasetName(cleanName(namespace), cleanName(schema), cleanName(table), firstNonBlank(rawName, ""));
    }

    private long countCreated(List<DatasetResolution> resolutions) {
        return resolutions.stream().filter(DatasetResolution::created).count();
    }

    private List<Map<String, Object>> evidence(List<DatasetResolution> inputs, List<DatasetResolution> outputs) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (DatasetResolution item : inputs) {
            result.add(evidenceItem(item));
        }
        for (DatasetResolution item : outputs) {
            result.add(evidenceItem(item));
        }
        return result;
    }

    private Map<String, Object> evidenceItem(DatasetResolution item) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        CatalogDataset dataset = item.dataset();
        evidence.put("role", item.role());
        evidence.put("datasetId", dataset != null && dataset.getId() != null ? dataset.getId().toString() : null);
        evidence.put("namespace", item.namespace());
        evidence.put("rawName", item.rawName());
        evidence.put("assetKey", item.assetKey());
        evidence.put("created", item.created());
        evidence.put("resolvedBy", item.resolvedBy());
        evidence.put("warehouseLayer", dataset != null ? dataset.getWarehouseLayer() : null);
        evidence.put("lifecycleStatus", dataset != null ? dataset.getLifecycleStatus() : null);
        evidence.put("governanceStatus", CatalogAssetGovernancePolicy.resolveGovernanceStatus(dataset));
        evidence.put("owner", dataset != null ? dataset.getOwner() : null);
        evidence.put("ownerDept", dataset != null ? dataset.getOwnerDept() : null);
        evidence.put("classification", dataset != null ? dataset.getClassification() : null);
        return evidence;
    }

    private String safeDatasetKey(CatalogDataset dataset) {
        try {
            return CatalogAssetKey.dataset(dataset);
        } catch (IllegalArgumentException ignored) {
            return "dataset:" + (dataset != null && dataset.getId() != null ? dataset.getId() : "unknown");
        }
    }

    private String schemaFacet(Map<String, Object> payload) {
        Map<String, Object> facets = payloadMap(payload.get("facets"));
        Map<String, Object> schema = payloadMap(facets.get("schema"));
        return stringValue(schema.get("schemaName"));
    }

    private String ownerFacet(Map<String, Object> payload) {
        Map<String, Object> facets = payloadMap(payload.get("facets"));
        Map<String, Object> ownership = payloadMap(firstNonNull(facets.get("ownership"), facets.get("owners")));
        Object owners = firstNonNull(ownership.get("owners"), ownership.get("owner"));
        if (owners instanceof List<?> list) {
            for (Object item : list) {
                Map<String, Object> owner = payloadMap(item);
                String value = firstNonBlank(stringValue(owner.get("name")), stringValue(owner.get("owner")), stringValue(owner.get("email")));
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
        }
        return firstNonBlank(stringValue(ownership.get("name")), stringValue(ownership.get("owner")), stringValue(payload.get("owner")));
    }

    private String ownerDeptFacet(Map<String, Object> payload) {
        Map<String, Object> facets = payloadMap(payload.get("facets"));
        Map<String, Object> governance = payloadMap(firstNonNull(facets.get("governance"), facets.get("dtsGovernance")));
        return firstNonBlank(
            stringValue(governance.get("ownerDept")),
            stringValue(governance.get("department")),
            stringValue(payload.get("ownerDept")),
            stringValue(payload.get("department"))
        );
    }

    private String classificationFacet(Map<String, Object> payload) {
        Map<String, Object> facets = payloadMap(payload.get("facets"));
        Map<String, Object> governance = payloadMap(firstNonNull(facets.get("governance"), facets.get("dtsGovernance")));
        Map<String, Object> classification = payloadMap(firstNonNull(facets.get("classification"), facets.get("securityLevel")));
        return firstNonBlank(
            stringValue(classification.get("name")),
            stringValue(classification.get("level")),
            stringValue(classification.get("classification")),
            stringValue(governance.get("classification")),
            stringValue(governance.get("securityLevel")),
            stringValue(payload.get("classification"))
        );
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to calculate OpenLineage classification checksum", ex);
        }
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

    private Object firstNonNull(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null) {
                return value;
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

    private record DatasetName(String namespace, String schema, String table, String rawName) {}

    private record DatasetResolution(
        CatalogDataset dataset,
        String role,
        String namespace,
        String rawName,
        String assetKey,
        boolean created,
        String resolvedBy
    ) {}
}
