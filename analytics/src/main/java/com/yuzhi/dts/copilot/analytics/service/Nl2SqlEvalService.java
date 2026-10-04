package com.yuzhi.dts.copilot.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsNl2SqlEvalCase;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsNl2SqlEvalCaseRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class Nl2SqlEvalService {

    private final AnalyticsNl2SqlEvalCaseRepository evalCaseRepository;
    private final ScreenAiGenerationService screenAiGenerationService;
    private final ObjectMapper objectMapper;

    public Nl2SqlEvalService(
            AnalyticsNl2SqlEvalCaseRepository evalCaseRepository,
            ScreenAiGenerationService screenAiGenerationService,
            ObjectMapper objectMapper) {
        this.evalCaseRepository = evalCaseRepository;
        this.screenAiGenerationService = screenAiGenerationService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listCases(boolean enabledOnly, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 1000));
        PageRequest page = PageRequest.of(0, safeLimit);
        List<AnalyticsNl2SqlEvalCase> rows = enabledOnly
                ? evalCaseRepository.findAllByEnabledTrueOrderByIdAsc(page)
                : evalCaseRepository.findAllByOrderByIdAsc(page);
        return rows.stream().map(this::toCaseResponse).toList();
    }

    public Map<String, Object> saveCase(Long caseId, JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new IllegalArgumentException("payload must be object");
        }
        AnalyticsNl2SqlEvalCase row = caseId == null
                ? new AnalyticsNl2SqlEvalCase()
                : evalCaseRepository.findById(caseId).orElseThrow(() -> new IllegalArgumentException("eval case not found"));

        String name = trimToNull(body.path("name").asText(null));
        String prompt = trimToNull(body.path("promptText").asText(null));
        if (name == null) {
            throw new IllegalArgumentException("name is required");
        }
        if (prompt == null) {
            throw new IllegalArgumentException("promptText is required");
        }
        row.setName(name);
        row.setPromptText(prompt);
        row.setDomain(trimToNull(body.path("domain").asText(null)));
        row.setNotes(trimToNull(body.path("notes").asText(null)));

        if (body.has("enabled")) {
            row.setEnabled(body.path("enabled").asBoolean(true));
        } else if (caseId == null) {
            row.setEnabled(true);
        }

        String expectedJson = resolveExpectedJson(body, row.getExpectedJson());
        row.setExpectedJson(expectedJson);

        AnalyticsNl2SqlEvalCase saved = evalCaseRepository.save(row);
        return toCaseResponse(saved);
    }

    public void deleteCase(long caseId) {
        if (!evalCaseRepository.existsById(caseId)) {
            throw new IllegalArgumentException("eval case not found");
        }
        evalCaseRepository.deleteById(caseId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> runEvaluation(List<Long> caseIds, boolean enabledOnly, int limit) {
        List<AnalyticsNl2SqlEvalCase> cases = resolveCases(caseIds, enabledOnly, limit);
        List<Map<String, Object>> rows = new ArrayList<>(cases.size());
        int passed = 0;
        long totalScore = 0;
        for (AnalyticsNl2SqlEvalCase row : cases) {
            Map<String, Object> result = evaluateCase(row);
            rows.add(result);
            boolean success = Boolean.TRUE.equals(result.get("passed"));
            if (success) {
                passed += 1;
            }
            Number score = result.get("score") instanceof Number ? (Number) result.get("score") : 0;
            totalScore += score.longValue();
        }

        int total = rows.size();
        int failed = Math.max(0, total - passed);
        double passRate = total <= 0 ? 0.0 : ((double) passed / (double) total);
        double avgScore = total <= 0 ? 0.0 : ((double) totalScore / (double) total);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("executedAt", Instant.now());
        summary.put("total", total);
        summary.put("passed", passed);
        summary.put("failed", failed);
        summary.put("passRate", passRate);
        summary.put("averageScore", avgScore);
        summary.put("rows", rows);
        return summary;
    }

    Map<String, Object> evaluateCase(AnalyticsNl2SqlEvalCase evalCase) {
        JsonNode result = screenAiGenerationService.generate(evalCase.getPromptText(), 1920, 1080);
        JsonNode intent = result.path("intent");
        JsonNode semanticHints = result.path("semanticModelHints");
        JsonNode diagnostics = result.path("nl2sqlDiagnostics");
        JsonNode sqlBlueprints = result.path("sqlBlueprints");
        int blueprintCount = sqlBlueprints.isArray() ? sqlBlueprints.size() : 0;
        String generatedDomain = trimToNull(intent.path("domain").asText(null));
        String generatedFactTable = trimToNull(semanticHints.path("factTable").asText(null));
        String generatedSafetyStatus = trimToNull(diagnostics.path("status").asText(null));

        JsonNode expectedNode = parseJsonNode(evalCase.getExpectedJson());
        ArrayNode checks = objectMapper.createArrayNode();
        int totalChecks = 0;
        int passedChecks = 0;

        boolean hasBlueprints = blueprintCount > 0;
        totalChecks += 1;
        if (hasBlueprints) {
            passedChecks += 1;
        }
        checks.add(buildCheck("hasBlueprints", hasBlueprints, "> 0", String.valueOf(blueprintCount), "must generate sql blueprints"));

        String expectedDomain = trimToNull(expectedNode.path("domain").asText(null));
        if (expectedDomain == null) {
            expectedDomain = trimToNull(evalCase.getDomain());
        }
        if (expectedDomain != null) {
            boolean pass = expectedDomain.equalsIgnoreCase(generatedDomain == null ? "" : generatedDomain);
            totalChecks += 1;
            if (pass) {
                passedChecks += 1;
            }
            checks.add(buildCheck("domain", pass, expectedDomain, generatedDomain, "intent.domain should match expected domain"));
        }

        String expectedFactTable = trimToNull(expectedNode.path("factTable").asText(null));
        if (expectedFactTable != null) {
            boolean pass = expectedFactTable.equalsIgnoreCase(generatedFactTable == null ? "" : generatedFactTable);
            totalChecks += 1;
            if (pass) {
                passedChecks += 1;
            }
            checks.add(buildCheck(
                    "factTable",
                    pass,
                    expectedFactTable,
                    generatedFactTable,
                    "semanticModelHints.factTable should match expected fact table"));
        }

        String expectedSafetyStatus = trimToNull(expectedNode.path("safetyStatus").asText(null));
        if (expectedSafetyStatus != null) {
            boolean pass = expectedSafetyStatus.equalsIgnoreCase(generatedSafetyStatus == null ? "" : generatedSafetyStatus);
            totalChecks += 1;
            if (pass) {
                passedChecks += 1;
            }
            checks.add(buildCheck(
                    "safetyStatus",
                    pass,
                    expectedSafetyStatus,
                    generatedSafetyStatus,
                    "nl2sqlDiagnostics.status should match expected"));
        }

        int expectedMinBlueprintCount = expectedNode.path("minBlueprintCount").asInt(0);
        if (expectedMinBlueprintCount > 0) {
            boolean pass = blueprintCount >= expectedMinBlueprintCount;
            totalChecks += 1;
            if (pass) {
                passedChecks += 1;
            }
            checks.add(buildCheck(
                    "minBlueprintCount",
                    pass,
                    String.valueOf(expectedMinBlueprintCount),
                    String.valueOf(blueprintCount),
                    "sql blueprint count should be at least expected minimum"));
        }

        List<String> sqlList = extractSqlList(sqlBlueprints);
        List<String> sqlContainsAll = readStringList(expectedNode.path("sqlContains"));
        for (String keyword : sqlContainsAll) {
            boolean pass = containsSqlKeyword(sqlList, keyword);
            totalChecks += 1;
            if (pass) {
                passedChecks += 1;
            }
            checks.add(buildCheck(
                    "sqlContains",
                    pass,
                    keyword,
                    pass ? keyword : "",
                    "at least one generated SQL should contain this keyword"));
        }

        List<String> sqlContainsAny = readStringList(expectedNode.path("sqlContainsAny"));
        if (!sqlContainsAny.isEmpty()) {
            String matched = null;
            for (String keyword : sqlContainsAny) {
                if (containsSqlKeyword(sqlList, keyword)) {
                    matched = keyword;
                    break;
                }
            }
            boolean pass = matched != null;
            totalChecks += 1;
            if (pass) {
                passedChecks += 1;
            }
            checks.add(buildCheck(
                    "sqlContainsAny",
                    pass,
                    String.join(" | ", sqlContainsAny),
                    matched,
                    "at least one keyword should be matched by generated SQL"));
        }

        int score = totalChecks <= 0 ? 100 : (int) Math.round((passedChecks * 100.0) / totalChecks);
        boolean passed = passedChecks == totalChecks;

        Map<String, Object> generated = new LinkedHashMap<>();
        generated.put("engine", trimToNull(result.path("engine").asText(null)));
        generated.put("domain", generatedDomain);
        generated.put("factTable", generatedFactTable);
        generated.put("safetyStatus", generatedSafetyStatus);
        generated.put("sqlBlueprintCount", blueprintCount);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", evalCase.getId());
        out.put("name", evalCase.getName());
        out.put("passed", passed);
        out.put("score", score);
        out.put("totalChecks", totalChecks);
        out.put("passedChecks", passedChecks);
        out.put("checks", checks);
        out.put("generated", generated);
        return out;
    }

    private List<AnalyticsNl2SqlEvalCase> resolveCases(List<Long> caseIds, boolean enabledOnly, int limit) {
        if (caseIds != null && !caseIds.isEmpty()) {
            return evalCaseRepository.findAllByIdIn(caseIds).stream()
                    .sorted(java.util.Comparator.comparing(AnalyticsNl2SqlEvalCase::getId))
                    .toList();
        }
        int safeLimit = Math.max(1, Math.min(limit, 1000));
        PageRequest page = PageRequest.of(0, safeLimit);
        if (enabledOnly) {
            return evalCaseRepository.findAllByEnabledTrueOrderByIdAsc(page);
        }
        return evalCaseRepository.findAllByOrderByIdAsc(page);
    }

    private Map<String, Object> toCaseResponse(AnalyticsNl2SqlEvalCase row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.getId());
        out.put("name", row.getName());
        out.put("domain", row.getDomain());
        out.put("promptText", row.getPromptText());
        out.put("expected", parseJsonNode(row.getExpectedJson()));
        out.put("notes", row.getNotes());
        out.put("enabled", row.isEnabled());
        out.put("createdAt", row.getCreatedAt());
        out.put("updatedAt", row.getUpdatedAt());
        return out;
    }

    private String resolveExpectedJson(JsonNode body, String fallback) {
        if (body.has("expectedJson")) {
            String raw = trimToNull(body.path("expectedJson").asText(null));
            if (raw == null) {
                return null;
            }
            JsonNode parsed;
            try {
                parsed = objectMapper.readTree(raw);
            } catch (Exception e) {
                throw new IllegalArgumentException("expectedJson must be valid JSON object");
            }
            if (parsed == null || !parsed.isObject()) {
                throw new IllegalArgumentException("expectedJson must be valid JSON object");
            }
            return toJson(parsed);
        }
        if (body.has("expected")) {
            JsonNode expected = body.path("expected");
            if (expected == null || expected.isNull() || expected.isMissingNode()) {
                return null;
            }
            if (!expected.isObject()) {
                throw new IllegalArgumentException("expected must be object");
            }
            return toJson(expected);
        }
        return fallback;
    }

    private String toJson(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalArgumentException("failed to serialize expected json");
        }
    }

    private JsonNode parseJsonNode(String raw) {
        if (raw == null || raw.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            return node == null ? objectMapper.createObjectNode() : node;
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private JsonNode buildCheck(String key, boolean passed, String expected, String actual, String message) {
        return objectMapper.createObjectNode()
                .put("key", key)
                .put("passed", passed)
                .put("expected", expected == null ? "" : expected)
                .put("actual", actual == null ? "" : actual)
                .put("message", message == null ? "" : message);
    }

    private List<String> extractSqlList(JsonNode sqlBlueprints) {
        if (sqlBlueprints == null || !sqlBlueprints.isArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (JsonNode item : sqlBlueprints) {
            String sql = trimToNull(item.path("sql").asText(null));
            if (sql != null) {
                out.add(sql);
            }
        }
        return out;
    }

    private boolean containsSqlKeyword(List<String> sqlList, String keyword) {
        String expected = trimToNull(keyword);
        if (expected == null || sqlList == null || sqlList.isEmpty()) {
            return false;
        }
        String needle = expected.toLowerCase(Locale.ROOT);
        for (String sql : sqlList) {
            if (sql != null && sql.toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private List<String> readStringList(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        if (node.isTextual()) {
            String value = trimToNull(node.asText(null));
            if (value != null) {
                values.add(value);
            }
            return values;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                String value = trimToNull(item == null ? null : item.asText(null));
                if (value != null) {
                    values.add(value);
                }
            }
        }
        return values;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
