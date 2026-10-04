package com.yuzhi.dts.copilot.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class FinanceCaliberGuardrailTest {

    private final FinanceCaliberGuardrail guardrail = new FinanceCaliberGuardrail();

    @Test
    void shouldRejectKnownFinanceCaliberViolations() {
        assertViolation(
                "CAL-SETTLEMENT-CHAIN",
                """
                SELECT SUM(m.receivable_total_amount) + SUM(s.receivable_amount)
                FROM a_month_accounting m
                JOIN a_sale_account s ON s.project_id = m.project_id
                """);
        assertViolation(
                "CAL-SALE-IN-RENT",
                """
                SELECT SUM(m.sale_total_amount) + SUM(s.receivable_amount)
                FROM a_month_accounting m
                JOIN a_green_accounting g ON g.month_accounting_id = m.id
                JOIN a_sale_account s ON s.id = g.source_id
                WHERE g.source_type = 8
                """);
        assertViolation(
                "CAL-SETTLEMENT-CHAIN",
                """
                SELECT SUM(s.receivable_amount) AS income_amount
                FROM a_sale_account s
                JOIN t_flower_biz_info b ON b.id = s.biz_id
                WHERE b.finish_time >= DATE '2026-01-01'
                """);
    }

    @Test
    void shouldAllowSaleAccountIncomeWhenBadDebtIsExcluded() {
        List<String> allowedSql = List.of(
                """
                SELECT SUM(s.receivable_amount) AS income_amount
                FROM a_sale_account s
                JOIN t_flower_biz_info b ON b.id = s.biz_id
                WHERE b.biz_type <> 6
                """,
                """
                SELECT SUM(s.receivable_amount) AS sale_income_amount
                FROM a_sale_account s
                JOIN t_flower_biz_info b ON b.id = s.biz_id
                WHERE b.biz_type IN (7, 8)
                """);

        for (String sql : allowedSql) {
            assertThat(guardrail.validate(sql)).as(sql).isEmpty();
        }
    }

    private void assertViolation(String expectedRuleId, String sql) {
        assertThat(guardrail.validate(sql))
                .extracting(FinanceCaliberGuardrail.Violation::ruleId)
                .contains(expectedRuleId);
    }
}
