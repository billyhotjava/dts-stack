package com.yuzhi.dts.platform.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
}
