package com.yuzhi.dts.copilot.analytics.domain;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Column;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AnalyticsReportTemplateMappingTest {

    @Test
    void shouldExposeFixedReportTemplateMetadataColumns() {
        Map<String, String> columnsByField = Arrays.stream(AnalyticsReportTemplate.class.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(Column.class))
                .collect(Collectors.toMap(Field::getName, field -> field.getAnnotation(Column.class).name()));

        assertThat(columnsByField).containsEntry("templateCode", "template_code");
        assertThat(columnsByField).containsEntry("domain", "domain");
        assertThat(columnsByField).containsEntry("category", "category");
        assertThat(columnsByField).containsEntry("dataSourceType", "data_source_type");
        assertThat(columnsByField).containsEntry("targetObject", "target_object");
        assertThat(columnsByField).containsEntry("refreshPolicy", "refresh_policy");
        assertThat(columnsByField).containsEntry("permissionPolicyJson", "permission_policy_json");
        assertThat(columnsByField).containsEntry("parameterSchemaJson", "parameter_schema_json");
        assertThat(columnsByField).containsEntry("metricDefinitionJson", "metric_definition_json");
        assertThat(columnsByField).containsEntry("presentationSchemaJson", "presentation_schema_json");
        assertThat(columnsByField).containsEntry("certificationStatus", "certification_status");
    }
}
