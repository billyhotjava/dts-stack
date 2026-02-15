package com.yuzhi.dts.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class QueryExecutionFacadeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void prepare_nativeSelect_allowsReadOnlyQuery() throws Exception {
        QueryExecutionFacade service = service();
        JsonNode datasetQuery = objectMapper.readTree("""
                {
                  "database": 1,
                  "type": "native",
                  "native": { "query": "select * from test_table where id = {{id}}" }
                }
                """);
        JsonNode requestBody = objectMapper.readTree("""
                {
                  "parameters": { "id": 42 }
                }
                """);

        QueryExecutionFacade.PreparedQuery prepared =
                service.prepare(datasetQuery, requestBody, null, DatasetQueryService.DatasetConstraints.defaults());

        assertThat(prepared.databaseId()).isEqualTo(1L);
        assertThat(prepared.type()).isEqualTo("native");
        assertThat(prepared.sql()).isEqualTo("select * from test_table where id = ?");
        assertThat(prepared.bindings()).containsExactly(42);
    }

    @Test
    void prepare_nativeUpdate_rejected() throws Exception {
        QueryExecutionFacade service = service();
        JsonNode datasetQuery = objectMapper.readTree("""
                {
                  "database": 1,
                  "type": "native",
                  "native": { "query": "update test_table set name = 'a'" }
                }
                """);

        assertThatThrownBy(() -> service.prepare(datasetQuery, objectMapper.createObjectNode(), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only SELECT/WITH read-only SQL is allowed");
    }

    @Test
    void prepare_nativeMultiStatements_rejected() throws Exception {
        QueryExecutionFacade service = service();
        JsonNode datasetQuery = objectMapper.readTree("""
                {
                  "database": 1,
                  "type": "native",
                  "native": { "query": "select 1; select 2" }
                }
                """);

        assertThatThrownBy(() -> service.prepare(datasetQuery, objectMapper.createObjectNode(), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Multiple SQL statements are not allowed");
    }

    private QueryExecutionFacade service() {
        NativeQueryTemplateService nativeQueryTemplateService = new NativeQueryTemplateService();
        return new QueryExecutionFacade(
                null,
                null,
                nativeQueryTemplateService,
                null);
    }
}
