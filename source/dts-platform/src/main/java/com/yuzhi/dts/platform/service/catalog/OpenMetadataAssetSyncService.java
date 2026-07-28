package com.yuzhi.dts.platform.service.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.OpenMetadataProperties;
import com.yuzhi.dts.platform.domain.catalog.CatalogAssetExtension;
import com.yuzhi.dts.platform.domain.catalog.CatalogAssetMapping;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.OpenMetadataAssetCache;
import com.yuzhi.dts.platform.domain.catalog.OpenMetadataColumnCache;
import com.yuzhi.dts.platform.domain.catalog.OpenMetadataLineageCache;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.catalog.CatalogAssetExtensionRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogAssetMappingRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.OpenMetadataAssetCacheRepository;
import com.yuzhi.dts.platform.repository.catalog.OpenMetadataColumnCacheRepository;
import com.yuzhi.dts.platform.repository.catalog.OpenMetadataLineageCacheRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.service.openmetadata.OpenMetadataClient;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class OpenMetadataAssetSyncService {

    private static final int DEFAULT_SYNC_LIMIT = 200;

    private final OpenMetadataClient client;
    private final OpenMetadataProperties props;
    private final OpenMetadataAssetCacheRepository assetRepository;
    private final OpenMetadataColumnCacheRepository columnRepository;
    private final OpenMetadataLineageCacheRepository lineageRepository;
    private final CatalogAssetMappingRepository mappingRepository;
    private final CatalogAssetExtensionRepository extensionRepository;
    private final CatalogDatasetRepository datasetRepository;
    private final InfraDataSourceRepository dataSourceRepository;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    public OpenMetadataAssetSyncService(
        OpenMetadataClient client,
        OpenMetadataProperties props,
        OpenMetadataAssetCacheRepository assetRepository,
        OpenMetadataColumnCacheRepository columnRepository,
        OpenMetadataLineageCacheRepository lineageRepository,
        CatalogAssetMappingRepository mappingRepository,
        CatalogAssetExtensionRepository extensionRepository,
        CatalogDatasetRepository datasetRepository,
        InfraDataSourceRepository dataSourceRepository,
        EntityManager entityManager,
        ObjectMapper objectMapper
    ) {
        this.client = client;
        this.props = props;
        this.assetRepository = assetRepository;
        this.columnRepository = columnRepository;
        this.lineageRepository = lineageRepository;
        this.mappingRepository = mappingRepository;
        this.extensionRepository = extensionRepository;
        this.datasetRepository = datasetRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SyncResult syncTables(Integer requestedLimit) {
        int limit = requestedLimit == null ? DEFAULT_SYNC_LIMIT : Math.max(1, Math.min(requestedLimit, 500));
        if (!props.isEnabled()) {
            return new SyncResult(false, "OpenMetadata 未启用", 0, 0, 0, 0, 0, 0);
        }
        Optional<Map<String, Object>> response = client.listTables(limit, props.getTableFields());
        if (response.isEmpty()) {
            return new SyncResult(true, "OpenMetadata 未返回表数据", 0, 0, 0, 0, 0, 0);
        }
        List<Map<String, Object>> tables = parseTableList(response.orElseThrow());
        int upserted = 0;
        int mapped = 0;
        int columns = 0;
        int skipped = 0;
        int failed = 0;
        for (Map<String, Object> table : tables) {
            if (isForbidden(table)) {
                skipped++;
                continue;
            }
            try {
                OpenMetadataAssetCache asset = upsertAsset(table);
                upsertColumns(asset, table);
                columns += Math.max(asset.getColumnCount() == null ? 0 : asset.getColumnCount(), 0);
                upsertMapping(asset);
                CatalogAssetMapping mapping = mappingRepository.findFirstByFqnIgnoreCase(asset.getFqn()).orElse(null);
                if (mapping != null && "MATCHED".equalsIgnoreCase(mapping.getMatchStatus())) {
                    mapped++;
                }
                upserted++;
            } catch (Exception ex) {
                failed++;
            }
        }
        String message = failed > 0 || skipped > 0 ? "同步完成，跳过 " + skipped + " 个，失败 " + failed + " 个" : null;
        return new SyncResult(true, message, upserted, mapped, columns, skipped, failed, 0);
    }

    @Transactional
    public SyncResult syncLineage(String assetRef, int upstreamDepth, int downstreamDepth) {
        if (!props.isEnabled()) {
            return new SyncResult(false, "OpenMetadata 未启用", 0, 0, 0, 0, 0, 0);
        }
        Optional<OpenMetadataAssetCache> asset = resolveAsset(assetRef);
        if (asset.isEmpty()) {
            return new SyncResult(true, "未找到资产缓存，需先同步资产", 0, 0, 0, 0, 1, 0);
        }
        OpenMetadataAssetCache root = asset.orElseThrow();
        if (!StringUtils.hasText(root.getOmEntityId())) {
            return new SyncResult(true, "资产缺少 OpenMetadata entity id", 0, 0, 0, 0, 1, 0);
        }
        Optional<Map<String, Object>> response = client.getLineage(
            root.getOmEntityId(),
            Math.max(1, Math.min(upstreamDepth, 10)),
            Math.max(1, Math.min(downstreamDepth, 10))
        );
        if (response.isEmpty()) {
            return new SyncResult(true, "OpenMetadata 未返回血缘数据", 0, 0, 0, 0, 0, 0);
        }
        Map<String, Object> body = response.orElseThrow();
        Map<String, Map<String, Object>> nodes = parseLineageNodes(body);
        int saved = 0;
        Object rawEdges = body.get("edges");
        if (rawEdges instanceof List<?> edges) {
            for (Object item : edges) {
                if (!(item instanceof Map<?, ?> edge)) {
                    continue;
                }
                String fromId = resolveEntityId(edge.get("fromEntity"));
                String toId = resolveEntityId(edge.get("toEntity"));
                if (!StringUtils.hasText(fromId) || !StringUtils.hasText(toId)) {
                    continue;
                }
                Map<String, Object> from = nodes.get(fromId);
                Map<String, Object> to = nodes.get(toId);
                String fromFqn = firstText(entityField(from, "fullyQualifiedName"), entityField(edge.get("fromEntity"), "fullyQualifiedName"));
                String toFqn = firstText(entityField(to, "fullyQualifiedName"), entityField(edge.get("toEntity"), "fullyQualifiedName"));
                if (!StringUtils.hasText(fromFqn) || !StringUtils.hasText(toFqn)) {
                    continue;
                }
                OpenMetadataLineageCache cache = lineageRepository
                    .findFirstByFromFqnIgnoreCaseAndToFqnIgnoreCaseAndSourceIgnoreCase(fromFqn, toFqn, "openmetadata")
                    .orElseGet(OpenMetadataLineageCache::new);
                cache.setFromOmEntityId(fromId);
                cache.setToOmEntityId(toId);
                cache.setFromFqn(fromFqn);
                cache.setToFqn(toFqn);
                cache.setEdgeType(firstText(edge.get("lineageDetails"), edge.get("edgeType"), "TABLE"));
                cache.setSource("openmetadata");
                cache.setRawJson(toJson(edge));
                cache.setLastSyncedAt(Instant.now());
                lineageRepository.save(cache);
                saved++;
            }
        }
        return new SyncResult(true, null, 0, 0, 0, 0, 0, saved);
    }

    private OpenMetadataAssetCache upsertAsset(Map<String, Object> table) {
        String fqn = firstText(table.get("fullyQualifiedName"), table.get("fqn"));
        if (!StringUtils.hasText(fqn)) {
            fqn = fallbackFqn(table);
        }
        if (!StringUtils.hasText(fqn)) {
            throw new IllegalArgumentException("OpenMetadata table missing fullyQualifiedName");
        }
        OpenMetadataAssetCache asset = assetRepository.findFirstByFqnIgnoreCase(fqn).orElseGet(OpenMetadataAssetCache::new);
        FqnParts parts = parseFqn(fqn);
        asset.setFqn(fqn);
        asset.setSourceType(firstText(table.get("serviceType"), table.get("entityType"), table.get("type"), "TABLE").toUpperCase());
        asset.setOmEntityId(firstText(table.get("id")));
        asset.setServiceName(firstText(extractEntityName(table.get("service")), parts.service()));
        asset.setDatabaseName(firstText(extractEntityName(table.get("database")), parts.database()));
        asset.setSchemaName(firstText(extractEntityName(table.get("databaseSchema")), extractEntityName(table.get("schema")), parts.schema()));
        asset.setTableName(firstText(table.get("name"), parts.table()));
        asset.setDisplayName(firstText(table.get("displayName"), table.get("name"), parts.table()));
        asset.setDescription(firstText(table.get("description")));
        asset.setOwnerName(extractEntityName(table.get("owner")));
        asset.setDomainName(extractEntityName(table.get("domain")));
        asset.setTagsJson(toJson(table.get("tags")));
        asset.setProfileJson(toJson(firstNonNull(table.get("profile"), table.get("tableProfile"))));
        asset.setRawJson(toJson(table));
        List<?> columnList = table.get("columns") instanceof List<?> list ? list : List.of();
        asset.setColumnCount(columnList.size());
        asset.setSyncStatus("SYNCED");
        asset.setSyncMessage(null);
        asset.setLastSyncedAt(Instant.now());
        return assetRepository.save(asset);
    }

    private Optional<OpenMetadataAssetCache> resolveAsset(String ref) {
        if (!StringUtils.hasText(ref)) {
            return Optional.empty();
        }
        String value = ref.trim();
        try {
            Optional<OpenMetadataAssetCache> byId = assetRepository.findById(java.util.UUID.fromString(value));
            if (byId.isPresent()) {
                return byId;
            }
        } catch (Exception ignored) {
            // continue with entity id / FQN lookup
        }
        Optional<OpenMetadataAssetCache> byEntityId = assetRepository.findFirstByOmEntityId(value);
        if (byEntityId.isPresent()) {
            return byEntityId;
        }
        return assetRepository.findFirstByFqnIgnoreCase(value);
    }

    private void upsertColumns(OpenMetadataAssetCache asset, Map<String, Object> table) {
        columnRepository.deleteByAsset(asset);
        Object raw = table.get("columns");
        if (!(raw instanceof List<?> columns)) {
            return;
        }
        List<OpenMetadataColumnCache> payload = new ArrayList<>();
        int index = 0;
        for (Object item : columns) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String name = firstText(map.get("name"));
            if (!StringUtils.hasText(name)) {
                continue;
            }
            OpenMetadataColumnCache column = new OpenMetadataColumnCache();
            column.setAsset(asset);
            column.setName(name);
            column.setOmColumnFqn(firstText(map.get("fullyQualifiedName"), asset.getFqn() + "." + name));
            column.setDataType(firstText(map.get("dataType"), map.get("dataTypeDisplay")));
            column.setDescription(firstText(map.get("description")));
            column.setOrdinalPosition(index++);
            column.setTagsJson(toJson(map.get("tags")));
            column.setProfileJson(toJson(firstNonNull(map.get("profile"), map.get("columnProfile"))));
            column.setRawJson(toJson(map));
            payload.add(column);
        }
        if (!payload.isEmpty()) {
            columnRepository.saveAll(payload);
        }
    }

    private void upsertMapping(OpenMetadataAssetCache asset) {
        CatalogAssetMapping mapping = mappingRepository.findFirstByFqnIgnoreCase(asset.getFqn()).orElseGet(CatalogAssetMapping::new);
        mapping.setFqn(asset.getFqn());
        mapping.setOmEntityId(asset.getOmEntityId());
        mapping.setLastCheckedAt(Instant.now());

        List<CatalogDataset> physicalCandidates = findLegacyCandidates(asset);
        Map<UUID, String> explicitServicesBySource = resolveExplicitServicesBySource(physicalCandidates);
        List<CatalogDataset> candidates = physicalCandidates
            .stream()
            .filter(candidate -> hasReliableServiceMapping(asset, candidate, explicitServicesBySource))
            .toList();
        boolean hasExplicitServiceConfiguration = explicitServicesBySource.values().stream().anyMatch(StringUtils::hasText);
        CatalogDataset retainedExisting = candidates.isEmpty() && !hasExplicitServiceConfiguration
            ? findInternallyConsistentExistingCandidate(mapping, physicalCandidates)
            : null;
        if (candidates.size() == 1 || retainedExisting != null) {
            CatalogDataset dataset = retainedExisting != null ? retainedExisting : candidates.get(0);
            entityManager.lock(dataset, LockModeType.PESSIMISTIC_WRITE);
            String claimConflict = legacyClaimConflict(asset, mapping, dataset);
            if (StringUtils.hasText(claimConflict)) {
                markManualReview(mapping, claimConflict, asset);
                return;
            }
            mapping.setLegacyDatasetId(dataset.getId());
            mapping.setSourceId(dataset.getSourceId());
            mapping.setMatchStatus("MATCHED");
            mapping.setMatchReason(
                retainedExisting != null
                    ? "existing internally consistent mapping retained pending explicit OpenMetadata service configuration"
                    : "explicit OpenMetadata service-to-source mapping and unique table candidate"
            );
            mapping.setConfidence(retainedExisting != null ? 90 : 100);
            mappingRepository.save(mapping);
            upsertExtension(asset, dataset);
            return;
        }

        mapping.setLegacyDatasetId(null);
        mapping.setSourceId(null);
        if (candidates.size() > 1) {
            mapping.setMatchStatus("MANUAL_REVIEW");
            mapping.setMatchReason("multiple explicitly service-mapped legacy catalog_dataset candidates: " + candidates.size());
        } else if (!physicalCandidates.isEmpty()) {
            mapping.setMatchStatus("MANUAL_REVIEW");
            mapping.setMatchReason(
                "no reliable OpenMetadata service-to-source mapping for " +
                asset.getServiceName() +
                " across " +
                physicalCandidates.size() +
                " physical candidate(s)"
            );
        } else {
            mapping.setMatchStatus("UNMATCHED");
            mapping.setMatchReason("no legacy catalog_dataset matched schema/database and table");
        }
        mapping.setConfidence(0);
        mappingRepository.save(mapping);
        ensurePendingExtension(asset);
    }

    private CatalogDataset findInternallyConsistentExistingCandidate(
        CatalogAssetMapping mapping,
        List<CatalogDataset> physicalCandidates
    ) {
        if (
            mapping == null ||
            !"MATCHED".equalsIgnoreCase(mapping.getMatchStatus()) ||
            mapping.getLegacyDatasetId() == null ||
            mapping.getSourceId() == null
        ) {
            return null;
        }
        return physicalCandidates
            .stream()
            .filter(candidate -> mapping.getLegacyDatasetId().equals(candidate.getId()))
            .filter(candidate -> mapping.getSourceId().equals(candidate.getSourceId()))
            .findFirst()
            .orElse(null);
    }

    private Map<UUID, String> resolveExplicitServicesBySource(List<CatalogDataset> candidates) {
        Map<UUID, String> services = new LinkedHashMap<>();
        for (CatalogDataset candidate : candidates) {
            UUID sourceId = candidate.getSourceId();
            if (sourceId == null || services.containsKey(sourceId)) {
                continue;
            }
            services.put(
                sourceId,
                dataSourceRepository.findById(sourceId).map(this::explicitOpenMetadataServiceName).orElse(null)
            );
        }
        return services;
    }

    private boolean hasReliableServiceMapping(
        OpenMetadataAssetCache asset,
        CatalogDataset dataset,
        Map<UUID, String> explicitServicesBySource
    ) {
        if (
            asset == null ||
            dataset == null ||
            dataset.getSourceId() == null ||
            !StringUtils.hasText(asset.getServiceName())
        ) {
            return false;
        }
        String configuredService = explicitServicesBySource.get(dataset.getSourceId());
        return StringUtils.hasText(configuredService) && configuredService.equalsIgnoreCase(asset.getServiceName().trim());
    }

    private String explicitOpenMetadataServiceName(InfraDataSource source) {
        if (source == null || !StringUtils.hasText(source.getProps())) {
            return null;
        }
        try {
            Map<String, Object> sourceProperties = objectMapper.readValue(
                source.getProps(),
                new TypeReference<Map<String, Object>>() {}
            );
            String direct = firstText(
                sourceProperties.get("openmetadataServiceName"),
                sourceProperties.get("openMetadataServiceName"),
                sourceProperties.get("omServiceName")
            );
            if (StringUtils.hasText(direct)) {
                return direct;
            }
            Object nested = sourceProperties.get("openmetadata");
            if (nested instanceof Map<?, ?> nestedProperties) {
                return firstText(
                    nestedProperties.get("serviceName"),
                    nestedProperties.get("databaseServiceName")
                );
            }
        } catch (JsonProcessingException ignored) {
            return null;
        }
        return null;
    }

    private String legacyClaimConflict(
        OpenMetadataAssetCache asset,
        CatalogAssetMapping mapping,
        CatalogDataset dataset
    ) {
        Optional<CatalogAssetMapping> mappingClaim = mappingRepository.findFirstByLegacyDatasetId(dataset.getId());
        if (mappingClaim.isPresent()) {
            CatalogAssetMapping claimedMapping = mappingClaim.orElseThrow();
            if (
                !StringUtils.hasText(claimedMapping.getFqn()) ||
                !claimedMapping.getFqn().equalsIgnoreCase(mapping.getFqn())
            ) {
                return "legacy dataset is already claimed by OpenMetadata mapping " + claimedMapping.getFqn();
            }
        }
        Optional<CatalogAssetExtension> extensionClaim = extensionRepository.findFirstByLegacyDatasetId(dataset.getId());
        if (extensionClaim.isPresent()) {
            CatalogAssetExtension claimedExtension = extensionClaim.orElseThrow();
            if (
                claimedExtension.getOmAsset() != null &&
                !sameOpenMetadataAsset(claimedExtension.getOmAsset(), asset)
            ) {
                return "legacy dataset extension is already claimed by another OpenMetadata asset";
            }
        }
        return null;
    }

    private boolean sameOpenMetadataAsset(OpenMetadataAssetCache left, OpenMetadataAssetCache right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        if (left.getId() != null && right.getId() != null) {
            return left.getId().equals(right.getId());
        }
        return StringUtils.hasText(left.getFqn()) &&
            StringUtils.hasText(right.getFqn()) &&
            left.getFqn().equalsIgnoreCase(right.getFqn());
    }

    private void markManualReview(CatalogAssetMapping mapping, String reason, OpenMetadataAssetCache asset) {
        mapping.setLegacyDatasetId(null);
        mapping.setSourceId(null);
        mapping.setMatchStatus("MANUAL_REVIEW");
        mapping.setMatchReason(reason);
        mapping.setConfidence(0);
        mappingRepository.save(mapping);
        ensurePendingExtension(asset);
    }

    private List<CatalogDataset> findLegacyCandidates(OpenMetadataAssetCache asset) {
        String table = asset.getTableName();
        if (!StringUtils.hasText(table)) {
            return List.of();
        }
        Map<String, CatalogDataset> candidates = new LinkedHashMap<>();
        String schema = asset.getSchemaName();
        if (StringUtils.hasText(schema)) {
            addLegacyCandidates(
                candidates,
                datasetRepository.findByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(schema, table)
            );
        }
        String database = asset.getDatabaseName();
        if (StringUtils.hasText(database)) {
            addLegacyCandidates(
                candidates,
                datasetRepository.findByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(database, table)
            );
        }
        return List.copyOf(candidates.values());
    }

    private void addLegacyCandidates(Map<String, CatalogDataset> candidates, List<CatalogDataset> matches) {
        for (CatalogDataset dataset : matches) {
            String key = dataset.getId() != null
                ? "id:" + dataset.getId()
                : String.join(
                    ":",
                    "physical",
                    String.valueOf(dataset.getSourceId()),
                    String.valueOf(dataset.getHiveDatabase()).toLowerCase(),
                    String.valueOf(dataset.getHiveTable()).toLowerCase()
                );
            candidates.putIfAbsent(key, dataset);
        }
    }

    private void upsertExtension(OpenMetadataAssetCache asset, CatalogDataset dataset) {
        Optional<CatalogAssetExtension> extensionByAsset = extensionRepository.findFirstByOmAsset(asset);
        Optional<CatalogAssetExtension> extensionByLegacy = extensionByAsset.isPresent()
            ? Optional.empty()
            : extensionRepository.findFirstByLegacyDatasetId(dataset.getId());
        if (
            extensionByLegacy.isPresent() &&
            extensionByLegacy.orElseThrow().getOmAsset() != null &&
            !sameOpenMetadataAsset(extensionByLegacy.orElseThrow().getOmAsset(), asset)
        ) {
            throw new IllegalStateException("legacy dataset extension is already claimed by another OpenMetadata asset");
        }
        boolean newExtension = extensionByAsset.isEmpty() && extensionByLegacy.isEmpty();
        CatalogAssetExtension extension = extensionByAsset.or(() -> extensionByLegacy).orElseGet(CatalogAssetExtension::new);
        extension.setOmAsset(asset);
        extension.setLegacyDatasetId(dataset.getId());
        if (extension.getDomainId() == null && dataset.getDomain() != null) {
            extension.setDomainId(dataset.getDomain().getId());
        }
        if (!StringUtils.hasText(extension.getClassification())) {
            extension.setClassification(dataset.getClassification());
        }
        if (!StringUtils.hasText(extension.getWarehouseLayer())) {
            extension.setWarehouseLayer(dataset.getWarehouseLayer());
        }
        if (!StringUtils.hasText(extension.getOwnerDept())) {
            extension.setOwnerDept(dataset.getOwnerDept());
        }
        if (!StringUtils.hasText(extension.getBusinessOwner())) {
            extension.setBusinessOwner(dataset.getOwner());
        }
        if (!StringUtils.hasText(extension.getLifecycleStatus())) {
            extension.setLifecycleStatus(CatalogAssetGovernancePolicy.normalizeLifecycle(dataset.getLifecycleStatus()));
        }
        if (newExtension || extension.getEnabled() == null) {
            extension.setEnabled(dataset.getEnabled() == null ? Boolean.TRUE : dataset.getEnabled());
        }
        extension.setGovernanceStatus(resolveGovernanceStatus(extension));
        extensionRepository.save(extension);
    }

    private void ensurePendingExtension(OpenMetadataAssetCache asset) {
        CatalogAssetExtension extension = extensionRepository.findFirstByOmAsset(asset).orElseGet(CatalogAssetExtension::new);
        extension.setOmAsset(asset);
        extension.setLegacyDatasetId(null);
        if (extension.getEnabled() == null) {
            extension.setEnabled(Boolean.TRUE);
        }
        extension.setGovernanceStatus(resolveGovernanceStatus(extension));
        extensionRepository.save(extension);
    }

    private String resolveGovernanceStatus(CatalogAssetExtension extension) {
        if (!StringUtils.hasText(extension.getBusinessOwner()) && !StringUtils.hasText(extension.getOwnerDept())) {
            return "PENDING_CLAIM";
        }
        if (!StringUtils.hasText(extension.getClassification())) {
            return "PENDING_CLASSIFICATION";
        }
        if (extension.getDomainId() == null) {
            return "PENDING_DOMAIN";
        }
        return "GOVERNED";
    }

    private List<Map<String, Object>> parseTableList(Map<String, Object> response) {
        Object data = response.get("data");
        if (data instanceof List<?> list) {
            return maps(list);
        }
        Object tables = response.get("tables");
        if (tables instanceof List<?> list) {
            return maps(list);
        }
        Object hits = response.get("hits");
        if (hits instanceof Map<?, ?> hitMap && hitMap.get("hits") instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Object source = firstNonNull(map.get("_source"), map.get("source"));
                    if (source instanceof Map<?, ?> sourceMap) {
                        out.add(copyMap(sourceMap));
                    }
                }
            }
            return out;
        }
        return List.of();
    }

    private Map<String, Map<String, Object>> parseLineageNodes(Map<String, Object> response) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        Object rawNodes = response != null ? response.get("nodes") : null;
        if (rawNodes instanceof List<?> nodes) {
            for (Object item : nodes) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> node = copyMap(map);
                    String id = firstText(node.get("id"));
                    if (StringUtils.hasText(id)) {
                        out.put(id, node);
                    }
                }
            }
        }
        return out;
    }

    private boolean isForbidden(Map<String, Object> table) {
        List<String> forbidden = props.getForbiddenDatabases();
        if (forbidden == null || forbidden.isEmpty()) {
            return false;
        }
        String database = firstText(extractEntityName(table.get("database")));
        String schema = firstText(extractEntityName(table.get("databaseSchema")), extractEntityName(table.get("schema")));
        String fqn = firstText(table.get("fullyQualifiedName"), table.get("fqn"), fallbackFqn(table));
        for (String item : forbidden) {
            if (!StringUtils.hasText(item)) {
                continue;
            }
            String pattern = item.trim().toLowerCase();
            if (
                pattern.equalsIgnoreCase(database) ||
                pattern.equalsIgnoreCase(schema) ||
                (StringUtils.hasText(fqn) && fqn.toLowerCase().contains("." + pattern + "."))
            ) {
                return true;
            }
        }
        return false;
    }

    private List<Map<String, Object>> maps(List<?> list) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                out.add(copyMap(map));
            }
        }
        return out;
    }

    private Map<String, Object> copyMap(Map<?, ?> map) {
        Map<String, Object> out = new LinkedHashMap<>();
        map.forEach((key, value) -> out.put(String.valueOf(key), value));
        return out;
    }

    private String fallbackFqn(Map<String, Object> table) {
        String service = extractEntityName(table.get("service"));
        String database = extractEntityName(table.get("database"));
        String schema = firstText(extractEntityName(table.get("databaseSchema")), extractEntityName(table.get("schema")));
        String name = firstText(table.get("name"));
        List<String> parts = new ArrayList<>();
        for (String value : new String[] { service, database, schema, name }) {
            if (StringUtils.hasText(value)) {
                parts.add(value.trim());
            }
        }
        return parts.isEmpty() ? null : String.join(".", parts);
    }

    private FqnParts parseFqn(String fqn) {
        if (!StringUtils.hasText(fqn)) {
            return new FqnParts(null, null, null, null);
        }
        String[] parts = fqn.trim().split("\\.");
        if (parts.length >= 4) {
            return new FqnParts(parts[0], parts[1], parts[2], parts[parts.length - 1]);
        }
        if (parts.length == 3) {
            return new FqnParts(parts[0], parts[1], null, parts[2]);
        }
        if (parts.length == 2) {
            return new FqnParts(parts[0], null, null, parts[1]);
        }
        return new FqnParts(null, null, null, fqn);
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

    private String extractEntityName(Object value) {
        if (value instanceof Map<?, ?> map) {
            return firstText(map.get("name"), map.get("displayName"), map.get("fullyQualifiedName"));
        }
        return firstText(value);
    }

    private String resolveEntityId(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            return firstText(map.get("id"), map.get("entity"));
        }
        return firstText(raw);
    }

    private Object entityField(Object raw, String field) {
        if (raw instanceof Map<?, ?> map) {
            return map.get(field);
        }
        return null;
    }

    private String firstText(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        return null;
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    private record FqnParts(String service, String database, String schema, String table) {}

    public record SyncResult(
        boolean enabled,
        String message,
        int assetCount,
        int mappedCount,
        int columnCount,
        int skippedCount,
        int failedCount,
        int lineageEdgeCount
    ) {}
}
