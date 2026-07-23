package com.yuzhi.dts.platform.service.modeling.warehouse;

import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.LifecycleStatus.DRAFT;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.OnboardingMode.BUSINESS_FIRST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.admin.gateway.directory.AdminDirectoryGateway;
import com.yuzhi.dts.platform.service.admin.gateway.directory.AdminDirectoryGateway.UserSummary;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider.WarehousePlanActor;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanApplicationService.WarehousePlanException;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.WarehousePlanHeader;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class WarehousePlanAuthorizationGuardTest {

    @Mock
    private AdminDirectoryGateway directoryGateway;

    private WarehousePlanAuthorizationGuard guard;

    @BeforeEach
    void setUp() {
        guard = new WarehousePlanAuthorizationGuard(directoryGateway);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void departmentMaintainerMayKeepOrTransferOwnershipOnlyInsideTheAuthenticatedDepartment() {
        authenticate(AuthoritiesConstants.DEPT_DATA_OWNER);
        WarehousePlanActor actor = new WarehousePlanActor("actor-1", "dept-a");
        WarehousePlanHeader current = plan("owner-1", "dept-a");

        assertThatCode(() -> guard.validateHeaderUpdate(current, "owner-1", "dept-a", actor)).doesNotThrowAnyException();
        verifyNoInteractions(directoryGateway);

        when(directoryGateway.findUserByPrincipalKey("owner-2"))
            .thenReturn(Optional.of(new UserSummary("owner-2", "bob", "Bob", "dept-a", "A")));
        assertThatCode(() -> guard.validateHeaderUpdate(current, "owner-2", "dept-a", actor)).doesNotThrowAnyException();

        when(directoryGateway.findUserByPrincipalKey("owner-3"))
            .thenReturn(Optional.of(new UserSummary("owner-3", "carol", "Carol", "dept-b", "B")));
        assertThatThrownBy(() -> guard.validateHeaderUpdate(current, "owner-3", "dept-b", actor))
            .isInstanceOfSatisfying(
                WarehousePlanException.class,
                error -> org.assertj.core.api.Assertions.assertThat(error.code()).isEqualTo("WAREHOUSE_PLAN_OWNER_DEPARTMENT_FORBIDDEN")
            );
    }

    @Test
    void unchangedOwnerWithoutDepartmentDoesNotRequireDirectoryLookup() {
        authenticate(AuthoritiesConstants.INST_DATA_OWNER);
        WarehousePlanActor actor = new WarehousePlanActor("actor-1", null);
        WarehousePlanHeader current = plan("owner-1", null);

        assertThatCode(() -> guard.validateHeaderUpdate(current, "owner-1", null, actor)).doesNotThrowAnyException();
        verifyNoInteractions(directoryGateway);
    }

    @Test
    void fakeOwnerAndCrossDepartmentPlanMaintenanceAreRejected() {
        authenticate(AuthoritiesConstants.DEPT_LEADER);
        WarehousePlanActor actor = new WarehousePlanActor("actor-1", "dept-a");
        when(directoryGateway.findUserByPrincipalKey("missing-owner")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.validateHeaderUpdate(plan("owner-1", "dept-a"), "missing-owner", "dept-a", actor))
            .isInstanceOfSatisfying(
                WarehousePlanException.class,
                error -> org.assertj.core.api.Assertions.assertThat(error.code()).isEqualTo("WAREHOUSE_PLAN_OWNER_FORBIDDEN")
            );
        assertThatThrownBy(() -> guard.requirePlanMaintenance(plan("owner-2", "dept-b"), actor))
            .isInstanceOfSatisfying(
                WarehousePlanException.class,
                error -> org.assertj.core.api.Assertions.assertThat(error.code()).isEqualTo("WAREHOUSE_PLAN_OWNER_DEPARTMENT_FORBIDDEN")
            );

        assertThatThrownBy(() -> guard.requirePlanMaintenance(plan("owner-3", "dept-ba"), actor))
            .isInstanceOfSatisfying(
                WarehousePlanException.class,
                error -> org.assertj.core.api.Assertions.assertThat(error.code()).isEqualTo("WAREHOUSE_PLAN_OWNER_DEPARTMENT_FORBIDDEN")
            );
    }

    @Test
    void departmentSuffixCollisionCannotAuthorizeOwnershipTransfer() {
        authenticate(AuthoritiesConstants.DEPT_DATA_OWNER);
        WarehousePlanActor actor = new WarehousePlanActor("actor-1", "dept-a");
        WarehousePlanHeader current = plan("owner-1", "dept-a");
        when(directoryGateway.findUserByPrincipalKey("owner-ba"))
            .thenReturn(Optional.of(new UserSummary("owner-ba", "ba", "BA", "dept-ba", "BA")));

        assertThatThrownBy(() -> guard.validateHeaderUpdate(current, "owner-ba", "dept-ba", actor))
            .isInstanceOfSatisfying(
                WarehousePlanException.class,
                error -> org.assertj.core.api.Assertions.assertThat(error.code()).isEqualTo("WAREHOUSE_PLAN_OWNER_DEPARTMENT_FORBIDDEN")
            );
    }

    @Test
    void instituteMaintainerMayTransferOnlyToARealDirectoryIdentityWithMatchingDepartment() {
        authenticate(AuthoritiesConstants.INST_DATA_OWNER);
        WarehousePlanActor actor = new WarehousePlanActor("actor-1", "dept-a");
        WarehousePlanHeader current = plan("owner-1", "dept-a");
        when(directoryGateway.findUserByPrincipalKey("owner-3"))
            .thenReturn(Optional.of(new UserSummary("owner-3", "carol", "Carol", "dept-b", "B")));

        assertThatCode(() -> guard.validateHeaderUpdate(current, "owner-3", "dept-b", actor)).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.validateHeaderUpdate(current, "owner-3", "forged-dept", actor))
            .isInstanceOfSatisfying(
                WarehousePlanException.class,
                error -> org.assertj.core.api.Assertions.assertThat(error.code()).isEqualTo("WAREHOUSE_PLAN_OWNER_DEPARTMENT_FORBIDDEN")
            );
    }

    @Test
    void planReadsAreLimitedToInstituteScopeOwnerOrTheExactAuthenticatedDepartment() {
        WarehousePlanHeader departmentPlan = plan("owner-1", "dept-a");
        WarehousePlanActor departmentActor = new WarehousePlanActor("employee-1", "dept-a");

        authenticate(AuthoritiesConstants.EMPLOYEE);
        assertThat(guard.canReadPlan(departmentPlan, departmentActor)).isTrue();
        assertThat(guard.canReadPlan(plan("owner-2", "dept-ba"), departmentActor)).isFalse();
        assertThat(guard.canReadPlan(plan("owner-3", "10010"), new WarehousePlanActor("employee-1", "10"))).isFalse();

        authenticate(AuthoritiesConstants.USER);
        assertThat(guard.canReadPlan(departmentPlan, new WarehousePlanActor("owner-1", "another-department"))).isTrue();
        assertThat(guard.canReadPlan(departmentPlan, new WarehousePlanActor("employee-1", "dept-a"))).isFalse();

        authenticate(AuthoritiesConstants.INST_LEADER);
        assertThat(guard.canReadPlan(plan("owner-2", "dept-b"), departmentActor)).isTrue();

        SecurityContextHolder.clearContext();
        assertThat(guard.canReadPlan(departmentPlan, departmentActor)).isFalse();
        assertThatThrownBy(() -> guard.requirePlanRead(departmentPlan, departmentActor))
            .isInstanceOfSatisfying(WarehousePlanException.class, error ->
                assertThat(error.code()).isEqualTo("WAREHOUSE_PLAN_NOT_FOUND")
            );
    }

    private static void authenticate(String authority) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("actor", "n/a", authority));
    }

    private static WarehousePlanHeader plan(String ownerId, String departmentId) {
        return new WarehousePlanHeader(
            UUID.fromString("10000000-0000-0000-0000-000000000001"),
            "server-tenant",
            "PLAN_1",
            "Plan",
            "Objective",
            "Scope",
            ownerId,
            departmentId,
            BUSINESS_FIRST,
            DRAFT,
            1
        );
    }
}
