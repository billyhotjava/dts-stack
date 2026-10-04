package com.yuzhi.dts.copilot.analytics.domain;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Column;
import jakarta.persistence.Table;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AnalyticsAnalysisDraftMappingTest {

    @Test
    void shouldExposeAnalysisDraftColumns() throws Exception {
        Class<?> entityClass =
                Class.forName("com.yuzhi.dts.copilot.analytics.domain.AnalyticsAnalysisDraft");
        Table table = entityClass.getAnnotation(Table.class);
        assertThat(table).isNotNull();
        assertThat(table.name()).isEqualTo("analysis_draft");

        Map<String, String> columnsByField = Arrays.stream(entityClass.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(Column.class))
                .collect(Collectors.toMap(Field::getName, field -> field.getAnnotation(Column.class).name()));

        assertThat(columnsByField).containsEntry("entityId", "entity_id");
        assertThat(columnsByField).containsEntry("title", "title");
        assertThat(columnsByField).containsEntry("sourceType", "source_type");
        assertThat(columnsByField).containsEntry("sessionId", "session_id");
        assertThat(columnsByField).containsEntry("messageId", "message_id");
        assertThat(columnsByField).containsEntry("question", "question");
        assertThat(columnsByField).containsEntry("databaseId", "database_id");
        assertThat(columnsByField).containsEntry("sqlText", "sql_text");
        assertThat(columnsByField).containsEntry("explanationText", "explanation_text");
        assertThat(columnsByField).containsEntry("suggestedDisplay", "suggested_display");
        assertThat(columnsByField).containsEntry("responseKind", "response_kind");
        assertThat(columnsByField).containsEntry("dataSurface", "data_surface");
        assertThat(columnsByField).containsEntry("qualityLevel", "quality_level");
        assertThat(columnsByField).containsEntry("qualityNotes", "quality_notes");
        assertThat(columnsByField).containsEntry("reportCode", "report_code");
        assertThat(columnsByField).containsEntry("status", "status");
        assertThat(columnsByField).containsEntry("linkedCardId", "linked_card_id");
        assertThat(columnsByField).containsEntry("linkedDashboardId", "linked_dashboard_id");
        assertThat(columnsByField).containsEntry("linkedScreenId", "linked_screen_id");
        assertThat(columnsByField).containsEntry("creatorId", "creator_id");
    }
}
