package com.yuzhi.dts.platform.service.ingestion;

import com.yuzhi.dts.common.ingestion.ManagedDatabaseLandingPlan;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.governance.GovQualityWorkflowRun;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.governance.GovQualityWorkflowRunRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleBindingRepository;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetKey;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetStatusViewService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetStatusViewService.AssetDeliveryStatus;
import com.yuzhi.dts.platform.service.catalog.dto.AssetRef;
import com.yuzhi.dts.platform.service.governance.QualityDatasetReadGuard;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read-only join from an ingestion task/execution to its canonical catalog,
 * quality-workflow and consumption-eligibility evidence.
 */
@Service
@Transactional(readOnly = true)
public class IngestionFlowProjectionService {

    private static final String DATASET_PREFIX = "dataset:";
    private static final Set<String> FILE_SOURCE_TYPES = Set.of(
        "txtfilereader",
        "excelreader",
        "csv",
        "excel",
        "file"
    );

    private final CatalogDatasetRepository datasetRepository;
    private final GovRuleBindingRepository bindingRepository;
    private final GovQualityWorkflowRunRepository workflowRepository;
    private final CatalogAssetStatusViewService assetStatusViewService;
    private final QualityDatasetReadGuard datasetReadGuard;

    public IngestionFlowProjectionService(
        CatalogDatasetRepository datasetRepository,
        GovRuleBindingRepository bindingRepository,
        GovQualityWorkflowRunRepository workflowRepository,
        CatalogAssetStatusViewService assetStatusViewService,
        QualityDatasetReadGuard datasetReadGuard
    ) {
        this.datasetRepository = datasetRepository;
        this.bindingRepository = bindingRepository;
        this.workflowRepository = workflowRepository;
        this.assetStatusViewService = assetStatusViewService;
        this.datasetReadGuard = datasetReadGuard;
    }

    public Map<String, Object> enrichDesign(Map<String, Object> design, String activeDeptHeader) {
        Map<String, Object> result = copy(design);
        UUID datasetId = designDatasetId(result);
        if (datasetId == null) {
            result.put("assetProjection", Map.of(
                "resolutionState", "UNRESOLVED",
                "qualityBindingCount", 0,
                "qualityConfigured", false
            ));
            return result;
        }
        CatalogDataset dataset = requireReadableDataset(datasetId, activeDeptHeader);
        result.put("assetProjection", assetProjection(dataset));
        return result;
    }

    public Map<String, Object> enrichExecution(Map<String, Object> execution, String activeDeptHeader) {
        return enrichExecution(execution, activeDeptHeader, text(execution == null ? null : execution.get("id")));
    }

    public Map<String, Object> enrichExecution(
        Map<String, Object> execution,
        String activeDeptHeader,
        String latestExecutionId
    ) {
        return enrichExecution(
            execution,
            activeDeptHeader,
            latestExecutionId,
            new LinkedHashMap<>(),
            new LinkedHashMap<>()
        );
    }

    public Map<String, Object> enrichExecutionPage(
        Map<String, Object> page,
        String activeDeptHeader,
        String latestExecutionId
    ) {
        Map<String, Object> result = copy(page);
        Object rawContent = result.get("content");
        if (!(rawContent instanceof Iterable<?> content)) {
            return result;
        }
        List<Map<String, Object>> executions = new java.util.ArrayList<>();
        for (Object value : content) {
            executions.add(map(value));
        }
        Set<UUID> datasetIds = new LinkedHashSet<>();
        Set<UUID> workflowIds = new LinkedHashSet<>();
        for (Map<String, Object> execution : executions) {
            UUID datasetId = executionTargetDatasetId(execution);
            if (datasetId != null) {
                datasetIds.add(datasetId);
            }
            UUID workflowId = uuid(execution.get("qualityWorkflowId"));
            if (workflowId != null) {
                workflowIds.add(workflowId);
            }
        }
        Map<UUID, AssetProjection> assetCache = loadAssetProjections(datasetIds, activeDeptHeader);
        Map<UUID, Optional<GovQualityWorkflowRun>> workflowCache = loadWorkflows(workflowIds);
        List<Map<String, Object>> projected = new java.util.ArrayList<>();
        for (Map<String, Object> execution : executions) {
            projected.add(
                enrichExecution(execution, activeDeptHeader, latestExecutionId, assetCache, workflowCache)
            );
        }
        result.put("content", projected);
        return result;
    }

    private Map<String, Object> enrichExecution(
        Map<String, Object> execution,
        String activeDeptHeader,
        String latestExecutionId,
        Map<UUID, AssetProjection> assetCache,
        Map<UUID, Optional<GovQualityWorkflowRun>> workflowCache
    ) {
        Map<String, Object> result = copy(execution);
        UUID qualityDatasetId = parseDatasetId(text(result.get("qualityPolicyRef")));
        UUID targetDatasetId = executionTargetDatasetId(result);
        if (targetDatasetId == null) {
            result.put("qualityEvidence", missingEvidence("TARGET_ASSET_UNRESOLVED"));
            return result;
        }

        AssetProjection asset = assetCache.computeIfAbsent(
            targetDatasetId,
            id -> assetProjection(requireReadableDataset(id, activeDeptHeader))
        );
        if (qualityDatasetId == null) {
            result.put("qualityEvidence", evidence(
                targetDatasetId,
                asset,
                null,
                null,
                "NOT_CONFIGURED",
                "NOT_CONFIGURED",
                false,
                List.of("POST_INGESTION_QUALITY_DISABLED")
            ));
            return result;
        }
        if (!targetDatasetId.equals(qualityDatasetId)) {
            result.put("qualityEvidence", evidence(
                targetDatasetId,
                asset,
                null,
                null,
                "STALE",
                "UNKNOWN",
                false,
                List.of("QUALITY_ASSET_MISMATCH")
            ));
            return result;
        }

        String executionDbId = text(result.get("id"));
        UUID workflowId = uuid(result.get("qualityWorkflowId"));
        GovQualityWorkflowRun workflow = workflowId == null
            ? null
            : workflowCache.computeIfAbsent(workflowId, workflowRepository::findById).orElse(null);

        String evidenceState;
        if (workflow == null) {
            evidenceState = triggerEvidenceState(text(execution.get("qualityWorkflowStatus")));
        } else if (
            targetDatasetId.equals(workflow.getDatasetId())
                && StringUtils.hasText(executionDbId)
                && executionDbId.equals(latestExecutionId)
                && ("ingestion:" + executionDbId).equals(workflow.getTriggerRef())
        ) {
            evidenceState = "CURRENT";
        } else {
            evidenceState = "STALE";
        }

        String qualityStatus = workflow == null
            ? qualityStatus(text(result.get("qualityWorkflowStatus")))
            : qualityStatus(workflow.getStatus());
        boolean trustedUsable = "CURRENT".equals(evidenceState)
            && "PASSED".equals(qualityStatus)
            && "ELIGIBLE".equals(asset.consumptionEligibility());
        result.put("qualityEvidence", evidence(
            targetDatasetId,
            asset,
            workflowId,
            workflow == null ? null : workflow.getTriggerRef(),
            evidenceState,
            qualityStatus,
            trustedUsable,
            asset.eligibilityReasons()
        ));
        return result;
    }

    /**
     * Platform-owned validation for references that the ingestion service cannot
     * safely resolve: catalog identity, physical target equality, department and
     * classification visibility, and published quality bindings.
     */
    public AssetProjection validateDesignReferences(Map<String, Object> design, String activeDeptHeader) {
        Map<String, Object> payload = copy(design);
        boolean qualityEnabled = booleanValue(payload.get("postIngestionQualityEnabled"));
        String policyRef = text(payload.get("qualityPolicyRef"));
        if (!payload.containsKey("postIngestionQualityEnabled")) {
            qualityEnabled = StringUtils.hasText(policyRef);
        }
        UUID policyDatasetId = parseDatasetId(policyRef);
        UUID destinationDatasetId = destinationDatasetId(payload);
        if (destinationDatasetId == null) {
            // Initial physical landing precedes catalog observation. A complete
            // managed plan can run without claiming asset or quality readiness.
            if (!qualityEnabled && !StringUtils.hasText(policyRef) && !hasExplicitTargetAsset(payload)
                && (isNewManagedFileTarget(payload) || isManagedDatabaseTarget(payload))) {
                return unresolvedAssetProjection();
            }
            throw unprocessable("TARGET_ASSET_UNRESOLVED");
        }
        if (qualityEnabled && policyDatasetId == null) {
            throw unprocessable("TARGET_ASSET_UNRESOLVED");
        }
        if (policyDatasetId != null && !policyDatasetId.equals(destinationDatasetId)) {
            throw unprocessable("QUALITY_ASSET_MISMATCH");
        }
        CatalogDataset dataset = requireReadableDataset(destinationDatasetId, activeDeptHeader);
        if (!Boolean.TRUE.equals(dataset.getEnabled())) {
            throw unprocessable("TARGET_ASSET_UNRESOLVED");
        }
        requirePhysicalTargetMatch(payload, dataset);
        AssetProjection asset = assetProjection(dataset);
        if (qualityEnabled && asset.qualityBindingCount() <= 0) {
            throw unprocessable("QUALITY_BINDING_UNAVAILABLE");
        }
        return asset;
    }

    private boolean isNewManagedFileTarget(Map<String, Object> design) {
        String sourceType = text(design.get("sourceType"));
        Map<String, Object> source = map(design.get("source"));
        if (!StringUtils.hasText(sourceType)) {
            sourceType = text(source.get("type"));
        }
        if (!StringUtils.hasText(sourceType) || !FILE_SOURCE_TYPES.contains(sourceType.toLowerCase(Locale.ROOT))) {
            return false;
        }

        Map<String, Object> sourceConfig = map(design.get("sourceConfig"));
        if (sourceConfig.isEmpty()) {
            sourceConfig = map(source.get("config"));
        }
        Map<String, Object> landing = map(sourceConfig.get("_fileLanding"));
        if (!"create_new".equalsIgnoreCase(text(landing.get("landingMode"))) || !StringUtils.hasText(text(landing.get("targetTable")))) {
            return false;
        }

        Map<String, Object> destinationConfig = map(design.get("destinationConfig"));
        if (destinationConfig.isEmpty()) {
            destinationConfig = map(map(design.get("destination")).get("config"));
        }
        return StringUtils.hasText(text(destinationConfig.get("targetDataSourceId")));
    }

    private boolean isManagedDatabaseTarget(Map<String, Object> design) {
        Map<String, Object> source = map(design.get("source"));
        Map<String, Object> destination = map(design.get("destination"));
        Map<String, Object> config = map(design.get("destinationConfig"));
        if (config.isEmpty()) config = map(destination.get("config"));
        List<String> sources = new java.util.ArrayList<>();
        List<String> targets = new java.util.ArrayList<>();
        if (design.get("tableMapping") instanceof Iterable<?> mappings) {
            for (Object entry : mappings) {
                Map<String, Object> mapping = map(entry);
                sources.add(text(mapping.get("source")));
                targets.add(text(mapping.get("target")));
            }
        }
        return ManagedDatabaseLandingPlan.isConfigured(
            text(design.getOrDefault("sourceType", source.get("type"))),
            text(design.getOrDefault("sourceDataSourceId", source.get("dataSourceId"))),
            text(design.getOrDefault("destinationType", destination.get("type"))),
            text(config.get("targetDataSourceId")), text(design.get("syncMode")), sources, targets
        );
    }

    private boolean hasExplicitTargetAsset(Map<String, Object> design) {
        Map<String, Object> destination = map(design.get("destination"));
        Map<String, Object> config = map(design.get("destinationConfig"));
        if (config.isEmpty()) config = map(destination.get("config"));
        return StringUtils.hasText(text(design.get("targetDatasetId")))
            || StringUtils.hasText(text(map(destination.get("assetRef")).get("datasetId")))
            || StringUtils.hasText(text(map(config.get("assetRef")).get("datasetId")));
    }

    private AssetProjection unresolvedAssetProjection() {
        return new AssetProjection(
            "UNRESOLVED",
            null,
            null,
            null,
            false,
            null,
            0,
            false,
            "CONDITIONAL",
            List.of("TARGET_ASSET_PENDING_OBSERVATION"),
            "UNKNOWN"
        );
    }

    private CatalogDataset requireReadableDataset(UUID datasetId, String activeDeptHeader) {
        if (datasetRepository.findById(datasetId).isEmpty()) {
            throw new IllegalArgumentException("目标数据资产不存在");
        }
        return datasetReadGuard.requireReadable(datasetId, activeDeptHeader);
    }

    private AssetProjection assetProjection(CatalogDataset dataset) {
        String assetKey = CatalogAssetKey.dataset(dataset);
        AssetRef ref = new AssetRef("DATASET", assetKey);
        AssetDeliveryStatus status = assetStatusViewService.read(List.of(ref)).get(ref);
        List<?> bindings = bindingRepository.findWorkflowBindings(dataset.getId(), "PUBLISHED");
        int bindingCount = bindings == null ? 0 : bindings.size();
        return assetProjection(dataset, status, bindingCount);
    }

    private AssetProjection assetProjection(
        CatalogDataset dataset,
        AssetDeliveryStatus status,
        int bindingCount
    ) {
        String assetKey = CatalogAssetKey.dataset(dataset);
        return new AssetProjection(
            "RESOLVED",
            dataset.getId().toString(),
            dataset.getName(),
            assetKey,
            Boolean.TRUE.equals(dataset.getEnabled()),
            dataset.getLifecycleStatus(),
            bindingCount,
            bindingCount > 0,
            status == null ? "CONDITIONAL" : status.consumptionEligibility(),
            status == null ? List.of("ASSET_STATUS_MISSING") : status.eligibilityReasons(),
            status == null ? "UNKNOWN" : status.qualityStatus()
        );
    }

    private Map<UUID, AssetProjection> loadAssetProjections(
        Set<UUID> datasetIds,
        String activeDeptHeader
    ) {
        Map<UUID, AssetProjection> result = new LinkedHashMap<>();
        if (datasetIds.isEmpty()) {
            return result;
        }
        List<CatalogDataset> datasets = datasetRepository.findAllById(datasetIds);
        Map<UUID, CatalogDataset> byId = new LinkedHashMap<>();
        datasets.forEach(dataset -> byId.put(dataset.getId(), dataset));
        if (!byId.keySet().containsAll(datasetIds)) {
            throw new IllegalArgumentException("目标数据资产不存在");
        }
        Set<UUID> readableIds = datasetReadGuard.readableDatasetIds(datasets, activeDeptHeader);
        if (!readableIds.containsAll(datasetIds)) {
            throw new AccessDeniedException("当前账号无权访问该数据集");
        }

        List<AssetRef> refs = datasets.stream()
            .map(dataset -> new AssetRef("DATASET", CatalogAssetKey.dataset(dataset)))
            .toList();
        Map<AssetRef, AssetDeliveryStatus> statuses = assetStatusViewService.read(refs);
        if (statuses == null) {
            statuses = Map.of();
        }
        Map<UUID, Integer> bindingCounts = new LinkedHashMap<>();
        List<Object[]> counts = bindingRepository.countWorkflowBindingsForDatasets(datasetIds, "PUBLISHED");
        if (counts != null) {
            for (Object[] row : counts) {
                if (row != null && row.length >= 2 && row[0] instanceof UUID datasetId && row[1] instanceof Number count) {
                    bindingCounts.put(datasetId, count.intValue());
                }
            }
        }
        for (CatalogDataset dataset : datasets) {
            AssetRef ref = new AssetRef("DATASET", CatalogAssetKey.dataset(dataset));
            result.put(
                dataset.getId(),
                assetProjection(dataset, statuses.get(ref), bindingCounts.getOrDefault(dataset.getId(), 0))
            );
        }
        return result;
    }

    private Map<UUID, Optional<GovQualityWorkflowRun>> loadWorkflows(Set<UUID> workflowIds) {
        Map<UUID, Optional<GovQualityWorkflowRun>> result = new LinkedHashMap<>();
        workflowIds.forEach(workflowId -> result.put(workflowId, Optional.empty()));
        if (!workflowIds.isEmpty()) {
            workflowRepository.findAllById(workflowIds)
                .forEach(workflow -> result.put(workflow.getId(), Optional.of(workflow)));
        }
        return result;
    }

    private UUID executionTargetDatasetId(Map<String, Object> execution) {
        UUID explicit = uuid(execution.get("targetDatasetId"));
        return explicit == null
            ? parseDatasetId(text(execution.get("qualityPolicyRef")))
            : explicit;
    }

    private UUID designDatasetId(Map<String, Object> design) {
        Map<String, Object> destination = map(design.get("destination"));
        Map<String, Object> assetRef = map(destination.get("assetRef"));
        UUID direct = uuid(assetRef.get("datasetId"));
        if (direct != null) {
            return direct;
        }
        Map<String, Object> quality = map(design.get("postIngestionQuality"));
        return parseDatasetId(text(quality.get("policyRef")));
    }

    private UUID destinationDatasetId(Map<String, Object> design) {
        UUID direct = uuid(design.get("targetDatasetId"));
        if (direct != null) {
            return direct;
        }
        Map<String, Object> destination = map(design.get("destination"));
        Map<String, Object> config = map(design.get("destinationConfig"));
        if (config.isEmpty()) {
            config = map(destination.get("config"));
        }
        Map<String, Object> assetRef = map(destination.get("assetRef"));
        if (assetRef.isEmpty()) {
            assetRef = map(config.get("assetRef"));
        }
        return uuid(assetRef.get("datasetId"));
    }

    private Map<String, Object> evidence(
        UUID datasetId,
        AssetProjection asset,
        UUID workflowId,
        String triggerRef,
        String evidenceState,
        String qualityStatus,
        boolean trustedUsable,
        List<String> eligibilityReasons
    ) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("datasetId", datasetId.toString());
        evidence.put("assetKey", asset.assetKey());
        evidence.put("assetName", asset.assetName());
        evidence.put("qualityBindingCount", asset.qualityBindingCount());
        evidence.put("qualityConfigured", asset.qualityConfigured());
        if (workflowId != null) {
            evidence.put("workflowId", workflowId.toString());
        }
        if (StringUtils.hasText(triggerRef)) {
            evidence.put("triggerRef", triggerRef);
        }
        evidence.put("evidenceState", evidenceState);
        evidence.put("qualityStatus", qualityStatus);
        evidence.put("consumptionEligibility", asset.consumptionEligibility());
        evidence.put("eligibilityReasons", eligibilityReasons == null ? List.of() : eligibilityReasons);
        evidence.put("assetQualityStatus", asset.assetQualityStatus());
        evidence.put("trustedUsable", trustedUsable);
        return evidence;
    }

    private void requirePhysicalTargetMatch(Map<String, Object> design, CatalogDataset dataset) {
        if (!StringUtils.hasText(dataset.getHiveTable())) {
            throw unprocessable("TARGET_ASSET_UNRESOLVED");
        }
        Set<String> targets = targetTables(design);
        if (targets.isEmpty()) {
            return;
        }
        String catalogTarget = normalizeObjectName(dataset.getHiveTable());
        if (targets.size() != 1 || !targets.contains(catalogTarget)) {
            throw unprocessable("QUALITY_ASSET_MISMATCH");
        }
    }

    private Set<String> targetTables(Map<String, Object> design) {
        Set<String> result = new LinkedHashSet<>();
        Object mappings = design.get("tableMapping");
        if (mappings instanceof Iterable<?> values) {
            for (Object value : values) {
                String target = text(map(value).get("target"));
                if (StringUtils.hasText(target)) {
                    result.add(normalizeObjectName(target));
                }
            }
        }
        Map<String, Object> destination = map(design.get("destinationConfig"));
        if (destination.isEmpty()) {
            destination = map(map(design.get("destination")).get("config"));
        }
        for (String key : List.of("targetTable", "tableName", "table")) {
            Object value = destination.get(key);
            if (value instanceof Iterable<?> values) {
                for (Object item : values) {
                    if (StringUtils.hasText(text(item))) {
                        result.add(normalizeObjectName(text(item)));
                    }
                }
            } else if (StringUtils.hasText(text(value))) {
                result.add(normalizeObjectName(text(value)));
            }
        }
        result.remove("");
        return result;
    }

    private String normalizeObjectName(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim().replace("`", "").replace("\"", "");
        int separator = normalized.lastIndexOf('.');
        return (separator >= 0 ? normalized.substring(separator + 1) : normalized).toLowerCase(Locale.ROOT);
    }

    private boolean booleanValue(Object value) {
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(text(value));
    }

    private ResponseStatusException unprocessable(String code) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, code);
    }

    private Map<String, Object> missingEvidence(String state) {
        return Map.of(
            "evidenceState", state,
            "qualityStatus", "UNKNOWN",
            "consumptionEligibility", "CONDITIONAL",
            "eligibilityReasons", List.of(state),
            "trustedUsable", false
        );
    }

    private String triggerEvidenceState(String workflowStatus) {
        String normalized = normalize(workflowStatus);
        return switch (normalized) {
            case "PENDING", "TRIGGERING", "RETRY_WAIT", "QUEUED", "RUNNING" -> "PENDING";
            case "EXHAUSTED", "TRIGGER_FAILED" -> "TRIGGER_FAILED";
            default -> "MISSING";
        };
    }

    private String qualityStatus(String workflowStatus) {
        return switch (normalize(workflowStatus)) {
            case "PASSED", "SUCCEEDED", "SUCCESS" -> "PASSED";
            case "FAILED", "BLOCKED", "CANCELLED", "EXHAUSTED" -> "FAILED";
            case "PENDING", "TRIGGERING", "RETRY_WAIT", "QUEUED", "RUNNING" -> "RUNNING";
            default -> "UNKNOWN";
        };
    }

    private UUID parseDatasetId(String policyRef) {
        if (!StringUtils.hasText(policyRef) || !policyRef.trim().startsWith(DATASET_PREFIX)) {
            return null;
        }
        return uuid(policyRef.trim().substring(DATASET_PREFIX.length()));
    }

    private UUID uuid(Object value) {
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(value).trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "UNKNOWN";
    }

    private Map<String, Object> copy(Map<String, Object> source) {
        return source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
    }

    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, nested) -> result.put(String.valueOf(key), nested));
        return result;
    }

    public record AssetProjection(
        String resolutionState,
        String datasetId,
        String assetName,
        String assetKey,
        boolean enabled,
        String lifecycleStatus,
        int qualityBindingCount,
        boolean qualityConfigured,
        String consumptionEligibility,
        List<String> eligibilityReasons,
        String assetQualityStatus
    ) {}
}
