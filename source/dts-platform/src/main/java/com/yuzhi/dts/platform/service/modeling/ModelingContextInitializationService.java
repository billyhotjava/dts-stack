package com.yuzhi.dts.platform.service.modeling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider.WarehousePlanActor;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanApplicationService;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.CreateWarehousePlanCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.OnboardingMode;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves only omitted creation context; initialization and the existing save owner share one transaction. */
@Service
public class ModelingContextInitializationService {

    static final String DEFAULT_KEY = "modeling-context:default:v1";
    private final JdbcTemplate jdbc;
    private final WarehousePlanApplicationService plans;

    public ModelingContextInitializationService(JdbcTemplate jdbc, WarehousePlanApplicationService plans) {
        this.jdbc = jdbc;
        this.plans = plans;
    }

    @Transactional(timeout = 30)
    public <T> T withContext(
        String tenantId,
        WarehousePlanActor actor,
        List<JsonNode> requests,
        Function<List<JsonNode>, T> save
    ) {
        // Malformed, explicit and partially specified requests retain the strict decoder's diagnostics.
        if (requests.isEmpty() || requests.stream().anyMatch(node -> node == null || !node.isObject() || node.hasNonNull("planId"))) {
            return save.apply(requests);
        }
        if (tenantId == null || tenantId.isBlank() || actor == null || actor.ownerId() == null ||
            !SecurityUtils.isAuthenticated() || !SecurityContextHolder.getContext().getAuthentication().isAuthenticated() ||
            !(SecurityUtils.getCurrentUserId().filter(actor.ownerId()::equals).isPresent() ||
              SecurityUtils.getCurrentUserLogin().filter(actor.ownerId()::equals).isPresent())) {
            throw new ModelSpecException("MODEL_SPEC_WRITE_FORBIDDEN", "An authenticated modeling actor is required", ModelSpecException.Kind.FORBIDDEN, null);
        }
        UUID planId = findDefault(tenantId);
        if (planId == null) {
            // Only cold starts serialize. Transaction timeout bounds lock acquisition; recheck after a competing commit.
            jdbc.query("select pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> { }, "modeling-context:" + tenantId);
            planId = findDefault(tenantId);
            if (planId == null) {
                planId = plans.create(tenantId, new CreateWarehousePlanCommand(
                    "数据建模", "维护数据模型设计与实现", null, actor.ownerId(), actor.ownerDepartmentId(),
                    OnboardingMode.BUSINESS_FIRST, List.of(), DEFAULT_KEY
                )).planId();
            }
        }
        String resolved = planId.toString();
        List<JsonNode> normalized = requests.stream().map(node -> {
            ObjectNode copy = node.deepCopy();
            copy.put("planId", resolved);
            return (JsonNode) copy;
        }).toList();
        return save.apply(normalized);
    }

    private UUID findDefault(String tenantId) {
        return jdbc.query("""
            select id, lifecycle_status from modeling_warehouse_plan
             where tenant_id = ? and (idempotency_key = ? or
                 (idempotency_key like 'modeling-context:%' and lifecycle_status not in ('PUBLISHED', 'ARCHIVED')))
             order by case when idempotency_key = ? then 0 else 1 end, created_date, id
             limit 1
            """, (rs, row) -> {
                if ("PUBLISHED".equals(rs.getString("lifecycle_status")) || "ARCHIVED".equals(rs.getString("lifecycle_status"))) {
                    throw new ModelSpecException("MODEL_SPEC_PLAN_READ_ONLY", "The default modeling context is read-only", ModelSpecException.Kind.CONFLICT, null);
                }
                return rs.getObject("id", UUID.class);
            }, tenantId, DEFAULT_KEY, DEFAULT_KEY).stream().findFirst().orElse(null);
    }
}
