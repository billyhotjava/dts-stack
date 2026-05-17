package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.OpenMetadataAssetCache;
import com.yuzhi.dts.platform.domain.explore.QueryDatasetAsset;
import java.util.Locale;
import java.util.UUID;
import org.springframework.util.StringUtils;

public final class CatalogAssetKey {

    private CatalogAssetKey() {}

    public static String dataset(CatalogDataset dataset) {
        if (dataset == null) {
            throw new IllegalArgumentException("dataset is required");
        }
        return dataset(dataset.getSourceId(), dataset.getHiveDatabase(), dataset.getHiveDatabase(), dataset.getHiveTable(), dataset.getName());
    }

    public static String openMetadataDataset(OpenMetadataAssetCache asset) {
        if (asset == null) {
            throw new IllegalArgumentException("openmetadata asset is required");
        }
        return openMetadataDataset(asset.getFqn());
    }

    public static String openMetadataDataset(String fqn) {
        return "om:" + segment(required(fqn, "fqn"));
    }

    public static String dataset(UUID sourceId, String database, String schema, String table, String fallbackName) {
        String scope = sourceId != null ? "source:" + sourceId : "source:unknown";
        String normalizedSchema = firstText(schema, database, "default");
        String normalizedTable = firstText(table, fallbackName);
        if (!StringUtils.hasText(normalizedTable)) {
            throw new IllegalArgumentException("dataset table or name is required");
        }
        return scope + "/schema:" + segment(normalizedSchema) + "/table:" + segment(normalizedTable);
    }

    public static String scopedDataset(
        String tenantNamespace,
        String environment,
        String dialect,
        String sourceFqn,
        String schema,
        String table
    ) {
        return scopePrefix(tenantNamespace, environment, dialect)
            + "/source:" + segment(required(sourceFqn, "source fqn"))
            + "/schema:" + segment(firstText(schema, "default"))
            + "/table:" + segment(required(table, "table"));
    }

    public static String dbtModel(String uniqueId, String relationName) {
        return "dbt:" + segment(firstText(uniqueId, relationName));
    }

    public static String biDataset(QueryDatasetAsset dataset) {
        if (dataset == null || dataset.getId() == null) {
            throw new IllegalArgumentException("query dataset id is required");
        }
        return biDataset(dataset.getId());
    }

    public static String biDataset(UUID id) {
        return "bi-dataset:" + id;
    }

    public static String screen(String id) {
        return "screen:" + segment(required(id, "screen id"));
    }

    public static String metric(String packId, String metricCode) {
        return "metric:" + segment(firstText(packId, "local")) + "/" + segment(required(metricCode, "metric code"));
    }

    public static String metricPack(String tenantNamespace, String packId, String version) {
        return scopePrefix(tenantNamespace, null, null)
            + "/metric-pack:" + segment(required(packId, "pack id"))
            + "/version:" + segment(required(version, "version"));
    }

    public static String semanticModel(String modelIdOrCode) {
        return "semantic-model:" + segment(required(modelIdOrCode, "semantic model id or code"));
    }

    public static String codeAsset(CatalogAssetType type, String tenantNamespace, String naturalKey) {
        if (type == null) {
            throw new IllegalArgumentException("asset type is required");
        }
        return scopePrefix(tenantNamespace, null, null)
            + "/" + segment(type.name().toLowerCase(Locale.ROOT)) + ":" + segment(required(naturalKey, "natural key"));
    }

    private static String scopePrefix(String tenantNamespace, String environment, String dialect) {
        String tenant = segment(firstText(tenantNamespace, "default"));
        String env = segment(firstText(environment, "prod"));
        String sqlDialect = segment(firstText(dialect, "generic"));
        return "tenant:" + tenant + "/env:" + env + "/dialect:" + sqlDialect;
    }

    private static String required(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value;
    }

    private static String firstText(String... values) {
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

    private static String segment(String value) {
        return required(value, "asset key segment")
            .trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9_.:-]+", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_+|_+$", "");
    }
}
