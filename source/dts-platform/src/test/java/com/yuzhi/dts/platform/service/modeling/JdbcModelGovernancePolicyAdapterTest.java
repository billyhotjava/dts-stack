package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class JdbcModelGovernancePolicyAdapterTest {

    @Test
    void resolvesThePlatformGlobalPolicyWithoutLookingUpAWarehousePlan() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate(
            List.of(
                ModelGovernancePolicyPort.Policy.available(
                    ModelGovernancePolicyPort.StandardCoverage.KEY_AND_MEASURE,
                    ModelGovernancePolicyPort.QualityGate.BLOCKING
                )
            ),
            null
        );

        ModelGovernancePolicyPort.Policy policy = new JdbcModelGovernancePolicyAdapter(jdbc).resolve();

        assertThat(policy.available()).isTrue();
        assertThat(policy.standardCoverage()).isEqualTo(ModelGovernancePolicyPort.StandardCoverage.KEY_AND_MEASURE);
        assertThat(policy.qualityGate()).isEqualTo(ModelGovernancePolicyPort.QualityGate.BLOCKING);
        assertThat(jdbc.sql())
            .contains("modeling_platform_governance_policy", "policy_key = 'PLATFORM_DEFAULT'")
            .doesNotContain("modeling_warehouse_plan", "tenant_id", "plan_id");
    }

    @Test
    void failsClosedWhenThePlatformGlobalPolicyRowIsMissing() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate(List.of(), null);

        ModelGovernancePolicyPort.Policy policy = new JdbcModelGovernancePolicyAdapter(jdbc).resolve();

        assertThat(policy.available()).isFalse();
        assertThat(policy.reasonCode()).isEqualTo("PLATFORM_MODEL_GOVERNANCE_POLICY_NOT_FOUND");
    }

    @Test
    void failsClosedWhenThePlatformGlobalPolicyCannotBeRead() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate(
            List.of(),
            new IllegalStateException("database unavailable")
        );

        ModelGovernancePolicyPort.Policy policy = new JdbcModelGovernancePolicyAdapter(jdbc).resolve();

        assertThat(policy.available()).isFalse();
        assertThat(policy.reasonCode()).isEqualTo("PLATFORM_MODEL_GOVERNANCE_POLICY_UNREADABLE");
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {

        private final List<?> results;
        private final RuntimeException failure;
        private String sql;

        private RecordingJdbcTemplate(List<?> results, RuntimeException failure) {
            this.results = results;
            this.failure = failure;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, RowMapper<T> rowMapper) {
            this.sql = sql;
            if (failure != null) {
                throw failure;
            }
            return (List<T>) results;
        }

        private String sql() {
            return sql;
        }
    }
}
