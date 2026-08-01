package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;

class EtlResourceLegacyDbtRouteRetirementTest {

    private static final Set<String> RETIRED_ROUTES = Set.of(
        "/dbt/output",
        "/dbt/output/truncate",
        "/dbt/output/rebuild",
        "/dbt/run",
        "/dbt/compile",
        "/dbt/test",
        "/dbt/docs",
        "/dbt/quality-gate/check",
        "/dbt/release-gate/check",
        "/dbt/release/submit"
    );

    @Test
    void retiredDirectDbtExecutionRoutesAreNotRegistered() {
        java.util.List<String> registeredRetiredRoutes = Arrays
            .stream(EtlResource.class.getDeclaredMethods())
            .map(method -> AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class))
            .filter(java.util.Objects::nonNull)
            .flatMap(mapping -> Stream.concat(Arrays.stream(mapping.path()), Arrays.stream(mapping.value())))
            .filter(RETIRED_ROUTES::contains)
            .sorted()
            .toList();

        assertThat(registeredRetiredRoutes).isEmpty();
    }

    @Test
    void controllerDoesNotDependOnRetiredDbtExecutionServices() {
        Set<String> retiredDependencies = Set.of(
            "DbtOutputRelationService",
            "DbtQualityGateService",
            "DbtReleaseGateService",
            "DbtReleaseSubmissionService",
            "DbtScopedProjectService",
            "ModelingSqlModelRepository"
        );
        java.util.List<String> constructorDependencies = Arrays
            .stream(EtlResource.class.getDeclaredConstructors())
            .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
            .map(Class::getSimpleName)
            .filter(retiredDependencies::contains)
            .sorted()
            .toList();

        assertThat(constructorDependencies).isEmpty();
    }
}
