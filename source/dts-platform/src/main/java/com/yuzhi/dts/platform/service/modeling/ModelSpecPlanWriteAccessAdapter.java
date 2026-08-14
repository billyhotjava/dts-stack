package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.DepartmentUtils;
import com.yuzhi.dts.platform.security.SecurityUtils;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Authenticated institute-wide or department-scoped plan authorization with an owner fallback. */
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
        String ownerDepartmentId = text(row.get("owner_department_id"));
        if (!matchesAuthenticatedActor(actor)) {
            // Scheduled legacy reconciliation has no user SecurityContext. Keep the historical owner-only
            // fallback there, while authenticated requests must always pass the role and scope checks below.
            return actor.equals(ownerId) && !hasAuthenticatedPrincipal();
        }
        if (
            SecurityUtils.hasCurrentUserAnyOfAuthorities(
                AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES
            )
        ) {
            return true;
        }
        return (
            SecurityUtils.hasCurrentUserThisAuthority(
                AuthoritiesConstants.DEPT_DATA_OWNER
            ) &&
            SecurityUtils
                .getCurrentUserDept()
                .map(activeDepartment ->
                    DepartmentUtils.matches(
                        ownerDepartmentId,
                        activeDepartment
                    )
                )
                .orElse(false)
        );
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
