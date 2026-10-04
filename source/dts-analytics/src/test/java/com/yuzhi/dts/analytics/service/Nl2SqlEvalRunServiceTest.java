package com.yuzhi.dts.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.analytics.domain.AnalyticsNl2SqlEvalRun;
import com.yuzhi.dts.analytics.repository.AnalyticsNl2SqlEvalRunRepository;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class Nl2SqlEvalRunServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void runWithGate_persistsRunAndPassesThresholds() {
        RunRepoStub runRepoStub = new RunRepoStub();
        Nl2SqlEvalService evalService = evalServiceWithSummary(buildSummary(
                2, 2, 0, 1.0, 96.0, List.of(
                        row(1L, "case-1", true, 98, "safe"),
                        row(2L, "case-2", true, 94, "safe"))));
        Nl2SqlEvalRunService service =
                new Nl2SqlEvalRunService(evalService, runRepoStub.repository(), objectMapper);

        ObjectNode gate = objectMapper.createObjectNode();
        gate.put("minPassRate", 0.95);
        gate.put("minAverageScore", 90);
        gate.put("maxBlockedRate", 0.1);
        ObjectNode version = objectMapper.createObjectNode();
        version.put("label", "v1.0.0");
        version.put("modelVersion", "qwen2.5-72b");
        version.put("promptVersion", "prompt-20260221");
        version.put("dictionaryVersion", "dict-20260221");

        Map<String, Object> response = service.runWithGate(List.of(), true, 100, version, gate);

        assertThat(response.get("runId")).isEqualTo(1L);
        @SuppressWarnings("unchecked")
        Map<String, Object> gateResult = (Map<String, Object>) response.get("gate");
        assertThat(gateResult.get("passed")).isEqualTo(true);
        assertThat(runRepoStub.rows).hasSize(1);
        assertThat(runRepoStub.rows.get(0).getGatePassed()).isEqualTo(true);
        assertThat(runRepoStub.rows.get(0).getLabel()).isEqualTo("v1.0.0");
    }

    @Test
    void runWithGate_andCompare_detectsRegressionAgainstBaseline() {
        RunRepoStub runRepoStub = new RunRepoStub();
        AnalyticsNl2SqlEvalRun baseline = new AnalyticsNl2SqlEvalRun();
        baseline.setLabel("baseline");
        baseline.setModelVersion("qwen2.5-72b");
        baseline.setPromptVersion("prompt-1");
        baseline.setDictionaryVersion("dict-1");
        baseline.setCaseCount(2);
        baseline.setPassCount(2);
        baseline.setFailCount(0);
        baseline.setPassRate(1.0);
        baseline.setAverageScore(95.0);
        baseline.setBlockedRate(0.0);
        baseline.setGatePassed(true);
        baseline.setSummaryJson(toJson(objectMapper, buildSummary(
                2, 2, 0, 1.0, 95.0, List.of(
                        row(1L, "case-1", true, 95, "safe"),
                        row(2L, "case-2", true, 95, "safe")))));
        runRepoStub.saveDirect(baseline);

        Nl2SqlEvalService evalService = evalServiceWithSummary(buildSummary(
                2, 1, 1, 0.5, 75.0, List.of(
                        row(1L, "case-1", false, 60, "blocked"),
                        row(2L, "case-2", true, 90, "safe"))));
        Nl2SqlEvalRunService service =
                new Nl2SqlEvalRunService(evalService, runRepoStub.repository(), objectMapper);

        ObjectNode gate = objectMapper.createObjectNode();
        gate.put("baselineRunId", baseline.getId());
        gate.put("maxPassRateDrop", 0.1);
        gate.put("maxAverageScoreDrop", 5);
        gate.put("maxBlockedRate", 0.2);

        Map<String, Object> runResponse = service.runWithGate(List.of(), true, 100, null, gate);
        Long candidateRunId = ((Number) runResponse.get("runId")).longValue();

        @SuppressWarnings("unchecked")
        Map<String, Object> gateResult = (Map<String, Object>) runResponse.get("gate");
        assertThat(gateResult.get("passed")).isEqualTo(false);

        Map<String, Object> compare = service.compareRuns(baseline.getId(), candidateRunId);
        @SuppressWarnings("unchecked")
        Map<String, Object> changes = (Map<String, Object>) compare.get("changes");
        assertThat(changes.get("regressionCount")).isEqualTo(1);
        assertThat(changes.get("improvementCount")).isEqualTo(0);
        assertThat(changes.get("totalCompared")).isEqualTo(2);
    }

    private Nl2SqlEvalService evalServiceWithSummary(Map<String, Object> summary) {
        return new Nl2SqlEvalService(null, null, objectMapper) {
            @Override
            public Map<String, Object> runEvaluation(List<Long> caseIds, boolean enabledOnly, int limit) {
                return summary;
            }
        };
    }

    private Map<String, Object> buildSummary(
            int total, int passed, int failed, double passRate, double avgScore, List<Map<String, Object>> rows) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", total);
        summary.put("passed", passed);
        summary.put("failed", failed);
        summary.put("passRate", passRate);
        summary.put("averageScore", avgScore);
        summary.put("rows", rows);
        return summary;
    }

    private Map<String, Object> row(long id, String name, boolean passed, int score, String safetyStatus) {
        Map<String, Object> generated = new LinkedHashMap<>();
        generated.put("safetyStatus", safetyStatus);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("name", name);
        row.put("passed", passed);
        row.put("score", score);
        row.put("generated", generated);
        return row;
    }

    private static String toJson(ObjectMapper objectMapper, Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize json in test", e);
        }
    }

    private static final class RunRepoStub {
        private final List<AnalyticsNl2SqlEvalRun> rows = new ArrayList<>();
        private long idSeed = 1L;

        private AnalyticsNl2SqlEvalRunRepository repository() {
            return (AnalyticsNl2SqlEvalRunRepository) Proxy.newProxyInstance(
                    AnalyticsNl2SqlEvalRunRepository.class.getClassLoader(),
                    new Class<?>[] {AnalyticsNl2SqlEvalRunRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "save" -> save((AnalyticsNl2SqlEvalRun) args[0]);
                        case "findById" -> findById(args);
                        case "findAllByOrderByCreatedAtDescIdDesc" -> findAll(args);
                        case "toString" -> "Nl2SqlEvalRunRepoStub";
                        default -> throw new UnsupportedOperationException("Method not stubbed: " + method.getName());
                    });
        }

        private AnalyticsNl2SqlEvalRun saveDirect(AnalyticsNl2SqlEvalRun row) {
            return save(row);
        }

        private AnalyticsNl2SqlEvalRun save(AnalyticsNl2SqlEvalRun row) {
            if (row.getId() == null || row.getId() <= 0) {
                setField(row, "id", idSeed++);
            }
            if (row.getCreatedAt() == null) {
                setField(row, "createdAt", Instant.now());
            }
            rows.removeIf(it -> it.getId() != null && it.getId().equals(row.getId()));
            rows.add(row);
            return row;
        }

        private java.util.Optional<AnalyticsNl2SqlEvalRun> findById(Object[] args) {
            long id = args != null && args.length > 0 && args[0] instanceof Number n ? n.longValue() : 0L;
            return rows.stream().filter(it -> it.getId() != null && it.getId() == id).findFirst();
        }

        private List<AnalyticsNl2SqlEvalRun> findAll(Object[] args) {
            int limit = Integer.MAX_VALUE;
            if (args != null && args.length > 0 && args[0] instanceof Pageable pageable) {
                limit = Math.max(1, pageable.getPageSize());
            }
            return rows.stream()
                    .sorted(Comparator.comparing(AnalyticsNl2SqlEvalRun::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(AnalyticsNl2SqlEvalRun::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(limit)
                    .toList();
        }

        private void setField(AnalyticsNl2SqlEvalRun row, String fieldName, Object value) {
            try {
                Field field = AnalyticsNl2SqlEvalRun.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(row, value);
            } catch (Exception e) {
                throw new IllegalStateException("failed to set field for test: " + fieldName, e);
            }
        }
    }
}
