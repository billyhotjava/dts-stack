package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.security.SecurityUtils;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Modeling follows menu access; tenant and actor checks also protect background owner operations. */
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
        String actor = actorId.trim();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            select owner_id, owner_department_id
              from modeling_warehouse_plan
             where tenant_id = ? and id = ?
            """,
            tenantId,
            planId
        );
        if (rows.size() != 1) return false;
        Map<String, Object> row = rows.getFirst();
        String ownerId = text(row.get("owner_id"));
        if (hasAuthenticatedPrincipal() && SecurityUtils.isAuthenticated() && matchesAuthenticatedActor(actor)) return true;
        // Scheduled reconciliation has no user principal; it retains the historical owner-only fallback.
        return actor.equals(ownerId) && SecurityContextHolder.getContext().getAuthentication() == null;
    }

    private static boolean matchesAuthenticatedActor(String actorId) {
        return (
            SecurityUtils.getCurrentUserId().filter(actorId::equals).isPresent() ||
            SecurityUtils.getCurrentUserLogin().filter(actorId::equals).isPresent()
        );
    }

    private static boolean hasAuthenticatedPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated();
    }

    private static String text(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
