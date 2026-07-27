package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryActorRole;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReleaseDutyResolverTest {

    @Test
    void mapsOnlyDedicatedReleaseAuthoritiesToDeliveryDuties() {
        assertThat(
            ReleaseDutyResolver.resolveAuthorities(
                List.of(
                    AuthoritiesConstants.MODEL_MAINTAINER,
                    AuthoritiesConstants.MODEL_RELEASE_REVIEWER,
                    AuthoritiesConstants.MODEL_RELEASE_OPERATOR
                )
            )
        )
            .containsExactlyInAnyOrder(
                DeliveryActorRole.MODEL_MAINTAINER,
                DeliveryActorRole.RELEASE_REVIEWER,
                DeliveryActorRole.RELEASE_OPERATOR
            );
    }

    @Test
    void doesNotPromoteLegacyMaintainersOrPlatformAdministrators() {
        assertThat(
            ReleaseDutyResolver.resolveAuthorities(
                List.of(
                    AuthoritiesConstants.ADMIN,
                    AuthoritiesConstants.AUTH_ADMIN,
                    AuthoritiesConstants.AUDITOR_ADMIN,
                    AuthoritiesConstants.OP_ADMIN,
                    AuthoritiesConstants.INST_DATA_OWNER,
                    AuthoritiesConstants.DEPT_DATA_OWNER
                )
            )
        )
            .isEmpty();
    }
}
