package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class ModelingVNextResourceContractTest {

    private static final String MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).CATALOG_MAINTAINERS)";

    @Test
    void mutatingModelingEndpointsRequireCatalogMaintainer() {
        Set<String> mutators = Set.of(
            "createBusinessObject",
            "updateBusinessObject",
            "createPlan",
            "updatePlan",
            "createModelSpec",
            "updateModelSpec",
            "importDbt",
            "compile",
            "createRun",
            "callbackRun"
        );

        for (Method method : ModelingVNextResource.class.getDeclaredMethods()) {
            if (!mutators.contains(method.getName())) continue;
            PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);
            assertThat(authorization)
                .as("%s must carry a maintainer gate", method.getName())
                .isNotNull();
            assertThat(authorization.value()).isEqualTo(MAINTAINER_EXPRESSION);
        }
    }

    @Test
    void readOnlyLedgerEndpointsRemainAvailableForConsumers() {
        Set<String> readers = Set.of("listBusinessObjects", "listPlans", "listModelSpecs", "artifacts", "drift", "releaseGate", "getRun", "lineage");

        for (Method method : ModelingVNextResource.class.getDeclaredMethods()) {
            if (!readers.contains(method.getName())) continue;
            assertThat(method.getAnnotation(PreAuthorize.class)).as("%s should not add a write gate", method.getName()).isNull();
        }
    }
}
