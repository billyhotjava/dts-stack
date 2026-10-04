package com.yuzhi.dts.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.domain.AnalyticsNl2SqlEvalCase;
import com.yuzhi.dts.analytics.repository.AnalyticsNl2SqlEvalCaseRepository;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class Nl2SqlEvalServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void runEvaluation_returnsPassAndFailSummary() {
        RepoStub stub = new RepoStub();
        stub.rows.add(evalCase(
                1L,
                "sales-pass",
                "sales",
                "生成销售分析大屏，关注订单量和转化率，按月趋势分析",
                """
                {
                  "domain": "sales",
                  "factTable": "fact_sales_order",
                  "safetyStatus": "needs-params",
                  "minBlueprintCount": 5,
                  "sqlContainsAny": ["fact_sales_order", "event_month"]
                }
                """,
                true));
        stub.rows.add(evalCase(
                2L,
                "sales-fail-domain",
                "sales",
                "生成销售分析大屏，关注订单量和转化率，按月趋势分析",
                """
                {
                  "domain": "energy"
                }
                """,
                true));

        Nl2SqlEvalService service = new Nl2SqlEvalService(
                stub.repository(),
                new ScreenAiGenerationService(objectMapper),
                objectMapper);

        Map<String, Object> summary = service.runEvaluation(List.of(), true, 100);

        assertThat(summary.get("total")).isEqualTo(2);
        assertThat(summary.get("passed")).isEqualTo(1);
        assertThat(summary.get("failed")).isEqualTo(1);
        assertThat(summary.get("passRate")).isEqualTo(0.5d);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resultRows = (List<Map<String, Object>>) summary.get("rows");
        assertThat(resultRows).hasSize(2);
        assertThat(resultRows.get(0).get("passed")).isEqualTo(true);
        assertThat(resultRows.get(1).get("passed")).isEqualTo(false);
    }

    @Test
    void runEvaluation_enabledOnly_filtersDisabledCase() {
        RepoStub stub = new RepoStub();
        stub.rows.add(evalCase(
                1L,
                "enabled-case",
                "sales",
                "生成销售分析大屏，关注订单量和转化率，按月趋势分析",
                "{\"domain\":\"sales\"}",
                true));
        stub.rows.add(evalCase(
                2L,
                "disabled-case",
                "sales",
                "生成销售分析大屏，关注订单量和转化率，按月趋势分析",
                "{\"domain\":\"sales\"}",
                false));

        Nl2SqlEvalService service = new Nl2SqlEvalService(
                stub.repository(),
                new ScreenAiGenerationService(objectMapper),
                objectMapper);

        Map<String, Object> summary = service.runEvaluation(List.of(), true, 100);
        assertThat(summary.get("total")).isEqualTo(1);
        assertThat(summary.get("passed")).isEqualTo(1);
    }

    private AnalyticsNl2SqlEvalCase evalCase(
            long id,
            String name,
            String domain,
            String prompt,
            String expectedJson,
            boolean enabled) {
        AnalyticsNl2SqlEvalCase row = new AnalyticsNl2SqlEvalCase();
        setId(row, id);
        row.setName(name);
        row.setDomain(domain);
        row.setPromptText(prompt);
        row.setExpectedJson(expectedJson);
        row.setEnabled(enabled);
        return row;
    }

    private void setId(AnalyticsNl2SqlEvalCase row, long id) {
        try {
            Field field = AnalyticsNl2SqlEvalCase.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(row, id);
        } catch (Exception e) {
            throw new IllegalStateException("failed to set id for test", e);
        }
    }

    private static final class RepoStub {
        private final List<AnalyticsNl2SqlEvalCase> rows = new ArrayList<>();

        private AnalyticsNl2SqlEvalCaseRepository repository() {
            return (AnalyticsNl2SqlEvalCaseRepository) Proxy.newProxyInstance(
                    AnalyticsNl2SqlEvalCaseRepository.class.getClassLoader(),
                    new Class<?>[] {AnalyticsNl2SqlEvalCaseRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findAllByOrderByIdAsc" -> listAll(args);
                        case "findAllByEnabledTrueOrderByIdAsc" -> listEnabled(args);
                        case "findAllByIdIn" -> listByIds(args);
                        case "toString" -> "Nl2SqlEvalCaseRepoStub";
                        default -> throw new UnsupportedOperationException("Method not stubbed: " + method.getName());
                    });
        }

        private List<AnalyticsNl2SqlEvalCase> listAll(Object[] args) {
            int limit = resolvePageSize(args);
            return rows.stream()
                    .sorted(Comparator.comparing(AnalyticsNl2SqlEvalCase::getId))
                    .limit(limit)
                    .toList();
        }

        private List<AnalyticsNl2SqlEvalCase> listEnabled(Object[] args) {
            int limit = resolvePageSize(args);
            return rows.stream()
                    .filter(AnalyticsNl2SqlEvalCase::isEnabled)
                    .sorted(Comparator.comparing(AnalyticsNl2SqlEvalCase::getId))
                    .limit(limit)
                    .toList();
        }

        @SuppressWarnings("unchecked")
        private List<AnalyticsNl2SqlEvalCase> listByIds(Object[] args) {
            List<Long> ids = args != null && args.length > 0 && args[0] instanceof List<?> ? (List<Long>) args[0] : List.of();
            return rows.stream()
                    .filter(row -> row.getId() != null && ids.contains(row.getId()))
                    .sorted(Comparator.comparing(AnalyticsNl2SqlEvalCase::getId))
                    .toList();
        }

        private int resolvePageSize(Object[] args) {
            if (args != null && args.length > 0 && args[0] instanceof Pageable pageable) {
                return Math.max(1, pageable.getPageSize());
            }
            return Integer.MAX_VALUE;
        }
    }
}
