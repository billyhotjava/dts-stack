package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

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
        ModelingAuthorizationService.BatchAccessCheck check = (tenant, permission, ids, actor) -> {
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
            (tenant, permission, ids, actor) -> List.of(PolicyOutcome.allow("MODEL_SPEC_ACCESS_OK", "model-spec-access-v1"));
        var allowed = service.decideBatch("default", PermissionCodes.MODELING_MODEL_UPDATE,
            List.of(UUID.randomUUID()), "actor-1", allow);
        assertThat(allowed.decision()).isEqualTo(PermissionDecision.ALLOW);

        ModelingAuthorizationService.BatchAccessCheck deny =
            (tenant, permission, ids, actor) -> List.of(PolicyOutcome.deny("MODEL_OPERATION_SCOPE_DENIED", "model-spec-access-v1"));
        var denied = service.decideBatch("default", PermissionCodes.MODELING_MODEL_UPDATE,
            List.of(UUID.randomUUID()), "actor-1", deny);
        assertThat(denied.decision()).isEqualTo(PermissionDecision.DENY);
        assertThat(denied.reasonCode()).contains("MODEL_OPERATION_SCOPE_DENIED");

        ModelingAuthorizationService.BatchAccessCheck throwing =
            (tenant, permission, ids, actor) -> { throw new ModelSpecException("MODEL_OPERATION_SCOPE_DENIED", "x", ModelSpecException.Kind.FORBIDDEN); };
        var failed = service.decideBatch("default", PermissionCodes.MODELING_MODEL_UPDATE,
            List.of(UUID.randomUUID()), "actor-1", throwing);
        assertThat(failed.decision()).isEqualTo(PermissionDecision.DENY);
        assertThat(failed.reasonCode()).contains("MODEL_OPERATION_SCOPE_DENIED");
    }

    @Test
    @DisplayName("T04：combiner经由统一入口保持拒绝优先")
    void combinerContractHoldsThroughEntry() {
        AuthorizationCombiner.CombinedDecision combined = service.decideBatch("default", PermissionCodes.MODELING_MODEL_UPDATE,
            List.of(UUID.randomUUID()), "actor-1", (tenant, permission, ids, actor) ->
                List.of(PolicyOutcome.allow("A", "v1"), PolicyOutcome.deny("B", "v1")));
        assertThat(combined.decision()).isEqualTo(PermissionDecision.DENY);
    }

    @Test
    void adapterUsesRequestedActionAndRejectsMixedReadBatch() {
        ModelSpecAccessService access = mock(ModelSpecAccessService.class);
        var provider = service.modelSpecAccessProvider(access);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        var ids = List.of(first, second);
        assertThat(service.decideBatch("default", PermissionCodes.MODELING_MODEL_UPDATE, ids, "actor", provider).decision())
            .isEqualTo(PermissionDecision.ALLOW);
        verify(access).requireOperation("default", ids, "actor");
        verify(access, never()).requirePathRead(anyString());
        doThrow(new ModelSpecException("MODEL_NOT_VISIBLE", "hidden", ModelSpecException.Kind.FORBIDDEN))
            .when(access).requirePathRead("/api/modeling/model-specs/" + second);
        assertThat(service.decideBatch("default", PermissionCodes.MODELING_MODEL_READ, ids, "actor", provider).decision())
            .isEqualTo(PermissionDecision.DENY);
        verify(access).requirePathRead("/api/modeling/model-specs/" + first);
    }

    @Test
    void supportedActionBoundaryAndMaximumBatch() {
        ModelSpecAccessService access = mock(ModelSpecAccessService.class);
        var provider = service.modelSpecAccessProvider(access);
        var ids = java.util.stream.IntStream.range(0, 200).mapToObj(i -> UUID.randomUUID()).toList();
        assertThat(service.decideBatch("default", PermissionCodes.MODELING_MODEL_UPDATE, ids, "actor", provider).decision())
            .isEqualTo(PermissionDecision.ALLOW);
        verify(access).requireOperation("default", ids, "actor");
        clearInvocations(access);
        assertThat(service.decideBatch("default", PermissionCodes.CATALOG_DATASET_EXPORT, ids, "actor", provider).decision())
            .isEqualTo(PermissionDecision.DENY);
        verifyNoInteractions(access);
    }
}
