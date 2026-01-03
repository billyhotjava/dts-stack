package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Sort;
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

@RestController
@RequestMapping("/api/catalog")
@Transactional(readOnly = true)
public class CatalogSearchResource {

    private final CatalogDatasetRepository datasetRepo;
    private final CatalogTableSchemaRepository tableRepo;
    private final CatalogColumnSchemaRepository columnRepo;
    private final AccessChecker accessChecker;
    private final AuditService audit;

    public CatalogSearchResource(
        CatalogDatasetRepository datasetRepo,
        CatalogTableSchemaRepository tableRepo,
        CatalogColumnSchemaRepository columnRepo,
        AccessChecker accessChecker,
        AuditService audit
    ) {
        this.datasetRepo = datasetRepo;
        this.tableRepo = tableRepo;
        this.columnRepo = columnRepo;
        this.accessChecker = accessChecker;
        this.audit = audit;
    }

    @GetMapping("/search")
    public ApiResponse<Map<String, Object>> search(
        @RequestParam(name = "keyword") String keyword,
        @RequestParam(name = "types", required = false) String types,
        @RequestParam(name = "limit", required = false, defaultValue = "50") int limit,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String k = trimToNull(keyword);
        if (k == null) {
            return ApiResponses.ok(Map.of("datasets", List.of(), "tables", List.of(), "columns", List.of()));
        }
        int safeLimit = Math.max(1, Math.min(limit, 200));

        Set<String> typeSet = parseTypeSet(types);
        boolean includeDatasets = typeSet.isEmpty() || typeSet.contains("DATASET");
        boolean includeTables = typeSet.isEmpty() || typeSet.contains("TABLE");
        boolean includeColumns = typeSet.isEmpty() || typeSet.contains("COLUMN");

        String effDept = resolveActiveDept(activeDept);
        String needle = k.toLowerCase(Locale.ROOT);

        Map<UUID, Map<String, Object>> visibleDatasetDtoById = new LinkedHashMap<>();
        if (includeDatasets || includeTables || includeColumns) {
            for (CatalogDataset ds : datasetRepo.findAll(Sort.by("createdDate").descending())) {
                if (!accessChecker.canRead(ds)) continue;
                if (effDept != null && !accessChecker.departmentAllowed(ds, effDept)) continue;
                visibleDatasetDtoById.put(ds.getId(), toDatasetDto(ds));
            }
        }

        List<Map<String, Object>> datasetHits = new ArrayList<>();
        if (includeDatasets) {
            for (Map<String, Object> dto : visibleDatasetDtoById.values()) {
                if (datasetHits.size() >= safeLimit) break;
                if (containsAny(dto, needle, "name", "owner", "ownerDept", "tags", "description", "hiveDatabase", "hiveTable")) {
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
                if (matchesTable(table, needle)) {
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
                    if (matchesColumn(col.getName(), col.getTags(), col.getSensitiveTags(), col.getComment(), needle)) {
                        String tableName = tableId != null ? tableIdToName.get(tableId) : null;
                        columnHits.add(toColumnDto(col, tableId, tableName, visibleDatasetDtoById.get(datasetId)));
                    }
                });
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("keyword", k);
        payload.put("types", typeSet.isEmpty() ? List.of("DATASET", "TABLE", "COLUMN") : typeSet.stream().sorted().toList());
        payload.put("limit", safeLimit);
        payload.put("datasets", datasetHits);
        payload.put("tables", tableHits);
        payload.put("columns", columnHits);

        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "检索元数据");
        auditPayload.put("keyword", k);
        auditPayload.put("types", payload.get("types"));
        auditPayload.put("limit", safeLimit);
        auditPayload.put("datasetHits", datasetHits.size());
        auditPayload.put("tableHits", tableHits.size());
        auditPayload.put("columnHits", columnHits.size());
        audit.auditAction("CATALOG_SEARCH", AuditStage.SUCCESS, "search", auditPayload);

        return ApiResponses.ok(payload);
    }

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

    private Map<String, Object> toDatasetDto(CatalogDataset ds) {
        Map<String, Object> dto = new LinkedHashMap<>();
        if (ds == null) return dto;
        if (ds.getId() != null) dto.put("id", ds.getId().toString());
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
        }
        return dto;
    }
}
