package com.yuzhi.dts.platform.service.modeling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.platform.security.modeling.ModelingIdentity;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider.WarehousePlanActor;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanApplicationService;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.CreateWarehousePlanCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.OnboardingMode;
import java.util.*;
import java.util.function.Function;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The department context and first model save commit or roll back together. Reads never initialize. */
@Service
public class ModelingContextInitializationService {
    private final JdbcTemplate jdbc;
    private final WarehousePlanApplicationService plans;
    private final ModelingDepartmentScope departments;
    public ModelingContextInitializationService(JdbcTemplate jdbc, WarehousePlanApplicationService plans, ModelingDepartmentScope departments) {
        this.jdbc = jdbc; this.plans = plans; this.departments = departments;
    }
    @Transactional(readOnly = true)
    public UUID existingContextId(String tenant) { return context(tenant, null).planId(); }
    @Transactional(readOnly = true)
    public CreationContext context(String tenant, String requestedDepartment) {
        String department = departments.resolve(requestedDepartment);
        var rows = jdbc.query("select id, lifecycle_status from modeling_warehouse_plan where tenant_id = ? and idempotency_key = ?",
            (rs, n) -> new CreationContext(rs.getObject(1, UUID.class), department, !Set.of("ARCHIVED", "PUBLISHED").contains(rs.getString(2))), tenant, key(department));
        return rows.isEmpty() ? new CreationContext(null, department, true) : rows.getFirst();
    }
    @Transactional(timeout = 30)
    public <T> T withContext(String tenant, WarehousePlanActor actor, List<JsonNode> requests, Function<List<JsonNode>, T> save) {
        if (actor == null || !ModelingIdentity.matchesActor(actor.ownerId())) throw new ModelSpecException("MODELING_ROLE_REQUIRED", "当前建模身份无效", ModelSpecException.Kind.FORBIDDEN);
        if (requests.isEmpty() || requests.stream().anyMatch(node -> node == null || !node.isObject())) return save.apply(requests);
        Set<String> selected = new HashSet<>();
        Set<String> planIds = new HashSet<>();
        for (JsonNode node : requests) {
            if (node.hasNonNull("departmentCode")) selected.add(node.get("departmentCode").asText());
            if (node.hasNonNull("planId")) planIds.add(node.get("planId").asText());
        }
        if (selected.size() > 1 || planIds.size() > 1) throw mismatch();
        UUID planId;
        String department;
        if (!planIds.isEmpty()) {
            try { planId = UUID.fromString(planIds.iterator().next()); }
            catch (IllegalArgumentException ex) { return save.apply(requests); }
            var plan = plans.get(tenant, planId);
            department = plan.ownerDepartmentId();
            if (!selected.isEmpty() && !department.equals(selected.iterator().next())) throw mismatch();
            if (Set.of("PUBLISHED", "ARCHIVED").contains(plan.lifecycleStatus().name())) throw readOnly();
        } else {
            department = departments.resolve(selected.isEmpty() ? null : selected.iterator().next());
            // The create service owns the single lock/key for all entry points, including direct plan creation.
            planId = plans.create(tenant, new CreateWarehousePlanCommand("部门公共层", "共同维护部门数据模型", null,
                actor.ownerId(), department, OnboardingMode.BUSINESS_FIRST, List.of(), key(department))).planId();
        }
        String resolvedId = planId.toString();
        return save.apply(requests.stream().map(node -> {
            ObjectNode copy = node.deepCopy();
            copy.remove("departmentCode");
            copy.put("planId", resolvedId);
            return (JsonNode) copy;
        }).toList());
    }
    private static String key(String department) { return "modeling-context:dept:" + department + ":v1"; }
    private static ModelSpecException mismatch() { return new ModelSpecException("MODELING_CONTEXT_DEPARTMENT_MISMATCH", "模型与所选部门公共层不一致", ModelSpecException.Kind.BAD_REQUEST); }
    private static ModelSpecException readOnly() { return new ModelSpecException("MODELING_CONTEXT_NOT_WRITABLE", "部门公共层当前不可写，请联系管理员恢复", ModelSpecException.Kind.CONFLICT); }
    public record CreationContext(UUID planId, String departmentCode, boolean writable) {}
}
