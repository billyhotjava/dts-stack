package com.yuzhi.dts.metrics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MetricFormulaSqlGeneratorTest {

    private final MetricFormulaSqlGenerator generator = new MetricFormulaSqlGenerator();

    @Test
    void rendersRatioWithZeroDivisionGuard() {
        String sql = generator.render(
            Map.of(
                "type",
                "ratio",
                "numerator",
                Map.of("type", "aggregation", "aggregation", "sum", "field", "direct_cost_amount"),
                "denominator",
                Map.of("type", "aggregation", "aggregation", "sum", "field", "direct_cost_control_amount"),
                "multiply",
                100
            )
        );

        assertThat(sql)
            .contains("case when")
            .contains("sum(coalesce(direct_cost_amount, 0))")
            .contains("sum(coalesce(direct_cost_control_amount, 0))")
            .contains("then null")
            .contains("* 100");
    }

    @Test
    void rendersConditionalDistinctCount() {
        String sql = generator.render(
            Map.of(
                "type",
                "conditional_count",
                "field",
                "project_id",
                "distinct",
                true,
                "condition",
                Map.of("field", "project_status", "operator", "=", "value", "在研")
            )
        );

        assertThat(sql).isEqualTo("count(distinct case when project_status = '在研' then project_id end)");
    }

    @Test
    void rejectsUnsafeIdentifier() {
        assertThatThrownBy(() ->
                generator.render(Map.of("type", "aggregation", "aggregation", "sum", "field", "amount); drop table metric_pack; --"))
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsafe identifier");
    }
}
