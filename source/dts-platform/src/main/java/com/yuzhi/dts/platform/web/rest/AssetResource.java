package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogDatasetJob;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogMaskingRuleRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogRowFilterRuleRepository;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.security.DatasetSecurityMetadataResolver;
import com.yuzhi.dts.platform.security.policy.DataLevel;
import com.yuzhi.dts.platform.service.query.QueryGateway;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.service.catalog.DatasetJobService;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import com.yuzhi.dts.platform.service.security.DatasetSqlBuilder;
import com.yuzhi.dts.platform.service.security.SecurityGuardException;
import com.yuzhi.dts.platform.service.security.SecuritySqlRewriter;
import jakarta.validation.Valid;
import java.lang.reflect.Array;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Asset APIs aligned with .cotmp definitions, delegating to catalog repositories.
 */
@RestController
@RequestMapping("/api")
@Transactional
public class AssetResource {

    private static final String CATALOG_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).CATALOG_MAINTAINERS)";

    private final CatalogDatasetRepository datasetRepo;
    private final CatalogRowFilterRuleRepository rowFilterRepo;
    private final CatalogMaskingRuleRepository maskingRepo;
    private final AccessChecker accessChecker;
    private final AuditService audit;
    private final DatasetJobService datasetJobService;
    private final QueryGateway queryGateway;
    private final DatasetSqlBuilder datasetSqlBuilder;
    private final DatasetSecurityMetadataResolver metadataResolver;
    private final SecuritySqlRewriter securitySqlRewriter;

    public AssetResource(
        CatalogDatasetRepository datasetRepo,
        CatalogRowFilterRuleRepository rowFilterRepo,
        CatalogMaskingRuleRepository maskingRepo,
        AccessChecker accessChecker,
        AuditService audit,
        DatasetJobService datasetJobService,
        QueryGateway queryGateway,
        DatasetSqlBuilder datasetSqlBuilder,
        DatasetSecurityMetadataResolver metadataResolver,
        SecuritySqlRewriter securitySqlRewriter
    ) {
        this.datasetRepo = datasetRepo;
        this.rowFilterRepo = rowFilterRepo;
        this.maskingRepo = maskingRepo;
        this.accessChecker = accessChecker;
        this.audit = audit;
        this.datasetJobService = datasetJobService;
        this.queryGateway = queryGateway;
        this.datasetSqlBuilder = datasetSqlBuilder;
        this.metadataResolver = metadataResolver;
        this.securitySqlRewriter = securitySqlRewriter;
    }

    /**
     * POST /api/datasets/{id}/sync-schema
     * MVP: if dataset has no tables, create one using hiveTable/name and a few example columns.
     */
    @PostMapping("/datasets/{id}/sync-schema")
    public ApiResponse<Map<String, Object>> syncSchema(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        CatalogDataset dataset = datasetRepo.findById(id).orElse(null);
        if (dataset == null) {
            audit.auditAction(
                "CATALOG_ASSET_EDIT",
                AuditStage.FAIL,
                id.toString(),
                Map.of("reason", "DATASET_NOT_FOUND")
            );
            return ApiResponses.error("数据集不存在或已被删除");
        }
        try {
            CatalogDatasetJob job = datasetJobService.submitSchemaSync(id, body != null ? body : Map.of(), SecurityUtils.getCurrentUserLogin().orElse("anonymous"));
            audit.auditAction(
                "CATALOG_ASSET_EDIT",
                AuditStage.SUCCESS,
                id.toString(),
                Map.of("jobId", job.getId(), "action", "syncSchema")
            );
            return ApiResponses.ok(Map.of("job", datasetJobService.toDto(job)));
        } catch (RuntimeException ex) {
            audit.auditAction(
                "CATALOG_ASSET_EDIT",
                AuditStage.FAIL,
                id.toString(),
                Map.of("error", sanitize(ex.getMessage()))
            );
            throw ex;
        }
    }

    @GetMapping("/dataset-jobs/{jobId}")
    public ApiResponse<Map<String, Object>> getJob(@PathVariable UUID jobId) {
        CatalogDatasetJob job = datasetJobService
            .findJob(jobId)
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "job not found"));
        audit.auditAction(
            "CATALOG_ASSET_VIEW",
            AuditStage.SUCCESS,
            jobId.toString(),
            Map.of("status", job.getStatus())
        );
        return ApiResponses.ok(datasetJobService.toDto(job));
    }

    @GetMapping("/datasets/{id}/jobs")
    public ApiResponse<List<Map<String, Object>>> recentJobs(@PathVariable UUID id) {
        List<Map<String, Object>> jobs = datasetJobService
            .recentJobs(id)
            .stream()
            .map(datasetJobService::toDto)
            .toList();
        audit.auditAction(
            "CATALOG_ASSET_VIEW",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of("jobs", jobs.size())
        );
        return ApiResponses.ok(jobs);
    }

    /**
     * GET /api/datasets/{id}/preview?rows=50
     * Returns sample rows applying simple masking and noting row filter presence.
     */
    @GetMapping("/datasets/{id}/preview")
    public ApiResponse<Map<String, Object>> preview(
        @PathVariable UUID id,
        @RequestParam(defaultValue = "50") int rows,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        CatalogDataset dataset = datasetRepo.findById(id).orElseThrow();
        String effDept = activeDept != null ? activeDept : claim("dept_code");
        boolean read = accessChecker.canRead(dataset);
        boolean deptOk = accessChecker.departmentAllowed(dataset, effDept);
        if (!read || !deptOk) {
            audit.auditAction(
                "CATALOG_ASSET_VIEW",
                AuditStage.FAIL,
                id.toString(),
                Map.of("reason", !read ? "RBAC_DENY" : "INVALID_CONTEXT")
            );
            String code = !read
                ? com.yuzhi.dts.platform.security.policy.PolicyErrorCodes.RBAC_DENY
                : com.yuzhi.dts.platform.security.policy.PolicyErrorCodes.INVALID_CONTEXT;
            return ApiResponses.error(code, "Access denied for dataset");
        }

        int safeRows = Math.max(1, Math.min(rows, 500));
        String sql;
        try {
            sql = buildPreviewSql(dataset, safeRows);
            sql = securitySqlRewriter.guard(sql, dataset);
        } catch (SecurityGuardException ex) {
            audit.auditAction(
                "CATALOG_ASSET_VIEW",
                AuditStage.FAIL,
                id.toString(),
                Map.of("reason", ex.getMessage())
            );
            return ApiResponses.error(ex.getMessage());
        } catch (IllegalStateException ex) {
            audit.auditAction(
                "CATALOG_ASSET_VIEW",
                AuditStage.FAIL,
                id.toString(),
                Map.of("reason", ex.getMessage())
            );
            return ApiResponses.error(ex.getMessage());
        }

        try {
            Map<String, Object> queryResult = queryGateway.execute(sql);
            List<String> headers = extractHeaders(queryResult.get("headers"));
            List<Map<String, Object>> rowsData = extractRows(queryResult.get("rows"), headers);
            if (!rowsData.isEmpty()) {
                applyDataLevelFilter(dataset, headers, rowsData);
            }
            Map<String, String> maskingMap = new HashMap<>();
            maskingRepo
                .findByDataset(dataset)
                .forEach(rule -> maskingMap.put(rule.getColumn(), rule.getFunction()));
            if (!maskingMap.isEmpty()) {
                for (Map<String, Object> row : rowsData) {
                    for (String header : headers) {
                        String fn = maskingMap.get(header);
                        if (fn != null && row.containsKey(header)) {
                            Object original = row.get(header);
                            row.put(header, applyMask(fn, original != null ? String.valueOf(original) : null));
                        }
                    }
                }
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("headers", headers);
            result.put("rows", rowsData);
            result.put("rowCount", rowsData.size());
            result.put("sql", sql);
            if (queryResult.containsKey("connectMillis")) {
                result.put("connectMillis", queryResult.get("connectMillis"));
            }
            if (queryResult.containsKey("queryMillis")) {
                result.put("queryMillis", queryResult.get("queryMillis"));
            }
            audit.auditAction(
                "CATALOG_ASSET_VIEW",
                AuditStage.SUCCESS,
                id.toString(),
                Map.of("rows", rowsData.size(), "requestedRows", safeRows)
            );
            return ApiResponses.ok(result);
        } catch (Exception ex) {
            String message = sanitize(ex.getMessage());
            audit.auditAction(
                "CATALOG_ASSET_VIEW",
                AuditStage.FAIL,
                id.toString(),
                Map.of("error", message, "sql", sql)
            );
            return ApiResponses.error("数据预览失败: " + message);
        }
    }

    private Object applyMask(String function, String value) {
        if (value == null) {
            return null;
        }
        return switch (function == null ? "" : function.toLowerCase()) {
            case "hash" -> Integer.toHexString(Objects.hashCode(value));
            case "mask_email" -> value.replaceAll("(^.).*(@.*$)", "$1***$2");
            case "mask_phone" -> value.replaceAll("(\\\\d{3})\\\\d{4}(\\\\d{4})", "$1****$2");
            case "partial" -> value.length() <= 2 ? "*".repeat(value.length()) : value.charAt(0) + "***" + value.charAt(value.length() - 1);
            default -> value;
        };
    }

    private String sanitize(String message) {
        if (message == null) {
            return "";
        }
        String cleaned = message.replaceAll("\n", " ").trim();
        return cleaned.length() > 160 ? cleaned.substring(0, 160) : cleaned;
    }

    private String buildPreviewSql(CatalogDataset dataset, int limit) {
        return datasetSqlBuilder.buildSampleQuery(dataset, limit);
    }

    private List<String> extractHeaders(Object headersObj) {
        if (headersObj instanceof List<?> list) {
            List<String> headers = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    headers.add(String.valueOf(item));
                }
            }
            if (!headers.isEmpty()) {
                return headers;
            }
        }
        return List.of("col_1", "col_2", "col_3");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractRows(Object rowsObj, List<String> headers) {
        if (rowsObj instanceof List<?> list) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (String header : headers) {
                        Object value = map.get(header);
                        row.put(header, value);
                    }
                    rows.add(row);
                }
            }
            return rows;
        }
        return List.of();
    }

    private void applyDataLevelFilter(CatalogDataset dataset, List<String> headers, List<Map<String, Object>> rows) {
        if (dataset == null || rows == null || rows.isEmpty()) {
            return;
        }
        Optional<String> columnOpt = metadataResolver.findDataLevelColumn(dataset);
        if (columnOpt.isEmpty()) {
            return;
        }
        List<DataLevel> allowedList = accessChecker.resolveAllowedDataLevels();
        if (allowedList == null || allowedList.isEmpty()) {
            rows.clear();
            return;
        }
        Set<DataLevel> allowedLevels = allowedList
            .stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (allowedLevels.isEmpty()) {
            rows.clear();
            return;
        }
        Set<String> allowedTokens = allowedLevels
            .stream()
            .flatMap(level -> level.tokens().stream())
            .map(token -> token.toUpperCase(Locale.ROOT))
            .collect(Collectors.toCollection(LinkedHashSet::new));

        String columnName = columnOpt.get();
        int headerIndex = resolveHeaderIndex(headers, columnName);
        rows.removeIf(row -> !isRowAllowed(row, headers, headerIndex, columnName, allowedLevels, allowedTokens));
    }

    private int resolveHeaderIndex(List<String> headers, String columnName) {
        if (headers == null || columnName == null) {
            return -1;
        }
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i);
            if (header != null && header.trim().equalsIgnoreCase(columnName)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isRowAllowed(
        Map<String, Object> row,
        List<String> headers,
        int headerIndex,
        String columnName,
        Set<DataLevel> allowedLevels,
        Set<String> allowedTokens
    ) {
        if (row == null || row.isEmpty()) {
            return false;
        }
        Object rawValue = lookupValue(row, columnName);
        if (rawValue == null && headerIndex >= 0 && headers != null && headerIndex < headers.size()) {
            rawValue = lookupValue(row, headers.get(headerIndex));
        }
        if (rawValue == null) {
            return false;
        }
        String text = rawValue.toString().trim();
        if (text.isEmpty()) {
            return false;
        }
        DataLevel normalized = DataLevel.normalize(text);
        if (normalized != null) {
            return allowedLevels.contains(normalized);
        }
        for (String variant : expandValueVariants(text)) {
            if (allowedTokens.contains(variant)) {
                return true;
            }
        }
        return false;
    }

    private Object lookupValue(Map<String, Object> row, String key) {
        if (key == null) {
            return null;
        }
        if (row.containsKey(key)) {
            return row.get(key);
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private Set<String> expandValueVariants(String raw) {
        Set<String> variants = new LinkedHashSet<>();
        if (raw == null) {
            return variants;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return variants;
        }
        String upper = trimmed.toUpperCase(Locale.ROOT);
        variants.add(upper);
        variants.add(upper.replace('-', '_'));
        variants.add(upper.replace('_', '-'));
        variants.add(upper.replace(' ', '_'));
        variants.add(upper.replace(' ', '-'));
        variants.add(upper.replace('-', ' '));
        variants.add(upper.replace('_', ' '));
        return variants;
    }


    private String claim(String name) {
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

    private String stringifyClaim(Object raw) {
        Object flattened = flattenClaim(raw);
        if (flattened == null) return null;
        String text = flattened.toString();
        return text == null || text.isBlank() ? null : text;
    }

    private Object flattenClaim(Object raw) {
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
}
