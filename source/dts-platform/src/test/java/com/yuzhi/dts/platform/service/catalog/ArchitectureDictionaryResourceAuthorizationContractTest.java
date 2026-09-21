package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.web.rest.DataMartResource;
import com.yuzhi.dts.platform.web.rest.ModelingBusinessProcessResource;
import com.yuzhi.dts.platform.web.rest.Sprint64GovernanceResource;
import com.yuzhi.dts.platform.web.rest.SubjectDomainResource;
import com.yuzhi.dts.platform.web.rest.WarehouseLayerResource;
import com.yuzhi.dts.platform.web.rest.catalog.CatalogDomainResource;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class ArchitectureDictionaryResourceAuthorizationContractTest {

    @Test
    void everyNewAndCompatibilityWriteEndpointUsesTheApprovedExactAllowlist() {
        Map<Class<?>, List<String>> writes = Map.of(
            CatalogDomainResource.class,
            List.of("createDomain", "updateDomain", "deleteDomain", "moveDomain"),
            WarehouseLayerResource.class,
            List.of("create", "delete"),
            DataMartResource.class,
            List.of("create", "update", "confirm", "retire"),
            SubjectDomainResource.class,
            List.of("create", "update", "confirm", "retire"),
            ModelingBusinessProcessResource.class,
            List.of("create", "update", "delete"),
            Sprint64GovernanceResource.class,
            List.of("createProcess", "deleteProcess")
        );

        writes.forEach((resource, methods) ->
            methods.forEach(methodName ->
                assertThat(method(resource, methodName).getAnnotation(PreAuthorize.class))
                    .as(resource.getSimpleName() + "." + methodName)
                    .isNotNull()
                    .extracting(PreAuthorize::value)
                    .isEqualTo(ArchitectureDictionaryWriteGuard.WRITE_EXPRESSION)
            )
        );
    }

    private static Method method(Class<?> type, String name) {
        return java.util.Arrays
            .stream(type.getDeclaredMethods())
            .filter(candidate -> candidate.getName().equals(name))
            .findFirst()
            .orElseThrow();
    }
}
