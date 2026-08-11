package com.yuzhi.dts.platform.service.modeling;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** PostgreSQL projection adapter for the platform-global model governance policy. */
@Component
public class JdbcModelGovernancePolicyAdapter implements ModelGovernancePolicyPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcModelGovernancePolicyAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Policy resolve() {
        try {
            return jdbcTemplate
                .query(
                    """
                    select standard_coverage, quality_gate
                      from modeling_platform_governance_policy
                     where policy_key = 'PLATFORM_DEFAULT'
                    """,
                    (row, rowNumber) ->
                        Policy.available(
                            StandardCoverage.valueOf(row.getString("standard_coverage")),
                            QualityGate.valueOf(row.getString("quality_gate"))
                        )
                )
                .stream()
                .findFirst()
                .orElseGet(() -> Policy.unavailable("PLATFORM_MODEL_GOVERNANCE_POLICY_NOT_FOUND"));
        } catch (RuntimeException unavailable) {
            return Policy.unavailable("PLATFORM_MODEL_GOVERNANCE_POLICY_UNREADABLE");
        }
    }
}
