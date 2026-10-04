package com.yuzhi.dts.platform.service.sql;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.explore.QueryDatasetAsset;
import com.yuzhi.dts.platform.domain.explore.QueryDatasetVersion;
import com.yuzhi.dts.platform.domain.explore.ResultSet;
import com.yuzhi.dts.platform.service.catalog.CanonicalModelIdentityReadPort.ModelIdentity;
import com.yuzhi.dts.platform.service.catalog.CatalogConsumerClassificationService.DerivationResult;
import com.yuzhi.dts.platform.service.catalog.CatalogConsumerClassificationService.ResolvedSource;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelSemanticContract.DimensionPayload;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelSemanticContract.JoinPayload;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelSemanticContract.MetricPayload;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelSemanticContract.PublishPayload;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Builds the immutable governed semantic contract persisted with a published query dataset version. */
@Component
public class QueryDatasetContractSnapshotAssembler {

    public static final String CONTRACT_SCHEMA = "dts.query-dataset-contract/v1";

    private static final Set<String> NUMERIC_TYPES = Set.of(
        "int", "integer", "int2", "int4", "int8", "bigint", "smallint", "tinyint", "numeric", "decimal", "number",
        "float", "float4", "float8", "double", "double precision", "real", "money"
    );

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
        return new Snapshot(
            CONTRACT_SCHEMA,
            contractVersion,
            status,
            contractJson,
            QueryDatasetContractChecksum.compute(contractJson, sqlText)
        );
    }

    public Snapshot assemblePublishedModel(
        QueryDatasetAsset asset,
        QueryDatasetVersion version,
        CatalogDataset physical,
        ModelIdentity identity,
        PublishPayload semantic,
        DerivationResult classification
    ) {
        return assemblePublishedModel(asset, version, physical, identity, semantic, List.of(), classification);
    }

    /**
     * Model MEASURE fields without a published atomic indicator are still part of the physical result
     * schema; exposing them keeps the BI field list aligned with what the DWS/ADS model materialized.
     */
    public Snapshot assemblePublishedModel(
        QueryDatasetAsset asset,
        QueryDatasetVersion version,
        CatalogDataset physical,
        ModelIdentity identity,
        PublishPayload semantic,
        List<ModelMeasure> measures,
        DerivationResult classification
    ) {
        List<String> blockers = publishedModelBlockers(asset, physical, identity, semantic, classification);
        String status = blockers.isEmpty() ? "READY" : "UNRESOLVED";
        String contractVersion = semantic != null && StringUtils.hasText(semantic.specVersion())
            ? semantic.specVersion().trim()
            : identity == null ? "unresolved" : identity.contractVersion();
        List<ModelEntry> models = identity == null
            ? List.of()
            : List.of(new ModelEntry(modelReference(semantic, identity), identity));

        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("schema", CONTRACT_SCHEMA);
        contract.put("contractVersion", contractVersion);
        contract.put("datasetId", asset == null ? null : asset.getId());
        contract.put("datasetVersion", version == null ? null : version.getVersionNo());
        contract.put("sourceDatasourceId", asset == null ? null : asset.getSourceDatasourceId());
        contract.put("sourceDatasourceName", asset == null ? null : asset.getSourceDatasourceName());
        contract.put("warehouseLayer", physical == null ? null : upper(physical.getWarehouseLayer()));
        contract.put("bizDomain", physical == null || physical.getDomain() == null ? null : physical.getDomain().getCode());
        contract.put("dimensions", publishedDimensions(semantic));
        contract.put("metrics", publishedMetrics(semantic, measures));
        contract.put("joins", publishedJoins(semantic));
        contract.put("sourceModels", modelContracts(models));
        contract.put("classificationFloor", classification == null ? null : classification.effectiveLevel());
        contract.put("classificationSnapshot", classificationContract(classification));
        contract.put("policyRefs", policyRefs(classification));
        contract.put("blockers", blockers);

        String contractJson = json(contract);
        String sqlText = version == null || version.getSqlText() == null ? "" : version.getSqlText();
        return new Snapshot(
            CONTRACT_SCHEMA,
            contractVersion,
            status,
            contractJson,
            QueryDatasetContractChecksum.compute(contractJson, sqlText)
        );
    }

    private List<String> publishedModelBlockers(
        QueryDatasetAsset asset,
        CatalogDataset physical,
        ModelIdentity identity,
        PublishPayload semantic,
        DerivationResult classification
    ) {
        List<String> blockers = new ArrayList<>();
        if (identity == null) blockers.add("SEMANTIC_SOURCE_REQUIRED");
        if (semantic == null || semantic.dimensions().isEmpty()) blockers.add("RESULT_SCHEMA_REQUIRED");
        if (asset == null || asset.getSourceDatasourceId() == null) blockers.add("SOURCE_DATASOURCE_REQUIRED");
        String layer = physical == null ? null : upper(physical.getWarehouseLayer());
        if (!"DWS".equals(layer) && !"ADS".equals(layer)) blockers.add("PUBLISHED_WAREHOUSE_LAYER_REQUIRED");
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

    private List<Map<String, Object>> publishedDimensions(PublishPayload semantic) {
        if (semantic == null) return List.of();
        return semantic
            .dimensions()
            .stream()
            .filter(dimension -> dimension != null && StringUtils.hasText(dimension.name()))
            .map(dimension -> publishedDimension(dimension, semantic.securityLevel()))
            .toList();
    }

    private Map<String, Object> publishedDimension(DimensionPayload source, String classification) {
        Map<String, Object> dimension = new LinkedHashMap<>();
        dimension.put("code", source.name().trim());
        dimension.put("label", StringUtils.hasText(source.displayName()) ? source.displayName().trim() : source.name().trim());
        dimension.put("dataType", "UNKNOWN");
        dimension.put("timeGrains", StringUtils.hasText(source.timeGrain()) ? List.of(upper(source.timeGrain())) : List.of());
        dimension.put("classification", upper(classification));
        dimension.put("filterOps", List.of("EQ", "NE", "IN", "NOT_IN", "IS_NULL", "IS_NOT_NULL"));
        return dimension;
    }

    private List<Map<String, Object>> publishedMetrics(PublishPayload semantic, List<ModelMeasure> measures) {
        List<Map<String, Object>> metrics = new ArrayList<>();
        if (semantic != null) {
            semantic
                .metrics()
                .stream()
                .filter(metric -> metric != null && StringUtils.hasText(metric.name()))
                .map(this::publishedMetric)
                .forEach(metrics::add);
        }
        Set<String> taken = new HashSet<>();
        metrics.forEach(metric -> {
            taken.add(lower(metric.get("code")));
            taken.add(lower(metric.get("expression")));
        });
        publishedDimensions(semantic).forEach(dimension -> taken.add(lower(dimension.get("code"))));
        if (measures != null) {
            for (ModelMeasure measure : measures) {
                if (measure == null || !StringUtils.hasText(measure.name())) continue;
                if (!taken.add(lower(measure.name()))) continue;
                metrics.add(modelMeasureMetric(measure));
            }
        }
        boolean hasRecordCount = metrics.stream().anyMatch(metric -> "record_count".equals(metric.get("code")));
        if (!hasRecordCount) metrics.addAll(defaultMetrics());
        return List.copyOf(metrics);
    }

    private Map<String, Object> publishedMetric(MetricPayload source) {
        Map<String, Object> metric = new LinkedHashMap<>();
        metric.put("code", source.name().trim());
        metric.put("label", StringUtils.hasText(source.displayName()) ? source.displayName().trim() : source.name().trim());
        metric.put("aggregation", upper(source.aggregation()));
        metric.put("unit", StringUtils.hasText(source.unit()) ? source.unit().trim() : null);
        metric.put("expression", StringUtils.hasText(source.field()) ? source.field().trim() : source.name().trim());
        return metric;
    }

    private static Map<String, Object> modelMeasureMetric(ModelMeasure source) {
        String code = source.name().trim();
        Map<String, Object> metric = new LinkedHashMap<>();
        metric.put("code", code);
        metric.put("label", StringUtils.hasText(source.displayName()) ? source.displayName().trim() : code);
        metric.put("aggregation", isNumeric(source.dataType()) ? "SUM" : "COUNT");
        metric.put("unit", null);
        metric.put("expression", code);
        return metric;
    }

    private static boolean isNumeric(String dataType) {
        String type = lower(dataType);
        int precision = type.indexOf('(');
        return NUMERIC_TYPES.contains(precision < 0 ? type : type.substring(0, precision).trim());
    }

    /** True when a stored contract already exposes every model measure, so it can be reused as current. */
    public boolean coversMeasures(String contractJson, List<ModelMeasure> measures) {
        if (measures == null || measures.isEmpty()) return true;
        if (!StringUtils.hasText(contractJson)) return false;
        Set<String> exposed = new HashSet<>();
        try {
            JsonNode contract = objectMapper.readTree(contractJson);
            for (JsonNode metric : contract.path("metrics")) {
                exposed.add(lower(metric.path("code").asText(null)));
                exposed.add(lower(metric.path("expression").asText(null)));
            }
            for (JsonNode dimension : contract.path("dimensions")) {
                exposed.add(lower(dimension.path("code").asText(null)));
            }
        } catch (JsonProcessingException unreadable) {
            return false;
        }
        return measures
            .stream()
            .filter(measure -> measure != null && StringUtils.hasText(measure.name()))
            .allMatch(measure -> exposed.contains(lower(measure.name())));
    }

    private static String lower(Object value) {
        return value == null ? "" : value.toString().trim().toLowerCase(Locale.ROOT);
    }

    private List<Map<String, Object>> publishedJoins(PublishPayload semantic) {
        if (semantic == null) return List.of();
        return semantic
            .joins()
            .stream()
            .filter(join -> join != null && StringUtils.hasText(join.to()))
            .map(join -> publishedJoin(semantic, join))
            .toList();
    }

    private Map<String, Object> publishedJoin(PublishPayload semantic, JoinPayload source) {
        Map<String, Object> join = new LinkedHashMap<>();
        join.put("fromModel", semantic.modelName());
        join.put("toModel", source.to().trim());
        join.put("relationship", source.relationship());
        join.put("approvalRequired", source.approvalRequired());
        return join;
    }

    private static String modelReference(PublishPayload semantic, ModelIdentity identity) {
        if (semantic != null && StringUtils.hasText(semantic.tableName())) return semantic.tableName().trim();
        return identity.modelName();
    }

    private static String upper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
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

    private record ModelEntry(String reference, ModelIdentity identity) {}

    /** A MEASURE field of the published model revision, as materialized in the physical table. */
    public record ModelMeasure(String name, String displayName, String dataType) {}

    public record Snapshot(String schema, String version, String status, String contractJson, String checksum) {}
}
