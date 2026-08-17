package com.yuzhi.dts.platform.service.sql;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.explore.QueryDatasetAsset;
import com.yuzhi.dts.platform.domain.explore.QueryDatasetVersion;
import com.yuzhi.dts.platform.domain.explore.ResultSet;
import com.yuzhi.dts.platform.service.catalog.CanonicalModelIdentityReadPort.ModelIdentity;
import com.yuzhi.dts.platform.service.catalog.CatalogConsumerClassificationService.DerivationResult;
import com.yuzhi.dts.platform.service.catalog.CatalogConsumerClassificationService.ResolvedSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Builds the immutable governed semantic contract persisted with a published query dataset version. */
@Component
public class QueryDatasetContractSnapshotAssembler {

    public static final String CONTRACT_SCHEMA = "dts.query-dataset-contract/v1";

    private final ObjectMapper objectMapper;

    public QueryDatasetContractSnapshotAssembler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Snapshot assemble(
        QueryDatasetAsset asset,
        QueryDatasetVersion version,
        ResultSet resultSet,
        Map<String, ModelIdentity> sourceModels,
        DerivationResult classification
    ) {
        List<ModelEntry> models = orderedModels(sourceModels);
        List<String> dimensions = columns(resultSet);
        List<String> blockers = blockers(models, dimensions, classification);
        String status = blockers.isEmpty() ? "READY" : "UNRESOLVED";
        String contractVersion = models.isEmpty()
            ? "unresolved"
            : "r" + models.stream().mapToInt(entry -> entry.identity().modelRevision()).max().orElse(0);

        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("schema", CONTRACT_SCHEMA);
        contract.put("contractVersion", contractVersion);
        contract.put("datasetId", asset == null ? null : asset.getId());
        contract.put("datasetVersion", version == null ? null : version.getVersionNo());
        contract.put("sourceDatasourceId", asset == null ? null : asset.getSourceDatasourceId());
        contract.put("sourceDatasourceName", asset == null ? null : asset.getSourceDatasourceName());
        contract.put("dimensions", dimensionContracts(dimensions));
        contract.put("metrics", defaultMetrics());
        contract.put("joins", List.of());
        contract.put("sourceModels", modelContracts(models));
        contract.put("classificationFloor", classification == null ? null : classification.effectiveLevel());
        contract.put("classificationSnapshot", classificationContract(classification));
        contract.put("policyRefs", policyRefs(classification));
        contract.put("blockers", blockers);

        String contractJson = json(contract);
        String sqlText = version == null || version.getSqlText() == null ? "" : version.getSqlText();
        return new Snapshot(CONTRACT_SCHEMA, contractVersion, status, contractJson, sha256(contractJson + "\n" + sqlText));
    }

    private List<ModelEntry> orderedModels(Map<String, ModelIdentity> sourceModels) {
        if (sourceModels == null || sourceModels.isEmpty()) return List.of();
        return sourceModels
            .entrySet()
            .stream()
            .filter(entry -> StringUtils.hasText(entry.getKey()) && entry.getValue() != null)
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> new ModelEntry(entry.getKey().trim(), entry.getValue()))
            .toList();
    }

    private List<String> columns(ResultSet resultSet) {
        if (resultSet == null || !StringUtils.hasText(resultSet.getColumns())) return List.of();
        List<String> result = new ArrayList<>();
        for (String column : resultSet.getColumns().split(",")) {
            if (StringUtils.hasText(column)) result.add(column.trim());
        }
        return List.copyOf(result);
    }

    private List<String> blockers(
        List<ModelEntry> models,
        List<String> dimensions,
        DerivationResult classification
    ) {
        List<String> blockers = new ArrayList<>();
        if (models.isEmpty()) blockers.add("SEMANTIC_SOURCE_REQUIRED");
        if (dimensions.isEmpty()) blockers.add("RESULT_SCHEMA_REQUIRED");
        if (
            classification == null ||
            !StringUtils.hasText(classification.effectiveLevel()) ||
            classification.snapshotId() == null ||
            classification.snapshotVersion() < 1
        ) {
            blockers.add("CLASSIFICATION_SNAPSHOT_REQUIRED");
        } else if (classification.blockers() != null) {
            classification
                .blockers()
                .stream()
                .filter(StringUtils::hasText)
                .sorted()
                .forEach(blockers::add);
        }
        return List.copyOf(blockers);
    }

    private List<Map<String, Object>> dimensionContracts(List<String> dimensions) {
        return dimensions
            .stream()
            .map(column -> {
                Map<String, Object> dimension = new LinkedHashMap<>();
                dimension.put("code", column);
                dimension.put("label", column);
                dimension.put("dataType", "UNKNOWN");
                dimension.put("timeGrains", List.of());
                dimension.put("classification", null);
                dimension.put("filterOps", List.of("EQ", "NE", "IN", "NOT_IN", "IS_NULL", "IS_NOT_NULL"));
                return dimension;
            })
            .toList();
    }

    private List<Map<String, Object>> defaultMetrics() {
        Map<String, Object> recordCount = new LinkedHashMap<>();
        recordCount.put("code", "record_count");
        recordCount.put("label", "记录数");
        recordCount.put("aggregation", "COUNT");
        recordCount.put("unit", null);
        recordCount.put("expression", "*");
        return List.of(recordCount);
    }

    private List<Map<String, Object>> modelContracts(List<ModelEntry> models) {
        return models
            .stream()
            .map(entry -> {
                ModelIdentity model = entry.identity();
                Map<String, Object> contract = new LinkedHashMap<>();
                contract.put("reference", entry.reference());
                contract.put("assetKey", model.assetKey());
                contract.put("modelSpecId", model.modelSpecId());
                contract.put("implementationId", model.implementationId());
                contract.put("modelName", model.modelName());
                contract.put("modelRevision", model.modelRevision());
                contract.put("dbtUniqueId", model.dbtUniqueId());
                return contract;
            })
            .toList();
    }

    private Map<String, Object> classificationContract(DerivationResult classification) {
        if (classification == null) return null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("snapshotId", classification.snapshotId());
        result.put("snapshotVersion", classification.snapshotVersion());
        result.put("effectiveLevel", classification.effectiveLevel());
        result.put("manualFloor", classification.manualFloor());
        result.put("sources", classificationSources(classification.upstreams()));
        return result;
    }

    private List<Map<String, Object>> classificationSources(List<ResolvedSource> sources) {
        if (sources == null || sources.isEmpty()) return List.of();
        return sources
            .stream()
            .sorted((left, right) -> (left.subjectType() + ":" + left.subjectKey()).compareTo(right.subjectType() + ":" + right.subjectKey()))
            .map(source -> {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("subjectType", source.subjectType());
                result.put("subjectKey", source.subjectKey());
                result.put("snapshotId", source.snapshotId());
                result.put("snapshotVersion", source.snapshotVersion());
                result.put("effectiveLevel", source.effectiveLevel());
                return result;
            })
            .toList();
    }

    private List<String> policyRefs(DerivationResult classification) {
        if (classification == null || classification.upstreams() == null) return List.of();
        return classification
            .upstreams()
            .stream()
            .filter(source -> source.snapshotId() != null)
            .map(source -> source.snapshotId().toString())
            .distinct()
            .sorted()
            .toList();
    }

    private String json(Map<String, Object> contract) {
        try {
            return objectMapper.writeValueAsString(contract);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Unable to serialize query dataset semantic contract", failure);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    private record ModelEntry(String reference, ModelIdentity identity) {}

    public record Snapshot(String schema, String version, String status, String contractJson, String checksum) {}
}
