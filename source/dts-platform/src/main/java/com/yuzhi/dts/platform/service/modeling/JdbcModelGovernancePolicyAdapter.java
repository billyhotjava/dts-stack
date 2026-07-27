package com.yuzhi.dts.platform.service.modeling;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** PostgreSQL projection adapter for the warehouse-plan-owned governance policy. */
@Component
public class JdbcModelGovernancePolicyAdapter implements ModelGovernancePolicyPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcModelGovernancePolicyAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Policy resolve(String tenantId, UUID planId) {
        if (tenantId == null || tenantId.isBlank() || planId == null) {
            return Policy.unavailable("WAREHOUSE_PLAN_POLICY_IDENTITY_REQUIRED");
        }
        try {
            return jdbcTemplate
                .query(
                    """
                    select coalesce(policy.standard_coverage, 'ALL_FIELDS') as standard_coverage,
                           coalesce(policy.quality_gate, 'BLOCKING') as quality_gate
                      from modeling_warehouse_plan plan
                      left join modeling_warehouse_plan_policy policy
                        on policy.tenant_id = plan.tenant_id and policy.plan_id = plan.id
                     where plan.tenant_id = ? and plan.id = ?
                    """,
                    (row, rowNumber) ->
                        Policy.available(
                            StandardCoverage.valueOf(row.getString("standard_coverage")),
                            QualityGate.valueOf(row.getString("quality_gate"))
                        ),
                    tenantId,
                    planId
                )
                .stream()
                .findFirst()
                .orElseGet(() -> Policy.unavailable("WAREHOUSE_PLAN_POLICY_NOT_FOUND"));
        } catch (RuntimeException unavailable) {
            return Policy.unavailable("WAREHOUSE_PLAN_POLICY_UNREADABLE");
        }
    }
}
