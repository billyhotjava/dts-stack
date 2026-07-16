package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class ModelingVNextFixtureResourceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void pjmFixtureResourceKeepsTheGoldenPathIdentity() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/fixtures/modeling-vnext/pjm-project-node.json")) {
            assertThat(stream).as("PJM ModelSpec fixture").isNotNull();
            JsonNode fixture = objectMapper.readTree(stream);

            assertThat(fixture.path("contractVersion").asInt()).isEqualTo(ModelingVNextContract.CONTRACT_VERSION);
            assertThat(fixture.path("businessObject").path("id").asText()).isEqualTo("pjm-project-node");
            assertThat(fixture.path("modelSpec").path("name").asText()).isEqualTo("project_node_detail");
            assertThat(fixture.path("modelSpec").path("grain").path("keys")).hasSize(4);
            assertThat(fixture.path("modelSpec").path("standardBindings")).hasSize(6);
        }
    }
}
