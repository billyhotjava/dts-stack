package com.yuzhi.dts.copilot.analytics.web.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class AnalysisDraftResourceContractTest {

    @Test
    void shouldExposeDraftEndpointsUnderApiAnalysisDrafts() throws Exception {
        Class<?> resourceClass =
                Class.forName("com.yuzhi.dts.copilot.analytics.web.rest.AnalysisDraftResource");

        RequestMapping root = resourceClass.getAnnotation(RequestMapping.class);
        assertThat(root).isNotNull();
        assertThat(root.value()).containsExactly("/api/analysis-drafts");

        Map<String, Method> methods = Arrays.stream(resourceClass.getDeclaredMethods())
                .collect(Collectors.toMap(Method::getName, method -> method));

        assertThat(methods.keySet()).contains("list", "create", "get", "archive", "delete", "run", "saveCard");

        assertThat(methods.get("list").getAnnotation(GetMapping.class)).isNotNull();
        assertThat(methods.get("create").getAnnotation(PostMapping.class)).isNotNull();
        assertThat(methods.get("get").getAnnotation(GetMapping.class)).isNotNull();
        assertThat(methods.get("archive").getAnnotation(PostMapping.class)).isNotNull();
        assertThat(methods.get("delete").getAnnotation(DeleteMapping.class)).isNotNull();
        assertThat(methods.get("run").getAnnotation(PostMapping.class)).isNotNull();
        assertThat(methods.get("saveCard").getAnnotation(PostMapping.class)).isNotNull();
    }
}
