package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.explore.ExecEnums;
import com.yuzhi.dts.platform.domain.explore.QueryExecution;
import com.yuzhi.dts.platform.domain.explore.ResultSet;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.explore.QueryExecutionRepository;
import com.yuzhi.dts.platform.repository.explore.ResultSetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogMaskingRuleRepository;
import com.yuzhi.dts.platform.domain.catalog.CatalogMaskingRule;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.query.QueryGateway;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import com.yuzhi.dts.platform.service.security.DatasetDataAccessApprovalService;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/explore/legacy")
@Transactional
public class ExploreExecResource {

    private final QueryGateway queryGateway;
    private final AuditService audit;
    private final CatalogDatasetRepository datasetRepo;
    private final AccessChecker accessChecker;
    private final DatasetDataAccessApprovalService dataAccessApprovalService;
    private final QueryExecutionRepository executionRepository;
    private final ResultSetRepository resultSetRepository;
    private final CatalogMaskingRuleRepository maskingRepository;
    private final ObjectMapper objectMapper;

    public ExploreExecResource(
        QueryGateway queryGateway,
        AuditService audit,
        CatalogDatasetRepository datasetRepo,
        AccessChecker accessChecker,
        DatasetDataAccessApprovalService dataAccessApprovalService,
        QueryExecutionRepository executionRepository,
        ResultSetRepository resultSetRepository,
        CatalogMaskingRuleRepository maskingRepository,
        ObjectMapper objectMapper
    ) {
        this.queryGateway = queryGateway;
        this.audit = audit;
        this.datasetRepo = datasetRepo;
        this.accessChecker = accessChecker;
        this.dataAccessApprovalService = dataAccessApprovalService;
        this.executionRepository = executionRepository;
        this.resultSetRepository = resultSetRepository;
        this.maskingRepository = maskingRepository;
        this.objectMapper = objectMapper;
    }

    public record ExecuteRequest(String sqlText, String connection, String engine, UUID datasetId, Map<String, Object> variables) {}

    @PostMapping("/execute")
    public ApiResponse<Map<String, Object>> execute(
        @RequestBody ExecuteRequest req,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String sql = Objects.toString(req.sqlText, "");
        if (sql.isBlank()) return ApiResponses.error("sqlText is required");

        // Optional dataset-based permission check
        if (req.datasetId != null) {
            CatalogDataset ds = datasetRepo.findById(req.datasetId).orElse(null);
            if (!datasetWithinScope(ds, resolveActiveDeptContext(activeDept))) {
                audit.audit("DENY", "explore.execute", Objects.toString(req.datasetId));
                return ApiResponses.error("Access denied for dataset");
            }
            DatasetDataAccessApprovalService.Decision decision = dataAccessApprovalService.checkDataAccess(
                ds,
                DatasetDataAccessApprovalService.DataAction.QUERY,
                activeDept
            );
            if (!decision.allowed()) {
                audit.audit("DENY", "explore.execute", Objects.toString(req.datasetId));
                return ApiResponses.error(decision.code(), decision.message());
            }
        } else {
            DatasetDataAccessApprovalService.Decision decision = dataAccessApprovalService.checkDataAccess(
                null,
                DatasetDataAccessApprovalService.DataAction.QUERY,
                activeDept
            );
            if (!decision.allowed()) {
                audit.audit("DENY", "explore.execute", "missing-dataset");
                return ApiResponses.error(decision.code(), decision.message());
            }
        }

        // Simple variable substitution: ${var} and :var
        String effective = applyVariables(sql, req.variables);
        if (!isReadOnlyQuery(effective)) {
            audit.audit("DENY", "explore.execute", "write-operation");
            return ApiResponses.error("Only read-only queries are allowed");
        }

        QueryExecution exec = new QueryExecution();
        exec.setEngine(parseEngine(req.engine));
        exec.setConnection(req.connection);
        exec.setSqlText(effective);
        exec.setStatus(ExecEnums.ExecStatus.PENDING);
        try { exec.setDatasetId(req.datasetId); } catch (Exception ignored) {}
        exec.setStartedAt(Instant.now());
        exec = executionRepository.save(exec);

        Map<String, Object> result;
        try {
            exec.setStatus(ExecEnums.ExecStatus.RUNNING);
            executionRepository.save(exec);

            result = queryGateway.execute(effective);
            List<?> rows = (List<?>) result.getOrDefault("rows", List.of());
            exec.setRowCount((long) rows.size());
            exec.setBytesProcessed(Long.valueOf(Math.max(0, rows.size() * 10)));
            exec.setStatus(ExecEnums.ExecStatus.SUCCESS);
        } catch (IllegalStateException ex) {
            exec.setStatus(ExecEnums.ExecStatus.FAILED);
            exec.setErrorMessage(ex.getMessage());
            executionRepository.save(exec);
            audit.audit("ERROR", "explore.execute", exec.getId().toString());
            return ApiResponses.error(ex.getMessage());
        } catch (Exception e) {
            exec.setStatus(ExecEnums.ExecStatus.FAILED);
            exec.setErrorMessage(e.getMessage());
            executionRepository.save(exec);
            audit.audit("ERROR", "explore.execute", exec.getId().toString());
            return ApiResponses.error("Query execution failed: " + e.getMessage());
        } finally {
            exec.setFinishedAt(Instant.now());
            executionRepository.save(exec);
        }

        audit.audit("EXECUTE", "explore.execute", exec.getId().toString());
        Map<String, Object> payload = new LinkedHashMap<>(result);
        payload.put("executionId", exec.getId());
        return ApiResponses.ok(payload);
    }

    public record ExplainRequest(String sqlText, Map<String, Object> variables) {}

    @PostMapping("/explain")
    public ApiResponse<Map<String, Object>> explain(@RequestBody ExplainRequest req) {
        String sql = Objects.toString(req.sqlText, "");
        String effective = applyVariables(sql, req.variables);
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("effectiveSql", "EXPLAIN " + effective);
        plan.put("steps", List.of(
            "Parse query",
            "Resolve tables/views",
            "Apply row/column policies",
            "Plan scan + filter + aggregate",
            "Estimate cost"
        ));
        audit.audit("READ", "explore.explain", "inline");
        return ApiResponses.ok(plan);
    }

    public record SaveResultRequest(Integer ttlDays) {}

    @PostMapping("/save-result/{executionId}")
    public ApiResponse<ResultSet> saveResult(
        @PathVariable UUID executionId,
        @RequestBody(required = false) SaveResultRequest req,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        var exec = executionRepository.findById(executionId).orElse(null);
        if (exec == null) return ApiResponses.error("Execution not found");
        assertExecutionAccess(exec);
        CatalogDataset dataset = exec.getDatasetId() != null ? datasetRepo.findById(exec.getDatasetId()).orElse(null) : null;
        String effDept = resolveActiveDeptContext(activeDept);
        if (dataset != null && !datasetWithinScope(dataset, effDept)) {
            audit.audit("DENY", "explore.saveResult", executionId.toString());
            return ApiResponses.error(
                com.yuzhi.dts.platform.security.policy.PolicyErrorCodes.INVALID_CONTEXT,
                "Access denied for dataset"
            );
        }
        int ttl = req != null && req.ttlDays != null ? Math.max(1, req.ttlDays) : 7;

        Map<String, Object> preview;
        try {
            preview = queryGateway.execute(exec.getSqlText());
        } catch (Exception ex) {
            audit.audit("ERROR", "explore.saveResult", executionId.toString());
            return ApiResponses.error("执行结果预览失败: " + ex.getMessage());
        }

        List<String> headers = normalizeHeaders(preview.get("headers"));
        if (headers.isEmpty()) {
            return ApiResponses.error("未获取到结果字段，请重新执行查询");
        }
        String columns = String.join(",", headers);
        String previewJson = buildPreviewJson(headers, preview.get("rows"));

        ResultSet rs = new ResultSet();
        rs.setStorageUri("inline://result-set/pending");
        rs.setColumns(columns);
        rs.setRowCount(exec.getRowCount());
        rs.setPreviewColumns(previewJson);
        rs.setTtlDays(ttl);
        rs.setExpiresAt(Instant.now().plus(ttl, ChronoUnit.DAYS));
        rs = resultSetRepository.save(rs);
        rs.setStorageUri("inline://result-set/" + rs.getId());
        rs = resultSetRepository.save(rs);

        exec.setResultSetId(rs.getId());
        executionRepository.save(exec);

        audit.audit("EXPORT", "explore.saveResult", executionId.toString());
        return ApiResponses.ok(rs);
    }

    @GetMapping("/result-preview/{resultSetId}")
    public ApiResponse<Map<String, Object>> resultPreview(
        @PathVariable UUID resultSetId,
        @RequestParam(name = "rows", defaultValue = "100") int rows,
        @RequestParam(name = "datasetId", required = false) UUID datasetId,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        var rs = resultSetRepository.findById(resultSetId).orElse(null);
        if (rs == null) return ApiResponses.error("Result set not found");
        assertResultSetAccess(rs);
        String effDept = resolveActiveDeptContext(activeDept);

        List<String> headers = new ArrayList<>();
        List<Map<String, Object>> data = new ArrayList<>();
        if (StringUtils.hasText(rs.getPreviewColumns())) {
            try {
                Map<?, ?> payload = objectMapper.readValue(rs.getPreviewColumns(), Map.class);
                headers = normalizeHeaders(payload.get("headers"));
                data = normalizeRows(payload.get("rows"));
            } catch (Exception ex) {
                // ignore and fallback
            }
        }
        if (headers.isEmpty()) {
            for (String c : rs.getColumns().split(",")) headers.add(c.trim());
        }
        if (data.isEmpty()) {
            return ApiResponses.error("结果集暂无可预览数据，请重新执行保存");
        }
        int n = Math.min(Math.max(rows, 1), 1000);
        if (data.size() > n) {
            data = data.subList(0, n);
        }
        // Apply simple masking by policy (if dataset provided/derived) or by heuristic and collect metadata
        Map<String, Object> maskingMeta = new LinkedHashMap<>();
        UUID effectiveDatasetId = datasetId;
        if (effectiveDatasetId == null) {
            var execs = executionRepository.findByResultSetId(resultSetId);
            if (!execs.isEmpty()) effectiveDatasetId = execs.get(0).getDatasetId();
        }
        if (effectiveDatasetId != null) {
            var ds = datasetRepo.findById(effectiveDatasetId).orElse(null);
            if (!datasetWithinScope(ds, effDept)) {
                audit.audit("DENY", "explore.resultPreview", resultSetId.toString());
                return ApiResponses.error(
                    com.yuzhi.dts.platform.security.policy.PolicyErrorCodes.INVALID_CONTEXT,
                    "Access denied for dataset"
                );
            }
            if (ds != null) {
                var rules = maskingRepository.findByDataset(ds);
                data = applyMasking(data, headers, rules, null);
                maskingMeta.put("mode", "rules");
                List<Map<String, String>> ruleList = new ArrayList<>();
                if (rules != null) for (var rr : rules) ruleList.add(Map.of("column", String.valueOf(rr.getColumn()), "fn", String.valueOf(rr.getFunction())));
                maskingMeta.put("rules", ruleList);
            }
        } else {
            data = applyHeuristicMasking(data, headers);
            maskingMeta.put("mode", "heuristic");
            maskingMeta.put("columns", detectHeuristicSensitive(headers));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("headers", headers);
        result.put("rows", data);
        if (!maskingMeta.isEmpty()) result.put("masking", maskingMeta);
        audit.audit("READ", "explore.resultPreview", resultSetId.toString());
        return ApiResponses.ok(result);
    }

    @DeleteMapping("/result-sets/{id}")
    public ApiResponse<Boolean> deleteResultSet(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        ResultSet rs = resultSetRepository.findById(id).orElse(null);
        if (rs == null) {
            return ApiResponses.ok(Boolean.TRUE);
        }
        assertResultSetAccess(rs);
        String effDept = resolveActiveDeptContext(activeDept);
        UUID datasetId = executionRepository
            .findByResultSetId(id)
            .stream()
            .map(QueryExecution::getDatasetId)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
        CatalogDataset dataset = datasetId != null ? datasetRepo.findById(datasetId).orElse(null) : null;
        if (dataset != null && !datasetWithinScope(dataset, effDept)) {
            audit.audit("DENY", "explore.resultSet", id.toString());
            return ApiResponses.error(
                com.yuzhi.dts.platform.security.policy.PolicyErrorCodes.INVALID_CONTEXT,
                "Access denied for dataset"
            );
        }
        // Remove link from executions then delete result set
        executionRepository.clearResultSetReferences(id);
        resultSetRepository.deleteById(id);
        audit.audit("DELETE", "explore.resultSet", id.toString());
        return ApiResponses.ok(Boolean.TRUE);
    }

    private String resolveCurrentUsername() {
        return SecurityUtils
            .getCurrentUserLogin()
            .map(name -> name == null ? null : name.trim())
            .filter(name -> name != null && !name.isEmpty())
            .orElse(null);
    }

    private boolean isResultSetOwner(ResultSet rs) {
        if (rs == null || rs.getCreatedBy() == null) {
            return false;
        }
        String owner = rs.getCreatedBy().trim();
        if (owner.isEmpty()) {
            return false;
        }
        String current = resolveCurrentUsername();
        return current != null && owner.equalsIgnoreCase(current);
    }

    private boolean isExecutionOwner(QueryExecution exec) {
        if (exec == null || exec.getCreatedBy() == null) {
            return false;
        }
        String owner = exec.getCreatedBy().trim();
        if (owner.isEmpty()) {
            return false;
        }
        String current = resolveCurrentUsername();
        return current != null && owner.equalsIgnoreCase(current);
    }

    private void assertResultSetAccess(ResultSet rs) {
        if (isResultSetOwner(rs)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前结果集仅创建人可访问");
    }

    private void assertExecutionAccess(QueryExecution exec) {
        if (isExecutionOwner(exec)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前查询仅创建人可保存为结果集");
    }

    private ExecEnums.ExecEngine parseEngine(String engine) {
        try { return ExecEnums.ExecEngine.valueOf(Objects.toString(engine, "TRINO").toUpperCase()); } catch (Exception e) { return ExecEnums.ExecEngine.TRINO; }
    }

    private boolean datasetWithinScope(CatalogDataset dataset, String activeDept) {
        if (dataset == null) {
            return false;
        }
        if (!accessChecker.canRead(dataset)) {
            return false;
        }
        return accessChecker.departmentAllowed(dataset, activeDept);
    }

    private String resolveActiveDeptContext(String header) {
        if (StringUtils.hasText(header)) {
            return header.trim();
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        try {
            if (authentication instanceof JwtAuthenticationToken token) {
                String candidate = extractDeptClaim(token.getToken().getClaims().get("dept_code"));
                if (candidate != null) return candidate;
                candidate = extractDeptClaim(token.getToken().getClaims().get("deptCode"));
                if (candidate != null) return candidate;
                return extractDeptClaim(token.getToken().getClaims().get("department"));
            }
            if (authentication != null && authentication.getPrincipal() instanceof OAuth2AuthenticatedPrincipal principal) {
                String candidate = extractDeptClaim(principal.getAttribute("dept_code"));
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
        String text = flattened.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private Object flattenValue(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Collection<?> collection) {
            return collection.stream().filter(Objects::nonNull).findFirst().orElse(null);
        }
        if (raw.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(raw);
            for (int i = 0; i < length; i++) {
                Object element = java.lang.reflect.Array.get(raw, i);
                if (element != null) {
                    return element;
                }
            }
            return null;
        }
        return raw;
    }

    private static String applyVariables(String sql, Map<String, Object> variables) {
        if (sql == null || sql.isBlank() || variables == null || variables.isEmpty()) return sql;
        String out = sql;
        for (Map.Entry<String, Object> e : variables.entrySet()) {
            String key = e.getKey();
            String val = String.valueOf(e.getValue());
            out = out.replace("${" + key + "}", val);
            out = out.replace(":" + key, val);
        }
        return out;
    }

    private static boolean isReadOnlyQuery(String sql) {
        String s = sql == null ? "" : sql.trim().toLowerCase();
        return s.startsWith("select") || s.startsWith("with");
    }

    private List<String> normalizeHeaders(Object raw) {
        if (raw instanceof List<?> list) {
            List<String> headers = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    String text = String.valueOf(item).trim();
                    if (!text.isEmpty()) {
                        headers.add(text);
                    }
                }
            }
            return headers;
        }
        return List.of();
    }

    private List<Map<String, Object>> normalizeRows(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() != null) {
                        row.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                }
                rows.add(row);
            }
        }
        return rows;
    }

    private String buildPreviewJson(List<String> headers, Object rows) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("headers", headers);
        payload.put("rows", rows instanceof List<?> ? rows : List.of());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return null;
        }
    }

    private static java.util.List<java.util.Map<String, Object>> applyMasking(
        java.util.List<java.util.Map<String, Object>> rows,
        java.util.List<String> headers,
        java.util.List<CatalogMaskingRule> rules,
        String defaultMask
    ) {
        java.util.Map<String, String> colMask = new java.util.HashMap<>();
        if (rules != null) {
            for (var r : rules) {
                if (r.getColumn() != null && r.getFunction() != null) {
                    colMask.put(r.getColumn(), r.getFunction());
                }
            }
        }
        java.util.List<java.util.Map<String, Object>> masked = new java.util.ArrayList<>();
        for (var row : rows) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            for (String h : headers) {
                Object v = row.get(h);
                String fn = colMask.getOrDefault(h, defaultMask);
                m.put(h, applyMaskFn(v, fn));
            }
            masked.add(m);
        }
        return masked;
    }

    private static Object applyMaskFn(Object value, String fn) {
        if (value == null || fn == null || fn.isBlank()) return value;
        String f = fn.toUpperCase();
        String s = String.valueOf(value);
        return switch (f) {
            case "HASH" -> Integer.toHexString(s.hashCode());
            case "PARTIAL" -> s.length() <= 4 ? "**" : s.substring(0, 2) + "****" + s.substring(s.length() - 2);
            case "NULL" -> null;
            case "REDACT" -> "****";
            default -> value;
        };
    }

    private static java.util.List<java.util.Map<String, Object>> applyHeuristicMasking(
        java.util.List<java.util.Map<String, Object>> rows,
        java.util.List<String> headers
    ) {
        java.util.Set<String> sensitive = new java.util.HashSet<>(detectHeuristicSensitive(headers));
        java.util.List<java.util.Map<String, Object>> masked = new java.util.ArrayList<>();
        for (var row : rows) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            for (String h : headers) {
                Object v = row.get(h);
                if (sensitive.contains(h) && v != null) {
                    String s = String.valueOf(v);
                    m.put(h, s.length() <= 3 ? "***" : s.substring(0, 1) + "***" + s.substring(s.length() - 2));
                } else {
                    m.put(h, v);
                }
            }
            masked.add(m);
        }
        return masked;
    }

    private static java.util.List<String> detectHeuristicSensitive(java.util.List<String> headers) {
        java.util.List<String> cols = new java.util.ArrayList<>();
        for (String h : headers) {
            String n = h.toLowerCase();
            if (n.contains("phone") || n.contains("mobile") || n.contains("email") || n.contains("name") || n.contains("card") || n.contains("id_no") || n.contains("idno")) {
                cols.add(h);
            }
        }
        return cols;
    }
}
