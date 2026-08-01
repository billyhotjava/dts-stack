package com.yuzhi.dts.ingestion.web.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

class IngestionJsonResponseSanitizerAdviceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final IngestionJsonResponseSanitizerAdvice advice = new IngestionJsonResponseSanitizerAdvice(objectMapper);

    @Test
    void responseContractShouldRetainTokenControlsAndRemoveExactCredentials() {
        Object body = Map.of(
            "sourceConfig",
            Map.ofEntries(
                Map.entry("tokenUrl", "https://id.example/oauth/token"),
                Map.entry("tokenPath", "$.access_token"),
                Map.entry("tokenPlacement", "HEADER"),
                Map.entry("tokenHeaderName", "X-Access-Token"),
                Map.entry("nextTokenPath", "$.paging.next"),
                Map.entry("checkpointToken", "cursor-42"),
                Map.entry("secrets", Map.of("headerName", "X-API-Key", "value", "raw-container-key")),
                Map.entry("password", "raw-password"),
                Map.entry("accessToken", "raw-token")
            )
        );

        Object response = advice.beforeBodyWrite(
            body,
            null,
            MediaType.APPLICATION_JSON,
            MappingJackson2HttpMessageConverter.class,
            null,
            null
        );

        JsonNode config = ((JsonNode) response).path("sourceConfig");
        assertThat(config.path("tokenUrl").asText()).isEqualTo("https://id.example/oauth/token");
        assertThat(config.path("tokenPath").asText()).isEqualTo("$.access_token");
        assertThat(config.path("tokenPlacement").asText()).isEqualTo("HEADER");
        assertThat(config.path("tokenHeaderName").asText()).isEqualTo("X-Access-Token");
        assertThat(config.path("nextTokenPath").asText()).isEqualTo("$.paging.next");
        assertThat(config.path("checkpointToken").asText()).isEqualTo("cursor-42");
        assertThat(config.has("secrets")).isFalse();
        assertThat(config.has("password")).isFalse();
        assertThat(config.has("accessToken")).isFalse();
    }
}
