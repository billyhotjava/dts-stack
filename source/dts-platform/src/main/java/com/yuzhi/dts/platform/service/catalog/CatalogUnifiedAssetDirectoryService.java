package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.service.catalog.CatalogAssetDirectoryReadAdapter.AssetRelationship;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetDirectoryReadAdapter.OwnerAsset;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetPortalService.AssetPage;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetPortalService.AssetQuery;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetPortalService.AssetSummary;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.StatusAxes;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetStatusViewService.ModelRef;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetStatusViewService.ServingSync;
import com.yuzhi.dts.platform.service.catalog.dto.AssetRef;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogTagDto;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/** Read-only, bounded aggregation of the current enterprise warehouse asset owners. */
@Service
public class CatalogUnifiedAssetDirectoryService {

    private static final int MAX_CANDIDATES = 5_000;
    private static final int MAX_PAGE_SIZE = 200;
    private static final Set<CatalogAssetType> SUPPORTED_TYPES = Set.copyOf(
        EnumSet.of(
            CatalogAssetType.DATASET,
            CatalogAssetType.SEMANTIC_MODEL,
            CatalogAssetType.GOV_INDICATOR,
            CatalogAssetType.BI_DATASET,
            CatalogAssetType.SCREEN,
            CatalogAssetType.DATA_PRODUCT,
            CatalogAssetType.API_SERVICE
        )
    );

    private final CatalogAssetPortalService datasetService;
    private final CatalogAssetDirectoryReadAdapter readAdapter;
    private final CatalogAssetTagService tagService;
    private final CatalogAssetDirectoryVisibilityPolicy visibilityPolicy;

    public CatalogUnifiedAssetDirectoryService(
        CatalogAssetPortalService datasetService,
        CatalogAssetDirectoryReadAdapter readAdapter,
        CatalogAssetTagService tagService,
        CatalogAssetDirectoryVisibilityPolicy visibilityPolicy
    ) {
        this.datasetService = datasetService;
        this.readAdapter = readAdapter;
        this.tagService = tagService;
        this.visibilityPolicy = visibilityPolicy;
    }

    public UnifiedAssetPage list(AssetQuery query, String requestedFamily, String activeDept) {
        AssetQuery safeQuery = query == null ? AssetQuery.unscoped() : query;
        validatePage(safeQuery.page(), safeQuery.size());
        Set<CatalogAssetType> requestedTypes = requestedTypes(requestedFamily);
        List<UnifiedAssetSummary> candidates = new ArrayList<>();
        if (requestedTypes.contains(CatalogAssetType.DATASET)) {
            candidates.addAll(loadDatasets(safeQuery, activeDept));
        }
        Set<CatalogAssetType> ownerTypes = EnumSet.copyOf(requestedTypes);
        ownerTypes.remove(CatalogAssetType.DATASET);
        if (!ownerTypes.isEmpty()) {
            List<OwnerAsset> ownerAssets = readAdapter.load(ownerTypes);
            if (ownerAssets.size() > MAX_CANDIDATES) {
                throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "统一资产目录候选超过 5000，请选择更具体的资产家族"
                );
            }
            ownerAssets
                .stream()
                .filter(asset -> visibilityPolicy.canRead(asset, activeDept))
                .filter(asset -> matchesOwnerFilters(asset, safeQuery))
                .map(this::fromOwner)
                .forEach(candidates::add);
        }
        if (candidates.size() > MAX_CANDIDATES) {
            throw new ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "统一资产目录候选超过 5000，请增加资产家族、关键词或数据域筛选条件"
            );
        }
        List<UnifiedAssetSummary> tagFiltered = applyTagFilter(candidates, safeQuery.tagIds());
        tagFiltered.sort(
            Comparator.comparing(UnifiedAssetSummary::updatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(UnifiedAssetSummary::displayName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(UnifiedAssetSummary::catalogIdentity)
        );
        int from = Math.min(Math.multiplyExact(safeQuery.page(), safeQuery.size()), tagFiltered.size());
        int to = Math.min(from + safeQuery.size(), tagFiltered.size());
        List<UnifiedAssetSummary> pageItems = hydrate(tagFiltered.subList(from, to));
        return new UnifiedAssetPage(
            pageItems,
            tagFiltered.size(),
            safeQuery.page(),
            safeQuery.size(),
            pageItems.size(),
            "dts-owner-aggregation"
        );
    }

    private List<UnifiedAssetSummary> loadDatasets(AssetQuery query, String activeDept) {
        List<UnifiedAssetSummary> result = new ArrayList<>();
        int scanPage = 0;
        while (result.size() <= MAX_CANDIDATES) {
            AssetPage page = datasetService.listAssets(datasetScanQuery(query, scanPage), activeDept);
            if (page == null || page.content() == null || page.content().isEmpty()) {
                break;
            }
            page.content().stream().map(this::fromDataset).forEach(result::add);
            if (result.size() >= page.total() || page.returned() == 0) {
                break;
            }
            scanPage++;
        }
        return result;
    }

    private AssetQuery datasetScanQuery(AssetQuery query, int page) {
        return new AssetQuery(
            query.keyword(),
            query.service(),
            query.type(),
            query.database(),
            query.schema(),
            query.syncStatus(),
            query.classification(),
            query.warehouseLayer(),
            query.ownerDept(),
            query.governanceStatus(),
            query.matchStatus(),
            query.domainId(),
            query.domainUnassigned(),
            List.of(),
            page,
            MAX_PAGE_SIZE,
            query.unclassified(),
            query.stale(),
            query.eligibility(),
            query.servingStatus(),
            query.qualityStatus()
        );
    }

    private boolean matchesOwnerFilters(OwnerAsset asset, AssetQuery query) {
        if (hasDatasetOnlyFilter(query)) {
            return false;
        }
        if (!matchesKeyword(asset, query.keyword())) {
            return false;
        }
        if (query.domainId() != null && !query.domainId().equals(asset.domainId())) {
            return false;
        }
        if (query.domainUnassigned() && asset.domainId() != null) {
            return false;
        }
        if (Boolean.TRUE.equals(query.unclassified()) && StringUtils.hasText(asset.classification())) {
            return false;
        }
        return matches(query.classification(), asset.classification()) &&
        matches(query.warehouseLayer(), asset.warehouseLayer()) &&
        matches(query.ownerDept(), asset.ownerDept()) &&
        matches(query.governanceStatus(), asset.governanceStatus()) &&
        matches(query.qualityStatus(), asset.qualityStatus()) &&
        matches(query.eligibility(), "CONDITIONAL") &&
        matches(query.servingStatus(), "NOT_APPLICABLE");
    }

    private boolean hasDatasetOnlyFilter(AssetQuery query) {
        return StringUtils.hasText(query.type()) ||
        StringUtils.hasText(query.database()) ||
        StringUtils.hasText(query.schema()) ||
        StringUtils.hasText(query.syncStatus()) ||
        StringUtils.hasText(query.matchStatus()) ||
        Boolean.TRUE.equals(query.stale());
    }

    private boolean matchesKeyword(OwnerAsset asset, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String needle = keyword.trim().toLowerCase(Locale.ROOT);
        return Stream.of(
            asset.displayName(),
            asset.description(),
            asset.assetKey(),
            asset.subtype(),
            asset.service()
        )
            .filter(StringUtils::hasText)
            .map(value -> value.toLowerCase(Locale.ROOT))
            .anyMatch(value -> value.contains(needle));
    }

    private boolean matches(String expected, String actual) {
        return !StringUtils.hasText(expected) ||
        (StringUtils.hasText(actual) && expected.trim().equalsIgnoreCase(actual.trim()));
    }

    private List<UnifiedAssetSummary> applyTagFilter(
        List<UnifiedAssetSummary> candidates,
        List<UUID> tagIds
    ) {
        if (tagIds == null || tagIds.isEmpty() || candidates.isEmpty()) {
            return new ArrayList<>(candidates);
        }
        Map<CatalogAssetType, List<UnifiedAssetSummary>> byType = candidates
            .stream()
            .collect(Collectors.groupingBy(UnifiedAssetSummary::assetType, LinkedHashMap::new, Collectors.toList()));
        Map<CatalogAssetType, Set<String>> matchesByType = new LinkedHashMap<>();
        byType.forEach((type, assets) ->
            matchesByType.put(
                type,
                tagService.findMatchingAssetKeysWithin(
                    type.name(),
                    assets.stream().map(UnifiedAssetSummary::assetKey).toList(),
                    tagIds
                )
            )
        );
        return candidates
            .stream()
            .filter(asset -> matchesByType.getOrDefault(asset.assetType(), Set.of()).contains(asset.assetKey()))
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<UnifiedAssetSummary> hydrate(List<UnifiedAssetSummary> pageItems) {
        if (pageItems.isEmpty()) {
            return List.of();
        }
        List<AssetRef> refs = pageItems.stream().map(this::ref).toList();
        Map<AssetRef, List<CatalogTagDto>> tags = tagService.listAssetTags(refs);
        Map<AssetRef, List<AssetRelationship>> relationships = readAdapter.loadRelationships(refs);
        return pageItems
            .stream()
            .map(item -> item.withHydration(
                tags.getOrDefault(ref(item), List.of()),
                relationships.getOrDefault(ref(item), List.of())
            ))
            .toList();
    }

    private UnifiedAssetSummary fromDataset(AssetSummary asset) {
        CatalogAssetType type = CatalogAssetType.DATASET;
        String assetKey = StringUtils.hasText(asset.assetKey()) ? asset.assetKey() : asset.fqn();
        return new UnifiedAssetSummary(
            asset.id(),
            type,
            assetKey,
            identity(type, assetKey),
            type.name(),
            null,
            asset.displayName(),
            asset.description(),
            asset.type(),
            asset.service(),
            asset.database(),
            asset.schema(),
            asset.table(),
            asset.domainId(),
            asset.classification(),
            asset.warehouseLayer(),
            asset.owner(),
            asset.ownerDept(),
            asset.lifecycleStatus(),
            asset.governanceStatus(),
            asset.statusAxes(),
            asset.consumptionEligibility(),
            asset.eligibilityReasons(),
            asset.modelRefs(),
            asset.servingSync(),
            asset.qualityStatus(),
            firstInstant(asset.projectionUpdatedAt(), asset.lastSyncedAt()),
            asset.assetTags(),
            List.of(),
            "/catalog/datasets/" + asset.id()
        );
    }

    private UnifiedAssetSummary fromOwner(OwnerAsset asset) {
        return new UnifiedAssetSummary(
            asset.resourceId(),
            asset.assetType(),
            asset.assetKey(),
            identity(asset.assetType(), asset.assetKey()),
            asset.assetType().name(),
            asset.subtype(),
            asset.displayName(),
            asset.description(),
            null,
            asset.service(),
            null,
            null,
            null,
            asset.domainId(),
            asset.classification(),
            asset.warehouseLayer(),
            asset.owner(),
            asset.ownerDept(),
            asset.lifecycleStatus(),
            asset.governanceStatus(),
            null,
            "CONDITIONAL",
            List.of("OWNER_FACT_ONLY"),
            List.of(),
            new ServingSync("NOT_APPLICABLE", 0, null, null, asset.updatedAt()),
            StringUtils.hasText(asset.qualityStatus()) ? asset.qualityStatus() : "UNKNOWN",
            asset.updatedAt(),
            List.of(),
            List.of(),
            asset.detailRoute()
        );
    }

    private Set<CatalogAssetType> requestedTypes(String requestedFamily) {
        if (!StringUtils.hasText(requestedFamily) || "ALL".equalsIgnoreCase(requestedFamily.trim())) {
            return EnumSet.copyOf(SUPPORTED_TYPES);
        }
        CatalogAssetType type;
        try {
            type = CatalogAssetType.from(requestedFamily);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的资产家族：" + requestedFamily);
        }
        if (!SUPPORTED_TYPES.contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "本目录暂不支持该资产家族：" + type.name());
        }
        return EnumSet.of(type);
    }

    private void validatePage(int page, int size) {
        if (page < 0 || page > 100_000 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页参数无效：page >= 0，size 为 1 到 200");
        }
    }

    private AssetRef ref(UnifiedAssetSummary asset) {
        return new AssetRef(asset.assetType().name(), asset.assetKey());
    }

    private String identity(CatalogAssetType type, String assetKey) {
        return type.name() + "\u0000" + assetKey;
    }

    private Instant firstInstant(Instant... values) {
        for (Instant value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    public record UnifiedAssetPage(
        List<UnifiedAssetSummary> content,
        long total,
        int page,
        int size,
        int returned,
        String metadataSource
    ) {}

    public record UnifiedAssetSummary(
        UUID id,
        CatalogAssetType assetType,
        String assetKey,
        String catalogIdentity,
        String assetFamily,
        String subtype,
        String displayName,
        String description,
        String type,
        String service,
        String database,
        String schema,
        String table,
        UUID domainId,
        String classification,
        String warehouseLayer,
        String owner,
        String ownerDept,
        String lifecycleStatus,
        String governanceStatus,
        StatusAxes statusAxes,
        String consumptionEligibility,
        List<String> eligibilityReasons,
        List<ModelRef> modelRefs,
        ServingSync servingSync,
        String qualityStatus,
        Instant updatedAt,
        List<CatalogTagDto> assetTags,
        List<AssetRelationship> relationships,
        String detailRoute
    ) {
        public UnifiedAssetSummary {
            eligibilityReasons = eligibilityReasons == null ? List.of() : List.copyOf(eligibilityReasons);
            modelRefs = modelRefs == null ? List.of() : List.copyOf(modelRefs);
            assetTags = assetTags == null ? List.of() : List.copyOf(assetTags);
            relationships = relationships == null ? List.of() : List.copyOf(relationships);
            qualityStatus = StringUtils.hasText(qualityStatus) ? qualityStatus : "UNKNOWN";
        }

        UnifiedAssetSummary withHydration(List<CatalogTagDto> tags, List<AssetRelationship> relatedAssets) {
            return new UnifiedAssetSummary(
                id,
                assetType,
                assetKey,
                catalogIdentity,
                assetFamily,
                subtype,
                displayName,
                description,
                type,
                service,
                database,
                schema,
                table,
                domainId,
                classification,
                warehouseLayer,
                owner,
                ownerDept,
                lifecycleStatus,
                governanceStatus,
                statusAxes,
                consumptionEligibility,
                eligibilityReasons,
                modelRefs,
                servingSync,
                qualityStatus,
                updatedAt,
                tags,
                relatedAssets,
                detailRoute
            );
        }
    }
}
