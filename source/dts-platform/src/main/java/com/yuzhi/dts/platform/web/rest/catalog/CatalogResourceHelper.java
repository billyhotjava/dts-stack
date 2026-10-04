package com.yuzhi.dts.platform.web.rest.catalog;

import com.yuzhi.dts.common.security.SecurityLevelCatalog;
import com.yuzhi.dts.platform.config.CatalogFeatureProperties;
import com.yuzhi.dts.platform.domain.catalog.*;
import com.yuzhi.dts.platform.domain.modeling.DataStandard;
import com.yuzhi.dts.platform.repository.catalog.*;
import com.yuzhi.dts.platform.repository.modeling.DataStandardRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.DepartmentUtils;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.security.policy.DataLevel;
import com.yuzhi.dts.platform.service.security.OrganizationVisibilityService;
import jakarta.persistence.criteria.Predicate;
import java.lang.reflect.Array;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Shared utility methods for Catalog controllers.
 * Extracted from the monolithic CatalogResource during the domain-controller split.
 */
@Component
public class CatalogResourceHelper {

    public static final String CATALOG_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).CATALOG_MAINTAINERS)";

    public static final String TYPE_INCEPTOR = "INCEPTOR";
    public static final Pattern STD_CODE_PATTERN = Pattern.compile("(?i)(?:\\bSTD\\b|标准)\\s*[:：]\\s*([A-Za-z0-9_\\-\\.]+)");
    public static final List<String> CLASSIFICATION_LEVEL_ORDER = SecurityLevelCatalog.dataCodesInOrder();
    public static final Set<String> SUPPORTED_CLASSIFICATION_LEVELS = Set.copyOf(CLASSIFICATION_LEVEL_ORDER);
    public static final List<String> ISSUE_OPEN_STATUSES = List.of("OPEN", "NEW", "IN_PROGRESS", "PROCESSING", "REOPENED");
    public static final List<String> ISSUE_CLOSED_STATUSES = List.of("CLOSED", "RESOLVED");
    public static final List<String> QUALITY_PASS_STATUSES = List.of("SUCCESS", "SUCCEEDED", "PASSED", "COMPLETED");
    public static final List<String> QUALITY_FAIL_STATUSES = List.of("FAILED", "ERROR");
    public static final int GOVERNANCE_TREND_DAYS = 7;

    private final CatalogDatasetRepository datasetRepo;
    private final CatalogTableSchemaRepository tableRepo;
    private final CatalogColumnSchemaRepository columnRepo;
    private final CatalogMetadataChangeLogRepository metadataChangeLogRepo;
    private final DataStandardRepository dataStandardRepository;
    private final InfraDataSourceRepository infraDataSourceRepository;
    private final CatalogFeatureProperties catalogFeatures;
    private final OrganizationVisibilityService organizationVisibilityService;

    public CatalogResourceHelper(
        CatalogDatasetRepository datasetRepo,
        CatalogTableSchemaRepository tableRepo,
        CatalogColumnSchemaRepository columnRepo,
        CatalogMetadataChangeLogRepository metadataChangeLogRepo,
        DataStandardRepository dataStandardRepository,
        InfraDataSourceRepository infraDataSourceRepository,
        CatalogFeatureProperties catalogFeatures,
        OrganizationVisibilityService organizationVisibilityService
    ) {
        this.datasetRepo = datasetRepo;
        this.tableRepo = tableRepo;
        this.columnRepo = columnRepo;
        this.metadataChangeLogRepo = metadataChangeLogRepo;
        this.dataStandardRepository = dataStandardRepository;
        this.infraDataSourceRepository = infraDataSourceRepository;
        this.catalogFeatures = catalogFeatures;
        this.organizationVisibilityService = organizationVisibilityService;
    }

    // --- Text utilities ---

    public String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public String safeText(Object raw) {
        if (raw == null) {
            return null;
        }
        String text = String.valueOf(raw).trim();
        return text.isEmpty() ? null : text;
    }

    public void putIfHasText(Map<String, Object> target, String key, Object raw) {
        if (target == null || key == null) {
            return;
        }
        String text = safeText(raw);
        if (text != null) {
            target.put(key, text);
        }
    }

    public String sanitize(String message) {
        if (!StringUtils.hasText(message)) {
            return "";
        }
        String cleaned = message.replaceAll("\\s+", " ").trim();
        return cleaned.length() > 160 ? cleaned.substring(0, 160) : cleaned;
    }

    public String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public String normalizeUpper(String text) {
        String value = trimToNull(text);
        return value != null ? value.toUpperCase(Locale.ROOT) : "";
    }

    public String safeBool(Boolean value) {
        if (value == null) {
            return null;
        }
        return Boolean.TRUE.equals(value) ? "true" : "false";
    }

    // --- JWT claim helpers ---

    public String claim(String name) {
        try {
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
                Object v = token.getToken().getClaims().get(name);
                return stringifyClaim(v);
            }
            if (auth != null && auth.getPrincipal() instanceof org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal principal) {
                Object v = principal.getAttribute(name);
                return stringifyClaim(v);
            }
        } catch (Exception ignored) {}
        return null;
    }

    public String stringifyClaim(Object raw) {
        Object flattened = flattenClaim(raw);
        if (flattened == null) return null;
        String text = flattened.toString();
        return text == null || text.isBlank() ? null : text;
    }

    public Object flattenClaim(Object raw) {
        if (raw == null) return null;
        if (raw instanceof java.util.Collection<?> collection) {
            return collection.stream().filter(Objects::nonNull).findFirst().orElse(null);
        }
        if (raw.getClass().isArray()) {
            int len = Array.getLength(raw);
            for (int i = 0; i < len; i++) {
                Object element = Array.get(raw, i);
                if (element != null) {
                    return element;
                }
            }
            return null;
        }
        return raw;
    }

    // --- Dataset DTO conversion ---

    public Map<String, Object> toDatasetDto(CatalogDataset d) {
        return toDatasetDto(d, false, Collections.emptyMap(), Collections.emptyMap());
    }

    public Map<String, Object> toDatasetDto(CatalogDataset d, boolean includeMetadata) {
        if (!includeMetadata) {
            return toDatasetDto(d, false, Collections.emptyMap(), Collections.emptyMap());
        }
        List<CatalogTableSchema> allTables = tableRepo.findByDataset(d);
        Map<UUID, List<CatalogTableSchema>> tablesByDataset = new HashMap<>();
        tablesByDataset.put(d.getId(), allTables);
        Map<UUID, List<CatalogColumnSchema>> columnsByTable;
        if (allTables.isEmpty()) {
            columnsByTable = Collections.emptyMap();
        } else {
            columnsByTable = columnRepo.findByTableIn(allTables).stream()
                .collect(Collectors.groupingBy(col -> col.getTable().getId()));
        }
        return toDatasetDto(d, true, tablesByDataset, columnsByTable);
    }

    public Map<String, Object> toDatasetDto(
        CatalogDataset d,
        boolean includeMetadata,
        Map<UUID, List<CatalogTableSchema>> tablesByDatasetId,
        Map<UUID, List<CatalogColumnSchema>> columnsByTableId
    ) {
        UUID domainId = d.getDomain() != null ? d.getDomain().getId() : null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("version", d.getVersion());
        m.put("name", d.getName());
        m.put("domainId", domainId);
        m.put("domainName", d.getDomain() != null ? d.getDomain().getName() : null);
        m.put("type", d.getType());
        m.put("sourceId", d.getSourceId());
        m.put("classification", d.getClassification());
        m.put("ownerDept", d.getOwnerDept());
        m.put("owner", d.getOwner());
        m.put("hiveDatabase", d.getHiveDatabase());
        m.put("hiveTable", d.getHiveTable());
        m.put("trinoCatalog", d.getTrinoCatalog());
        m.put("tags", d.getTags());
        m.put("description", d.getDescription());
        m.put("warehouseLayer", d.getWarehouseLayer());
        m.put("enabled", d.getEnabled());
        m.put("exposedBy", d.getExposedBy());
        m.put("lifecycleStatus", d.getLifecycleStatus());
        m.put("retentionDays", d.getRetentionDays());
        m.put("expiresAt", d.getExpiresAt());
        m.put("snapshotTime", d.getSnapshotTime());
        m.put("createdDate", d.getCreatedDate());
        m.put("lastModifiedDate", d.getLastModifiedDate());
        m.put("editable", canEditDataset(d));
        if (includeMetadata) {
            List<CatalogTableSchema> datasetTables = tablesByDatasetId.getOrDefault(d.getId(), Collections.emptyList());
            List<Map<String, Object>> tables = new ArrayList<>();
            datasetTables
                .stream()
                .sorted(Comparator.comparing(table -> table.getName() != null ? table.getName().toLowerCase(Locale.ROOT) : ""))
                .forEach(table -> {
                    Map<String, Object> tableDto = new LinkedHashMap<>();
                    tableDto.put("id", table.getId());
                    tableDto.put("name", table.getName());
                    tableDto.put("tableName", table.getName());
                    List<CatalogColumnSchema> columns = columnsByTableId.getOrDefault(table.getId(), Collections.emptyList());
                    List<Map<String, Object>> columnDtos = new ArrayList<>();
                    columns
                        .stream()
                        .sorted(Comparator.comparing(col -> col.getName() != null ? col.getName().toLowerCase(Locale.ROOT) : ""))
                        .forEach(col -> {
                            Map<String, Object> colDto = new LinkedHashMap<>();
                            colDto.put("id", col.getId());
                            colDto.put("name", col.getName());
                            colDto.put("dataType", col.getDataType());
                            colDto.put("nullable", col.getNullable());
                            colDto.put("tags", col.getTags());
                            colDto.put("sensitiveTags", col.getSensitiveTags());
                            colDto.put("status", trimToNull(col.getStatus()));
                            String columnComment = StringUtils.hasText(col.getComment()) ? col.getComment().trim() : null;
                            colDto.put("comment", columnComment);
                            colDto.put("description", columnComment);
                            if (columnComment != null) {
                                colDto.put("displayName", columnComment);
                            }
                            columnDtos.add(colDto);
                        });
                    tableDto.put("columns", columnDtos);
                    tables.add(tableDto);
                });
            m.put("tables", tables);
        }
        return m;
    }

    // --- Dataset list specification ---

    public Specification<CatalogDataset> buildDatasetListSpecification(
        UUID domainId,
        boolean domainUnassigned,
        UUID sourceId,
        String keyword,
        String classification,
        String ownerDept,
        String warehouseLayer,
        boolean enabledOnly,
        String type,
        String exposedBy,
        String owner,
        String tag
    ) {
        String keywordText = trimToNull(keyword);
        String ownerText = trimToNull(owner);
        String tagText = trimToNull(tag);
        String classificationText = trimToNull(classification);
        String ownerDeptText = trimToNull(ownerDept);
        String layerText = trimToNull(warehouseLayer);
        String typeText = trimToNull(type);
        String exposedByText = trimToNull(exposedBy);
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (domainUnassigned) {
                predicates.add(cb.isNull(root.get("domain")));
            } else if (domainId != null) {
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
            if (typeText != null) {
                predicates.add(cb.equal(cb.lower(root.get("type")), typeText.toLowerCase(Locale.ROOT)));
            }
            if (exposedByText != null) {
                predicates.add(cb.equal(cb.lower(root.get("exposedBy")), exposedByText.toLowerCase(Locale.ROOT)));
            }
            if (ownerText != null) {
                predicates.add(cb.like(cb.lower(root.get("owner")), "%" + ownerText.toLowerCase(Locale.ROOT) + "%"));
            }
            if (tagText != null) {
                predicates.add(cb.like(cb.lower(root.get("tags")), "%" + tagText.toLowerCase(Locale.ROOT) + "%"));
            }
            if (enabledOnly) {
                predicates.add(cb.or(cb.isNull(root.get("enabled")), cb.isTrue(root.get("enabled"))));
            }
            if (keywordText != null) {
                String keywordLike = "%" + keywordText.toLowerCase(Locale.ROOT) + "%";
                predicates.add(
                    cb.or(
                        cb.like(cb.lower(root.get("name")), keywordLike),
                        cb.like(cb.lower(root.get("owner")), keywordLike),
                        cb.like(cb.lower(root.get("ownerDept")), keywordLike),
                        cb.like(cb.lower(root.get("tags")), keywordLike),
                        cb.like(cb.lower(root.get("description")), keywordLike),
                        cb.like(cb.lower(root.get("hiveDatabase")), keywordLike),
                        cb.like(cb.lower(root.get("hiveTable")), keywordLike)
                    )
                );
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    public String normalizeDatasetSortBy(String sortBy) {
        String normalized = trimToNull(sortBy);
        if (normalized == null) {
            return "createdDate";
        }
        return switch (normalized.toLowerCase(Locale.ROOT)) {
            case "name" -> "name";
            case "lastmodifieddate", "updatedat", "updated_at" -> "lastModifiedDate";
            case "createddate", "createdat", "created_at" -> "createdDate";
            default -> "createdDate";
        };
    }

    // --- Dataset change audit helpers ---

    public Map<String, Object> datasetChangePayload(String summary, Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (StringUtils.hasText(summary)) {
            payload.put("summary", summary);
        }
        payload.put("resourceType", "CATALOG_DATASET");
        if (before != null && !before.isEmpty()) {
            payload.put("before", before);
        }
        if (after != null && !after.isEmpty()) {
            payload.put("after", after);
        }
        Object targetId = after != null && after.containsKey("id")
            ? after.get("id")
            : before != null ? before.get("id") : null;
        if (targetId != null) {
            payload.put("targetId", targetId);
        }
        String resourceName = null;
        if (after != null && after.get("name") instanceof String afterName && StringUtils.hasText(afterName)) {
            resourceName = afterName.trim();
        } else if (before != null && before.get("name") instanceof String beforeName && StringUtils.hasText(beforeName)) {
            resourceName = beforeName.trim();
        }
        if (StringUtils.hasText(resourceName)) {
            payload.put("resourceName", resourceName);
            payload.put("targetName", resourceName);
        }
        return payload;
    }

    public Map<String, Object> datasetSnapshot(CatalogDataset dataset) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (dataset == null) {
            return snapshot;
        }
        if (dataset.getId() != null) {
            snapshot.put("id", dataset.getId().toString());
        }
        if (StringUtils.hasText(dataset.getName())) {
            snapshot.put("name", dataset.getName());
        }
        if (dataset.getDomain() != null) {
            CatalogDomain domain = dataset.getDomain();
            if (domain.getId() != null) {
                snapshot.put("domainId", domain.getId().toString());
            }
            if (StringUtils.hasText(domain.getName())) {
                snapshot.put("domainName", domain.getName());
            }
        }
        if (StringUtils.hasText(dataset.getType())) {
            snapshot.put("type", dataset.getType());
        }
        if (dataset.getSourceId() != null) {
            snapshot.put("sourceId", dataset.getSourceId().toString());
        }
        if (StringUtils.hasText(dataset.getClassification())) {
            snapshot.put("classification", dataset.getClassification());
        }
        if (StringUtils.hasText(dataset.getOwnerDept())) {
            snapshot.put("ownerDept", dataset.getOwnerDept());
        }
        if (StringUtils.hasText(dataset.getOwner())) {
            snapshot.put("owner", dataset.getOwner());
        }
        if (StringUtils.hasText(dataset.getHiveDatabase())) {
            snapshot.put("hiveDatabase", dataset.getHiveDatabase());
        }
        if (StringUtils.hasText(dataset.getHiveTable())) {
            snapshot.put("hiveTable", dataset.getHiveTable());
        }
        if (StringUtils.hasText(dataset.getDescription())) {
            snapshot.put("description", dataset.getDescription());
        }
        if (StringUtils.hasText(dataset.getTags())) {
            snapshot.put("tags", dataset.getTags());
        }
        if (StringUtils.hasText(dataset.getWarehouseLayer())) {
            snapshot.put("warehouseLayer", dataset.getWarehouseLayer());
        }
        if (dataset.getEnabled() != null) {
            snapshot.put("enabled", dataset.getEnabled());
        }
        if (StringUtils.hasText(dataset.getExposedBy())) {
            snapshot.put("exposedBy", dataset.getExposedBy());
        }
        if (StringUtils.hasText(dataset.getTrinoCatalog())) {
            snapshot.put("trinoCatalog", dataset.getTrinoCatalog());
        }
        if (StringUtils.hasText(dataset.getLifecycleStatus())) {
            snapshot.put("lifecycleStatus", dataset.getLifecycleStatus());
        }
        if (dataset.getRetentionDays() != null) {
            snapshot.put("retentionDays", dataset.getRetentionDays());
        }
        if (dataset.getExpiresAt() != null) {
            snapshot.put("expiresAt", dataset.getExpiresAt().toString());
        }
        return snapshot;
    }

    public String displayName(CatalogDataset dataset) {
        if (dataset == null) {
            return "";
        }
        if (StringUtils.hasText(dataset.getName())) {
            return dataset.getName();
        }
        return dataset.getId() != null ? dataset.getId().toString() : "";
    }

    // --- Dataset policy methods ---

    public void applyOwnerDepartmentPolicy(CatalogDataset dataset, String previousOwnerDept, boolean enforceNoChangeForNonOp) {
        String trimmedPrevious = Optional.ofNullable(previousOwnerDept).map(String::trim).filter(s -> !s.isEmpty()).orElse(null);
        String requested = Optional.ofNullable(dataset.getOwnerDept()).map(String::trim).filter(s -> !s.isEmpty()).orElse(null);
        String rootDept = organizationVisibilityService.resolveDefaultRootDept().orElse(null);
        if (!StringUtils.hasText(rootDept)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "所属部门不能为空（未获取到所级部门，请先同步组织树）");
        }

        if (SecurityUtils.isOpAdminAccount()) {
            dataset.setOwnerDept(requested != null ? requested : rootDept);
            return;
        }

        if (enforceNoChangeForNonOp && !Objects.equals(requested, trimmedPrevious)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅运维管理员可以调整数据资产归属部门");
        }

        dataset.setOwnerDept(trimmedPrevious != null ? trimmedPrevious : rootDept);
    }

    public void applySourcePolicy(CatalogDataset dataset) {
        String requested = Optional.ofNullable(dataset.getType()).map(String::trim).orElse("");
        String defaultSource = defaultSourceType();
        boolean multiSourceEnabled = catalogFeatures.isMultiSourceEnabled();

        if (!multiSourceEnabled) {
            if (!requested.isBlank() && !requested.equalsIgnoreCase(defaultSource)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "多数据源能力尚未解锁，请联系管理员升级");
            }
            dataset.setType(defaultSource);
            dataset.setSourceId(null);
            return;
        }

        if (requested.isBlank()) {
            dataset.setType(defaultSource);
        } else {
            dataset.setType(requested.toUpperCase(Locale.ROOT));
        }
    }

    public void normalizeClassification(CatalogDataset dataset) {
        String value = dataset.getClassification();
        DataLevel normalized = DataLevel.normalize(value);
        if (normalized != null) {
            dataset.setClassification(normalized.classification());
            return;
        }
        if (value != null && !value.isBlank()) {
            dataset.setClassification(value.trim().toUpperCase(Locale.ROOT));
        } else {
            dataset.setClassification(SecurityLevelCatalog.DEFAULT_DATA_SECURITY_LEVEL.code());
        }
    }

    public String normalizeClassificationString(String text) {
        String value = trimToNull(text);
        if (value == null) {
            return null;
        }
        DataLevel normalized = DataLevel.normalize(value);
        return normalized != null ? normalized.classification() : value.toUpperCase(Locale.ROOT);
    }

    public void normalizeWarehouseLayer(CatalogDataset dataset) {
        String value = trimToNull(dataset.getWarehouseLayer());
        if (value == null) {
            dataset.setWarehouseLayer(null);
            return;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        dataset.setWarehouseLayer(normalized);
    }

    public void ensurePrimarySourceIfRequired(CatalogDataset dataset) {
        if (isDefaultSource(dataset) && !hasPrimarySourceConfigured()) {
            throw new ResponseStatusException(
                HttpStatus.PRECONDITION_FAILED,
                "未检测到 Hive 数据源，请联系系统管理员！"
            );
        }
    }

    public boolean hasPrimarySourceConfigured() {
        String defaultSource = defaultSourceType();
        if (hasActiveDataSource(defaultSource)) {
            return true;
        }
        if (TYPE_INCEPTOR.equals(defaultSource)) {
            return hasActiveDataSource("HIVE");
        }
        return false;
    }

    public boolean isDefaultSource(CatalogDataset dataset) {
        return Optional.ofNullable(dataset.getType()).map(String::trim).map(s -> s.equalsIgnoreCase(defaultSourceType())).orElse(false);
    }

    public String defaultSourceType() {
        String configured = Optional
            .ofNullable(catalogFeatures.getDefaultSourceType())
            .map(String::trim)
            .map(String::toUpperCase)
            .filter(s -> !s.isBlank())
            .orElse(TYPE_INCEPTOR);
        if ("HIVE".equals(configured) || TYPE_INCEPTOR.equals(configured)) {
            if (hasActiveDataSource(TYPE_INCEPTOR) || hasActiveDataSource("HIVE")) {
                return TYPE_INCEPTOR;
            }
            if (hasActiveDataSource("POSTGRES")) {
                return "POSTGRES";
            }
            return TYPE_INCEPTOR;
        }
        if (hasActiveDataSource(configured)) {
            return configured;
        }
        if (hasActiveDataSource("POSTGRES")) {
            return "POSTGRES";
        }
        return configured;
    }

    public boolean hasActiveDataSource(String type) {
        if (!StringUtils.hasText(type)) {
            return false;
        }
        return infraDataSourceRepository
            .findFirstByTypeIgnoreCaseAndStatusIgnoreCase(type, "ACTIVE")
            .isPresent();
    }

    // --- Permission helpers ---

    public boolean canEditDataset(CatalogDataset dataset) {
        if (dataset == null) {
            return false;
        }
        if (SecurityUtils.isOpAdminAccount()) {
            return true;
        }
        if (SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES)) {
            return true;
        }
        if (SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.DEPARTMENT_PRIVILEGED_ROLES)) {
            String userDept = claim("dept_code");
            if (userDept == null || userDept.isBlank()) {
                return false;
            }
            return DepartmentUtils.matches(dataset.getOwnerDept(), userDept);
        }
        return false;
    }

    public void ensureDatasetEditPermission(CatalogDataset dataset) {
        if (canEditDataset(dataset)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前用户无权编辑该数据集");
    }

    // --- Standards loading ---

    public Map<UUID, DataStandard> loadStandardsById(List<CatalogColumnSchema> columns) {
        if (columns == null || columns.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<UUID> ids = new LinkedHashSet<>();
        for (CatalogColumnSchema c : columns) {
            if (c == null) continue;
            if (c.getStandardId() != null) {
                ids.add(c.getStandardId());
            }
        }
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<UUID, DataStandard> map = new HashMap<>();
        for (DataStandard standard : dataStandardRepository.findAllById(ids)) {
            if (standard != null && standard.getId() != null) {
                map.put(standard.getId(), standard);
            }
        }
        return map;
    }

    // --- Reconciliation & governance helpers ---

    public Map<String, Object> assertion(
        String code,
        String name,
        boolean passed,
        String severity,
        String detail,
        String suggestion
    ) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", code);
        row.put("name", name);
        row.put("passed", passed);
        row.put("severity", severity);
        row.put("detail", detail);
        row.put("suggestion", suggestion);
        return row;
    }

    public Map<String, Object> checklistItem(String code, String name, String route, String description) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", code);
        row.put("name", name);
        row.put("route", route);
        row.put("description", description);
        return row;
    }

    // --- Schema snapshot & change tracking ---

    public Map<String, Object> snapshotTable(CatalogTableSchema table) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (table == null) {
            return m;
        }
        m.put("name", trimToNull(table.getName()));
        m.put("owner", trimToNull(table.getOwner()));
        m.put("classification", trimToNull(table.getClassification()));
        m.put("bizDomain", trimToNull(table.getBizDomain()));
        m.put("tags", trimToNull(table.getTags()));
        return m;
    }

    public Map<String, Object> snapshotColumn(CatalogColumnSchema column) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (column == null) {
            return m;
        }
        m.put("name", trimToNull(column.getName()));
        m.put("dataType", trimToNull(column.getDataType()));
        m.put("nullable", column.getNullable());
        m.put("tags", trimToNull(column.getTags()));
        m.put("sensitiveTags", trimToNull(column.getSensitiveTags()));
        m.put("comment", trimToNull(column.getComment()));
        m.put("standardId", column.getStandardId() != null ? column.getStandardId().toString() : null);
        m.put("standardRule", trimToNull(column.getStandardRule()));
        m.put("standardMismatchReason", trimToNull(column.getStandardMismatchReason()));
        m.put("status", trimToNull(column.getStatus()));
        return m;
    }

    public void recordTableMetadataChanges(CatalogTableSchema saved, Map<String, Object> before, String source) {
        if (saved == null || saved.getId() == null) {
            return;
        }
        UUID tableId = saved.getId();
        UUID datasetId = saved.getDataset() != null ? saved.getDataset().getId() : null;
        String actorDept = claim("dept_code");
        List<CatalogMetadataChangeLog> changes = new ArrayList<>();

        addMetadataChange(changes, "TABLE", tableId, datasetId, null, "name", safeText(before.get("name")), saved.getName(), "更新数据表元数据", actorDept, source);
        addMetadataChange(changes, "TABLE", tableId, datasetId, null, "owner", safeText(before.get("owner")), saved.getOwner(), "更新数据表元数据", actorDept, source);
        addMetadataChange(changes, "TABLE", tableId, datasetId, null, "classification", safeText(before.get("classification")), saved.getClassification(), "更新数据表元数据", actorDept, source);
        addMetadataChange(changes, "TABLE", tableId, datasetId, null, "bizDomain", safeText(before.get("bizDomain")), saved.getBizDomain(), "更新数据表元数据", actorDept, source);
        addMetadataChange(changes, "TABLE", tableId, datasetId, null, "tags", safeText(before.get("tags")), saved.getTags(), "更新数据表元数据", actorDept, source);

        if (!changes.isEmpty()) {
            metadataChangeLogRepo.saveAll(changes);
        }
    }

    public void recordColumnMetadataChanges(CatalogColumnSchema saved, Map<String, Object> before, String source) {
        if (saved == null || saved.getId() == null) {
            return;
        }
        UUID columnId = saved.getId();
        UUID tableId = saved.getTable() != null ? saved.getTable().getId() : null;
        UUID datasetId = null;
        if (saved.getTable() != null && saved.getTable().getDataset() != null) {
            datasetId = saved.getTable().getDataset().getId();
        }
        String actorDept = claim("dept_code");
        List<CatalogMetadataChangeLog> changes = new ArrayList<>();

        addMetadataChange(changes, "COLUMN", columnId, datasetId, tableId, "name", safeText(before.get("name")), saved.getName(), "更新数据字段元数据", actorDept, source);
        addMetadataChange(changes, "COLUMN", columnId, datasetId, tableId, "dataType", safeText(before.get("dataType")), saved.getDataType(), "更新数据字段元数据", actorDept, source);
        addMetadataChange(changes, "COLUMN", columnId, datasetId, tableId, "nullable", safeText(before.get("nullable")), safeBool(saved.getNullable()), "更新数据字段元数据", actorDept, source);
        addMetadataChange(changes, "COLUMN", columnId, datasetId, tableId, "tags", safeText(before.get("tags")), saved.getTags(), "更新数据字段元数据", actorDept, source);
        addMetadataChange(changes, "COLUMN", columnId, datasetId, tableId, "sensitiveTags", safeText(before.get("sensitiveTags")), saved.getSensitiveTags(), "更新数据字段元数据", actorDept, source);
        addMetadataChange(changes, "COLUMN", columnId, datasetId, tableId, "comment", safeText(before.get("comment")), saved.getComment(), "更新数据字段元数据", actorDept, source);
        addMetadataChange(changes, "COLUMN", columnId, datasetId, tableId, "standardId", safeText(before.get("standardId")), safeText(saved.getStandardId()), "更新数据字段元数据", actorDept, source);
        addMetadataChange(changes, "COLUMN", columnId, datasetId, tableId, "standardRule", safeText(before.get("standardRule")), saved.getStandardRule(), "更新数据字段元数据", actorDept, source);
        addMetadataChange(changes, "COLUMN", columnId, datasetId, tableId, "standardMismatchReason", safeText(before.get("standardMismatchReason")), saved.getStandardMismatchReason(), "更新数据字段元数据", actorDept, source);

        if (!changes.isEmpty()) {
            metadataChangeLogRepo.saveAll(changes);
        }
    }

    private void addMetadataChange(
        List<CatalogMetadataChangeLog> out,
        String objectType,
        UUID objectId,
        UUID datasetId,
        UUID tableId,
        String fieldName,
        String beforeValue,
        String afterValue,
        String summary,
        String actorDept,
        String source
    ) {
        String before = normalizeText(beforeValue);
        String after = normalizeText(afterValue);
        if (Objects.equals(before, after)) {
            return;
        }
        CatalogMetadataChangeLog log = new CatalogMetadataChangeLog();
        log.setObjectType(objectType);
        log.setObjectId(objectId);
        log.setDatasetId(datasetId);
        log.setTableId(tableId);
        log.setFieldName(fieldName);
        log.setBeforeValue(before);
        log.setAfterValue(after);
        log.setChangeSummary(summary);
        log.setActorDept(normalizeText(actorDept));
        log.setSource(normalizeText(source));
        out.add(log);
    }

    // --- Standard mapping helpers (used by SchemaResource) ---

    public Map<String, Object> buildStandardIssue(CatalogColumnSchema col, DataStandard standard, String reason) {
        Map<String, Object> issue = new LinkedHashMap<>();
        issue.put("columnId", col.getId() != null ? col.getId().toString() : null);
        issue.put("columnName", col.getName());
        issue.put("columnDataType", trimToNull(col.getDataType()));
        issue.put("columnNullable", col.getNullable());
        issue.put("standardId", col.getStandardId() != null ? col.getStandardId().toString() : null);
        if (standard != null) {
            issue.put("standardCode", standard.getCode());
            issue.put("standardName", standard.getName());
            issue.put("standardDataType", trimToNull(standard.getDataType()));
            issue.put("standardNullable", standard.getNullable());
            issue.put("standardCodeSet", trimToNull(standard.getCodeSet()));
        }
        issue.put("reason", reason);
        return issue;
    }

    public boolean isTypeCompatible(String standardType, String columnType) {
        String standard = normalizeDataType(standardType);
        String column = normalizeDataType(columnType);
        if (standard == null || column == null) {
            return true;
        }
        return standard.equals(column);
    }

    public String normalizeDataType(String rawType) {
        if (!StringUtils.hasText(rawType)) {
            return null;
        }
        String t = rawType.trim().toLowerCase(Locale.ROOT);
        int paren = t.indexOf('(');
        if (paren > 0) {
            t = t.substring(0, paren).trim();
        }
        if (t.isEmpty()) {
            return null;
        }
        return switch (t) {
            case "varchar", "char", "character", "character varying", "string", "text" -> "string";
            case "bigint", "int8", "long" -> "bigint";
            case "int", "integer", "int4" -> "int";
            case "smallint", "int2", "short" -> "smallint";
            case "double", "float8" -> "double";
            case "float", "float4", "real" -> "float";
            case "decimal", "numeric" -> "decimal";
            case "boolean", "bool" -> "boolean";
            case "timestamp", "datetime" -> "timestamp";
            default -> t;
        };
    }

    public Map<String, Object> toColumnDto(CatalogColumnSchema col, Map<UUID, DataStandard> standards) {
        Map<String, Object> dto = new LinkedHashMap<>();
        if (col == null) {
            return dto;
        }
        dto.put("id", col.getId());
        dto.put("name", col.getName());
        dto.put("dataType", col.getDataType());
        dto.put("nullable", col.getNullable());
        dto.put("tags", col.getTags());
        dto.put("sensitiveTags", col.getSensitiveTags());
        dto.put("comment", trimToNull(col.getComment()));
        dto.put("standardId", col.getStandardId());
        dto.put("standardRule", trimToNull(col.getStandardRule()));
        dto.put("standardMismatchReason", trimToNull(col.getStandardMismatchReason()));
        dto.put("status", trimToNull(col.getStatus()));

        DataStandard standard = (col.getStandardId() != null && standards != null) ? standards.get(col.getStandardId()) : null;
        if (standard != null) {
            dto.put("standardCode", standard.getCode());
            dto.put("standardName", standard.getName());
            dto.put("standardDataType", standard.getDataType());
            dto.put("standardNullable", standard.getNullable());
            dto.put("standardCodeSet", standard.getCodeSet());
        }
        String computedMismatchReason = computeStandardMismatchReason(col, standard);
        dto.put("computedMismatchReason", computedMismatchReason);
        dto.put("mappingStatus", computeStandardMappingStatus(col, standard, computedMismatchReason));
        return dto;
    }

    public String extractStandardCodeHint(String comment) {
        if (!StringUtils.hasText(comment)) {
            return null;
        }
        Matcher matcher = STD_CODE_PATTERN.matcher(comment);
        if (matcher.find()) {
            String raw = matcher.group(1);
            return StringUtils.hasText(raw) ? raw.trim() : null;
        }
        return null;
    }

    public String computeStandardMismatchReason(CatalogColumnSchema col, DataStandard standard) {
        if (col == null) {
            return null;
        }
        UUID standardId = col.getStandardId();
        if (standardId == null) {
            return "未绑定数据元（字段标准）";
        }
        if (standard == null) {
            return "关联的数据元不存在或无权限";
        }
        String typeReason = null;
        String nullableReason = null;
        String standardType = trimToNull(standard.getDataType());
        String columnType = trimToNull(col.getDataType());
        if (standardType != null && columnType != null && !isTypeCompatible(standardType, columnType)) {
            typeReason = "字段类型与数据元不一致";
        }
        Boolean standardNullable = standard.getNullable();
        Boolean columnNullable = col.getNullable();
        if (standardNullable != null && Boolean.FALSE.equals(standardNullable) && Boolean.TRUE.equals(columnNullable)) {
            nullableReason = "字段可空性与数据元不一致（数据元要求不可空）";
        }
        if (typeReason != null || nullableReason != null) {
            return typeReason != null && nullableReason != null ? (typeReason + "；" + nullableReason) : (typeReason != null ? typeReason : nullableReason);
        }
        return null;
    }

    public String computeStandardMappingStatus(CatalogColumnSchema col, DataStandard standard, String computedMismatchReason) {
        if (col == null) {
            return "UNKNOWN";
        }
        if (col.getStandardId() == null) {
            return "UNMAPPED";
        }
        if (standard == null) {
            return "STANDARD_MISSING";
        }
        if (computedMismatchReason != null) {
            return "MISMATCHED";
        }
        return "OK";
    }

    // --- Masking helpers ---

    public boolean requiresMasking(String classification) {
        return Objects.equals(classification, "SECRET") || Objects.equals(classification, "CONFIDENTIAL");
    }

    public Map<String, Object> toMaskingRuleDto(CatalogMaskingRule rule) {
        Map<String, Object> row = new LinkedHashMap<>();
        if (rule == null) {
            return row;
        }
        if (rule.getId() != null) {
            row.put("id", rule.getId().toString());
        }
        row.put("column", trimToNull(rule.getColumn()));
        row.put("function", trimToNull(rule.getFunction()));
        row.put("args", trimToNull(rule.getArgs()));
        return row;
    }

    // --- Grant helpers ---

    public boolean canManageGrants() {
        return (
            SecurityUtils.isOpAdminAccount() ||
            SecurityUtils.hasCurrentUserAnyOfAuthorities(
                AuthoritiesConstants.DATA_MAINTAINER_ROLES
            )
        );
    }

    public Map<String, Object> toGrantDto(CatalogDatasetGrant grant) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", grant.getId());
        map.put("datasetId", grant.getDataset() != null ? grant.getDataset().getId() : null);
        map.put("userId", grant.getGranteeId());
        map.put("username", grant.getGranteeUsername());
        map.put("displayName", grant.getGranteeName());
        map.put("deptCode", grant.getGranteeDept());
        map.put("grantType", grant.getGrantType());
        map.put("canQuery", grant.getCanQuery());
        map.put("canPreview", grant.getCanPreview());
        map.put("validFrom", grant.getValidFrom());
        map.put("validTo", grant.getValidTo());
        map.put("sourceRequestId", grant.getSourceRequestId());
        map.put("createdBy", grant.getCreatedBy());
        map.put("createdDate", grant.getCreatedDate());
        return map;
    }

    // --- Records (inner types moved from CatalogResource) ---

    public record OpenMetadataBatchRequest(List<UUID> ids) {}

    public record DatasetGrantRequest(String userId, String username, String displayName, String deptCode) {}

    public record StandardAutoMapApplyRequest(Boolean overwrite, Boolean onlyUnmapped) {}

    public record MappingValidationResult(
        List<CatalogClassificationMapping> normalizedItems,
        List<Map<String, Object>> conflicts,
        List<Map<String, Object>> warnings
    ) {}
}
