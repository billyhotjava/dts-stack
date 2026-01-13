package com.yuzhi.dts.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.domain.AnalyticsField;
import com.yuzhi.dts.analytics.domain.AnalyticsTable;
import com.yuzhi.dts.analytics.repository.AnalyticsFieldRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsTableRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MbqlToSqlServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void translateSelect_rendersFilterWithBindings() throws Exception {
        AnalyticsTableRepository tableRepository = mock(AnalyticsTableRepository.class);
        AnalyticsFieldRepository fieldRepository = mock(AnalyticsFieldRepository.class);

        AnalyticsTable table = new AnalyticsTable();
        table.setId(10L);
        table.setDatabaseId(99L);
        table.setSchemaName("public");
        table.setName("orders");
        when(tableRepository.findById(10L)).thenReturn(Optional.of(table));

        AnalyticsField id = new AnalyticsField();
        id.setId(100L);
        id.setTableId(10L);
        id.setDatabaseId(99L);
        id.setName("id");

        AnalyticsField status = new AnalyticsField();
        status.setId(101L);
        status.setTableId(10L);
        status.setDatabaseId(99L);
        status.setName("status");

        when(fieldRepository.findAllByTableIdOrderByPositionAscIdAsc(10L)).thenReturn(List.of(id, status));

        MbqlToSqlService service = new MbqlToSqlService(tableRepository, fieldRepository);

        JsonNode mbql = objectMapper.readTree(
                """
                {
                  "source-table": 10,
                  "fields": [["field", 100], ["field", 101]],
                  "filter": ["and",
                    ["=", ["field", 100], 1],
                    ["in", ["field", 101], ["new", null]],
                    ["not-null", ["field", 101]]
                  ],
                  "limit": 50
                }
                """);

        MbqlToSqlService.TranslationResult result =
                service.translateSelect(99L, mbql, new DatasetQueryService.DatasetConstraints(2000, 60, "UTC"));

        assertThat(result.sql())
                .contains("SELECT \"id\", \"status\" FROM \"public\".\"orders\" WHERE")
                .contains("\"id\" = ?")
                .contains("\"status\" IN (?)")
                .contains("\"status\" IS NULL")
                .contains("\"status\" IS NOT NULL")
                .contains("LIMIT 50");

        assertThat(result.bindings()).containsExactly(1L, "new");
    }

    @Test
    void translateSelect_rejectsUnsupportedKeys() throws Exception {
        AnalyticsTableRepository tableRepository = mock(AnalyticsTableRepository.class);
        AnalyticsFieldRepository fieldRepository = mock(AnalyticsFieldRepository.class);
        MbqlToSqlService service = new MbqlToSqlService(tableRepository, fieldRepository);

        JsonNode mbql = objectMapper.readTree(
                """
                { "source-table": 10, "aggregation": [["count"]] }
                """);

        assertThatThrownBy(() -> service.translateSelect(1L, mbql, DatasetQueryService.DatasetConstraints.defaults()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MBQL query key is not supported yet");
    }
}

