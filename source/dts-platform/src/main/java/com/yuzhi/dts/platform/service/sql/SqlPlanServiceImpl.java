package com.yuzhi.dts.platform.service.sql;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.query.QueryGateway;
import com.yuzhi.dts.platform.service.security.SecuritySqlRewriter;
import com.yuzhi.dts.platform.service.sql.dto.ExplainRequest;
import com.yuzhi.dts.platform.service.sql.dto.PlanNodeDto;
import com.yuzhi.dts.platform.service.sql.dto.PlanResultDto;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SqlPlanServiceImpl implements SqlPlanService {

    private final QueryGateway queryGateway;
    private final SecuritySqlRewriter rewriter;
    private final ObjectMapper objectMapper;

    public SqlPlanServiceImpl(QueryGateway queryGateway, SecuritySqlRewriter rewriter, ObjectMapper objectMapper) {
        this.queryGateway = queryGateway;
        this.rewriter = rewriter;
        this.objectMapper = objectMapper;
    }

    @Override
    public PlanResultDto explain(ExplainRequest req) {
        long t0 = System.currentTimeMillis();
        // guard() with null dataset strips trailing semicolons and applies no row-level policy
        String guarded = rewriter.guard(req.sql(), null);
        String engine = req.engine() == null ? "trino" : req.engine().toLowerCase();
        String explainSql;
        switch (engine) {
            case "trino" -> explainSql = "EXPLAIN (FORMAT JSON) " + guarded;
            case "postgresql", "postgres" -> explainSql = "EXPLAIN (FORMAT JSON, ANALYZE false) " + guarded;
            default -> explainSql = "EXPLAIN " + guarded;  // hive / unknown
        }

        Map<String, Object> raw;
        try {
            // QueryGateway interface has execute(sql) and execute(sql, UUID datasourceId).
            // No catalog parameter exists — catalog selection is handled at datasource level.
            raw = queryGateway.execute(explainSql, req.datasourceId());
        } catch (Exception e) {
            String errMsg = e.getMessage() == null ? "unknown" : e.getMessage();
            PlanNodeDto err = new PlanNodeDto(
                "err", "ExplainError", null, null, null,
                Map.of("error", errMsg), List.of()
            );
            return new PlanResultDto(err, errMsg, engine, System.currentTimeMillis() - t0);
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) raw.getOrDefault("rows", List.of());
        String rawText = rows.stream()
            .map(r -> r.values().stream().findFirst().map(String::valueOf).orElse(""))
            .reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);

        PlanNodeDto root;
        if (engine.equals("trino") || engine.startsWith("postgres")) {
            root = parseJsonPlan(rawText, engine);
        } else {
            root = parseHiveTextPlan(rawText);
        }
        return new PlanResultDto(root, rawText, engine, System.currentTimeMillis() - t0);
    }

    private PlanNodeDto parseJsonPlan(String rawText, String engine) {
        try {
            String trimmed = rawText.trim();
            if (trimmed.isEmpty()) {
                return new PlanNodeDto("n0", "EmptyPlan", null, null, null, Map.of(), List.of());
            }
            // PG returns "[ { ... } ]"; Trino returns nested object — both parsable
            JsonNode tree = objectMapper.readTree(trimmed);
            JsonNode planRoot = tree.isArray() && tree.size() > 0 ? tree.get(0) : tree;
            // PG nests under "Plan"; Trino under root.
            JsonNode actual = planRoot.has("Plan") ? planRoot.get("Plan") : planRoot;
            return jsonToNode(actual, "n0");
        } catch (Exception e) {
            return new PlanNodeDto("n0", "ParseError", null, null, null,
                Map.of("error", String.valueOf(e.getMessage())), List.of());
        }
    }

    private PlanNodeDto jsonToNode(JsonNode node, String id) {
        String operator = node.has("Node Type") ? node.get("Node Type").asText() :
                          node.has("operator") ? node.get("operator").asText() :
                          node.has("name") ? node.get("name").asText() : "Operator";
        String table = node.has("Relation Name") ? node.get("Relation Name").asText() :
                       node.has("table") ? node.get("table").asText() : null;
        Double estRows = node.has("Plan Rows") ? node.get("Plan Rows").asDouble() :
                         node.has("estimatedRows") ? node.get("estimatedRows").asDouble() : null;
        Double estCost = node.has("Total Cost") ? node.get("Total Cost").asDouble() :
                         node.has("estimatedCost") ? node.get("estimatedCost").asDouble() : null;
        Map<String, String> attrs = new LinkedHashMap<>();
        node.fieldNames().forEachRemaining(f -> {
            if (List.of("Plans", "children", "Plan").contains(f)) return;
            JsonNode v = node.get(f);
            if (v.isValueNode()) attrs.put(f, v.asText());
        });
        List<PlanNodeDto> children = new ArrayList<>();
        JsonNode kids = node.has("Plans") ? node.get("Plans") :
                        node.has("children") ? node.get("children") : null;
        if (kids != null && kids.isArray()) {
            int i = 0;
            for (JsonNode k : kids) children.add(jsonToNode(k, id + "." + (i++)));
        }
        return new PlanNodeDto(id, operator, table, estRows, estCost, attrs, children);
    }

    private PlanNodeDto parseHiveTextPlan(String rawText) {
        // Hive's EXPLAIN is multi-section text; capture as a single attribute.
        return new PlanNodeDto("n0", "HivePlan", null, null, null,
            Map.of("text", rawText), List.of());
    }
}
