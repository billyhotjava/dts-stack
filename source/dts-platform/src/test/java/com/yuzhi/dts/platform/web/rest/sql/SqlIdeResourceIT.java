package com.yuzhi.dts.platform.web.rest.sql;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.platform.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@IntegrationTest
@WithMockUser
class SqlIdeResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void pingReturnsOk() throws Exception {
        mockMvc
            .perform(get("/api/sql/v2/ping"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value(is("ok")));
    }
}
