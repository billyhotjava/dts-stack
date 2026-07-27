package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogAssetExtension;
import com.yuzhi.dts.platform.domain.catalog.CatalogAssetMapping;
import com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema;
import com.yuzhi.dts.platform.domain.catalog.OpenMetadataAssetCache;
import com.yuzhi.dts.platform.domain.catalog.OpenMetadataColumnCache;
import com.yuzhi.dts.platform.domain.catalog.OpenMetadataLineageCache;
import com.yuzhi.dts.platform.repository.catalog.CatalogAssetExtensionRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogAssetMappingRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.OpenMetadataAssetCacheRepository;
import com.yuzhi.dts.platform.repository.catalog.OpenMetadataColumnCacheRepository;
import com.yuzhi.dts.platform.repository.catalog.OpenMetadataLineageCacheRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.catalog.dto.AssetRef;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogTagDto;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class CatalogAssetPortalService {

    static final long MAX_TAG_FILTER_CANDIDATES = 5_000;

    /**
     * 概览统计的内存聚合扫描上限。批量元数据加载消除逐行查询后，与标签筛选候选上限同档。
     * 达到该上限即视为截断，与 total 的精确性无关。
     */
    static final int ASSET_STATS_SCAN_CAP = 5_000;

    private final OpenMetadataAssetCacheRepository assetRepository;
    private final OpenMetadataColumnCacheRepository columnRepository;
    private final OpenMetadataLineageCacheRepository lineageRepository;
    private final CatalogAssetExtensionRepository extensionRepository;
    private final CatalogAssetMappingRepository mappingRepository;
    private final CatalogDatasetRepository datasetRepository;
    private final CatalogTableSchemaRepository tableSchemaRepository;
    private final CatalogColumnSchemaRepository catalogColumnSchemaRepository;
    private final AccessChecker accessChecker;
    private final CatalogAssetTagService assetTagService;

    public CatalogAssetPortalService(
        OpenMetadataAssetCacheRepository assetRepository,
        OpenMetadataColumnCacheRepository columnRepository,
        OpenMetadataLineageCacheRepository lineageRepository,
        CatalogAssetExtensionRepository extensionRepository,
        CatalogAssetMappingRepository mappingRepository,
        CatalogDatasetRepository datasetRepository,
        CatalogTableSchemaRepository tableSchemaRepository,
        CatalogColumnSchemaRepository catalogColumnSchemaRepository,
        AccessChecker accessChecker,
        CatalogAssetTagService assetTagService
    ) {
        this.assetRepository = assetRepository;
        this.columnRepository = columnRepository;
        this.lineageRepository = lineageRepository;
        this.extensionRepository = extensionRepository;
        this.mappingRepository = mappingRepository;
        this.datasetRepository = datasetRepository;
        this.tableSchemaRepository = tableSchemaRepository;
        this.catalogColumnSchemaRepository = catalogColumnSchemaRepository;
        this.accessChecker = accessChecker;
        this.assetTagService = assetTagService;
    }

    /** 资产概览聚合（地图页数据源）：内部翻页复用 listAssets，可见性规则单一来源。 */
    public CatalogAssetOverviewAggregator.AssetOverview overview(AssetQuery query, String activeDept) {
        final int scanPageSize = 200;
        final int scanMaxPages = ASSET_STATS_SCAN_CAP / scanPageSize;
        List<AssetSummary> rows = new ArrayList<>();
        long total = 0;
        for (int page = 0; page < scanMaxPages; page++) {
            AssetPage result = listAssets(
                new AssetQuery(
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
                    page,
                    scanPageSize
                ),
                activeDept
            );
            rows.addAll(result.content());
            total = result.total();
            if (result.content().isEmpty() || rows.size() >= total) {
                break;
            }
        }
        // 截断只由扫描上限决定。此前用 rows.size() < total 判定，一旦 total 因任何原因
        // 大于实际可枚举的行数（例如 legacy 行取不到），该条件恒真，警告便长期误报。
        boolean truncated = rows.size() >= ASSET_STATS_SCAN_CAP;
        return CatalogAssetOverviewAggregator.aggregate(rows, rows.size(), truncated);
    }

    public AssetPage listAssets(AssetQuery query, String activeDept) {
        if (!query.tagIds().isEmpty()) {
            return listAssetsByTags(query, activeDept);
        }
        return listAssetsWithoutTagFilter(query, activeDept);
    }

    private AssetPage listAssetsWithoutTagFilter(AssetQuery query, String activeDept) {
        int page = Math.max(0, query.page());
        int size = Math.max(1, Math.min(query.size(), 200));
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "lastSyncedAt").and(Sort.by("fqn").ascending()));
        var pageData = assetRepository.findAll(buildSpec(query), pageable);
        // 批量预载扩展/映射/legacy，避免逐行 findFirstByOmAsset + findFirstByFqnIgnoreCase 的 N+1；
        // 可见性判定仍逐行走 canRead，规则不变。
        CandidateMetadata candidateMetadata = loadCandidateMetadata(pageData.getContent());
        List<AssetSummary> items = new ArrayList<>();
        List<UUID> visibleLegacyIds = new ArrayList<>();
        for (OpenMetadataAssetCache asset : pageData.getContent()) {
            CatalogAssetExtension extension = candidateMetadata.extensionsByAssetId().get(asset.getId());
            CatalogAssetMapping mapping = candidateMetadata.mappingsByFqn().get(normalizedFqn(asset.getFqn()));
            CatalogDataset legacy = candidateMetadata.legacyById().get(resolveLegacyDatasetId(extension, mapping));
            if (!canRead(extension, legacy, activeDept)) {
                continue;
            }
            items.add(toSummary(asset, extension, mapping, legacy));
            if (legacy != null && legacy.getId() != null) {
                visibleLegacyIds.add(legacy.getId());
            }
        }
        int openMetadataReturned = items.size();
        // legacy 排在 OpenMetadata 之后组成同一个逻辑列表，其偏移量必须以「未过滤的」
        // OpenMetadata 总数为边界：该值是纯 DB count，逐页恒定。若改用「减去本页隐藏行」
        // 的近似值，边界会逐页漂移，翻页时出现重复或漏行。
        int legacyOffset = (int) Math.max(0, (long) page * size - pageData.getTotalElements());
        int legacyLimit = Math.max(0, size - openMetadataReturned);
        AssetPage legacyPage = listLegacyAssets(query, activeDept, legacyOffset, legacyLimit, visibleLegacyIds);
        if (!legacyPage.content().isEmpty()) {
            items.addAll(legacyPage.content());
        }
        items = hydrateAssetTags(items);
        long hiddenOnCurrentPage = Math.max(0, pageData.getNumberOfElements() - openMetadataReturned);
        long total = Math.max(0, pageData.getTotalElements() - hiddenOnCurrentPage) + legacyPage.total();
        String source = openMetadataReturned > 0 && legacyPage.returned() > 0
            ? "openmetadata-cache+dts-catalog"
            : openMetadataReturned > 0 ? "openmetadata-cache" : "dts-catalog";
        return new AssetPage(items, total, page, size, items.size(), source);
    }

    private AssetPage listAssetsByTags(AssetQuery query, String activeDept) {
        int page = Math.max(0, query.page());
        int size = Math.max(1, Math.min(query.size(), 200));
        Specification<OpenMetadataAssetCache> openMetadataSpec = buildSpec(query);
        Specification<CatalogDataset> legacySpec = buildLegacySpec(query);
        long openMetadataCandidates = assetRepository.count(openMetadataSpec);
        long legacyCandidates = datasetRepository.count(legacySpec);
        if (
            openMetadataCandidates > MAX_TAG_FILTER_CANDIDATES ||
            legacyCandidates > MAX_TAG_FILTER_CANDIDATES - openMetadataCandidates
        ) {
            throw new ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "标签筛选候选超过 5000，请增加关键词、数据域或其他基础条件"
            );
        }
        var openMetadataSort = Sort.by(Sort.Direction.DESC, "lastSyncedAt")
            .and(Sort.by("fqn").ascending())
            .and(Sort.by("id").ascending());
        var legacySort = Sort.by(Sort.Direction.DESC, "lastModifiedDate")
            .and(Sort.by(Sort.Direction.DESC, "createdDate"))
            .and(Sort.by("id").ascending());
        List<OpenMetadataAssetCache> openMetadataAssets = assetRepository.findAll(openMetadataSpec, openMetadataSort);
        List<CatalogDataset> legacyAssets = datasetRepository.findAll(legacySpec, legacySort);
        CandidateMetadata candidateMetadata = loadCandidateMetadata(openMetadataAssets);
        Map<String, AssetSummary> candidates = new LinkedHashMap<>();
        for (OpenMetadataAssetCache asset : openMetadataAssets) {
            CatalogAssetExtension extension = candidateMetadata.extensionsByAssetId().get(asset.getId());
            CatalogAssetMapping mapping = candidateMetadata.mappingsByFqn().get(normalizedFqn(asset.getFqn()));
            CatalogDataset legacy = candidateMetadata.legacyById().get(resolveLegacyDatasetId(extension, mapping));
            if (!canRead(extension, legacy, activeDept)) {
                continue;
            }
            AssetSummary summary = toSummary(asset, extension, mapping, legacy);
            candidates.putIfAbsent(assetIdentity(summary), summary);
        }
        for (CatalogDataset dataset : legacyAssets) {
            if (!canRead(null, dataset, activeDept)) {
                continue;
            }
            AssetSummary summary = toLegacySummary(dataset);
            candidates.putIfAbsent(assetIdentity(summary), summary);
        }
        Set<String> matchingAssetKeys =
            assetTagService.findMatchingAssetKeysWithin(
                "DATASET",
                candidates
                    .values()
                    .stream()
                    .map(AssetSummary::assetKey)
                    .toList(),
                query.tagIds()
            );
        if (matchingAssetKeys.isEmpty()) {
            return new AssetPage(
                List.of(),
                0,
                page,
                size,
                0,
                "openmetadata-cache+dts-catalog"
            );
        }
        candidates
            .values()
            .removeIf(summary ->
                !matchingAssetKeys.contains(summary.assetKey())
            );

        List<AssetSummary> filtered = new ArrayList<>(candidates.values());
        filtered.sort(assetSummaryComparator());
        long offset = (long) page * size;
        int fromIndex = offset >= filtered.size() ? filtered.size() : (int) offset;
        int toIndex = Math.min(fromIndex + size, filtered.size());
        List<AssetSummary> content = hydrateAssetTags(filtered.subList(fromIndex, toIndex));
        return new AssetPage(
            content,
            filtered.size(),
            page,
            size,
            content.size(),
            resolveMetadataSource(content)
        );
    }

    private CandidateMetadata loadCandidateMetadata(List<OpenMetadataAssetCache> assets) {
        Map<UUID, CatalogAssetExtension> extensionsByAssetId = new LinkedHashMap<>();
        for (CatalogAssetExtension extension : assets.isEmpty() ? List.<CatalogAssetExtension>of() : extensionRepository.findByOmAssetIn(assets)) {
            if (extension != null && extension.getOmAsset() != null && extension.getOmAsset().getId() != null) {
                extensionsByAssetId.putIfAbsent(extension.getOmAsset().getId(), extension);
            }
        }

        Set<String> candidateFqns = new LinkedHashSet<>();
        assets.stream().map(OpenMetadataAssetCache::getFqn).map(this::normalizedFqn).filter(StringUtils::hasText).forEach(candidateFqns::add);
        Map<String, CatalogAssetMapping> mappingsByFqn = new LinkedHashMap<>();
        for (
            CatalogAssetMapping mapping : candidateFqns.isEmpty()
                ? List.<CatalogAssetMapping>of()
                : mappingRepository.findByNormalizedFqnIn(candidateFqns)
        ) {
            String normalized = mapping == null ? null : normalizedFqn(mapping.getFqn());
            if (StringUtils.hasText(normalized) && candidateFqns.contains(normalized)) {
                mappingsByFqn.putIfAbsent(normalized, mapping);
            }
        }

        Set<UUID> legacyIds = new LinkedHashSet<>();
        extensionsByAssetId.values().stream().map(CatalogAssetExtension::getLegacyDatasetId).filter(java.util.Objects::nonNull).forEach(legacyIds::add);
        mappingsByFqn.values().stream().map(CatalogAssetMapping::getLegacyDatasetId).filter(java.util.Objects::nonNull).forEach(legacyIds::add);
        Map<UUID, CatalogDataset> legacyById = new LinkedHashMap<>();
        if (!legacyIds.isEmpty()) {
            datasetRepository.findAllById(legacyIds).forEach(dataset -> legacyById.put(dataset.getId(), dataset));
        }
        return new CandidateMetadata(extensionsByAssetId, mappingsByFqn, legacyById);
    }

    private UUID resolveLegacyDatasetId(CatalogAssetExtension extension, CatalogAssetMapping mapping) {
        if (extension != null && extension.getLegacyDatasetId() != null) {
            return extension.getLegacyDatasetId();
        }
        return mapping == null ? null : mapping.getLegacyDatasetId();
    }

    private String normalizedFqn(String fqn) {
        return StringUtils.hasText(fqn) ? fqn.trim().toLowerCase(Locale.ROOT) : null;
    }

    private Comparator<AssetSummary> assetSummaryComparator() {
        return Comparator
            .comparing(
                AssetSummary::lastSyncedAt,
                Comparator.nullsLast(Comparator.reverseOrder())
            )
            .thenComparing(AssetSummary::assetKey, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(summary -> summary.id() == null ? "" : summary.id().toString());
    }

    private record CandidateMetadata(
        Map<UUID, CatalogAssetExtension> extensionsByAssetId,
        Map<String, CatalogAssetMapping> mappingsByFqn,
        Map<UUID, CatalogDataset> legacyById
    ) {}

    /**
     * 取 legacy 目录中位于组合列表 {@code offset} 之后的至多 {@code limit} 行。
     *
     * <p>legacy 与 OpenMetadata 是两个独立数据源，拼成一个逻辑列表时必须按组合偏移量取数。
     * 历史实现沿用 OpenMetadata 的页码，导致 OpenMetadata 占满首页时 legacy 行永远取不到。
     * 可见性过滤在查询之后进行，因此 skip 必须作用于「已过滤」的行，而不是原始行。
     */
    private AssetPage listLegacyAssets(AssetQuery query, String activeDept, int offset, int limit, List<UUID> excludedIds) {
        Sort sort = Sort.by(Sort.Direction.DESC, "lastModifiedDate").and(Sort.by(Sort.Direction.DESC, "createdDate"));
        int fetchSize = Math.max(1, offset + Math.max(limit, 1));
        Page<CatalogDataset> legacyPage = datasetRepository.findAll(buildLegacySpec(query), PageRequest.of(0, fetchSize, sort));
        List<CatalogDataset> visible = legacyPage
            .getContent()
            .stream()
            .filter(dataset -> dataset.getId() == null || excludedIds == null || !excludedIds.contains(dataset.getId()))
            .filter(dataset -> canRead(null, dataset, activeDept))
            .toList();
        List<AssetSummary> items = visible
            .stream()
            .skip(offset)
            .limit(Math.max(0, limit))
            .map(this::toLegacySummary)
            .toList();
        long hidden = Math.max(0, legacyPage.getNumberOfElements() - visible.size());
        long total = Math.max(0, legacyPage.getTotalElements() - hidden);
        return new AssetPage(items, total, 0, fetchSize, items.size(), "dts-catalog");
    }

    public AssetDetail getAsset(UUID id, String activeDept) {
        OpenMetadataAssetCache asset = assetRepository
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "资产不存在"));
        CatalogAssetExtension extension = extensionRepository.findFirstByOmAsset(asset).orElse(null);
        CatalogAssetMapping mapping = mappingRepository.findFirstByFqnIgnoreCase(asset.getFqn()).orElse(null);
        CatalogDataset legacy = resolveContractLegacy(extension, mapping);
        if (!canRead(extension, legacy, activeDept)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "资产不存在或无权访问");
        }
        List<ColumnSummary> columns = columnRepository.findByAssetOrderByOrdinalPositionAsc(asset).stream().map(this::toColumn).toList();
        AssetSummary summary = hydrateAssetTags(List.of(toSummary(asset, extension, mapping, legacy))).get(0);
        return new AssetDetail(summary, columns, asset.getRawJson(), asset.getProfileJson());
    }

    public CatalogAssetContract getAssetContract(UUID id, String activeDept) {
        Optional<OpenMetadataAssetCache> assetOptional = assetRepository.findById(id);
        if (assetOptional.isPresent()) {
            OpenMetadataAssetCache asset = assetOptional.orElseThrow();
            CatalogAssetExtension extension = extensionRepository.findFirstByOmAsset(asset).orElse(null);
            CatalogAssetMapping mapping = mappingRepository.findFirstByFqnIgnoreCase(asset.getFqn()).orElse(null);
            CatalogDataset legacy = resolveContractLegacy(extension, mapping);
            if (!canRead(extension, legacy, activeDept)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "资产不存在或无权访问");
            }
            return CatalogAssetContractMapper.fromOpenMetadata(asset, extension, mapping, legacy);
        }

        CatalogDataset legacy = datasetRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "资产不存在"));
        if (!canRead(null, legacy, activeDept)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "资产不存在或无权访问");
        }
        return CatalogAssetContractMapper.fromLegacy(legacy);
    }

    public CatalogAssetSchemaContract getAssetSchemaContract(UUID id, String activeDept) {
        Optional<OpenMetadataAssetCache> assetOptional = assetRepository.findById(id);
        if (assetOptional.isPresent()) {
            OpenMetadataAssetCache asset = assetOptional.orElseThrow();
            CatalogAssetExtension extension = extensionRepository.findFirstByOmAsset(asset).orElse(null);
            CatalogAssetMapping mapping = mappingRepository.findFirstByFqnIgnoreCase(asset.getFqn()).orElse(null);
            CatalogDataset legacy = resolveContractLegacy(extension, mapping);
            if (!canRead(extension, legacy, activeDept)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "资产不存在或无权访问");
            }
            CatalogAssetContract contract = CatalogAssetContractMapper.fromOpenMetadata(asset, extension, mapping, legacy);
            List<OpenMetadataColumnCache> columns = columnRepository.findByAssetOrderByOrdinalPositionAsc(asset);
            if (!columns.isEmpty() || legacy == null) {
                return CatalogAssetSchemaContractMapper.fromOpenMetadata(contract, columns);
            }
            return buildLegacySchemaContract(contract, legacy);
        }

        CatalogDataset legacy = datasetRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "资产不存在"));
        if (!canRead(null, legacy, activeDept)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "资产不存在或无权访问");
        }
        return buildLegacySchemaContract(CatalogAssetContractMapper.fromLegacy(legacy), legacy);
    }

    public GovernanceGapReport governanceGaps(AssetQuery query, String activeDept) {
        AssetPage page = listAssets(query, activeDept);
        List<GovernanceGapAsset> content = new ArrayList<>();
        Map<String, Long> severityCounts = new LinkedHashMap<>();
        Map<String, Long> gapCounts = new LinkedHashMap<>();
        long inspected = 0;
        long skipped = 0;
        for (AssetSummary summary : page.content()) {
            try {
                CatalogAssetContract asset = getAssetContract(summary.id(), activeDept);
                CatalogAssetSchemaContract schema = getAssetSchemaContract(summary.id(), activeDept);
                CatalogGovernanceGapEvaluation evaluation = CatalogGovernanceGapEvaluator.evaluate(asset, schema, hasLineageEvidence(asset));
                inspected++;
                increment(severityCounts, evaluation.severity());
                for (String gap : evaluation.blockingGaps()) {
                    increment(gapCounts, "blocking:" + gap);
                }
                for (String gap : evaluation.warningGaps()) {
                    increment(gapCounts, "warning:" + gap);
                }
                if (!"READY".equals(evaluation.severity())) {
                    content.add(
                        new GovernanceGapAsset(
                            asset.id(),
                            asset.displayName(),
                            asset.fqn(),
                            asset.assetKey(),
                            asset.grantAssetType(),
                            asset.grantAssetId(),
                            asset.lifecycleStatus(),
                            asset.governanceStatus(),
                            evaluation.severity(),
                            evaluation.blockingGaps(),
                            evaluation.warningGaps(),
                            asset.metadataSource()
                        )
                    );
                }
            } catch (ResponseStatusException ex) {
                if (HttpStatus.NOT_FOUND.equals(ex.getStatusCode())) {
                    skipped++;
                    continue;
                }
                throw ex;
            }
        }
        return new GovernanceGapReport(
            content,
            severityCounts,
            gapCounts,
            inspected,
            skipped,
            page.total(),
            page.page(),
            page.size(),
            page.metadataSource()
        );
    }

    public CatalogLineageFailureReport lineageFailures(AssetQuery query, String activeDept) {
        return CatalogLineageFailureReportBuilder.fromGovernanceGapReport(governanceGaps(query, activeDept));
    }

    @Transactional
    public AssetDetail updateGovernance(UUID id, GovernanceUpdate update, String activeDept) {
        OpenMetadataAssetCache asset = assetRepository
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "资产不存在"));
        CatalogAssetExtension extension = extensionRepository.findFirstByOmAsset(asset).orElseGet(CatalogAssetExtension::new);
        CatalogAssetMapping mapping = mappingRepository.findFirstByFqnIgnoreCase(asset.getFqn()).orElse(null);
        CatalogDataset legacy = resolveContractLegacy(extension, mapping);
        if (!isSuperAdmin() && !canRead(extension, legacy, activeDept)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "资产不存在或无权访问");
        }
        extension.setOmAsset(asset);
        if (update != null) {
            if (update.domainId() != null) {
                extension.setDomainId(update.domainId());
            }
            if (update.classification() != null) {
                extension.setClassification(normalize(update.classification()));
            }
            if (update.warehouseLayer() != null) {
                extension.setWarehouseLayer(normalize(update.warehouseLayer()));
            }
            if (update.ownerDept() != null) {
                extension.setOwnerDept(blankToNull(update.ownerDept()));
            }
            if (update.businessOwner() != null) {
                extension.setBusinessOwner(blankToNull(update.businessOwner()));
            }
            if (update.lifecycleStatus() != null) {
                extension.setLifecycleStatus(normalize(update.lifecycleStatus()));
            }
            if (update.enabled() != null) {
                extension.setEnabled(update.enabled());
            }
            if (update.securityPolicyRefs() != null) {
                extension.setSecurityPolicyRefs(blankToNull(update.securityPolicyRefs()));
            }
        }
        if (legacy != null) {
            extension.setLegacyDatasetId(legacy.getId());
        }
        extension.setGovernanceStatus(resolveGovernanceStatus(extension));
        extensionRepository.save(extension);
        List<ColumnSummary> columns = columnRepository.findByAssetOrderByOrdinalPositionAsc(asset).stream().map(this::toColumn).toList();
        AssetSummary summary = hydrateAssetTags(List.of(toSummary(asset, extension, mapping, legacy))).get(0);
        return new AssetDetail(summary, columns, asset.getRawJson(), asset.getProfileJson());
    }

    public LineageView getLineage(UUID id, String activeDept) {
        OpenMetadataAssetCache asset = assetRepository
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "资产不存在"));
        CatalogAssetExtension extension = extensionRepository.findFirstByOmAsset(asset).orElse(null);
        CatalogAssetMapping mapping = mappingRepository.findFirstByFqnIgnoreCase(asset.getFqn()).orElse(null);
        CatalogDataset legacy = extension != null && extension.getLegacyDatasetId() != null
            ? datasetRepository.findById(extension.getLegacyDatasetId()).orElse(null)
            : null;
        if (!canRead(extension, legacy, activeDept)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "资产不存在或无权访问");
        }
        List<OpenMetadataLineageCache> cached = lineageRepository.findByFromOmEntityIdOrToOmEntityIdOrFromFqnIgnoreCaseOrToFqnIgnoreCase(
            asset.getOmEntityId(),
            asset.getOmEntityId(),
            asset.getFqn(),
            asset.getFqn()
        );
        Map<String, LineageNode> nodes = new LinkedHashMap<>();
        nodes.put(asset.getFqn(), new LineageNode(asset.getFqn(), asset.getOmEntityId(), asset.getDisplayName(), asset.getFqn(), "ROOT"));
        List<LineageEdge> edges = new ArrayList<>();
        for (OpenMetadataLineageCache edge : cached) {
            if (edge == null || !StringUtils.hasText(edge.getFromFqn()) || !StringUtils.hasText(edge.getToFqn())) {
                continue;
            }
            nodes.putIfAbsent(
                edge.getFromFqn(),
                new LineageNode(edge.getFromFqn(), edge.getFromOmEntityId(), edge.getFromFqn(), edge.getFromFqn(), "TABLE")
            );
            nodes.putIfAbsent(
                edge.getToFqn(),
                new LineageNode(edge.getToFqn(), edge.getToOmEntityId(), edge.getToFqn(), edge.getToFqn(), "TABLE")
            );
            edges.add(new LineageEdge(edge.getId(), edge.getFromFqn(), edge.getToFqn(), edge.getSource(), edge.getEdgeType(), edge.getLastSyncedAt()));
        }
        return new LineageView(asset.getId(), asset.getFqn(), new ArrayList<>(nodes.values()), edges, "openmetadata-cache");
    }

    public MappingDiagnostics diagnostics() {
        long assetCount = assetRepository.count();
        long extensionCount = extensionRepository.count();
        List<CatalogAssetMapping> mappings = mappingRepository.findAll();
        long matched = mappings.stream().filter(item -> "MATCHED".equalsIgnoreCase(item.getMatchStatus())).count();
        long unmatched = mappings.stream().filter(item -> "UNMATCHED".equalsIgnoreCase(item.getMatchStatus())).count();
        long manualReview = mappings.stream().filter(item -> "MANUAL_REVIEW".equalsIgnoreCase(item.getMatchStatus())).count();
        List<MappingIssue> issues = mappings
            .stream()
            .filter(item -> !"MATCHED".equalsIgnoreCase(item.getMatchStatus()))
            .limit(100)
            .map(item -> new MappingIssue(item.getFqn(), item.getMatchStatus(), item.getMatchReason(), item.getConfidence()))
            .toList();
        return new MappingDiagnostics(assetCount, extensionCount, mappings.size(), matched, unmatched, manualReview, issues);
    }

    private Specification<OpenMetadataAssetCache> buildSpec(AssetQuery query) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(query.keyword())) {
                String like = "%" + query.keyword().trim().toLowerCase() + "%";
                predicates.add(
                    cb.or(
                        cb.like(cb.lower(root.get("fqn")), like),
                        cb.like(cb.lower(root.get("tableName")), like),
                        cb.like(cb.lower(root.get("displayName")), like),
                        cb.like(cb.lower(root.get("description")), like)
                    )
                );
            }
            if (StringUtils.hasText(query.service())) {
                predicates.add(cb.equal(cb.lower(root.get("serviceName")), query.service().trim().toLowerCase()));
            }
            if (StringUtils.hasText(query.type())) {
                predicates.add(cb.equal(cb.upper(root.get("sourceType")), query.type().trim().toUpperCase()));
            }
            if (StringUtils.hasText(query.database())) {
                predicates.add(cb.equal(cb.lower(root.get("databaseName")), query.database().trim().toLowerCase()));
            }
            if (StringUtils.hasText(query.schema())) {
                predicates.add(cb.equal(cb.lower(root.get("schemaName")), query.schema().trim().toLowerCase()));
            }
            if (StringUtils.hasText(query.syncStatus())) {
                predicates.add(cb.equal(cb.upper(root.get("syncStatus")), query.syncStatus().trim().toUpperCase()));
            }
            if (
                StringUtils.hasText(query.classification()) ||
                StringUtils.hasText(query.warehouseLayer()) ||
                StringUtils.hasText(query.ownerDept()) ||
                StringUtils.hasText(query.governanceStatus()) ||
                query.domainId() != null ||
                query.domainUnassigned()
            ) {
                var extensionSubquery = cq.subquery(UUID.class);
                var extensionRoot = extensionSubquery.from(CatalogAssetExtension.class);
                List<Predicate> extensionPredicates = new ArrayList<>();
                extensionPredicates.add(cb.equal(extensionRoot.get("omAsset").get("id"), root.get("id")));
                if (StringUtils.hasText(query.classification())) {
                    extensionPredicates.add(
                        cb.equal(cb.upper(extensionRoot.get("classification")), query.classification().trim().toUpperCase())
                    );
                }
                if (StringUtils.hasText(query.warehouseLayer())) {
                    extensionPredicates.add(
                        cb.equal(cb.upper(extensionRoot.get("warehouseLayer")), query.warehouseLayer().trim().toUpperCase())
                    );
                }
                if (query.domainId() != null) {
                    extensionPredicates.add(cb.equal(extensionRoot.get("domainId"), query.domainId()));
                }
                if (query.domainUnassigned()) {
                    extensionPredicates.add(cb.isNull(extensionRoot.get("domainId")));
                }
                if (StringUtils.hasText(query.ownerDept())) {
                    extensionPredicates.add(cb.equal(extensionRoot.get("ownerDept"), query.ownerDept().trim()));
                }
                if (StringUtils.hasText(query.governanceStatus())) {
                    extensionPredicates.add(
                        cb.equal(cb.upper(extensionRoot.get("governanceStatus")), query.governanceStatus().trim().toUpperCase())
                    );
                }
                extensionSubquery.select(extensionRoot.get("id")).where(extensionPredicates.toArray(Predicate[]::new));
                predicates.add(cb.exists(extensionSubquery));
            }
            if (StringUtils.hasText(query.matchStatus())) {
                var mappingSubquery = cq.subquery(UUID.class);
                var mappingRoot = mappingSubquery.from(CatalogAssetMapping.class);
                mappingSubquery
                    .select(mappingRoot.get("id"))
                    .where(
                        cb.and(
                            cb.equal(cb.lower(mappingRoot.get("fqn")), cb.lower(root.get("fqn"))),
                            cb.equal(cb.upper(mappingRoot.get("matchStatus")), query.matchStatus().trim().toUpperCase())
                        )
                    );
                predicates.add(cb.exists(mappingSubquery));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Specification<CatalogDataset> buildLegacySpec(AssetQuery query) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.or(cb.isNull(root.get("enabled")), cb.isTrue(root.get("enabled"))));
            if (query.domainUnassigned()) {
                predicates.add(cb.isNull(root.get("domain").get("id")));
            } else if (query.domainId() != null) {
                predicates.add(cb.equal(root.get("domain").get("id"), query.domainId()));
            }
            if (StringUtils.hasText(query.keyword())) {
                String like = "%" + query.keyword().trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(
                    cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("owner")), like),
                        cb.like(cb.lower(root.get("ownerDept")), like),
                        cb.like(cb.lower(root.get("tags")), like),
                        cb.like(cb.lower(root.get("description")), like),
                        cb.like(cb.lower(root.get("hiveDatabase")), like),
                        cb.like(cb.lower(root.get("hiveTable")), like)
                    )
                );
            }
            if (StringUtils.hasText(query.type())) {
                predicates.add(cb.equal(cb.lower(root.get("type")), query.type().trim().toLowerCase(Locale.ROOT)));
            }
            if (StringUtils.hasText(query.database())) {
                predicates.add(cb.equal(cb.lower(root.get("hiveDatabase")), query.database().trim().toLowerCase(Locale.ROOT)));
            }
            if (StringUtils.hasText(query.schema())) {
                predicates.add(cb.equal(cb.lower(root.get("hiveDatabase")), query.schema().trim().toLowerCase(Locale.ROOT)));
            }
            if (StringUtils.hasText(query.classification())) {
                predicates.add(cb.equal(cb.lower(root.get("classification")), query.classification().trim().toLowerCase(Locale.ROOT)));
            }
            if (StringUtils.hasText(query.warehouseLayer())) {
                predicates.add(cb.equal(cb.lower(root.get("warehouseLayer")), query.warehouseLayer().trim().toLowerCase(Locale.ROOT)));
            }
            if (StringUtils.hasText(query.ownerDept())) {
                predicates.add(cb.equal(cb.lower(root.get("ownerDept")), query.ownerDept().trim().toLowerCase(Locale.ROOT)));
            }
            if (StringUtils.hasText(query.governanceStatus())) {
                String expected = query.governanceStatus().trim().toUpperCase(Locale.ROOT);
                if ("GOVERNED".equals(expected)) {
                    predicates.add(cb.isNotNull(root.get("classification")));
                    predicates.add(cb.isNotNull(root.get("domain").get("id")));
                } else if ("PENDING_CLASSIFICATION".equals(expected)) {
                    predicates.add(cb.or(cb.isNull(root.get("classification")), cb.equal(root.get("classification"), "")));
                } else if ("PENDING_DOMAIN".equals(expected)) {
                    predicates.add(cb.isNull(root.get("domain").get("id")));
                } else if ("DISABLED".equals(expected)) {
                    predicates.clear();
                    predicates.add(cb.isFalse(root.get("enabled")));
                }
            }
            if (StringUtils.hasText(query.matchStatus())) {
                String expected = query.matchStatus().trim().toUpperCase(Locale.ROOT);
                if (!"DTS_NATIVE".equals(expected) && !"MATCHED".equals(expected)) {
                    predicates.add(cb.disjunction());
                }
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private boolean canRead(CatalogAssetExtension extension, CatalogDataset legacy, String activeDept) {
        if (legacy != null) {
            return accessChecker.canRead(legacy) && accessChecker.departmentAllowed(legacy, activeDept);
        }
        if (isSuperAdmin()) {
            return true;
        }
        if (extension == null || Boolean.FALSE.equals(extension.getEnabled())) {
            return false;
        }
        CatalogDataset synthetic = new CatalogDataset();
        synthetic.setName("openmetadata-asset");
        synthetic.setEnabled(extension.getEnabled());
        synthetic.setClassification(extension.getClassification());
        synthetic.setOwnerDept(extension.getOwnerDept());
        return accessChecker.canRead(synthetic) && accessChecker.departmentAllowed(synthetic, activeDept);
    }

    private boolean isSuperAdmin() {
        return SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.OP_ADMIN, AuthoritiesConstants.ADMIN) ||
            SecurityUtils.isOpAdminAccount();
    }

    private AssetSummary toSummary(
        OpenMetadataAssetCache asset,
        CatalogAssetExtension extension,
        CatalogAssetMapping mapping,
        CatalogDataset legacy
    ) {
        CatalogAssetContract contract = CatalogAssetContractMapper.fromOpenMetadata(asset, extension, mapping, legacy);
        String governanceStatus = extension != null ? extension.getGovernanceStatus() : "PENDING_GOVERNANCE";
        String matchStatus = mapping != null ? mapping.getMatchStatus() : "UNMATCHED";
        return new AssetSummary(
            asset.getId(),
            asset.getOmEntityId(),
            asset.getFqn(),
            asset.getSourceType(),
            asset.getServiceName(),
            asset.getDatabaseName(),
            asset.getSchemaName(),
            asset.getTableName(),
            asset.getDisplayName(),
            firstNonBlank(extension != null ? extension.getClassification() : null, legacy != null ? legacy.getClassification() : null),
            firstNonBlank(extension != null ? extension.getWarehouseLayer() : null, legacy != null ? legacy.getWarehouseLayer() : null),
            firstNonBlank(extension != null ? extension.getOwnerDept() : null, legacy != null ? legacy.getOwnerDept() : null),
            firstNonBlank(extension != null ? extension.getBusinessOwner() : null, legacy != null ? legacy.getOwner() : null, asset.getOwnerName()),
            extension != null ? extension.getDomainId() : (legacy != null && legacy.getDomain() != null ? legacy.getDomain().getId() : null),
            firstNonBlank(extension != null ? extension.getLifecycleStatus() : null, legacy != null ? legacy.getLifecycleStatus() : null),
            firstNonBlank(asset.getDescription(), legacy != null ? legacy.getDescription() : null),
            governanceStatus,
            matchStatus,
            mapping != null ? mapping.getMatchReason() : null,
            contract.legacyDatasetId(),
            extension != null ? extension.getSecurityPolicyRefs() : null,
            asset.getColumnCount(),
            asset.getSyncStatus(),
            asset.getSyncMessage(),
            asset.getLastSyncedAt(),
            "openmetadata-cache",
            contract.assetType(),
            contract.assetKey(),
            List.of()
        );
    }

    private AssetSummary toLegacySummary(CatalogDataset dataset) {
        CatalogAssetContract contract = CatalogAssetContractMapper.fromLegacy(dataset);
        UUID domainId = dataset.getDomain() != null ? dataset.getDomain().getId() : null;
        String governanceStatus = resolveLegacyGovernanceStatus(dataset);
        return new AssetSummary(
            dataset.getId(),
            null,
            buildLegacyFqn(dataset),
            normalizeType(dataset.getType()),
            null,
            dataset.getHiveDatabase(),
            dataset.getHiveDatabase(),
            dataset.getHiveTable(),
            firstNonBlank(dataset.getName(), dataset.getHiveTable()),
            dataset.getClassification(),
            dataset.getWarehouseLayer(),
            dataset.getOwnerDept(),
            dataset.getOwner(),
            domainId,
            dataset.getLifecycleStatus(),
            dataset.getDescription(),
            governanceStatus,
            "DTS_NATIVE",
            "DTS原生资产，尚未与OpenMetadata缓存合并",
            dataset.getId(),
            null,
            null,
            Boolean.FALSE.equals(dataset.getEnabled()) ? "DISABLED" : "SYNCED",
            null,
            dataset.getLastModifiedDate() != null ? dataset.getLastModifiedDate() : dataset.getCreatedDate(),
            "dts-catalog",
            contract.assetType(),
            contract.assetKey(),
            List.of()
        );
    }

    private List<AssetSummary> hydrateAssetTags(List<AssetSummary> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return List.of();
        }
        List<AssetRef> refs = summaries.stream().map(summary -> new AssetRef(summary.assetType(), summary.assetKey())).toList();
        Map<AssetRef, List<CatalogTagDto>> tagsByAsset = assetTagService.listAssetTags(refs);
        return summaries
            .stream()
            .map(summary -> {
                AssetRef ref = new AssetRef(summary.assetType(), summary.assetKey());
                return summary.withAssetTags(tagsByAsset.getOrDefault(ref, List.of()));
            })
            .toList();
    }

    private String assetIdentity(AssetSummary summary) {
        return summary.assetType() + "\u0000" + summary.assetKey();
    }

    private String resolveMetadataSource(List<AssetSummary> summaries) {
        Set<String> sources = new LinkedHashSet<>();
        summaries.stream().map(AssetSummary::metadataSource).filter(StringUtils::hasText).forEach(sources::add);
        if (sources.size() > 1) {
            return "openmetadata-cache+dts-catalog";
        }
        return sources.stream().findFirst().orElse("openmetadata-cache+dts-catalog");
    }

    private String buildLegacyFqn(CatalogDataset dataset) {
        String database = blankToNull(dataset.getHiveDatabase());
        String table = blankToNull(dataset.getHiveTable());
        if (database != null && table != null) {
            return database + "." + table;
        }
        return firstNonBlank(table, dataset.getName(), dataset.getId() != null ? dataset.getId().toString() : null);
    }

    private String normalizeType(String value) {
        String text = blankToNull(value);
        return text == null ? "DATASET" : text.toUpperCase(Locale.ROOT);
    }

    private String resolveLegacyGovernanceStatus(CatalogDataset dataset) {
        return CatalogAssetGovernancePolicy.resolveGovernanceStatus(dataset);
    }

    private CatalogDataset resolveContractLegacy(CatalogAssetExtension extension, CatalogAssetMapping mapping) {
        UUID legacyDatasetId = extension != null ? extension.getLegacyDatasetId() : null;
        if (legacyDatasetId == null && mapping != null) {
            legacyDatasetId = mapping.getLegacyDatasetId();
        }
        return legacyDatasetId == null ? null : datasetRepository.findById(legacyDatasetId).orElse(null);
    }

    private CatalogAssetSchemaContract buildLegacySchemaContract(CatalogAssetContract contract, CatalogDataset dataset) {
        CatalogTableSchema table = resolveLegacyTable(dataset);
        if (table == null) {
            return CatalogAssetSchemaContractMapper.fromLegacy(contract, List.of());
        }
        List<CatalogColumnSchema> columns = catalogColumnSchemaRepository.findByTable(table);
        return CatalogAssetSchemaContractMapper.fromLegacy(contract, columns);
    }

    private CatalogTableSchema resolveLegacyTable(CatalogDataset dataset) {
        List<CatalogTableSchema> tables = tableSchemaRepository.findByDataset(dataset);
        if (tables.isEmpty()) {
            return null;
        }
        String hiveTable = blankToNull(dataset.getHiveTable());
        if (hiveTable != null) {
            return tables
                .stream()
                .filter(table -> table != null && hiveTable.equalsIgnoreCase(blankToNull(table.getName())))
                .findFirst()
                .orElse(tables.get(0));
        }
        return tables.get(0);
    }

    private boolean hasLineageEvidence(CatalogAssetContract asset) {
        if (asset == null || (!StringUtils.hasText(asset.omEntityId()) && !StringUtils.hasText(asset.fqn()))) {
            return false;
        }
        return !lineageRepository
            .findByFromOmEntityIdOrToOmEntityIdOrFromFqnIgnoreCaseOrToFqnIgnoreCase(
                asset.omEntityId(),
                asset.omEntityId(),
                asset.fqn(),
                asset.fqn()
            )
            .isEmpty();
    }

    private void increment(Map<String, Long> counts, String key) {
        if (StringUtils.hasText(key)) {
            counts.merge(key, 1L, Long::sum);
        }
    }

    private ColumnSummary toColumn(OpenMetadataColumnCache column) {
        return new ColumnSummary(
            column.getId(),
            column.getOmColumnFqn(),
            column.getName(),
            column.getDataType(),
            column.getDescription(),
            column.getOrdinalPosition(),
            column.getTagsJson(),
            column.getProfileJson()
        );
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

    private String resolveGovernanceStatus(CatalogAssetExtension extension) {
        if (extension == null || Boolean.FALSE.equals(extension.getEnabled())) {
            return "DISABLED";
        }
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

    private String normalize(String value) {
        String text = blankToNull(value);
        return text == null ? null : text.toUpperCase();
    }

    private String blankToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    public record AssetQuery(
        String keyword,
        String service,
        String type,
        String database,
        String schema,
        String syncStatus,
        String classification,
        String warehouseLayer,
        String ownerDept,
        String governanceStatus,
        String matchStatus,
        UUID domainId,
        boolean domainUnassigned,
        List<UUID> tagIds,
        int page,
        int size
    ) {
        public AssetQuery {
            tagIds = tagIds == null ? List.of() : List.copyOf(tagIds);
        }

        public AssetQuery(
            String keyword,
            String service,
            String type,
            String database,
            String schema,
            String syncStatus,
            String classification,
            String warehouseLayer,
            String ownerDept,
            String governanceStatus,
            String matchStatus,
            UUID domainId,
            boolean domainUnassigned,
            int page,
            int size
        ) {
            this(
                keyword,
                service,
                type,
                database,
                schema,
                syncStatus,
                classification,
                warehouseLayer,
                ownerDept,
                governanceStatus,
                matchStatus,
                domainId,
                domainUnassigned,
                List.of(),
                page,
                size
            );
        }
    }

    public record AssetPage(List<AssetSummary> content, long total, int page, int size, int returned, String metadataSource) {}

    public record AssetSummary(
        UUID id,
        String omEntityId,
        String fqn,
        String type,
        String service,
        String database,
        String schema,
        String table,
        String displayName,
        String classification,
        String warehouseLayer,
        String ownerDept,
        String owner,
        UUID domainId,
        String lifecycleStatus,
        String description,
        String governanceStatus,
        String matchStatus,
        String matchReason,
        UUID legacyDatasetId,
        String securityPolicyRefs,
        Integer columnCount,
        String syncStatus,
        String syncMessage,
        java.time.Instant lastSyncedAt,
        String metadataSource,
        String assetType,
        String assetKey,
        List<CatalogTagDto> assetTags
    ) {
        public AssetSummary {
            assetTags = assetTags == null ? List.of() : List.copyOf(assetTags);
        }

        public AssetSummary(
            UUID id,
            String omEntityId,
            String fqn,
            String type,
            String service,
            String database,
            String schema,
            String table,
            String displayName,
            String classification,
            String warehouseLayer,
            String ownerDept,
            String owner,
            UUID domainId,
            String lifecycleStatus,
            String description,
            String governanceStatus,
            String matchStatus,
            String matchReason,
            UUID legacyDatasetId,
            String securityPolicyRefs,
            Integer columnCount,
            String syncStatus,
            String syncMessage,
            java.time.Instant lastSyncedAt,
            String metadataSource
        ) {
            this(
                id,
                omEntityId,
                fqn,
                type,
                service,
                database,
                schema,
                table,
                displayName,
                classification,
                warehouseLayer,
                ownerDept,
                owner,
                domainId,
                lifecycleStatus,
                description,
                governanceStatus,
                matchStatus,
                matchReason,
                legacyDatasetId,
                securityPolicyRefs,
                columnCount,
                syncStatus,
                syncMessage,
                lastSyncedAt,
                metadataSource,
                "DATASET",
                null,
                List.of()
            );
        }

        public AssetSummary withAssetTags(List<CatalogTagDto> tags) {
            return new AssetSummary(
                id,
                omEntityId,
                fqn,
                type,
                service,
                database,
                schema,
                table,
                displayName,
                classification,
                warehouseLayer,
                ownerDept,
                owner,
                domainId,
                lifecycleStatus,
                description,
                governanceStatus,
                matchStatus,
                matchReason,
                legacyDatasetId,
                securityPolicyRefs,
                columnCount,
                syncStatus,
                syncMessage,
                lastSyncedAt,
                metadataSource,
                assetType,
                assetKey,
                tags
            );
        }
    }

    public record ColumnSummary(
        UUID id,
        String omColumnFqn,
        String name,
        String dataType,
        String description,
        Integer ordinalPosition,
        String tagsJson,
        String profileJson
    ) {}

    public record AssetDetail(AssetSummary asset, List<ColumnSummary> columns, String rawJson, String profileJson) {}

    public record GovernanceGapAsset(
        UUID id,
        String displayName,
        String fqn,
        String assetKey,
        String grantAssetType,
        String grantAssetId,
        String lifecycleStatus,
        String governanceStatus,
        String severity,
        List<String> blockingGaps,
        List<String> warningGaps,
        String metadataSource
    ) {}

    public record GovernanceGapReport(
        List<GovernanceGapAsset> content,
        Map<String, Long> severityCounts,
        Map<String, Long> gapCounts,
        long inspected,
        long skipped,
        long totalCandidates,
        int page,
        int size,
        String metadataSource
    ) {}

    public record GovernanceUpdate(
        UUID domainId,
        String classification,
        String warehouseLayer,
        String ownerDept,
        String businessOwner,
        String lifecycleStatus,
        Boolean enabled,
        String securityPolicyRefs
    ) {}

    public record LineageNode(String id, String omEntityId, String name, String fqn, String kind) {}

    public record LineageEdge(
        UUID id,
        String fromFqn,
        String toFqn,
        String source,
        String edgeType,
        java.time.Instant lastSyncedAt
    ) {}

    public record LineageView(
        UUID assetId,
        String fqn,
        List<LineageNode> nodes,
        List<LineageEdge> edges,
        String metadataSource
    ) {}

    public record MappingIssue(String fqn, String matchStatus, String matchReason, Integer confidence) {}

    public record MappingDiagnostics(
        long assetCount,
        long extensionCount,
        long mappingCount,
        long matchedCount,
        long unmatchedCount,
        long manualReviewCount,
        List<MappingIssue> issues
    ) {}
}
