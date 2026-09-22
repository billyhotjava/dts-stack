package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.common.security.AuthorizationCombiner;
import com.yuzhi.dts.common.security.PermissionCodes;
import com.yuzhi.dts.common.security.PolicyOutcome;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * F11-T04/T05 建模域统一授权入口（首切片）。
 *
 * <p>按“资源类型+动作”路由到领域检查并用 {@link AuthorizationCombiner} 组合：
 * 适用拒绝优先、必需异常失败关闭、未适用不允许。批量先展开全部目标并判定，
 * 任一目标不足则整批拒绝且零副作用（由调用方保证判定先于写入；本入口在 provider
 * 触发前先做租户/动作/范围形状校验，形状非法时不调用 provider）。
 *
 * <p>现有 {@link ModelSpecAccessService} 行为保持不变；新入口逐步接管调用方，
 * 老入口的差异逐条解释后切换（T08 对比要求）。
 */
@Service
public class ModelingAuthorizationService {

    /** 领域检查 provider：返回适用于本次资源类型+动作的策略结果（只传适用策略）。 */
    public interface BatchAccessCheck {
        List<PolicyOutcome> check(String tenant, String permission, Collection<java.util.UUID> ids, String actor);
    }

    private final String serverTenant;

    public ModelingAuthorizationService(
        @Value("${dts.platform.modeling.default-tenant-id:default}") String serverTenant
    ) {
        this.serverTenant = serverTenant;
    }

    /**
     * 批量动作判定。
     *
     * @param permission 权限码，必须在字典内
     * @param ids 目标模型；空/超 200/含 null 直接拒绝且不调用 provider（零副作用）
     */
    public AuthorizationCombiner.CombinedDecision decideBatch(
        String tenant,
        String permission,
        Collection<java.util.UUID> ids,
        String actor,
        BatchAccessCheck check
    ) {
        if (!serverTenant.equals(tenant)) {
            return new AuthorizationCombiner.CombinedDecision(
                com.yuzhi.dts.common.security.PermissionDecision.DENY, "TENANT_MISMATCH@f11-t04");
        }
        if (!PermissionCodes.isKnown(permission)) {
            return new AuthorizationCombiner.CombinedDecision(
                com.yuzhi.dts.common.security.PermissionDecision.DENY, "UNKNOWN_PERMISSION@f11-t04");
        }
        if (!PermissionCodes.MODELING_MODEL_READ.equals(permission) && !PermissionCodes.MODELING_MODEL_UPDATE.equals(permission)) {
            return new AuthorizationCombiner.CombinedDecision(
                com.yuzhi.dts.common.security.PermissionDecision.DENY, "MODEL_ACTION_NOT_SUPPORTED@f11-t04");
        }
        if (actor == null || actor.isBlank()) {
            return new AuthorizationCombiner.CombinedDecision(
                com.yuzhi.dts.common.security.PermissionDecision.DENY, "ACTOR_REQUIRED@f11-t04");
        }
        if (ids == null || ids.isEmpty() || ids.size() > 200 || ids.stream().anyMatch(Objects::isNull)) {
            return new AuthorizationCombiner.CombinedDecision(
                com.yuzhi.dts.common.security.PermissionDecision.DENY, "OPERATION_SCOPE_INVALID@f11-t04");
        }
        List<PolicyOutcome> outcomes;
        try {
            outcomes = check.check(tenant, permission, ids, actor);
        } catch (ModelSpecException ex) {
            return new AuthorizationCombiner.CombinedDecision(
                com.yuzhi.dts.common.security.PermissionDecision.DENY, ex.code() + "@model-spec-access");
        } catch (RuntimeException ex) {
            return new AuthorizationCombiner.CombinedDecision(
                com.yuzhi.dts.common.security.PermissionDecision.DENY, "DEPENDENCY_INDETERMINATE:access-check-failed");
        }
        return AuthorizationCombiner.combine(outcomes, "f11-t04");
    }

    /** 基于既有 ModelSpecAccessService 的 provider：成功→ALLOW，异常由 decideBatch 转 DENY。 */
    public BatchAccessCheck modelSpecAccessProvider(ModelSpecAccessService access) {
        return (tenant, permission, ids, actor) -> {
            if (PermissionCodes.MODELING_MODEL_UPDATE.equals(permission)) {
                access.requireOperation(tenant, ids, actor);
            } else if (PermissionCodes.MODELING_MODEL_READ.equals(permission)) {
                for (java.util.UUID id : ids) {
                    access.requirePathRead("/api/modeling/model-specs/" + id);
                }
            } else {
                throw new ModelSpecException("MODEL_ACTION_NOT_SUPPORTED", "该动作尚未接入统一入口",
                    ModelSpecException.Kind.BAD_REQUEST);
            }
            return List.of(PolicyOutcome.allow("MODEL_SPEC_ACCESS_OK", "model-spec-access-v1"));
        };
    }
}
