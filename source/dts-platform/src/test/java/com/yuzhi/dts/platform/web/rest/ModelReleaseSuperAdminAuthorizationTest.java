package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class ModelReleaseSuperAdminAuthorizationTest {

    @Test
    void dataAdministratorsBelongToScopedSelfServiceReleaseDuties() {
        assertThat(Arrays.asList(AuthoritiesConstants.MODEL_RELEASE_DUTIES))
            .contains(
                AuthoritiesConstants.INST_DATA_OWNER,
                AuthoritiesConstants.DEPT_DATA_OWNER,
                AuthoritiesConstants.OP_ADMIN
            );
        assertThat(Arrays.asList(AuthoritiesConstants.MODEL_MAINTAINERS))
            .contains(
                AuthoritiesConstants.INST_DATA_OWNER,
                AuthoritiesConstants.DEPT_DATA_OWNER,
                AuthoritiesConstants.INST_LEADER,
                AuthoritiesConstants.OP_ADMIN
            );
        assertThat(Arrays.asList(AuthoritiesConstants.MODEL_RELEASE_OPERATORS))
            .contains(
                AuthoritiesConstants.INST_DATA_OWNER,
                AuthoritiesConstants.DEPT_DATA_OWNER,
                AuthoritiesConstants.OP_ADMIN
            );
    }

    @Test
    void opAdminCanStartBuildAndPublicationIntents() throws Exception {
        assertUsesAuthorityGroup(
            ModelBuildIntentResource.class.getDeclaredMethod(
                "start",
                UUID.class,
                String.class,
                String.class,
                JsonNode.class
            ),
            "MODEL_MAINTAINERS"
        );
        assertUsesAuthorityGroup(
            ModelPublicationIntentResource.class.getDeclaredMethod(
                "start",
                UUID.class,
                String.class,
                String.class,
                JsonNode.class
            ),
            "MODEL_MAINTAINERS"
        );
    }

    @Test
    void opAdminCanRepairAndRunExecutionBindings() throws Exception {
        assertUsesAuthorityGroup(
            PlanExecutionBindingResource.class.getDeclaredMethod(
                "repair",
                UUID.class,
                UUID.class,
                String.class
            ),
            "MODEL_RELEASE_OPERATORS"
        );
        assertUsesAuthorityGroup(
            PlanExecutionBindingResource.class.getDeclaredMethod(
                "runNow",
                UUID.class,
                UUID.class,
                String.class
            ),
            "MODEL_RELEASE_OPERATORS"
        );
    }

    private static void assertUsesAuthorityGroup(
        Method method,
        String authorityGroup
    ) {
        PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);
        assertThat(authorization).isNotNull();
        assertThat(authorization.value())
            .contains("AuthoritiesConstants")
            .contains(authorityGroup);
    }
}
