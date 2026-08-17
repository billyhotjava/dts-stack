package com.yuzhi.dts.analytics.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalysisPolicyPlannerTest {

    private final AnalysisPolicyPlanner planner = new AnalysisPolicyPlanner();

    @Test
    void policyHashAndDepartmentPredicateAreActorScoped() {
        AnalyticsUser actor = new AnalyticsUser();
        actor.setId(7L);
        actor.setActive(true);
        GovernedAnalysisDatasetContract contract = contract("DATA_INTERNAL", List.of("rls:department:dept_code"));

        AnalysisPolicyPlanner.PolicyPlan left = planner.plan(actor, new AnalysisRequestContext("D1", "INTERNAL", "ROLE_ANALYST", "r1", "/api/analysis/1/query", null), contract);
        AnalysisPolicyPlanner.PolicyPlan right = planner.plan(actor, new AnalysisRequestContext("D2", "INTERNAL", "ROLE_ANALYST", "r2", "/api/analysis/1/query", null), contract);

        assertThat(left.rowPredicates()).containsExactly(new AnalysisSqlCompiler.RowPredicate("dept_code", "EQ", List.of("D1")));
        assertThat(left.policyContextHash()).isNotEqualTo(right.policyContextHash());
    }

    @Test
    void deniesWhenActorClearanceIsBelowDatasetClassification() {
        AnalyticsUser actor = new AnalyticsUser();
        actor.setId(7L);
        actor.setActive(true);

        assertThatThrownBy(() -> planner.plan(
            actor,
            new AnalysisRequestContext("D1", "PUBLIC", "ROLE_ANALYST", "r1", "/api/analysis/1/query", null),
            contract("DATA_SECRET", List.of())
        )).isInstanceOf(AnalysisForbiddenException.class);
    }

    private GovernedAnalysisDatasetContract contract(String classification, List<String> policyRefs) {
        return new GovernedAnalysisDatasetContract(
            UUID.randomUUID(),
            1,
            "PUBLISHED",
            UUID.randomUUID(),
            "select dept_code from ads_demo",
            List.of(new GovernedAnalysisDatasetContract.Dimension("dept_code", "部门", "text", List.of(), null, List.of("EQ"))),
            List.of(),
            List.of(),
            policyRefs,
            classification,
            "r1",
            "checksum"
        );
    }
}
