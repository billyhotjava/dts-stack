package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.explore.ExecEnums;
import com.yuzhi.dts.platform.domain.explore.QueryExecution;
import com.yuzhi.dts.platform.domain.explore.ResultSet;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.explore.QueryExecutionRepository;
import com.yuzhi.dts.platform.repository.explore.ResultSetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogAccessPolicyRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogMaskingRuleRepository;
import com.yuzhi.dts.platform.domain.catalog.CatalogMaskingRule;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.query.QueryGateway;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/explore")
@Transactional
public class ExploreExecResource {

    private final QueryGateway queryGateway;
    private final AuditService audit;
    private final CatalogDatasetRepository datasetRepo;
    private final AccessChecker accessChecker;
    private final QueryExecutionRepository executionRepository;
    private final ResultSetRepository resultSetRepository;
    private final CatalogAccessPolicyRepository policyRepository;
    private final CatalogMaskingRuleRepository maskingRepository;

    public ExploreExecResource(
        QueryGateway queryGateway,
        AuditService audit,
        CatalogDatasetRepository datasetRepo,
        AccessChecker accessChecker,
        QueryExecutionRepository executionRepository,
        ResultSetRepository resultSetRepository,
        CatalogAccessPolicyRepository policyRepository,
        CatalogMaskingRuleRepository maskingRepository
    ) {
        this.queryGateway = queryGateway;
        this.audit = audit;
        this.datasetRepo = datasetRepo;
        this.accessChecker = accessChecker;
        this.executionRepository = executionRepository;
        this.resultSetRepository = resultSetRepository;
        this.policyRepository = policyRepository;
        this.maskingRepository = maskingRepository;
    }

    public record ExecuteRequest(String sqlText, String connection, String engine, UUID datasetId, Map<String, Object> variables) {}

    @PostMapping("/execute")
    public ApiResponse<Map<String, Object>> execute(@RequestBody ExecuteRequest req) {
        String sql = Objects.toString(req.sqlText, "");
        if (sql.isBlank()) return ApiResponses.error("sqlText is required");

        // Optional dataset-based permission check
        if (req.datasetId != null) {
            CatalogDataset ds = datasetRepo.findById(req.datasetId).orElse(null);
            if (ds == null || !accessChecker.canRead(ds)) {
                audit.audit("DENY", "explore.execute", Objects.toString(req.datasetId));
                return ApiResponses.error("Access denied for dataset");
            }
        }

        // Simple variable substitution: ${var} and :var
        String effective = applyVariables(sql, req.variables);
        // Row filter pushdown when a dataset and policy are present (and not using RANGER)
        if (req.datasetId != null) {
            CatalogDataset ds = datasetRepo.findById(req.datasetId).orElse(null);
            if (ds != null) {
                var policy = policyRepository.findByDataset(ds).orElse(null);
                boolean usingRanger = "RANGER".equalsIgnoreCase(java.util.Objects.toString(ds.getExposedBy(), ""));
                if (!usingRanger && policy != null && policy.getRowFilter() != null && !policy.getRowFilter().isBlank()) {
                    effective = "SELECT * FROM (" + effective + ") t WHERE (" + policy.getRowFilter() + ")";
                }
            }
        }
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
    public ApiResponse<ResultSet> saveResult(@PathVariable UUID executionId, @RequestBody(required = false) SaveResultRequest req) {
        var exec = executionRepository.findById(executionId).orElse(null);
        if (exec == null) return ApiResponses.error("Execution not found");
        int ttl = req != null && req.ttlDays != null ? Math.max(1, req.ttlDays) : 7;

        // Build a fake storage URI and columns from execution SQL (not parsing; placeholder)
        String storageUri = "local://resultsets/" + UUID.randomUUID() + ".json";
        String columns = inferColumnsFromSql(exec.getSqlText());

        ResultSet rs = new ResultSet();
        rs.setStorageUri(storageUri);
        rs.setColumns(columns);
        rs.setRowCount(exec.getRowCount());
        rs.setTtlDays(ttl);
        rs.setExpiresAt(Instant.now().plus(ttl, ChronoUnit.DAYS));
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
        @RequestParam(name = "datasetId", required = false) UUID datasetId
    ) {
        var rs = resultSetRepository.findById(resultSetId).orElse(null);
        if (rs == null) return ApiResponses.error("Result set not found");

        List<String> headers = new ArrayList<>();
        for (String c : rs.getColumns().split(",")) headers.add(c.trim());

        List<Map<String, Object>> data = new ArrayList<>();
        var r = java.util.concurrent.ThreadLocalRandom.current();
        int n = Math.min(Math.max(rows, 1), 1000);
        for (int i = 0; i < n; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String h : headers) row.put(h, r.nextInt(1, 1000));
            data.add(row);
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
            if (ds != null) {
                var rules = maskingRepository.findByDataset(ds);
                String defaultMask = policyRepository.findByDataset(ds).map(com.yuzhi.dts.platform.domain.catalog.CatalogAccessPolicy::getDefaultMasking).orElse(null);
                data = applyMasking(data, headers, rules, defaultMask);
                maskingMeta.put("mode", "policy");
                List<Map<String, String>> ruleList = new ArrayList<>();
                if (rules != null) for (var rr : rules) ruleList.add(Map.of("column", String.valueOf(rr.getColumn()), "fn", String.valueOf(rr.getFunction())));
                maskingMeta.put("rules", ruleList);
                if (defaultMask != null && !defaultMask.isBlank()) maskingMeta.put("default", defaultMask);
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
    public ApiResponse<Boolean> deleteResultSet(@PathVariable UUID id) {
        // Remove link from executions then delete result set
        executionRepository.clearResultSetReferences(id);
        resultSetRepository.deleteById(id);
        audit.audit("DELETE", "explore.resultSet", id.toString());
        return ApiResponses.ok(Boolean.TRUE);
    }

    private ExecEnums.ExecEngine parseEngine(String engine) {
        try { return ExecEnums.ExecEngine.valueOf(Objects.toString(engine, "TRINO").toUpperCase()); } catch (Exception e) { return ExecEnums.ExecEngine.TRINO; }
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

    private static String inferColumnsFromSql(String sql) {
        // Naive: extract words after SELECT up to FROM
        if (sql == null) return "col1,col2,col3";
        Matcher m = Pattern.compile("select\\s+(.+?)\\s+from", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(sql);
        if (m.find()) {
            String part = m.group(1);
            if (part.contains("*")) return "col1,col2,col3,col4,col5";
            String cleaned = part.replaceAll("AS\\s+", "");
            String[] cols = cleaned.split(",");
            List<String> names = new ArrayList<>();
            for (String c : cols) {
                String n = c.trim();
                int idx = Math.max(n.lastIndexOf(' '), Math.max(n.lastIndexOf('.'), n.lastIndexOf(')')));
                if (idx > -1 && idx + 1 < n.length()) n = n.substring(idx + 1);
                names.add(n);
            }
            return String.join(",", names);
        }
        return "col1,col2,col3";
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
