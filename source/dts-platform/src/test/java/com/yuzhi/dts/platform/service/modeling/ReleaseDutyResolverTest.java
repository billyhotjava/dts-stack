package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryActorRole;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReleaseDutyResolverTest {

    @Test
    void mapsInstituteDataOwnerToEveryReleaseDuty() {
        assertThat(
            ReleaseDutyResolver.resolveAuthorities(
                List.of(AuthoritiesConstants.INST_DATA_OWNER)
            )
        )
            .containsExactlyInAnyOrder(
                DeliveryActorRole.MODEL_MAINTAINER,
                DeliveryActorRole.RELEASE_REVIEWER,
                DeliveryActorRole.RELEASE_OPERATOR
            );
    }

    @Test
    void mapsDepartmentDataOwnerToScopedMaintainerAndSelfServiceReleaseOperator() {
        assertThat(
            ReleaseDutyResolver.resolveAuthorities(
                List.of(AuthoritiesConstants.DEPT_DATA_OWNER)
            )
        )
            .containsExactlyInAnyOrder(
                DeliveryActorRole.MODEL_MAINTAINER,
                DeliveryActorRole.RELEASE_OPERATOR
            );
    }

    @Test
    void instituteLeaderMapsToEveryReleaseDuty() {
        assertThat(
            ReleaseDutyResolver.resolveAuthorities(
                List.of(AuthoritiesConstants.INST_LEADER)
            )
        )
            .containsExactlyInAnyOrder(
                DeliveryActorRole.MODEL_MAINTAINER,
                DeliveryActorRole.RELEASE_REVIEWER,
                DeliveryActorRole.RELEASE_OPERATOR
            );
    }

    @Test
    void doesNotPromoteDepartmentLeadersOrUnrelatedPlatformRoles() {
        assertThat(
            ReleaseDutyResolver.resolveAuthorities(
                List.of(
                    AuthoritiesConstants.ADMIN,
                    AuthoritiesConstants.AUTH_ADMIN,
                    AuthoritiesConstants.AUDITOR_ADMIN,
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
