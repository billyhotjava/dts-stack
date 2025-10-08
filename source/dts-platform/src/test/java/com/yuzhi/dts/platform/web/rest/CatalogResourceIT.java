package com.yuzhi.dts.platform.web.rest;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@IntegrationTest
@TestPropertySource(properties = {
    // allow non-default source types without restriction in tests
    "dts.platform.catalog.multi-source-enabled=true"
})
class CatalogResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(authorities = {"ROLE_CATALOG_ADMIN"})
    void createDatasetShouldFailWhenDeptScopeMissingOwnerDept() throws Exception {
        Map<String, Object> payload = Map.of(
            "name", "dept_asset",
            "type", "TRINO",
            "dataLevel", "DATA_INTERNAL",
            "scope", "DEPT"
        );
        mockMvc
            .perform(
                post("/api/catalog/datasets")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(payload))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = {"ROLE_CATALOG_ADMIN"})
    void createDatasetShouldFailWhenInstScopeMissingShareScope() throws Exception {
        Map<String, Object> payload = Map.of(
            "name", "inst_asset",
            "type", "TRINO",
            "dataLevel", "DATA_INTERNAL",
            "scope", "INST"
        );
        mockMvc
            .perform(
                post("/api/catalog/datasets")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(payload))
            )
            .andExpect(status().isBadRequest());
    }
}

