package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class LegacyEtlMutationAuthorizationTest {

    @Test
    void legacyEtlControlActionsRequireInfrastructureMaintainerAuthority() {
        assertInfraMaintainer(EtlResource.class, "updateDbtConfig");
        assertInfraMaintainer(EtlResource.class, "refreshDbtSources");
        assertInfraMaintainer(EtlResource.class, "triggerAirflowJob");

    }

    @Test
    void legacyDbtSyncAcceptsMaintainersOrTheAirflowServiceOnly() {
        PreAuthorize authorization = authorization(
            EtlResource.class,
            "syncDbtModels"
        );

        assertThat(authorization.value())
            .contains("INFRA_MAINTAINERS")
            .contains("SERVICE_INTERNAL")
            .contains("service:dts-airflow");
    }

    @Test
    void legacyReadRoutesWithDagWriteSideEffectsRequireInfrastructureMaintainerAuthority() {
        assertInfraMaintainer(EtlResource.class, "getDbtSyncStatus");
        assertInfraMaintainer(EtlResource.class, "listDbtRuns");
    }

    private static void assertInfraMaintainer(
        Class<?> resourceType,
        String methodName
    ) {
        assertThat(authorization(resourceType, methodName).value())
            .contains("INFRA_MAINTAINERS");
    }

    private static PreAuthorize authorization(
        Class<?> resourceType,
        String methodName
    ) {
        Method method = Arrays
            .stream(resourceType.getDeclaredMethods())
            .filter(candidate -> candidate.getName().equals(methodName))
            .findFirst()
            .orElseThrow();
        PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);
        assertThat(authorization).isNotNull();
        return authorization;
    }
}
