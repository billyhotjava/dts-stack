package com.yuzhi.dts.copilot.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsNl2SqlEvalRun;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsNl2SqlEvalRunRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class Nl2SqlEvalRunService {

    private final Nl2SqlEvalService nl2SqlEvalService;
    private final AnalyticsNl2SqlEvalRunRepository evalRunRepository;
    private final ObjectMapper objectMapper;

    public Nl2SqlEvalRunService(
            Nl2SqlEvalService nl2SqlEvalService,
            AnalyticsNl2SqlEvalRunRepository evalRunRepository,
            ObjectMapper objectMapper) {
        this.nl2SqlEvalService = nl2SqlEvalService;
        this.evalRunRepository = evalRunRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listRuns(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        List<AnalyticsNl2SqlEvalRun> rows =
                evalRunRepository.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(0, safeLimit));
        return rows.stream().map(this::toRunResponse).toList();
    }

    public Map<String, Object> runWithGate(
            List<Long> caseIds, boolean enabledOnly, int limit, JsonNode versionNode, JsonNode gateNode) {
        Map<String, Object> summary = nl2SqlEvalService.runEvaluation(caseIds, enabledOnly, limit);
        double passRate = readDouble(summary.get("passRate"), 0d);
        double averageScore = readDouble(summary.get("averageScore"), 0d);
        int total = readInt(summary.get("total"), 0);
        int passed = readInt(summary.get("passed"), 0);
        int failed = readInt(summary.get("failed"), Math.max(0, total - passed));
        double blockedRate = calculateBlockedRate(summary.get("rows"));

        String label = trimToNull(text(versionNode, "label"));
        String modelVersion = trimToNull(text(versionNode, "modelVersion"));
        String promptVersion = trimToNull(text(versionNode, "promptVersion"));
        String dictionaryVersion = trimToNull(text(versionNode, "dictionaryVersion"));

        long baselineRunId = readLong(node(gateNode, "baselineRunId"), 0L);
        double minPassRate = readDouble(node(gateNode, "minPassRate"), 0d);
        double minAverageScore = readDouble(node(gateNode, "minAverageScore"), 0d);
        double maxBlockedRate = readDouble(node(gateNode, "maxBlockedRate"), 1d);
        double maxPassRateDrop = readDouble(node(gateNode, "maxPassRateDrop"), Double.POSITIVE_INFINITY);
        double maxAverageScoreDrop = readDouble(node(gateNode, "maxAverageScoreDrop"), Double.POSITIVE_INFINITY);

        List<Map<String, Object>> checks = new ArrayList<>();
        addCheck(checks, "minPassRate", passRate >= minPassRate, minPassRate, passRate, "passRate must be >= threshold");
        addCheck(
                checks,
                "minAverageScore",
                averageScore >= minAverageScore,
                minAverageScore,
                averageScore,
                "averageScore must be >= threshold");
        addCheck(
                checks,
                "maxBlockedRate",
                blockedRate <= maxBlockedRate,
                maxBlockedRate,
                blockedRate,
                "blockedRate must be <= threshold");

        Map<String, Object> baselineSnapshot = null;
        if (baselineRunId > 0) {
            AnalyticsNl2SqlEvalRun baseline = evalRunRepository
                    .findById(baselineRunId)
                    .orElseThrow(() -> new IllegalArgumentException("baseline run not found"));
            double baselinePassRate = baseline.getPassRate() == null ? 0d : baseline.getPassRate();
            double baselineAverageScore = baseline.getAverageScore() == null ? 0d : baseline.getAverageScore();
            double passRateDrop = baselinePassRate - passRate;
            double averageScoreDrop = baselineAverageScore - averageScore;
            addCheck(
                    checks,
                    "maxPassRateDrop",
                    passRateDrop <= maxPassRateDrop,
                    maxPassRateDrop,
                    passRateDrop,
                    "passRate drop against baseline must be <= threshold");
            addCheck(
                    checks,
                    "maxAverageScoreDrop",
                    averageScoreDrop <= maxAverageScoreDrop,
                    maxAverageScoreDrop,
                    averageScoreDrop,
                    "averageScore drop against baseline must be <= threshold");

            baselineSnapshot = new LinkedHashMap<>();
            baselineSnapshot.put("runId", baseline.getId());
            baselineSnapshot.put("label", baseline.getLabel());
            baselineSnapshot.put("modelVersion", baseline.getModelVersion());
            baselineSnapshot.put("promptVersion", baseline.getPromptVersion());
            baselineSnapshot.put("dictionaryVersion", baseline.getDictionaryVersion());
            baselineSnapshot.put("passRate", baselinePassRate);
            baselineSnapshot.put("averageScore", baselineAverageScore);
            baselineSnapshot.put("blockedRate", baseline.getBlockedRate());
            baselineSnapshot.put("createdAt", baseline.getCreatedAt());
        }

        List<String> reasons = checks.stream()
                .filter(it -> !Boolean.TRUE.equals(it.get("passed")))
                .map(it -> text(it, "message"))
                .filter(msg -> msg != null && !msg.isBlank())
                .toList();
        boolean gatePassed = reasons.isEmpty();

        Map<String, Object> gate = new LinkedHashMap<>();
        gate.put("passed", gatePassed);
        gate.put("checks", checks);
        gate.put("reasons", reasons);
        gate.put("baseline", baselineSnapshot);
        Map<String, Object> gateConfig = new LinkedHashMap<>();
        gateConfig.put("minPassRate", minPassRate);
        gateConfig.put("minAverageScore", minAverageScore);
        gateConfig.put("maxBlockedRate", maxBlockedRate);
        gateConfig.put("maxPassRateDrop", maxPassRateDrop);
        gateConfig.put("maxAverageScoreDrop", maxAverageScoreDrop);
        gateConfig.put("baselineRunId", baselineRunId > 0 ? baselineRunId : null);
        gate.put("config", gateConfig);

        AnalyticsNl2SqlEvalRun saved = saveRun(
                label,
                modelVersion,
                promptVersion,
                dictionaryVersion,
                total,
                passed,
                failed,
                passRate,
                averageScore,
                blockedRate,
                gatePassed,
                gate,
                summary);

        Map<String, Object> version = new LinkedHashMap<>();
        version.put("label", label);
        version.put("modelVersion", modelVersion);
        version.put("promptVersion", promptVersion);
        version.put("dictionaryVersion", dictionaryVersion);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("runId", saved.getId());
        out.put("executedAt", Instant.now());
        out.put("version", version);
        out.put("summary", summary);
        out.put("gate", gate);
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> compareRuns(long baselineRunId, long candidateRunId) {
        if (baselineRunId <= 0 || candidateRunId <= 0) {
            throw new IllegalArgumentException("baselineRunId and candidateRunId are required");
        }

        AnalyticsNl2SqlEvalRun baseline = evalRunRepository
                .findById(baselineRunId)
                .orElseThrow(() -> new IllegalArgumentException("baseline run not found"));
        AnalyticsNl2SqlEvalRun candidate = evalRunRepository
                .findById(candidateRunId)
                .orElseThrow(() -> new IllegalArgumentException("candidate run not found"));

        Map<String, Object> baselineSummary = parseObject(baseline.getSummaryJson());
        Map<String, Object> candidateSummary = parseObject(candidate.getSummaryJson());
        Map<Long, Map<String, Object>> baselineRows = indexRowsByCaseId(listOfMap(baselineSummary.get("rows")));
        Map<Long, Map<String, Object>> candidateRows = indexRowsByCaseId(listOfMap(candidateSummary.get("rows")));

        List<Map<String, Object>> changedCases = new ArrayList<>();
        int regressions = 0;
        int improvements = 0;
        int unchanged = 0;

        for (Map.Entry<Long, Map<String, Object>> entry : candidateRows.entrySet()) {
            Long caseId = entry.getKey();
            Map<String, Object> candidateRow = entry.getValue();
            Map<String, Object> baselineRow = baselineRows.get(caseId);
            if (baselineRow == null) {
                continue;
            }
            boolean baselinePassed = readBoolean(baselineRow.get("passed"));
            boolean candidatePassed = readBoolean(candidateRow.get("passed"));
            int baselineScore = readInt(baselineRow.get("score"), 0);
            int candidateScore = readInt(candidateRow.get("score"), 0);
            String type;
            if (baselinePassed && !candidatePassed) {
                regressions += 1;
                type = "regression";
            } else if (!baselinePassed && candidatePassed) {
                improvements += 1;
                type = "improvement";
            } else {
                unchanged += 1;
                type = "unchanged";
            }
            if (!"unchanged".equals(type) || candidateScore != baselineScore) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", caseId);
                row.put("name", text(candidateRow, "name"));
                row.put("type", type);
                row.put("baselinePassed", baselinePassed);
                row.put("candidatePassed", candidatePassed);
                row.put("baselineScore", baselineScore);
                row.put("candidateScore", candidateScore);
                row.put("scoreDelta", candidateScore - baselineScore);
                changedCases.add(row);
            }
        }

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("passRateDelta", safeDouble(candidate.getPassRate()) - safeDouble(baseline.getPassRate()));
        metrics.put("averageScoreDelta", safeDouble(candidate.getAverageScore()) - safeDouble(baseline.getAverageScore()));
        metrics.put("failedDelta", safeInt(candidate.getFailCount()) - safeInt(baseline.getFailCount()));
        metrics.put("blockedRateDelta", safeDouble(candidate.getBlockedRate()) - safeDouble(baseline.getBlockedRate()));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("baseline", toRunResponse(baseline));
        out.put("candidate", toRunResponse(candidate));
        out.put("metrics", metrics);
        out.put("changes", Map.of(
                "regressionCount", regressions,
                "improvementCount", improvements,
                "unchangedCount", unchanged,
                "totalCompared", regressions + improvements + unchanged,
                "rows", changedCases));
        return out;
    }

    private AnalyticsNl2SqlEvalRun saveRun(
            String label,
            String modelVersion,
            String promptVersion,
            String dictionaryVersion,
            int total,
            int passed,
            int failed,
            double passRate,
            double averageScore,
            double blockedRate,
            boolean gatePassed,
            Object gate,
            Object summary) {
        AnalyticsNl2SqlEvalRun row = new AnalyticsNl2SqlEvalRun();
        row.setLabel(label);
        row.setModelVersion(modelVersion);
        row.setPromptVersion(promptVersion);
        row.setDictionaryVersion(dictionaryVersion);
        row.setCaseCount(total);
        row.setPassCount(passed);
        row.setFailCount(failed);
        row.setPassRate(passRate);
        row.setAverageScore(averageScore);
        row.setBlockedRate(blockedRate);
        row.setGatePassed(gatePassed);
        row.setGateSummaryJson(toJson(gate));
        row.setSummaryJson(toJson(summary));
        return evalRunRepository.save(row);
    }

    private Map<String, Object> toRunResponse(AnalyticsNl2SqlEvalRun row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.getId());
        out.put("label", row.getLabel());
        out.put("modelVersion", row.getModelVersion());
        out.put("promptVersion", row.getPromptVersion());
        out.put("dictionaryVersion", row.getDictionaryVersion());
        out.put("caseCount", row.getCaseCount());
        out.put("passCount", row.getPassCount());
        out.put("failCount", row.getFailCount());
        out.put("passRate", row.getPassRate());
        out.put("averageScore", row.getAverageScore());
        out.put("blockedRate", row.getBlockedRate());
        out.put("gatePassed", row.getGatePassed());
        out.put("gate", parseObject(row.getGateSummaryJson()));
        out.put("createdAt", row.getCreatedAt());
        return out;
    }

    private void addCheck(
            List<Map<String, Object>> checks, String key, boolean passed, double expected, double actual, String message) {
        Map<String, Object> check = new LinkedHashMap<>();
        check.put("key", key);
        check.put("passed", passed);
        check.put("expected", expected);
        check.put("actual", actual);
        check.put("message", message);
        checks.add(check);
    }

    private double calculateBlockedRate(Object rowsObject) {
        List<Map<String, Object>> rows = listOfMap(rowsObject);
        if (rows.isEmpty()) {
            return 0d;
        }
        int blocked = 0;
        for (Map<String, Object> row : rows) {
            Map<String, Object> generated = asMap(row.get("generated"));
            String status = trimToNull(text(generated, "safetyStatus"));
            if (status != null && "blocked".equalsIgnoreCase(status)) {
                blocked += 1;
            }
        }
        return (double) blocked / (double) rows.size();
    }

    private Map<Long, Map<String, Object>> indexRowsByCaseId(List<Map<String, Object>> rows) {
        Map<Long, Map<String, Object>> out = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Long id = readLong(row.get("id"), 0L);
            if (id > 0) {
                out.put(id, row);
            }
        }
        return out;
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseObject(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            Object parsed = objectMapper.readValue(raw, Object.class);
            if (parsed instanceof Map<?, ?> map) {
                Map<String, Object> out = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() != null) {
                        out.put(entry.getKey().toString(), entry.getValue());
                    }
                }
                return out;
            }
        } catch (Exception e) {
            // ignored
        }
        return Map.of();
    }

    private JsonNode node(JsonNode node, String fieldName) {
        if (node == null || !node.isObject()) {
            return null;
        }
        JsonNode child = node.get(fieldName);
        if (child == null || child.isMissingNode() || child.isNull()) {
            return null;
        }
        return child;
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node(node, fieldName);
        if (value == null) {
            return null;
        }
        String text = value.asText(null);
        return text == null ? null : text.trim();
    }

    private String text(Map<String, Object> map, String fieldName) {
        if (map == null || fieldName == null) {
            return null;
        }
        Object value = map.get(fieldName);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isBlank() ? null : text;
    }

    private double safeDouble(Double value) {
        return value == null ? 0d : value;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private double readDouble(Object value, double fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (Exception e) {
            return fallback;
        }
    }

    private int readInt(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return fallback;
        }
    }

    private long readLong(Object value, long fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (Exception e) {
            return fallback;
        }
    }

    private boolean readBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value == null) {
            return false;
        }
        String text = value.toString().trim();
        return "true".equalsIgnoreCase(text) || "1".equals(text);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    out.put(entry.getKey().toString(), entry.getValue());
                }
            }
            return out;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMap(Object value) {
        if (value instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object item : list) {
                Map<String, Object> map = asMap(item);
                if (!map.isEmpty()) {
                    out.add(map);
                }
            }
            return out;
        }
        return List.of();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
