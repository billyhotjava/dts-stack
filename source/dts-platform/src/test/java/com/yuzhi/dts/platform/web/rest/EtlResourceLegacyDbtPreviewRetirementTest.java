package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;

class EtlResourceLegacyDbtPreviewRetirementTest {

    @Test
    void legacyRawDbtPreviewRouteIsNotRegistered() {
        assertThat(
            Arrays
                .stream(EtlResource.class.getDeclaredMethods())
                .map(method -> AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class))
                .filter(Objects::nonNull)
                .flatMap(mapping -> Stream.concat(Arrays.stream(mapping.path()), Arrays.stream(mapping.value())))
        )
            .doesNotContain("/dbt/preview");
    }

    @Test
    void controllerDoesNotDependOnTheRawPreviewService() {
        assertThat(
            Arrays
                .stream(EtlResource.class.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .map(Class::getSimpleName)
        )
            .doesNotContain("DbtPreviewService");
    }

    @Test
    void sharedDbtGitControlPlaneIsPhysicallyRetired() {
        assertThatThrownBy(() ->
            Class.forName("com.yuzhi.dts.platform.web.rest.DbtGitResource")
        )
            .isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() ->
            Class.forName("com.yuzhi.dts.platform.service.etl.DbtGitService")
        )
            .isInstanceOf(ClassNotFoundException.class);
    }
}
