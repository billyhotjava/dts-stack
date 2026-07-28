package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryActorRole;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReleaseDutyResolverTest {

    @Test
    void mapsInstituteDataOwnerToModelMaintainer() {
        assertThat(
            ReleaseDutyResolver.resolveAuthorities(
                List.of(AuthoritiesConstants.INST_DATA_OWNER)
            )
        )
            .containsExactly(DeliveryActorRole.MODEL_MAINTAINER);
    }

    @Test
    void instituteLeaderInheritsMaintainerAndReviewerDuties() {
        assertThat(
            ReleaseDutyResolver.resolveAuthorities(
                List.of(AuthoritiesConstants.INST_LEADER)
            )
        )
            .containsExactlyInAnyOrder(
                DeliveryActorRole.MODEL_MAINTAINER,
                DeliveryActorRole.RELEASE_REVIEWER
            );
    }

    @Test
    void doesNotPromoteDepartmentOrUnrelatedPlatformRoles() {
        assertThat(
            ReleaseDutyResolver.resolveAuthorities(
                List.of(
                    AuthoritiesConstants.ADMIN,
                    AuthoritiesConstants.AUTH_ADMIN,
                    AuthoritiesConstants.AUDITOR_ADMIN,
                    AuthoritiesConstants.DEPT_DATA_OWNER,
                    AuthoritiesConstants.DEPT_LEADER
                )
            )
        )
            .isEmpty();
    }

    @Test
    void promotesOpAdminToAllReleaseDuties() {
        assertThat(
            ReleaseDutyResolver.resolveAuthorities(
                List.of(AuthoritiesConstants.OP_ADMIN)
            )
        )
            .containsExactlyInAnyOrder(
                DeliveryActorRole.MODEL_MAINTAINER,
                DeliveryActorRole.RELEASE_REVIEWER,
                DeliveryActorRole.RELEASE_OPERATOR
            );
    }
}
