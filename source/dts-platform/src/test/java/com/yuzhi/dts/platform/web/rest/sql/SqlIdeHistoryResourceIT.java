package com.yuzhi.dts.platform.web.rest.sql;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.platform.IntegrationTest;
import com.yuzhi.dts.platform.domain.explore.ExecEnums;
import com.yuzhi.dts.platform.domain.explore.QueryExecution;
import com.yuzhi.dts.platform.repository.explore.QueryExecutionRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(username = "alice")
class SqlIdeHistoryResourceIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private QueryExecutionRepository queryExecutionRepository;

    @BeforeEach
    void cleanup() {
        queryExecutionRepository.deleteAll();
    }

    @Test
    void emptyHistoryForFreshUser() throws Exception {
        mvc.perform(get("/api/sql/v2/history"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void supportsLimitParameter() throws Exception {
        mvc.perform(get("/api/sql/v2/history").param("limit", "10"))
            .andExpect(status().isOk());
    }

    @Test
    void supportsStatusFilter() throws Exception {
        // Insert one SUCCESS record for "alice" so the filter has data to work with
        QueryExecution qe = new QueryExecution();
        qe.setEngine(ExecEnums.ExecEngine.TRINO);
        qe.setStatus(ExecEnums.ExecStatus.SUCCESS);
        qe.setSqlText("SELECT 1");
        qe.setStartedAt(Instant.now());
        qe.setCreatedBy("alice");
        queryExecutionRepository.save(qe);

        mvc.perform(get("/api/sql/v2/history").param("status", "SUCCESS"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)));
    }
}
