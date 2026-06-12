package com.yuzhi.dts.ingestion.service.etl.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class JsonPathLiteTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void read_shouldSupportArrayIndexInsideDotPath() throws Exception {
        assertThat(JsonPathLite.read(objectMapper.readTree("{\"data\":{\"items\":[{\"id\":\"o-1\"}]}}"), "$.data.items[0].id").asText())
            .isEqualTo("o-1");
    }
}
