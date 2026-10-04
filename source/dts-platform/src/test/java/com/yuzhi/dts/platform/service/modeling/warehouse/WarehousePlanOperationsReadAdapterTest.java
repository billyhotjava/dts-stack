package com.yuzhi.dts.platform.service.modeling.warehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.admin.gateway.directory.AdminDirectoryGateway;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider.WarehousePlanActor;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.LifecycleStatus;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.OnboardingMode;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.WarehousePlanHeader;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class WarehousePlanOperationsReadAdapterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authorizesOnePlanWithoutListingAllPlans() {
        UUID planId = UUID.randomUUID();
        var plans = mock(WarehousePlanApplicationService.class);
        var actors = mock(WarehousePlanActorProvider.class);
        var guard = mock(WarehousePlanAuthorizationGuard.class);
        var actor = new WarehousePlanActor("reader", null);
        var header = mock(WarehousePlanHeader.class);
        when(actors.currentActor()).thenReturn(actor);
        when(plans.get("tenant-a", planId)).thenReturn(header);
        when(guard.canReadPlan(header, actor)).thenReturn(true);
        var adapter = new WarehousePlanOperationsReadAdapter(plans, actors, guard, "tenant-a");
        assertThat(adapter.canReadPlan(planId)).isTrue();
        when(guard.canReadPlan(header, actor)).thenReturn(false);
        assertThat(adapter.canReadPlan(planId)).isFalse();
        org.mockito.Mockito.verify(plans, org.mockito.Mockito.never()).list(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void readsOnlyTheConfiguredServerTenantAndMapsOperationsFields() {
        authenticate(AuthoritiesConstants.INST_LEADER);
        UUID planId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        WarehousePlanApplicationService plans = mock(WarehousePlanApplicationService.class);
        WarehousePlanActorProvider actorProvider = mock(WarehousePlanActorProvider.class);
        WarehousePlanHeader header = new WarehousePlanHeader(
            planId,
            "tenant-a",
            "FIN_BUDGET",
            "财务预算规划",
            "预算分析",
            "财务域",
            "alice",
            "FIN",
            OnboardingMode.BUSINESS_FIRST,
            LifecycleStatus.DESIGNING,
            3
        );
        when(plans.list("tenant-a", null)).thenReturn(List.of(header));
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("institute-leader", null));

        WarehousePlanOperationsReadAdapter adapter = new WarehousePlanOperationsReadAdapter(
            plans,
            actorProvider,
            new WarehousePlanAuthorizationGuard(mock(AdminDirectoryGateway.class)),
            "tenant-a"
        );

        assertThat(adapter.listPlans())
            .containsExactly(
                new com.yuzhi.dts.platform.service.ops.WarehousePlanOperationsReadPort.WarehousePlanProjection(
                    planId,
                    "财务预算规划",
                    "FIN",
                    "DESIGNING"
                )
            );
        verify(plans).list("tenant-a", null);
    }

    @Test
    void excludesPlansOutsideTheAuthenticatedDepartment() {
        authenticate(AuthoritiesConstants.EMPLOYEE);
        UUID readableId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID hiddenId = UUID.fromString("10000000-0000-0000-0000-000000000002");
        WarehousePlanApplicationService plans = mock(WarehousePlanApplicationService.class);
        WarehousePlanActorProvider actorProvider = mock(WarehousePlanActorProvider.class);
        when(plans.list("tenant-a", null))
            .thenReturn(
                List.of(
                    plan(readableId, "Readable", "owner-a", "dept-a"),
                    plan(hiddenId, "Hidden", "owner-b", "dept-b")
                )
            );
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("employee-a", "dept-a"));

        WarehousePlanOperationsReadAdapter adapter = new WarehousePlanOperationsReadAdapter(
            plans,
            actorProvider,
            new WarehousePlanAuthorizationGuard(mock(AdminDirectoryGateway.class)),
            "tenant-a"
        );

        assertThat(adapter.listPlans())
            .extracting(com.yuzhi.dts.platform.service.ops.WarehousePlanOperationsReadPort.WarehousePlanProjection::id)
            .containsExactly(readableId)
            .doesNotContain(hiddenId);
    }

    private static WarehousePlanHeader plan(UUID id, String name, String ownerId, String ownerDepartmentId) {
        return new WarehousePlanHeader(
            id,
            "tenant-a",
            "PLAN_" + id,
            name,
            "Objective",
            "Scope",
            ownerId,
            ownerDepartmentId,
            OnboardingMode.BUSINESS_FIRST,
            LifecycleStatus.DESIGNING,
            1
        );
    }

    private static void authenticate(String authority) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("actor", "n/a", authority));
    }
}
