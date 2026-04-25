package com.yuzhi.dts.platform.web.rest;

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
class WorkbenchResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(authorities = {"ROLE_INST_DATA_OWNER"})
    void overviewShouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/workbench/overview")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {"ROLE_INST_DATA_OWNER"})
    void favoritesEndpointIsRemoved() throws Exception {
        // Sprint-15 F2 removed the workbench favorites feature; the endpoint must be gone.
        mockMvc.perform(get("/api/workbench/favorites")).andExpect(status().isNotFound());
    }

    // P1-4 — security configuration regression: leader-overview must require auth.
    // If the endpoint pattern is ever moved to permitAll() this test fails.
    @Test
    void leaderOverviewWithoutAuthIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/workbench/leader-overview"))
            .andExpect(status().isUnauthorized());
    }

    // P1-4 — server-side scope downgrade: a plain employee may request scope=ALL
    // but the response must come back as scope=MINE rather than 403.
    @Test
    @WithMockUser(authorities = {"ROLE_EMPLOYEE"})
    void leaderOverviewEmployeeRequestingAllDowngradesToMine() throws Exception {
        mockMvc.perform(get("/api/workbench/leader-overview").param("scope", "ALL"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.scope", is("MINE")));
    }
}
