package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.common.security.AuthorizationCombiner;
import com.yuzhi.dts.common.security.PermissionCodes;
import com.yuzhi.dts.common.security.PermissionDecision;
import com.yuzhi.dts.common.security.PolicyOutcome;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F11 UT-028（批量零副作用）与 T04 首切片入口的可执行覆盖。
 * provider 用 stub 表达，不触数据库；真实模块集成由 T09 补证据。
 */
class ModelingAuthorizationServiceTest {

    private final ModelingAuthorizationService service = new ModelingAuthorizationService("default");

    @Test
    @DisplayName("F11-UT-028：形状非法整批拒绝且不调用provider（零副作用）")
    void invalidScopeIsRejectedWithoutProviderCall() {
        AtomicInteger calls = new AtomicInteger();
        ModelingAuthorizationService.BatchAccessCheck check = (tenant, ids, actor) -> {
            calls.incrementAndGet();
            return List.of(PolicyOutcome.allow("OK", "v1"));
        };
        UUID id = UUID.randomUUID();

        assertThat(service.decideBatch("default", PermissionCodes.MODELING_MODEL_UPDATE, List.of(), "actor-1", check).decision())
            .isEqualTo(PermissionDecision.DENY);
        List<UUID> tooMany = new ArrayList<>();
        for (int i = 0; i < 201; i++) {
            tooMany.add(UUID.randomUUID());
        }
        assertThat(service.decideBatch("default", PermissionCodes.MODELING_MODEL_UPDATE, tooMany, "actor-1", check).decision())
            .isEqualTo(PermissionDecision.DENY);
        assertThat(service.decideBatch("other-tenant", PermissionCodes.MODELING_MODEL_UPDATE, List.of(id), "actor-1", check).decision())
            .isEqualTo(PermissionDecision.DENY);
        assertThat(service.decideBatch("default", "modeling:model:delete", List.of(id), "actor-1", check).decision())
            .isEqualTo(PermissionDecision.DENY);
        assertThat(calls.get()).isZero();
    }

    @Test
    @DisplayName("F11-UT-023/028：provider拒绝整批DENY；异常转失败关闭且保留原因码")
    void providerOutcomesAreCombined() {
        ModelingAuthorizationService.BatchAccessCheck allow =
            (tenant, ids, actor) -> List.of(PolicyOutcome.allow("MODEL_SPEC_ACCESS_OK", "model-spec-access-v1"));
        var allowed = service.decideBatch("default", PermissionCodes.MODELING_MODEL_UPDATE,
            List.of(UUID.randomUUID()), "actor-1", allow);
        assertThat(allowed.decision()).isEqualTo(PermissionDecision.ALLOW);

        ModelingAuthorizationService.BatchAccessCheck deny =
            (tenant, ids, actor) -> List.of(PolicyOutcome.deny("MODEL_OPERATION_SCOPE_DENIED", "model-spec-access-v1"));
        var denied = service.decideBatch("default", PermissionCodes.MODELING_MODEL_UPDATE,
            List.of(UUID.randomUUID()), "actor-1", deny);
        assertThat(denied.decision()).isEqualTo(PermissionDecision.DENY);
        assertThat(denied.reasonCode()).contains("MODEL_OPERATION_SCOPE_DENIED");

        ModelingAuthorizationService.BatchAccessCheck throwing =
            (tenant, ids, actor) -> { throw new ModelSpecException("MODEL_OPERATION_SCOPE_DENIED", "x", ModelSpecException.Kind.FORBIDDEN); };
        var failed = service.decideBatch("default", PermissionCodes.MODELING_MODEL_UPDATE,
            List.of(UUID.randomUUID()), "actor-1", throwing);
        assertThat(failed.decision()).isEqualTo(PermissionDecision.DENY);
        assertThat(failed.reasonCode()).contains("MODEL_OPERATION_SCOPE_DENIED");
    }

    @Test
    @DisplayName("T04：combiner经由统一入口保持拒绝优先")
    void combinerContractHoldsThroughEntry() {
        AuthorizationCombiner.CombinedDecision combined = AuthorizationCombiner.combine(
            List.of(PolicyOutcome.allow("A", "v1"), PolicyOutcome.deny("B", "v1")), "v1");
        assertThat(combined.decision()).isEqualTo(PermissionDecision.DENY);
    }
}
