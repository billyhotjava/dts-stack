package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.service.catalog.dto.AssetRef;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Read-only adapter over the existing business owners. It deliberately does not create another
 * asset master table or copy lifecycle state into the catalog.
 */
@Service
public class CatalogAssetDirectoryReadAdapter {

    private static final int MAX_OWNER_SCAN_ROWS = 5_001;

    private final JdbcTemplate jdbcTemplate;

    public CatalogAssetDirectoryReadAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<OwnerAsset> load(Set<CatalogAssetType> requestedTypes) {
        if (requestedTypes == null || requestedTypes.isEmpty()) {
            return List.of();
        }
        List<OwnerAsset> assets = new ArrayList<>();
        if (requestedTypes.contains(CatalogAssetType.SEMANTIC_MODEL)) {
            assets.addAll(loadSemanticModels(remainingRows(assets)));
        }
        if (requestedTypes.contains(CatalogAssetType.GOV_INDICATOR) && hasRemainingRows(assets)) {
            assets.addAll(loadIndicators(remainingRows(assets)));
        }
        if (requestedTypes.contains(CatalogAssetType.BI_DATASET) && hasRemainingRows(assets)) {
            assets.addAll(loadBiDatasets(remainingRows(assets)));
        }
        if (requestedTypes.contains(CatalogAssetType.SCREEN) && hasRemainingRows(assets)) {
            assets.addAll(loadScreens(remainingRows(assets)));
        }
        if (requestedTypes.contains(CatalogAssetType.DATA_PRODUCT) && hasRemainingRows(assets)) {
            assets.addAll(loadDataProducts(remainingRows(assets)));
        }
        if (requestedTypes.contains(CatalogAssetType.API_SERVICE) && hasRemainingRows(assets)) {
            assets.addAll(loadApiServices(remainingRows(assets)));
        }
        return List.copyOf(assets);
    }

    private boolean hasRemainingRows(List<OwnerAsset> assets) {
        return assets.size() < MAX_OWNER_SCAN_ROWS;
    }

    private int remainingRows(List<OwnerAsset> assets) {
        return MAX_OWNER_SCAN_ROWS - assets.size();
    }

    public Map<AssetRef, List<AssetRelationship>> loadRelationships(List<AssetRef> requestedAssets) {
        LinkedHashSet<AssetRef> requested = new LinkedHashSet<>(requestedAssets == null ? List.of() : requestedAssets);
        if (requested.isEmpty()) {
            return Map.of();
        }
        Map<AssetRef, List<AssetRelationship>> result = new LinkedHashMap<>();
        requested.forEach(asset -> result.put(asset, new ArrayList<>()));
        Map<CatalogAssetType, Set<String>> keysByType = groupKeys(requested);
        hydrateModelDatasetRelations(result, keysByType);
        hydrateBiDatasetModelRelations(result, keysByType);
        hydrateScreenDatasetRelations(result, keysByType);
        hydrateApiDatasetRelations(result, keysByType);
        hydrateIndicatorDatasetRelations(result, keysByType);
        Map<AssetRef, List<AssetRelationship>> immutable = new LinkedHashMap<>();
        result.forEach((asset, relations) -> immutable.put(asset, List.copyOf(new LinkedHashSet<>(relations))));
        return Collections.unmodifiableMap(immutable);
    }

    private List<OwnerAsset> loadSemanticModels(int maxRows) {
        return jdbcTemplate.query(
            """
            select id, name, description, model_type, coalesce(subject_domain_id, domain_id) as domain_id,
                   coalesce(warehouse_layer_code, layer) as warehouse_layer, status, last_modified_date
              from modeling_model_spec
             where upper(coalesce(status, '')) <> 'ARCHIVED'
             order by last_modified_date desc nulls last, name, id
             limit ?
            """,
            (rs, rowNum) -> new OwnerAsset(
                uuid(rs, "id"),
                CatalogAssetType.SEMANTIC_MODEL,
                CatalogAssetKey.semanticModel(rs.getString("id")),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("model_type"),
                "数据建模",
                uuid(rs, "domain_id"),
                null,
                rs.getString("warehouse_layer"),
                null,
                null,
                rs.getString("status"),
                governanceStatus(rs.getString("status")),
                "UNKNOWN",
                instant(rs, "last_modified_date"),
                "/modeling/models/" + rs.getString("id")
            ),
            maxRows
        );
    }

    private List<OwnerAsset> loadIndicators(int maxRows) {
        return jdbcTemplate.query(
            """
            select id, code, name, definition, metric_type, data_domain_id, data_level, source_layer,
                   owner, owner_dept, status, last_validation_status, last_modified_date
              from gov_indicator_definition
             where code is not null and btrim(code) <> ''
             order by last_modified_date desc nulls last, name, id
             limit ?
            """,
            (rs, rowNum) -> new OwnerAsset(
                uuid(rs, "id"),
                CatalogAssetType.GOV_INDICATOR,
                CatalogAssetKey.codeAsset(CatalogAssetType.GOV_INDICATOR, "default", rs.getString("code")),
                rs.getString("name"),
                rs.getString("definition"),
                rs.getString("metric_type"),
                "指标管理",
                uuid(rs, "data_domain_id"),
                normalizeClassification(rs.getString("data_level")),
                rs.getString("source_layer"),
                rs.getString("owner"),
                rs.getString("owner_dept"),
                rs.getString("status"),
                governanceStatus(rs.getString("status")),
                reliableValidationStatus(rs.getString("last_validation_status")),
                instant(rs, "last_modified_date"),
                "/governance/indicators/dictionary"
            ),
            maxRows
        );
    }

    private List<OwnerAsset> loadBiDatasets(int maxRows) {
        return jdbcTemplate.query(
            """
            select id, name, description, source_datasource_name, owner_dept, status, last_modified_date
              from query_dataset_asset
             where enabled = true
             order by last_modified_date desc nulls last, name, id
             limit ?
            """,
            (rs, rowNum) -> new OwnerAsset(
                uuid(rs, "id"),
                CatalogAssetType.BI_DATASET,
                CatalogAssetKey.biDataset(uuid(rs, "id")),
                rs.getString("name"),
                rs.getString("description"),
                "ANALYTIC_DATASET",
                rs.getString("source_datasource_name"),
                null,
                null,
                null,
                null,
                rs.getString("owner_dept"),
                rs.getString("status"),
                governanceStatus(rs.getString("status")),
                "UNKNOWN",
                instant(rs, "last_modified_date"),
                "/bi/virtual-datasets/" + rs.getString("id")
            ),
            maxRows
        );
    }

    private List<OwnerAsset> loadScreens(int maxRows) {
        return jdbcTemplate.query(
            """
            select id, code, title, report_type, dept_codes, classification, enabled, url, last_modified_date
              from bi_report_link
             where upper(coalesce(report_type, '')) = 'SCREEN'
               and lower(code) like 'screen-%'
               and enabled = true
             order by last_modified_date desc nulls last, title, id
             limit ?
            """,
            (rs, rowNum) -> {
                String screenId = screenId(rs.getString("code"));
                return new OwnerAsset(
                    uuid(rs, "id"),
                    CatalogAssetType.SCREEN,
                    CatalogAssetKey.screen(screenId),
                    rs.getString("title"),
                    null,
                    rs.getString("report_type"),
                    "商业智能应用",
                    null,
                    normalizeClassification(rs.getString("classification")),
                    null,
                    null,
                    rs.getString("dept_codes"),
                    rs.getBoolean("enabled") ? "ONLINE" : "OFFLINE",
                    rs.getBoolean("enabled") ? "GOVERNED" : "INCOMPLETE",
                    "UNKNOWN",
                    instant(rs, "last_modified_date"),
                    StringUtils.hasText(rs.getString("url")) ? rs.getString("url") : "/bi/screens/" + screenId + "/preview"
                );
            },
            maxRows
        );
    }

    private List<OwnerAsset> loadDataProducts(int maxRows) {
        return jdbcTemplate.query(
            """
            select id, code, name, description, owner_dept, classification, lifecycle_status, status, last_modified_date
              from catalog_data_product
             where code is not null and btrim(code) <> ''
             order by last_modified_date desc nulls last, name, id
             limit ?
            """,
            (rs, rowNum) -> new OwnerAsset(
                uuid(rs, "id"),
                CatalogAssetType.DATA_PRODUCT,
                CatalogAssetKey.codeAsset(CatalogAssetType.DATA_PRODUCT, "default", rs.getString("code")),
                rs.getString("name"),
                rs.getString("description"),
                "DATA_PRODUCT",
                "数据产品",
                null,
                normalizeClassification(rs.getString("classification")),
                null,
                null,
                rs.getString("owner_dept"),
                firstText(rs.getString("lifecycle_status"), rs.getString("status")),
                governanceStatus(rs.getString("status")),
                "UNKNOWN",
                instant(rs, "last_modified_date"),
                "/catalog/data-products"
            ),
            maxRows
        );
    }

    private List<OwnerAsset> loadApiServices(int maxRows) {
        return jdbcTemplate.query(
            """
            select id, code, name, description, method, path, classification, status,
                   coalesce(last_published_at, last_modified_date) as updated_at
              from svc_api
             where code is not null and btrim(code) <> ''
             order by coalesce(last_published_at, last_modified_date) desc nulls last, name, id
             limit ?
            """,
            (rs, rowNum) -> new OwnerAsset(
                uuid(rs, "id"),
                CatalogAssetType.API_SERVICE,
                CatalogAssetKey.codeAsset(CatalogAssetType.API_SERVICE, "default", rs.getString("code")),
                rs.getString("name"),
                rs.getString("description"),
                firstText(rs.getString("method"), "API"),
                rs.getString("path"),
                null,
                normalizeClassification(rs.getString("classification")),
                null,
                null,
                null,
                rs.getString("status"),
                governanceStatus(rs.getString("status")),
                "UNKNOWN",
                instant(rs, "updated_at"),
                "/services/apis"
            ),
            maxRows
        );
    }

    private void hydrateModelDatasetRelations(
        Map<AssetRef, List<AssetRelationship>> result,
        Map<CatalogAssetType, Set<String>> keysByType
    ) {
        Set<String> modelKeys = keysByType.getOrDefault(CatalogAssetType.SEMANTIC_MODEL, Set.of());
        Set<String> datasetKeys = keysByType.getOrDefault(CatalogAssetType.DATASET, Set.of());
        if (modelKeys.isEmpty() && datasetKeys.isEmpty()) {
            return;
        }
        jdbcTemplate.query(
            """
            select p.model_spec_id, semantics.asset_key as dataset_asset_key,
                   semantics.resource_id::text as dataset_id, m.name as model_name,
                   coalesce(dataset.name, semantics.asset_key) as dataset_name
              from modeling_catalog_model_serving_projection p
              join modeling_model_spec m
                on m.tenant_id = p.tenant_id and m.id = p.model_spec_id
              join catalog_asset_semantic_projection semantics
                on semantics.asset_type = 'DATASET'
               and semantics.resource_id = case
                     when p.serving_ref ->> 'physicalAssetId'
                          ~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
                     then (p.serving_ref ->> 'physicalAssetId')::uuid
                     else null
                   end
              left join catalog_dataset dataset on dataset.id = semantics.resource_id
             where p.catalog_asset_type = 'SEMANTIC_MODEL'
               and p.serving_ref is not null
            """,
            rs -> {
                String modelKey = CatalogAssetKey.semanticModel(rs.getString("model_spec_id"));
                String datasetKey = rs.getString("dataset_asset_key");
                String datasetId = rs.getString("dataset_id");
                AssetRef model = ref(CatalogAssetType.SEMANTIC_MODEL, modelKey);
                AssetRef dataset = ref(CatalogAssetType.DATASET, datasetKey);
                if (modelKeys.contains(modelKey)) {
                    add(
                        result,
                        model,
                        relationship(
                            "MATERIALIZES_TO",
                            "OUTGOING",
                            dataset,
                            rs.getString("dataset_name"),
                            "/catalog/datasets/" + datasetId
                        )
                    );
                }
                if (datasetKeys.contains(datasetKey)) {
                    add(result, dataset, relationship("MATERIALIZED_FROM", "INCOMING", model, rs.getString("model_name"), "/modeling/models/" + rs.getString("model_spec_id")));
                }
            }
        );
    }

    private void hydrateBiDatasetModelRelations(
        Map<AssetRef, List<AssetRelationship>> result,
        Map<CatalogAssetType, Set<String>> keysByType
    ) {
        Set<String> modelKeys = keysByType.getOrDefault(CatalogAssetType.SEMANTIC_MODEL, Set.of());
        Set<String> biDatasetKeys = keysByType.getOrDefault(CatalogAssetType.BI_DATASET, Set.of());
        if (modelKeys.isEmpty() && biDatasetKeys.isEmpty()) {
            return;
        }
        jdbcTemplate.query(
            """
            select d.id, d.name, d.source_model_spec_id, m.name as model_name
              from query_dataset_asset d
              join modeling_model_spec m on m.id = d.source_model_spec_id
             where d.enabled = true and d.source_model_spec_id is not null
            """,
            rs -> {
                String biKey = CatalogAssetKey.biDataset(uuid(rs, "id"));
                String modelKey = CatalogAssetKey.semanticModel(rs.getString("source_model_spec_id"));
                AssetRef biDataset = ref(CatalogAssetType.BI_DATASET, biKey);
                AssetRef model = ref(CatalogAssetType.SEMANTIC_MODEL, modelKey);
                if (biDatasetKeys.contains(biKey)) {
                    add(result, biDataset, relationship("DERIVED_FROM", "OUTGOING", model, rs.getString("model_name"), "/modeling/models/" + rs.getString("source_model_spec_id")));
                }
                if (modelKeys.contains(modelKey)) {
                    add(result, model, relationship("SERVES_BI_DATASET", "INCOMING", biDataset, rs.getString("name"), "/bi/virtual-datasets/" + rs.getString("id")));
                }
            }
        );
    }

    private void hydrateScreenDatasetRelations(
        Map<AssetRef, List<AssetRelationship>> result,
        Map<CatalogAssetType, Set<String>> keysByType
    ) {
        Set<String> screenKeys = keysByType.getOrDefault(CatalogAssetType.SCREEN, Set.of());
        Set<String> biDatasetKeys = keysByType.getOrDefault(CatalogAssetType.BI_DATASET, Set.of());
        if (screenKeys.isEmpty() && biDatasetKeys.isEmpty()) {
            return;
        }
        jdbcTemplate.query(
            """
            select r.code, r.title, r.url, r.query_dataset_id, d.name as dataset_name
              from bi_report_link r
              join query_dataset_asset d on d.id = r.query_dataset_id
             where r.enabled = true and upper(coalesce(r.report_type, '')) = 'SCREEN'
               and lower(r.code) like 'screen-%'
            """,
            rs -> {
                String screenId = screenId(rs.getString("code"));
                String screenKey = CatalogAssetKey.screen(screenId);
                String biKey = CatalogAssetKey.biDataset(uuid(rs, "query_dataset_id"));
                AssetRef screen = ref(CatalogAssetType.SCREEN, screenKey);
                AssetRef biDataset = ref(CatalogAssetType.BI_DATASET, biKey);
                if (screenKeys.contains(screenKey)) {
                    add(result, screen, relationship("VISUALIZES", "OUTGOING", biDataset, rs.getString("dataset_name"), "/bi/virtual-datasets/" + rs.getString("query_dataset_id")));
                }
                if (biDatasetKeys.contains(biKey)) {
                    add(result, biDataset, relationship("VISUALIZED_BY", "INCOMING", screen, rs.getString("title"), rs.getString("url")));
                }
            }
        );
    }

    private void hydrateApiDatasetRelations(
        Map<AssetRef, List<AssetRelationship>> result,
        Map<CatalogAssetType, Set<String>> keysByType
    ) {
        Set<String> apiKeys = keysByType.getOrDefault(CatalogAssetType.API_SERVICE, Set.of());
        Set<String> datasetKeys = keysByType.getOrDefault(CatalogAssetType.DATASET, Set.of());
        if (apiKeys.isEmpty() && datasetKeys.isEmpty()) {
            return;
        }
        jdbcTemplate.query(
            """
            select a.code, a.name as api_name, a.dataset_id, d.name as dataset_name, d.source_id,
                   d.hive_database, d.hive_table
              from svc_api a
              join catalog_dataset d on d.id = a.dataset_id
             where a.dataset_id is not null
            """,
            rs -> {
                String apiKey = CatalogAssetKey.codeAsset(CatalogAssetType.API_SERVICE, "default", rs.getString("code"));
                String datasetKey = CatalogAssetKey.dataset(uuid(rs, "source_id"), rs.getString("hive_database"), rs.getString("hive_database"), rs.getString("hive_table"), rs.getString("dataset_name"));
                AssetRef api = ref(CatalogAssetType.API_SERVICE, apiKey);
                AssetRef dataset = ref(CatalogAssetType.DATASET, datasetKey);
                if (apiKeys.contains(apiKey)) {
                    add(result, api, relationship("SERVES", "OUTGOING", dataset, rs.getString("dataset_name"), "/catalog/datasets/" + rs.getString("dataset_id")));
                }
                if (datasetKeys.contains(datasetKey)) {
                    add(result, dataset, relationship("SERVED_BY", "INCOMING", api, rs.getString("api_name"), "/services/apis"));
                }
            }
        );
    }

    private void hydrateIndicatorDatasetRelations(
        Map<AssetRef, List<AssetRelationship>> result,
        Map<CatalogAssetType, Set<String>> keysByType
    ) {
        Set<String> indicatorKeys = keysByType.getOrDefault(CatalogAssetType.GOV_INDICATOR, Set.of());
        Set<String> datasetKeys = keysByType.getOrDefault(CatalogAssetType.DATASET, Set.of());
        if (indicatorKeys.isEmpty() && datasetKeys.isEmpty()) {
            return;
        }
        jdbcTemplate.query(
            """
            select i.code, i.name as indicator_name, d.id as dataset_id, d.name as dataset_name,
                   d.source_id, d.hive_database, d.hive_table
              from gov_indicator_definition i
              join catalog_dataset d on d.id::text = i.dataset_id
             where i.dataset_id is not null
            """,
            rs -> {
                String indicatorKey = CatalogAssetKey.codeAsset(CatalogAssetType.GOV_INDICATOR, "default", rs.getString("code"));
                String datasetKey = CatalogAssetKey.dataset(uuid(rs, "source_id"), rs.getString("hive_database"), rs.getString("hive_database"), rs.getString("hive_table"), rs.getString("dataset_name"));
                AssetRef indicator = ref(CatalogAssetType.GOV_INDICATOR, indicatorKey);
                AssetRef dataset = ref(CatalogAssetType.DATASET, datasetKey);
                if (indicatorKeys.contains(indicatorKey)) {
                    add(result, indicator, relationship("CALCULATED_FROM", "OUTGOING", dataset, rs.getString("dataset_name"), "/catalog/datasets/" + rs.getString("dataset_id")));
                }
                if (datasetKeys.contains(datasetKey)) {
                    add(result, dataset, relationship("SUPPORTS_INDICATOR", "INCOMING", indicator, rs.getString("indicator_name"), "/governance/indicators/dictionary"));
                }
            }
        );
    }

    private Map<CatalogAssetType, Set<String>> groupKeys(Set<AssetRef> assets) {
        Map<CatalogAssetType, Set<String>> result = new EnumMap<>(CatalogAssetType.class);
        for (AssetRef asset : assets) {
            CatalogAssetType type = CatalogAssetType.from(asset.assetType());
            result.computeIfAbsent(type, ignored -> new LinkedHashSet<>()).add(asset.assetKey());
        }
        return result;
    }

    private void add(Map<AssetRef, List<AssetRelationship>> result, AssetRef source, AssetRelationship relation) {
        List<AssetRelationship> relations = result.get(source);
        if (relations != null) {
            relations.add(relation);
        }
    }

    private AssetRelationship relationship(
        String relationType,
        String direction,
        AssetRef target,
        String displayName,
        String detailRoute
    ) {
        return new AssetRelationship(relationType, direction, target.assetType(), target.assetKey(), displayName, detailRoute);
    }

    private AssetRef ref(CatalogAssetType type, String key) {
        return new AssetRef(type.name(), key);
    }

    private String screenId(String code) {
        if (!StringUtils.hasText(code) || !code.toLowerCase().startsWith("screen-") || code.length() <= 7) {
            throw new IllegalStateException("看板缺少规范 screen-* 编码");
        }
        return code.substring(7);
    }

    private UUID uuid(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        if (value == null) {
            return null;
        }
        return value instanceof UUID uuid ? uuid : UUID.fromString(value.toString());
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private String governanceStatus(String lifecycleStatus) {
        String normalized = lifecycleStatus == null ? "" : lifecycleStatus.trim().toUpperCase();
        return switch (normalized) {
            case "PUBLISHED", "CURRENT", "ONLINE", "ACTIVE" -> "GOVERNED";
            default -> "INCOMPLETE";
        };
    }

    private String reliableValidationStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase();
        return switch (normalized) {
            case "PASSED", "SUCCESS" -> "PASSED";
            case "FAILED", "FAILURE" -> "FAILED";
            default -> "UNKNOWN";
        };
    }

    private String normalizeClassification(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "DATA_PUBLIC" -> "PUBLIC";
            case "DATA_INTERNAL" -> "INTERNAL";
            case "DATA_SECRET" -> "SECRET";
            case "DATA_CONFIDENTIAL" -> "CONFIDENTIAL";
            default -> value.trim().toUpperCase(Locale.ROOT);
        };
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    public record OwnerAsset(
        UUID resourceId,
        CatalogAssetType assetType,
        String assetKey,
        String displayName,
        String description,
        String subtype,
        String service,
        UUID domainId,
        String classification,
        String warehouseLayer,
        String owner,
        String ownerDept,
        String lifecycleStatus,
        String governanceStatus,
        String qualityStatus,
        Instant updatedAt,
        String detailRoute
    ) {}

    public record AssetRelationship(
        String relationType,
        String direction,
        String assetType,
        String assetKey,
        String displayName,
        String detailRoute
    ) {}
}
