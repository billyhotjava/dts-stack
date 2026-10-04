package com.yuzhi.dts.platform.web.rest;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@IntegrationTest
class OpsResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(authorities = {"ROLE_INST_DATA_OWNER"})
    void overviewShouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/ops/overview")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {"ROLE_INST_DATA_OWNER"})
    void createBackfillShouldReturnOk() throws Exception {
        Map<String, Object> payload = Map.of(
            "dagId", "sample_dag",
            "dateFrom", "2026-01-01",
            "dateTo", "2026-01-02",
            "note", "test"
        );
        mockMvc
            .perform(
                post("/api/ops/backfills")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(payload))
            )
            .andExpect(status().isOk());
    }
}
