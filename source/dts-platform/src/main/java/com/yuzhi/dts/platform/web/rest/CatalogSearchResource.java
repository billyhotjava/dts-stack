package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema;
import com.yuzhi.dts.platform.repository.catalog.CatalogAssetTagRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetKey;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetTagService;
import com.yuzhi.dts.platform.service.catalog.dto.AssetRef;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogTagDto;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import jakarta.persistence.criteria.Predicate;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/catalog")
@Transactional(readOnly = true)
public class CatalogSearchResource {

    private static final int TAG_SEARCH_SCAN_PAGE_SIZE = 200;
    private static final int MAX_TAG_SEARCH_CANDIDATES = 5_000;

    private final CatalogDatasetRepository datasetRepo;
    private final CatalogTableSchemaRepository tableRepo;
    private final CatalogColumnSchemaRepository columnRepo;
    private final CatalogAssetTagRepository assetTagRepository;
    private final AccessChecker accessChecker;
    private final AuditService audit;
    private final CatalogAssetTagService assetTagService;

    public CatalogSearchResource(
        CatalogDatasetRepository datasetRepo,
        CatalogTableSchemaRepository tableRepo,
        CatalogColumnSchemaRepository columnRepo,
        CatalogAssetTagRepository assetTagRepository,
        AccessChecker accessChecker,
        AuditService audit,
        CatalogAssetTagService assetTagService
    ) {
        this.datasetRepo = datasetRepo;
        this.tableRepo = tableRepo;
        this.columnRepo = columnRepo;
        this.assetTagRepository = assetTagRepository;
        this.accessChecker = accessChecker;
        this.audit = audit;
        this.assetTagService = assetTagService;
    }

    @GetMapping("/search")
    public ApiResponse<Map<String, Object>> search(
        @RequestParam(name = "keyword", required = false) String keyword,
        @RequestParam(name = "types", required = false) String types,
        @RequestParam(name = "domainId", required = false) UUID domainId,
        @RequestParam(name = "sourceId", required = false) UUID sourceId,
        @RequestParam(name = "classification", required = false) String classification,
        @RequestParam(name = "ownerDept", required = false) String ownerDept,
        @RequestParam(name = "warehouseLayer", required = false) String warehouseLayer,
        @RequestParam(name = "exposedBy", required = false) String exposedBy,
        @RequestParam(name = "datasetType", required = false) String datasetType,
        @RequestParam(name = "enabledOnly", required = false, defaultValue = "true") boolean enabledOnly,
        @RequestParam(name = "tagIds", required = false) List<UUID> tagIds,
        @RequestParam(name = "limit", required = false, defaultValue = "50") int limit,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String k = trimToNull(keyword);
        boolean hasTagFilter = tagIds != null && !tagIds.isEmpty();
        if (k == null && !hasTagFilter) {
            return ApiResponses.ok(Map.of("datasets", List.of(), "tables", List.of(), "columns", List.of()));
        }
        int safeLimit = Math.max(1, Math.min(limit, 200));

        Set<String> typeSet = parseTypeSet(types);
        boolean includeDatasets = typeSet.isEmpty() || typeSet.contains("DATASET");
        boolean includeTables = typeSet.isEmpty() || typeSet.contains("TABLE");
        boolean includeColumns = typeSet.isEmpty() || typeSet.contains("COLUMN");

        String effDept = resolveActiveDept(activeDept);
        String needle = k == null ? null : k.toLowerCase(Locale.ROOT);

        if (hasTagFilter) {
            return searchByTags(
                k,
                typeSet,
                includeDatasets,
                includeTables,
                includeColumns,
                domainId,
                sourceId,
                classification,
                ownerDept,
                warehouseLayer,
                exposedBy,
                datasetType,
                enabledOnly,
                tagIds,
                safeLimit,
                effDept,
                needle
            );
        }

        Map<UUID, CatalogDataset> visibleDatasetById = new LinkedHashMap<>();
        Map<UUID, Map<String, Object>> visibleDatasetDtoById = new LinkedHashMap<>();
        if (includeDatasets || includeTables || includeColumns) {
            List<CatalogDataset> datasetScope = datasetRepo.findAll(
                buildDatasetScopeSpecification(
                    domainId,
                    sourceId,
                    classification,
                    ownerDept,
                    warehouseLayer,
                    exposedBy,
                    datasetType,
                    enabledOnly
                ),
                Sort.by("createdDate").descending()
            );
            for (CatalogDataset ds : datasetScope) {
                if (!accessChecker.canRead(ds)) continue;
                if (effDept != null && !accessChecker.departmentAllowed(ds, effDept)) continue;
                visibleDatasetById.put(ds.getId(), ds);
            }
            List<AssetRef> visibleRefs = visibleDatasetById
                .values()
                .stream()
                .map(ds -> new AssetRef("DATASET", CatalogAssetKey.dataset(ds)))
                .toList();
            Map<AssetRef, List<CatalogTagDto>> tagsByAsset = assetTagService.listAssetTags(visibleRefs);
            for (Map.Entry<UUID, CatalogDataset> entry : visibleDatasetById.entrySet()) {
                CatalogDataset dataset = entry.getValue();
                AssetRef ref = new AssetRef("DATASET", CatalogAssetKey.dataset(dataset));
                visibleDatasetDtoById.put(entry.getKey(), toDatasetDto(dataset, tagsByAsset.getOrDefault(ref, List.of())));
            }
        }

        List<Map<String, Object>> datasetHits = new ArrayList<>();
        if (includeDatasets) {
            for (Map<String, Object> dto : visibleDatasetDtoById.values()) {
                if (datasetHits.size() >= safeLimit) break;
                if (
                    needle == null ||
                    containsAny(dto, needle, "name", "owner", "ownerDept", "tags", "description", "hiveDatabase", "hiveTable")
                ) {
                    datasetHits.add(dto);
                }
            }
        }

        Map<UUID, UUID> tableIdToDatasetId = new LinkedHashMap<>();
        Map<UUID, String> tableIdToName = new LinkedHashMap<>();
        List<Map<String, Object>> tableHits = new ArrayList<>();
        if (includeTables || includeColumns) {
            for (CatalogTableSchema table : tableRepo.findAll()) {
                UUID tableId = table.getId();
                UUID datasetId = table.getDataset() != null ? table.getDataset().getId() : null;
                if (tableId != null) {
                    tableIdToDatasetId.put(tableId, datasetId);
                    tableIdToName.put(tableId, table.getName());
                }
                if (!includeTables) continue;
                if (tableHits.size() >= safeLimit) continue;
                if (datasetId == null || !visibleDatasetDtoById.containsKey(datasetId)) continue;
                if (needle == null || matchesTable(table, needle)) {
                    tableHits.add(toTableDto(table, visibleDatasetDtoById.get(datasetId)));
                }
            }
        }

        List<Map<String, Object>> columnHits = new ArrayList<>();
        if (includeColumns) {
            columnRepo
                .findAll()
                .forEach(col -> {
                    if (columnHits.size() >= safeLimit) return;
                    UUID tableId = col.getTable() != null ? col.getTable().getId() : null;
                    UUID datasetId = tableId != null ? tableIdToDatasetId.get(tableId) : null;
                    if (datasetId == null || !visibleDatasetDtoById.containsKey(datasetId)) return;
                    if (needle == null || matchesColumn(col.getName(), col.getTags(), col.getSensitiveTags(), col.getComment(), needle)) {
                        String tableName = tableId != null ? tableIdToName.get(tableId) : null;
                        columnHits.add(toColumnDto(col, tableId, tableName, visibleDatasetDtoById.get(datasetId)));
                    }
                });
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("keyword", k);
        payload.put("types", typeSet.isEmpty() ? List.of("DATASET", "TABLE", "COLUMN") : typeSet.stream().sorted().toList());
        payload.put("limit", safeLimit);
        payload.put("domainId", domainId != null ? domainId.toString() : null);
        payload.put("sourceId", sourceId != null ? sourceId.toString() : null);
        payload.put("classification", trimToNull(classification));
        payload.put("ownerDept", trimToNull(ownerDept));
        payload.put("warehouseLayer", trimToNull(warehouseLayer));
        payload.put("exposedBy", trimToNull(exposedBy));
        payload.put("datasetType", trimToNull(datasetType));
        payload.put("enabledOnly", enabledOnly);
        payload.put("tagIds", tagIds == null ? List.of() : tagIds);
        payload.put("datasets", datasetHits);
        payload.put("tables", tableHits);
        payload.put("columns", columnHits);

        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "检索元数据");
        auditPayload.put("keyword", k);
        auditPayload.put("types", payload.get("types"));
        auditPayload.put("limit", safeLimit);
        if (domainId != null) {
            auditPayload.put("domainId", domainId.toString());
        }
        if (sourceId != null) {
            auditPayload.put("sourceId", sourceId.toString());
        }
        putIfHasText(auditPayload, "classification", classification);
        putIfHasText(auditPayload, "ownerDept", ownerDept);
        putIfHasText(auditPayload, "warehouseLayer", warehouseLayer);
        putIfHasText(auditPayload, "exposedBy", exposedBy);
        putIfHasText(auditPayload, "datasetType", datasetType);
        auditPayload.put("enabledOnly", enabledOnly);
        if (hasTagFilter) {
            auditPayload.put("tagCount", tagIds.stream().distinct().count());
        }
        auditPayload.put("datasetHits", datasetHits.size());
        auditPayload.put("tableHits", tableHits.size());
        auditPayload.put("columnHits", columnHits.size());
        audit.auditAction("CATALOG_SEARCH", AuditStage.SUCCESS, "search", auditPayload);

        return ApiResponses.ok(payload);
    }

    private ApiResponse<Map<String, Object>> searchByTags(
        String keyword,
        Set<String> typeSet,
        boolean includeDatasets,
        boolean includeTables,
        boolean includeColumns,
        UUID domainId,
        UUID sourceId,
        String classification,
        String ownerDept,
        String warehouseLayer,
        String exposedBy,
        String datasetType,
        boolean enabledOnly,
        List<UUID> requestedTagIds,
        int safeLimit,
        String activeDept,
        String needle
    ) {
        List<UUID> tagIds = normalizeSearchTagIds(requestedTagIds);
        TaggedSearchScope scope = new TaggedSearchScope(
            tagIds,
            domainId,
            sourceId,
            trimToNull(classification),
            trimToNull(ownerDept),
            trimToNull(warehouseLayer),
            trimToNull(exposedBy),
            trimToNull(datasetType),
            enabledOnly,
            activeDept,
            needle,
            safeLimit
        );

        List<TaggedSearchHit> datasetEntities = includeDatasets ? scanTaggedCandidates("DATASET", scope) : List.of();
        List<TaggedSearchHit> tableEntities = includeTables ? scanTaggedCandidates("TABLE", scope) : List.of();
        List<TaggedSearchHit> columnEntities = includeColumns ? scanTaggedCandidates("COLUMN", scope) : List.of();

        Map<UUID, CatalogDataset> hitDatasets = new LinkedHashMap<>();
        datasetEntities.forEach(hit -> hitDatasets.putIfAbsent(hit.dataset().getId(), hit.dataset()));
        tableEntities.forEach(hit -> hitDatasets.putIfAbsent(hit.dataset().getId(), hit.dataset()));
        columnEntities.forEach(hit -> hitDatasets.putIfAbsent(hit.dataset().getId(), hit.dataset()));

        List<AssetRef> refs = hitDatasets
            .values()
            .stream()
            .map(dataset -> new AssetRef("DATASET", CatalogAssetKey.dataset(dataset)))
            .toList();
        Map<AssetRef, List<CatalogTagDto>> tagsByAsset = assetTagService.listAssetTags(refs);
        Map<UUID, Map<String, Object>> datasetDtoById = new LinkedHashMap<>();
        for (CatalogDataset dataset : hitDatasets.values()) {
            AssetRef ref = new AssetRef("DATASET", CatalogAssetKey.dataset(dataset));
            datasetDtoById.put(dataset.getId(), toDatasetDto(dataset, tagsByAsset.getOrDefault(ref, List.of())));
        }

        List<Map<String, Object>> datasetHits = datasetEntities
            .stream()
            .map(hit -> datasetDtoById.get(hit.dataset().getId()))
            .filter(Objects::nonNull)
            .toList();
        List<Map<String, Object>> tableHits = tableEntities
            .stream()
            .map(hit -> toTableDto(hit.table(), datasetDtoById.get(hit.dataset().getId())))
            .toList();
        List<Map<String, Object>> columnHits = columnEntities
            .stream()
            .map(hit -> {
                CatalogColumnSchema column = hit.column();
                CatalogTableSchema table = column != null ? column.getTable() : null;
                UUID tableId = table != null ? table.getId() : null;
                String tableName = table != null ? table.getName() : null;
                return toColumnDto(column, tableId, tableName, datasetDtoById.get(hit.dataset().getId()));
            })
            .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("keyword", keyword);
        payload.put("types", typeSet.isEmpty() ? List.of("DATASET", "TABLE", "COLUMN") : typeSet.stream().sorted().toList());
        payload.put("limit", safeLimit);
        payload.put("domainId", domainId != null ? domainId.toString() : null);
        payload.put("sourceId", sourceId != null ? sourceId.toString() : null);
        payload.put("classification", trimToNull(classification));
        payload.put("ownerDept", trimToNull(ownerDept));
        payload.put("warehouseLayer", trimToNull(warehouseLayer));
        payload.put("exposedBy", trimToNull(exposedBy));
        payload.put("datasetType", trimToNull(datasetType));
        payload.put("enabledOnly", enabledOnly);
        payload.put("tagIds", requestedTagIds);
        payload.put("datasets", datasetHits);
        payload.put("tables", tableHits);
        payload.put("columns", columnHits);

        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "检索元数据");
        auditPayload.put("keyword", keyword);
        auditPayload.put("types", payload.get("types"));
        auditPayload.put("limit", safeLimit);
        if (domainId != null) {
            auditPayload.put("domainId", domainId.toString());
        }
        if (sourceId != null) {
            auditPayload.put("sourceId", sourceId.toString());
        }
        putIfHasText(auditPayload, "classification", classification);
        putIfHasText(auditPayload, "ownerDept", ownerDept);
        putIfHasText(auditPayload, "warehouseLayer", warehouseLayer);
        putIfHasText(auditPayload, "exposedBy", exposedBy);
        putIfHasText(auditPayload, "datasetType", datasetType);
        auditPayload.put("enabledOnly", enabledOnly);
        auditPayload.put("tagCount", tagIds.size());
        auditPayload.put("datasetHits", datasetHits.size());
        auditPayload.put("tableHits", tableHits.size());
        auditPayload.put("columnHits", columnHits.size());
        audit.auditAction("CATALOG_SEARCH", AuditStage.SUCCESS, "search", auditPayload);

        return ApiResponses.ok(payload);
    }

    private List<TaggedSearchHit> scanTaggedCandidates(String entityType, TaggedSearchScope scope) {
        List<TaggedSearchHit> hits = new ArrayList<>(scope.limit());
        Map<UUID, Boolean> visibilityByDatasetId = new LinkedHashMap<>();
        int scanned = 0;
        int page = 0;
        while (hits.size() < scope.limit()) {
            Slice<CatalogAssetTagRepository.CatalogSearchCandidateProjection> candidates =
                assetTagRepository.findCatalogSearchCandidatesHavingAllTags(
                    scope.tagIds(),
                    scope.tagIds().size(),
                    entityType,
                    scope.domainId(),
                    scope.sourceId(),
                    scope.classification(),
                    scope.ownerDept(),
                    scope.warehouseLayer(),
                    scope.exposedBy(),
                    scope.datasetType(),
                    scope.enabledOnly(),
                    PageRequest.of(page, TAG_SEARCH_SCAN_PAGE_SIZE)
                );
            List<CatalogAssetTagRepository.CatalogSearchCandidateProjection> rows = candidates.getContent();
            scanned += rows.size();

            Map<UUID, CatalogDataset> datasetsById = indexDatasets(
                datasetRepo.findAllById(rows.stream().map(CatalogAssetTagRepository.CatalogSearchCandidateProjection::getDatasetId).toList())
            );
            Map<UUID, CatalogTableSchema> tablesById = "TABLE".equals(entityType)
                ? indexTables(
                    tableRepo.findAllById(
                        rows.stream().map(CatalogAssetTagRepository.CatalogSearchCandidateProjection::getEntityId).toList()
                    )
                )
                : Map.of();
            Map<UUID, CatalogColumnSchema> columnsById = "COLUMN".equals(entityType)
                ? indexColumns(
                    columnRepo.findAllById(
                        rows.stream().map(CatalogAssetTagRepository.CatalogSearchCandidateProjection::getEntityId).toList()
                    )
                )
                : Map.of();

            for (CatalogAssetTagRepository.CatalogSearchCandidateProjection row : rows) {
                if (hits.size() >= scope.limit()) {
                    break;
                }
                CatalogDataset dataset = datasetsById.get(row.getDatasetId());
                if (dataset == null || !CatalogAssetKey.dataset(dataset).equals(row.getAssetKey())) {
                    continue;
                }
                boolean visible = visibilityByDatasetId.computeIfAbsent(
                    dataset.getId(),
                    ignored ->
                        accessChecker.canRead(dataset) &&
                        (scope.activeDept() == null || accessChecker.departmentAllowed(dataset, scope.activeDept()))
                );
                if (!visible) {
                    continue;
                }
                TaggedSearchHit hit = toTaggedSearchHit(entityType, row.getEntityId(), dataset, tablesById, columnsById);
                if (hit != null && matchesTaggedSearchHit(hit, scope.needle())) {
                    hits.add(hit);
                }
            }

            if (!candidates.hasNext()) {
                break;
            }
            if (scanned >= MAX_TAG_SEARCH_CANDIDATES) {
                throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "精确标签检索候选超过 5000，请增加检索条件或缩小标签范围"
                );
            }
            page++;
        }
        return List.copyOf(hits);
    }

    private TaggedSearchHit toTaggedSearchHit(
        String entityType,
        UUID entityId,
        CatalogDataset dataset,
        Map<UUID, CatalogTableSchema> tablesById,
        Map<UUID, CatalogColumnSchema> columnsById
    ) {
        if ("DATASET".equals(entityType)) {
            return dataset.getId().equals(entityId) ? new TaggedSearchHit(dataset, null, null) : null;
        }
        if ("TABLE".equals(entityType)) {
            CatalogTableSchema table = tablesById.get(entityId);
            UUID tableDatasetId = table != null && table.getDataset() != null ? table.getDataset().getId() : null;
            return dataset.getId().equals(tableDatasetId) ? new TaggedSearchHit(dataset, table, null) : null;
        }
        CatalogColumnSchema column = columnsById.get(entityId);
        CatalogTableSchema table = column != null ? column.getTable() : null;
        UUID columnDatasetId = table != null && table.getDataset() != null ? table.getDataset().getId() : null;
        return dataset.getId().equals(columnDatasetId) ? new TaggedSearchHit(dataset, null, column) : null;
    }

    private boolean matchesTaggedSearchHit(TaggedSearchHit hit, String needle) {
        if (needle == null) {
            return true;
        }
        if (hit.table() != null) {
            return matchesTable(hit.table(), needle);
        }
        if (hit.column() != null) {
            CatalogColumnSchema column = hit.column();
            return matchesColumn(column.getName(), column.getTags(), column.getSensitiveTags(), column.getComment(), needle);
        }
        CatalogDataset dataset = hit.dataset();
        return contains(Objects.toString(dataset.getName(), ""), needle)
            || contains(Objects.toString(dataset.getOwner(), ""), needle)
            || contains(Objects.toString(dataset.getOwnerDept(), ""), needle)
            || contains(Objects.toString(dataset.getTags(), ""), needle)
            || contains(Objects.toString(dataset.getDescription(), ""), needle)
            || contains(Objects.toString(dataset.getHiveDatabase(), ""), needle)
            || contains(Objects.toString(dataset.getHiveTable(), ""), needle);
    }

    private Map<UUID, CatalogDataset> indexDatasets(Iterable<CatalogDataset> datasets) {
        Map<UUID, CatalogDataset> result = new LinkedHashMap<>();
        datasets.forEach(dataset -> result.put(dataset.getId(), dataset));
        return result;
    }

    private Map<UUID, CatalogTableSchema> indexTables(Iterable<CatalogTableSchema> tables) {
        Map<UUID, CatalogTableSchema> result = new LinkedHashMap<>();
        tables.forEach(table -> result.put(table.getId(), table));
        return result;
    }

    private Map<UUID, CatalogColumnSchema> indexColumns(Iterable<CatalogColumnSchema> columns) {
        Map<UUID, CatalogColumnSchema> result = new LinkedHashMap<>();
        columns.forEach(column -> result.put(column.getId(), column));
        return result;
    }

    private List<UUID> normalizeSearchTagIds(List<UUID> tagIds) {
        if (tagIds == null || tagIds.isEmpty() || tagIds.stream().anyMatch(Objects::isNull)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "标签不能为空");
        }
        List<UUID> normalized = List.copyOf(new LinkedHashSet<>(tagIds));
        if (normalized.size() > CatalogAssetTagService.MAX_SEARCH_TAG_IDS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "精确标签筛选最多支持 50 个不同标签");
        }
        return normalized;
    }

    private record TaggedSearchScope(
        List<UUID> tagIds,
        UUID domainId,
        UUID sourceId,
        String classification,
        String ownerDept,
        String warehouseLayer,
        String exposedBy,
        String datasetType,
        boolean enabledOnly,
        String activeDept,
        String needle,
        int limit
    ) {}

    private record TaggedSearchHit(CatalogDataset dataset, CatalogTableSchema table, CatalogColumnSchema column) {}

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String resolveActiveDept(String activeDeptHeader) {
        String candidate = trimToNull(activeDeptHeader);
        if (candidate != null) {
            return candidate;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        try {
            if (authentication instanceof JwtAuthenticationToken token) {
                candidate = extractDeptClaim(token.getToken().getClaims().get("dept_code"));
                if (candidate != null) return candidate;
                candidate = extractDeptClaim(token.getToken().getClaims().get("deptCode"));
                if (candidate != null) return candidate;
                return extractDeptClaim(token.getToken().getClaims().get("department"));
            }
            if (authentication != null && authentication.getPrincipal() instanceof OAuth2AuthenticatedPrincipal principal) {
                candidate = extractDeptClaim(principal.getAttribute("dept_code"));
                if (candidate != null) return candidate;
                candidate = extractDeptClaim(principal.getAttribute("deptCode"));
                if (candidate != null) return candidate;
                return extractDeptClaim(principal.getAttribute("department"));
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String extractDeptClaim(Object raw) {
        Object flattened = flattenValue(raw);
        if (flattened == null) {
            return null;
        }
        return trimToNull(flattened.toString());
    }

    private Object flattenValue(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                if (element != null) {
                    return element;
                }
            }
            return null;
        }
        if (raw.getClass().isArray()) {
            int length = Array.getLength(raw);
            for (int i = 0; i < length; i++) {
                Object element = Array.get(raw, i);
                if (element != null) {
                    return element;
                }
            }
            return null;
        }
        return raw;
    }

    private Set<String> parseTypeSet(String raw) {
        if (!StringUtils.hasText(raw)) return Set.of();
        return java.util.Arrays
            .stream(raw.split("[,;\\s]+"))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(s -> s.toUpperCase(Locale.ROOT))
            .collect(java.util.stream.Collectors.toSet());
    }

    private Specification<CatalogDataset> buildDatasetScopeSpecification(
        UUID domainId,
        UUID sourceId,
        String classification,
        String ownerDept,
        String warehouseLayer,
        String exposedBy,
        String datasetType,
        boolean enabledOnly
    ) {
        String classificationText = trimToNull(classification);
        String ownerDeptText = trimToNull(ownerDept);
        String layerText = trimToNull(warehouseLayer);
        String exposedByText = trimToNull(exposedBy);
        String datasetTypeText = trimToNull(datasetType);
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (domainId != null) {
                predicates.add(cb.equal(root.get("domain").get("id"), domainId));
            }
            if (sourceId != null) {
                predicates.add(cb.equal(root.get("sourceId"), sourceId));
            }
            if (classificationText != null) {
                predicates.add(cb.equal(cb.lower(root.get("classification")), classificationText.toLowerCase(Locale.ROOT)));
            }
            if (ownerDeptText != null) {
                predicates.add(cb.equal(cb.lower(root.get("ownerDept")), ownerDeptText.toLowerCase(Locale.ROOT)));
            }
            if (layerText != null) {
                predicates.add(cb.equal(cb.lower(root.get("warehouseLayer")), layerText.toLowerCase(Locale.ROOT)));
            }
            if (exposedByText != null) {
                predicates.add(cb.equal(cb.lower(root.get("exposedBy")), exposedByText.toLowerCase(Locale.ROOT)));
            }
            if (datasetTypeText != null) {
                predicates.add(cb.equal(cb.lower(root.get("type")), datasetTypeText.toLowerCase(Locale.ROOT)));
            }
            if (enabledOnly) {
                predicates.add(cb.or(cb.isNull(root.get("enabled")), cb.isTrue(root.get("enabled"))));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void putIfHasText(Map<String, Object> payload, String key, String value) {
        if (payload == null || key == null) {
            return;
        }
        String text = trimToNull(value);
        if (text != null) {
            payload.put(key, text);
        }
    }

    private boolean matchesTable(CatalogTableSchema table, String needle) {
        if (table == null) return false;
        return contains(Objects.toString(table.getName(), ""), needle)
            || contains(Objects.toString(table.getOwner(), ""), needle)
            || contains(Objects.toString(table.getBizDomain(), ""), needle)
            || contains(Objects.toString(table.getTags(), ""), needle)
            || contains(Objects.toString(table.getClassification(), ""), needle);
    }

    private boolean matchesColumn(String name, String tags, String sensitiveTags, String comment, String needle) {
        return contains(Objects.toString(name, ""), needle)
            || contains(Objects.toString(tags, ""), needle)
            || contains(Objects.toString(sensitiveTags, ""), needle)
            || contains(Objects.toString(comment, ""), needle);
    }

    private boolean contains(String haystack, String needle) {
        if (haystack == null || haystack.isBlank()) return false;
        return haystack.toLowerCase(Locale.ROOT).contains(needle);
    }

    private boolean containsAny(Map<String, Object> dto, String needle, String... keys) {
        if (dto == null) return false;
        for (String key : keys) {
            Object v = dto.get(key);
            if (v != null && contains(String.valueOf(v), needle)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> toDatasetDto(CatalogDataset ds, List<CatalogTagDto> assetTags) {
        Map<String, Object> dto = new LinkedHashMap<>();
        if (ds == null) return dto;
        if (ds.getId() != null) dto.put("id", ds.getId().toString());
        if (ds.getDomain() != null) {
            if (ds.getDomain().getId() != null) {
                dto.put("domainId", ds.getDomain().getId());
            }
            dto.put("domainName", ds.getDomain().getName());
        }
        dto.put("name", ds.getName());
        dto.put("type", ds.getType());
        dto.put("classification", ds.getClassification());
        dto.put("ownerDept", ds.getOwnerDept());
        dto.put("owner", ds.getOwner());
        dto.put("tags", ds.getTags());
        dto.put("description", ds.getDescription());
        dto.put("hiveDatabase", ds.getHiveDatabase());
        dto.put("hiveTable", ds.getHiveTable());
        dto.put("warehouseLayer", ds.getWarehouseLayer());
        dto.put("enabled", ds.getEnabled());
        dto.put("assetType", "DATASET");
        dto.put("assetKey", CatalogAssetKey.dataset(ds));
        dto.put("assetTags", assetTags == null ? List.of() : List.copyOf(assetTags));
        return dto;
    }

    private Map<String, Object> toTableDto(CatalogTableSchema table, Map<String, Object> datasetDto) {
        Map<String, Object> dto = new LinkedHashMap<>();
        if (table == null) return dto;
        if (table.getId() != null) dto.put("id", table.getId().toString());
        dto.put("name", table.getName());
        dto.put("owner", table.getOwner());
        dto.put("classification", table.getClassification());
        dto.put("bizDomain", table.getBizDomain());
        dto.put("tags", table.getTags());
        if (datasetDto != null) {
            dto.put("datasetId", datasetDto.get("id"));
            dto.put("datasetName", datasetDto.get("name"));
            dto.put("datasetOwnerDept", datasetDto.get("ownerDept"));
            dto.put("domainId", datasetDto.get("domainId"));
            dto.put("domainName", datasetDto.get("domainName"));
            dto.put("datasetAssetKey", datasetDto.get("assetKey"));
            dto.put("datasetAssetTags", datasetDto.get("assetTags"));
        }
        return dto;
    }

    private Map<String, Object> toColumnDto(
        com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema col,
        UUID tableId,
        String tableName,
        Map<String, Object> datasetDto
    ) {
        Map<String, Object> dto = new LinkedHashMap<>();
        if (col == null) return dto;
        if (col.getId() != null) dto.put("id", col.getId().toString());
        dto.put("name", col.getName());
        dto.put("dataType", col.getDataType());
        dto.put("nullable", col.getNullable());
        dto.put("tags", col.getTags());
        dto.put("sensitiveTags", col.getSensitiveTags());
        dto.put("comment", col.getComment());
        if (tableId != null) dto.put("tableId", tableId.toString());
        dto.put("tableName", tableName);
        if (datasetDto != null) {
            dto.put("datasetId", datasetDto.get("id"));
            dto.put("datasetName", datasetDto.get("name"));
            dto.put("datasetOwnerDept", datasetDto.get("ownerDept"));
            dto.put("domainId", datasetDto.get("domainId"));
            dto.put("domainName", datasetDto.get("domainName"));
            dto.put("datasetAssetKey", datasetDto.get("assetKey"));
            dto.put("datasetAssetTags", datasetDto.get("assetTags"));
        }
        return dto;
    }
}
