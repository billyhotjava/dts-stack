package com.yuzhi.dts.platform.web.rest.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class CatalogSprint72OpAdminAuthorizationTest {

    @Test
    void platformOperatorCanMaintainClassificationLifecycleAndMigration() {
        assertMethodAllowsOpAdmin(CatalogGovernanceWorkbenchResource.class, "lifecycleMetrics");
        assertMethodAllowsOpAdmin(CatalogGovernanceWorkbenchResource.class, "issues");
        assertMethodAllowsOpAdmin(CatalogLifecycleGovernanceResource.class, "submit");
        assertMethodAllowsOpAdmin(CatalogClassificationResource.class, "seal");
        assertMethodAllowsOpAdmin(CatalogConsumerClassificationResource.class, "derive");

        PreAuthorize migrationAuthorization =
            CatalogClassificationMigrationResource.class.getAnnotation(PreAuthorize.class);
        assertThat(migrationAuthorization).isNotNull();
        assertThat(migrationAuthorization.value()).contains("'ROLE_OP_ADMIN'");
    }

    private static void assertMethodAllowsOpAdmin(Class<?> resourceType, String methodName) {
        Method method = java.util.Arrays
            .stream(resourceType.getDeclaredMethods())
            .filter(candidate -> candidate.getName().equals(methodName))
            .findFirst()
            .orElseThrow();
        PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);
        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).contains("'ROLE_OP_ADMIN'");
    }
}
