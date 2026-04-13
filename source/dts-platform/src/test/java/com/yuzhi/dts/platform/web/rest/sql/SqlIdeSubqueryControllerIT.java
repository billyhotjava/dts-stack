package com.yuzhi.dts.platform.web.rest.sql;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.platform.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration test for {@link SqlIdeSubqueryController} — Sprint-11 F5 T24.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(username = "alice")
class SqlIdeSubqueryControllerIT {

    @Autowired
    private MockMvc mvc;

    @Test
    void createTempViewForUnknownExecutionReturns200WithEmptyView() throws Exception {
        mvc.perform(post("/api/sql/v2/temp-views")
                .param("executionId", UUID.randomUUID().toString()))
            .andExpect(status().isOk());
    }
}
