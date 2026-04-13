package com.yuzhi.dts.platform.web.rest.sql;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.platform.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class SqlIdeExecutionControllerIT {

    @Autowired
    private MockMvc mvc;

    @Test
    void metaForUnknownExecutionReturns404() throws Exception {
        UUID unknownId = UUID.randomUUID();
        mvc.perform(get("/api/sql/v2/executions/{id}/meta", unknownId))
            .andExpect(status().isNotFound());
    }

    @Test
    void pageForUnknownExecutionReturns404() throws Exception {
        UUID unknownId = UUID.randomUUID();
        mvc.perform(get("/api/sql/v2/executions/{id}/page", unknownId)
                .param("from", "0")
                .param("size", "100"))
            .andExpect(status().isNotFound());
    }

    @Test
    void pageSizeParamParsedCorrectly() throws Exception {
        // Verifies that size param is accepted and parsed without error;
        // 404 is expected for unknown executionId (not a 400/500)
        UUID unknownId = UUID.randomUUID();
        mvc.perform(get("/api/sql/v2/executions/{id}/page", unknownId)
                .param("from", "0")
                .param("size", "50"))
            .andExpect(status().isNotFound());
    }
}
