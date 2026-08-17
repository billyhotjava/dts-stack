package com.yuzhi.dts.analytics.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalysisQueryBudgetTest {

    @Test
    void enforcesUserBudgetAndReleasesPermitsOnClose() {
        AnalysisQueryBudget budget = new AnalysisQueryBudget(3, 20, 100);
        List<AnalysisQueryBudget.Lease> leases = new ArrayList<>();
        leases.add(budget.acquire("user-1", "dept-1"));
        leases.add(budget.acquire("user-1", "dept-1"));
        leases.add(budget.acquire("user-1", "dept-1"));

        assertThatThrownBy(() -> budget.acquire("user-1", "dept-1"))
            .isInstanceOf(AnalysisRateLimitException.class)
            .hasMessageContaining("user");

        leases.remove(0).close();
        try (AnalysisQueryBudget.Lease ignored = budget.acquire("user-1", "dept-1")) {
            assertThat(budget.activeForUser("user-1")).isEqualTo(3);
        }
        leases.forEach(AnalysisQueryBudget.Lease::close);
        assertThat(budget.activeGlobal()).isZero();
    }
}
