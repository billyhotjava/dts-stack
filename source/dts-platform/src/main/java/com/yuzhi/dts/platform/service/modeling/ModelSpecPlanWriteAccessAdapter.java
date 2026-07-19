package com.yuzhi.dts.platform.service.modeling;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Owner-scoped plan authorization; broader delegation can be added through an explicit plan grant later. */
@Component
@Transactional(readOnly = true)
public class ModelSpecPlanWriteAccessAdapter implements ModelSpecPlanWriteAccessPort {

    private final JdbcTemplate jdbcTemplate;

    public ModelSpecPlanWriteAccessAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean canMaintain(String tenantId, UUID planId, String actorId) {
        if (tenantId == null || tenantId.isBlank() || planId == null || actorId == null || actorId.isBlank()) return false;
        Boolean allowed = jdbcTemplate.queryForObject(
            """
            select exists (
                select 1 from modeling_warehouse_plan
                 where tenant_id = ? and id = ? and owner_id = ?
            )
            """,
            Boolean.class,
            tenantId,
            planId,
            actorId
        );
        return Boolean.TRUE.equals(allowed);
    }
}
