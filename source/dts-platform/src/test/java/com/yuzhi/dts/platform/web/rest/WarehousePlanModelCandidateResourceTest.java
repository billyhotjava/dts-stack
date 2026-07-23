package com.yuzhi.dts.platform.web.rest;

import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.LifecycleStatus.DRAFT;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.OnboardingMode.BUSINESS_FIRST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider.WarehousePlanActor;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanApplicationService;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanApplicationService.WarehousePlanException;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanAuthorizationGuard;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.WarehousePlanHeader;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanModelCandidateService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class WarehousePlanModelCandidateResourceTest {

    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final WarehousePlanActor ACTOR = new WarehousePlanActor("owner-1", "department-1");

    @Mock
    private WarehousePlanModelCandidateService candidateService;

    @Mock
    private WarehousePlanActorProvider actorProvider;

    @Mock
    private WarehousePlanApplicationService planService;

    @Mock
    private WarehousePlanAuthorizationGuard authorizationGuard;

    private WarehousePlanModelCandidateResource resource;

    @BeforeEach
    void setUp() {
        resource = new WarehousePlanModelCandidateResource(
            candidateService,
            actorProvider,
            planService,
            authorizationGuard,
            "server-tenant"
        );
    }

    @Test
    void appliesReadAuthorizationToPreviewAndMaintenanceAuthorizationToConfirmation() {
        WarehousePlanHeader plan = planHeader();
        WarehousePlanException notFound = new WarehousePlanException(
            "WAREHOUSE_PLAN_NOT_FOUND",
            "Warehouse plan not found",
            null
        );
        WarehousePlanException forbidden = new WarehousePlanException(
            "WAREHOUSE_PLAN_OWNER_DEPARTMENT_FORBIDDEN",
            "The authenticated actor cannot maintain this warehouse plan",
            null
        );
        when(actorProvider.currentActor()).thenReturn(ACTOR);
        when(planService.get("server-tenant", PLAN_ID)).thenReturn(plan);
        doThrow(notFound).when(authorizationGuard).requirePlanRead(plan, ACTOR);
        doThrow(forbidden).when(authorizationGuard).requirePlanMaintenance(plan, ACTOR);

        assertThatThrownBy(() -> resource.preview(PLAN_ID)).isSameAs(notFound);
        assertThatThrownBy(() -> resource.confirm(PLAN_ID, null)).isSameAs(forbidden);

        verify(planService, org.mockito.Mockito.times(2)).get("server-tenant", PLAN_ID);
        verify(authorizationGuard).requirePlanRead(plan, ACTOR);
        verify(authorizationGuard).requirePlanMaintenance(plan, ACTOR);
        verifyNoInteractions(candidateService);
    }

    @Test
    void mapsMaskedPlanReadFailuresToNotFound() {
        WarehousePlanException notFound = new WarehousePlanException(
            "WAREHOUSE_PLAN_NOT_FOUND",
            "Warehouse plan not found",
            null
        );

        var response = resource.handleWarehousePlanError(notFound);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("WAREHOUSE_PLAN_NOT_FOUND");

        var forbiddenResponse = resource.handleWarehousePlanError(
            new WarehousePlanException(
                "WAREHOUSE_PLAN_OWNER_DEPARTMENT_FORBIDDEN",
                "The authenticated actor cannot maintain this warehouse plan",
                null
            )
        );
        assertThat(forbiddenResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private static WarehousePlanHeader planHeader() {
        return new WarehousePlanHeader(
            PLAN_ID,
            "server-tenant",
            "warehouse-neutral",
            "Neutral warehouse",
            "Trusted metrics",
            "Initial scope",
            "owner-1",
            "department-1",
            BUSINESS_FIRST,
            DRAFT,
            1
        );
    }
}
