package com.yuzhi.dts.platform.service.modeling.warehouse;

import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.LifecycleStatus.DRAFT;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.OnboardingMode.BUSINESS_FIRST;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.modeling.*;
import com.yuzhi.dts.platform.service.admin.gateway.directory.AdminDirectoryGateway;
import com.yuzhi.dts.platform.service.admin.gateway.directory.AdminDirectoryGateway.ModelingUser;
import com.yuzhi.dts.platform.service.modeling.ModelSpecException;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider.WarehousePlanActor;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.WarehousePlanHeader;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WarehousePlanAuthorizationGuardTest {
    private final AdminDirectoryGateway directory = mock(AdminDirectoryGateway.class);
    private final WarehousePlanAuthorizationGuard guard = new WarehousePlanAuthorizationGuard(directory);
    private final ModelingIdentityService identities = new ModelingIdentityService(directory);
    private final WarehousePlanActor actor = new WarehousePlanActor("actor-1", "dept-a");
    private void as(String role, Runnable action) {
        identities.withIdentity(new ModelingUser("actor-1", "alice", "Alice", "dept-a", "甲", List.of(role), true, "GENERAL"), () -> { action.run(); return null; });
    }
    @Test void publicLayerIsJointlyMaintainedButOwnerAndDepartmentAreImmutable() {
        as(AuthoritiesConstants.DEPT_DATA_OWNER, () -> {
            assertThatCode(() -> guard.validateHeaderUpdate(plan("owner-1", "dept-a"), "owner-1", "dept-a", actor)).doesNotThrowAnyException();
            assertThatThrownBy(() -> guard.validateHeaderUpdate(plan("owner-1", "dept-a"), "owner-2", "dept-a", actor)).isInstanceOf(ModelSpecException.class);
            assertThatThrownBy(() -> guard.validateHeaderUpdate(plan("owner-1", "dept-a"), "owner-1", "dept-b", actor)).isInstanceOf(ModelSpecException.class);
        });
        verifyNoInteractions(directory);
    }
    @Test void exactDepartmentMatchingRejectsSuffixAncestorAndOtherDepartment() {
        as(AuthoritiesConstants.DEPT_LEADER, () -> {
            for (String department : List.of("dept-b", "dept-ba", "dept-a/child", "dept")) assertThat(guard.canReadPlan(plan("other", department), actor)).isFalse();
        });
    }
    @Test void instituteRoleMayReadAnotherValidDepartmentButCannotReassignIt() {
        as(AuthoritiesConstants.INST_DATA_OWNER, () -> {
            assertThat(guard.canReadPlan(plan("other", "dept-b"), actor)).isTrue();
            assertThatThrownBy(() -> guard.validateHeaderUpdate(plan("other", "dept-b"), "other", "dept-c", actor)).isInstanceOf(ModelSpecException.class);
        });
    }
    @Test void missingDepartmentIsNeverADefaultWritableContext() {
        as(AuthoritiesConstants.INST_DATA_OWNER, () -> assertThat(guard.canReadPlan(plan("owner", null), actor)).isFalse());
    }
    @Test void employeeMenuGrantDoesNotAuthorizeModeling() {
        as(AuthoritiesConstants.EMPLOYEE, () -> assertThat(guard.canReadPlan(plan("owner", "dept-a"), actor)).isFalse());
    }
    @Test void forgedActorAndMissingCurrentIdentityAreRejected() {
        as(AuthoritiesConstants.DEPT_DATA_OWNER, () -> assertThat(guard.canReadPlan(plan("owner", "dept-a"), new WarehousePlanActor("forged", "dept-a"))).isFalse());
        assertThatThrownBy(() -> guard.canReadPlan(plan("owner", "dept-a"), actor)).isInstanceOf(ModelingIdentityException.class);
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
