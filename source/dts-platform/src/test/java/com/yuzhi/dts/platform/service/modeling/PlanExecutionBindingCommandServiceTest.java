package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.modeling.PlanExecutionBindingRepository;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryActorRole;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlanExecutionBindingCommandServiceTest {

    private static final Instant NOW =
        Instant.parse("2026-07-28T09:00:00Z");
    private static final UUID PLAN_ID =
        UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID BINDING_ID =
        UUID.fromString("20000000-0000-0000-0000-000000000002");

    @Test
    void operatorRepairUsesTheExpectedBindingVersion() {
        Fixture fixture = fixture(
            Set.of(DeliveryActorRole.RELEASE_OPERATOR),
            true
        );
        when(
            fixture.bindings.requestRedeployment(
                "tenant-a",
                PLAN_ID,
                BINDING_ID,
                4,
                "operator-a",
                NOW
            )
        ).thenReturn(true);

        var result = fixture.service.repair(
            "tenant-a",
            "operator-a",
            PLAN_ID,
            BINDING_ID,
            4
        );

        assertThat(result.deploymentStatus()).isEqualTo("DEPLOYING");
        assertThat(result.bindingVersion()).isEqualTo(4);
    }

    @Test
    void clientSuppliedRoleCannotBypassServerDuty() {
        Fixture fixture = fixture(
            Set.of(DeliveryActorRole.MODEL_MAINTAINER),
            true
        );

        assertThatThrownBy(() ->
            fixture.service.repair(
                "tenant-a",
                "operator-a",
                PLAN_ID,
                BINDING_ID,
                4
            )
        )
            .isInstanceOf(PlanExecutionException.class)
            .extracting(error ->
                ((PlanExecutionException) error).code()
            )
            .isEqualTo("MODEL_PLAN_EXECUTION_REPAIR_FORBIDDEN");
        verify(fixture.bindings, never())
            .requestRedeployment(
                "tenant-a",
                PLAN_ID,
                BINDING_ID,
                4,
                "operator-a",
                NOW
            );
    }

    private static Fixture fixture(
        Set<DeliveryActorRole> duties,
        boolean planAccess
    ) {
        PlanExecutionBindingRepository bindings =
            mock(PlanExecutionBindingRepository.class);
        ModelSpecPlanWriteAccessPort access =
            mock(ModelSpecPlanWriteAccessPort.class);
        when(access.canMaintain("tenant-a", PLAN_ID, "operator-a"))
            .thenReturn(planAccess);
        ReleaseDutyResolver resolver = mock(ReleaseDutyResolver.class);
        when(resolver.currentDuties()).thenReturn(duties);
        return new Fixture(
            bindings,
            new PlanExecutionBindingCommandService(
                bindings,
                access,
                resolver,
                Clock.fixed(NOW, ZoneOffset.UTC)
            )
        );
    }

    private record Fixture(
        PlanExecutionBindingRepository bindings,
        PlanExecutionBindingCommandService service
    ) {}
}
