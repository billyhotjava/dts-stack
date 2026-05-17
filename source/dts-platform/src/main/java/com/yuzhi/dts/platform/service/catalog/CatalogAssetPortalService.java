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
import com.yuzhi.dts.platform.service.security.AccessChecker;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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

    private final OpenMetadataAssetCacheRepository assetRepository;
    private final OpenMetadataColumnCacheRepository columnRepository;
    private final OpenMetadataLineageCacheRepository lineageRepository;
    private final CatalogAssetExtensionRepository extensionRepository;
    private final CatalogAssetMappingRepository mappingRepository;
    private final CatalogDatasetRepository datasetRepository;
    private final CatalogTableSchemaRepository tableSchemaRepository;
    private final CatalogColumnSchemaRepository catalogColumnSchemaRepository;
    private final AccessChecker accessChecker;

    public CatalogAssetPortalService(
        OpenMetadataAssetCacheRepository assetRepository,
        OpenMetadataColumnCacheRepository columnRepository,
        OpenMetadataLineageCacheRepository lineageRepository,
        CatalogAssetExtensionRepository extensionRepository,
        CatalogAssetMappingRepository mappingRepository,
        CatalogDatasetRepository datasetRepository,
        CatalogTableSchemaRepository tableSchemaRepository,
        CatalogColumnSchemaRepository catalogColumnSchemaRepository,
        AccessChecker accessChecker
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
    }

    public AssetPage listAssets(AssetQuery query, String activeDept) {
        int page = Math.max(0, query.page());
        int size = Math.max(1, Math.min(query.size(), 200));
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "lastSyncedAt").and(Sort.by("fqn").ascending()));
        var pageData = assetRepository.findAll(buildSpec(query), pageable);
        List<AssetSummary> items = new ArrayList<>();
        List<UUID> visibleLegacyIds = new ArrayList<>();
        for (OpenMetadataAssetCache asset : pageData.getContent()) {
            CatalogAssetExtension extension = extensionRepository.findFirstByOmAsset(asset).orElse(null);
            CatalogAssetMapping mapping = mappingRepository.findFirstByFqnIgnoreCase(asset.getFqn()).orElse(null);
            CatalogDataset legacy = extension != null && extension.getLegacyDatasetId() != null
                ? datasetRepository.findById(extension.getLegacyDatasetId()).orElse(null)
                : null;
            if (!canRead(extension, legacy, activeDept)) {
                continue;
            }
            items.add(toSummary(asset, extension, mapping, legacy));
            if (legacy != null && legacy.getId() != null) {
                visibleLegacyIds.add(legacy.getId());
            }
        }
        int openMetadataReturned = items.size();
        AssetPage legacyPage = listLegacyAssets(query, activeDept, page, size, visibleLegacyIds, Math.max(0, size - items.size()));
        if (!legacyPage.content().isEmpty()) {
            items.addAll(legacyPage.content());
        }
        long total = items.size();
        String source = openMetadataReturned > 0 && legacyPage.returned() > 0
            ? "openmetadata-cache+dts-catalog"
            : openMetadataReturned > 0 ? "openmetadata-cache" : "dts-catalog";
        return new AssetPage(items, total, page, size, items.size(), source);
    }

    private AssetPage listLegacyAssets(
        AssetQuery query,
        String activeDept,
        int page,
        int size,
        List<UUID> excludedIds,
        int remainingSlots
    ) {
        if (remainingSlots <= 0) {
            return new AssetPage(List.of(), 0, page, size, 0, "dts-catalog");
        }
        var pageable = PageRequest.of(
            page,
            size,
            Sort.by(Sort.Direction.DESC, "lastModifiedDate").and(Sort.by(Sort.Direction.DESC, "createdDate"))
        );
        Page<CatalogDataset> legacyPage = datasetRepository.findAll(buildLegacySpec(query), pageable);
        List<AssetSummary> items = legacyPage
            .getContent()
            .stream()
            .filter(dataset -> dataset.getId() == null || excludedIds == null || !excludedIds.contains(dataset.getId()))
            .filter(dataset -> canRead(null, dataset, activeDept))
            .limit(remainingSlots)
            .map(this::toLegacySummary)
            .toList();
        return new AssetPage(items, items.size(), page, size, items.size(), "dts-catalog");
    }

    public AssetDetail getAsset(UUID id, String activeDept) {
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
        List<ColumnSummary> columns = columnRepository.findByAssetOrderByOrdinalPositionAsc(asset).stream().map(this::toColumn).toList();
        return new AssetDetail(toSummary(asset, extension, mapping, legacy), columns, asset.getRawJson(), asset.getProfileJson());
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
        CatalogDataset legacy = extension.getLegacyDatasetId() != null
            ? datasetRepository.findById(extension.getLegacyDatasetId()).orElse(null)
            : null;
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
        return new AssetDetail(toSummary(asset, extension, mapping, legacy), columns, asset.getRawJson(), asset.getProfileJson());
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
            extension != null ? extension.getLegacyDatasetId() : null,
            extension != null ? extension.getSecurityPolicyRefs() : null,
            asset.getColumnCount(),
            asset.getSyncStatus(),
            asset.getSyncMessage(),
            asset.getLastSyncedAt(),
            "openmetadata-cache"
        );
    }

    private AssetSummary toLegacySummary(CatalogDataset dataset) {
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
            "dts-catalog"
        );
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
        int page,
        int size
    ) {}

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
        String metadataSource
    ) {}

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
