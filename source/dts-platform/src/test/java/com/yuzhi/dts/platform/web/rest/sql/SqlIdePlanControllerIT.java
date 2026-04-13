package com.yuzhi.dts.platform.web.rest.sql;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration test for {@link SqlIdePlanController} — Sprint-11 F5 T23.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(username = "alice")
class SqlIdePlanControllerIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void explainReturnsRootNodeEvenOnError() throws Exception {
        Map<String, Object> body = Map.of(
            "sql", "SELECT 1",
            "engine", "TRINO",
            "datasourceId", "00000000-0000-0000-0000-000000000000",
            "catalog", "mock"
        );
        mvc.perform(post("/api/sql/v2/explain")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.root.operator").exists());
    }
}
