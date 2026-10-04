package com.yuzhi.dts.platform.web.rest;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.platform.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@IntegrationTest
class WorkbenchResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void resetPreferences() throws Exception {
        mockMvc.perform(post("/api/workbench/preferences/reset")).andReturn();
    }

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

    @Test
    @WithMockUser(username = "alice", authorities = {"ROLE_EMPLOYEE"})
    void preferencesReturnsRoleDefaultForCurrentUser() throws Exception {
        mockMvc.perform(get("/api/workbench/preferences"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.version").value(1))
            .andExpect(jsonPath("$.data.availableComponents[0].key").value("leader-kpi"))
            .andExpect(jsonPath("$.data.items[0].key").value("leader-kpi"))
            .andExpect(jsonPath("$.data.items[0].order").value(10));
    }

    @Test
    @WithMockUser(username = "alice", authorities = {"ROLE_INST_DATA_OWNER"})
    void preferencesSaveAndResetAreBoundToCurrentUser() throws Exception {
        mockMvc.perform(put("/api/workbench/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"items":[
                      {"key":"golden-chain","visible":true,"order":30},
                      {"key":"todo","visible":false,"order":10}
                    ]}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].key").value("todo"))
            .andExpect(jsonPath("$.data.items[0].visible").value(false))
            .andExpect(jsonPath("$.data.items[1].key").value("golden-chain"));

        mockMvc.perform(get("/api/workbench/preferences"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].key").value("todo"))
            .andExpect(jsonPath("$.data.items[1].key").value("golden-chain"));

        mockMvc.perform(post("/api/workbench/preferences/reset"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].key").value("leader-kpi"));
    }

    @Test
    @WithMockUser(username = "alice", authorities = {"ROLE_EMPLOYEE"})
    void preferencesRejectUnknownComponentKey() throws Exception {
        mockMvc.perform(put("/api/workbench/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"items":[{"key":"customer-demo","visible":true,"order":10}]}
                    """))
            .andExpect(status().isBadRequest());
    }
}
