package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class PjmGoldenPathFixtureResourceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void sharedFixtureContainsThreeLayersAndLegacyReadOnlyMappings() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/fixtures/modeling-vnext/pjm-golden-path.json")) {
            assertThat(stream).as("PJM golden path fixture").isNotNull();
            JsonNode fixture = objectMapper.readTree(stream);

            assertThat(fixture.path("modelSpecs")).hasSize(3);
            assertThat(fixture.path("modelSpecs").get(0).path("layer").asText()).isEqualTo("DWD");
            assertThat(fixture.path("modelSpecs").get(1).path("layer").asText()).isEqualTo("DWS");
            assertThat(fixture.path("modelSpecs").get(2).path("layer").asText()).isEqualTo("ADS");
            assertThat(fixture.path("modelSpecs").get(1).path("dependsOn").get(0).asText()).isEqualTo("pjm-project-node-dwd");
            assertThat(fixture.path("legacyRefs")).allSatisfy(ref -> assertThat(ref.path("status").asText()).isEqualTo("LEGACY_READONLY"));
        }
    }
}
