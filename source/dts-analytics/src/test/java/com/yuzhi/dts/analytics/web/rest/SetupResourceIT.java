package com.yuzhi.dts.analytics.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SetupResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void setupShouldValidateTokenAndCreateAdminSession() throws Exception {
        MvcResult properties = mockMvc.perform(get("/api/session/properties").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode bootstrap = objectMapper.readTree(properties.getResponse().getContentAsString());
        String setupToken = bootstrap.get("setup-token").asText();

        mockMvc.perform(post("/api/setup/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"wrong\",\"details\":{\"engine\":\"postgres\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.token").value("Token does not match the setup token."));

        mockMvc.perform(post("/api/setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "token": "%s",
                                  "prefs": {"site_name": "DTS Analytics", "allow_tracking": false},
                                  "user": {"email": "admin@example.com", "first_name": "Admin", "last_name": "User", "password": "abc123"}
                                }
                                """
                                        .formatted(setupToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(cookie().exists("metabase.SESSION"))
                .andExpect(cookie().exists("metabase.DEVICE"))
                .andExpect(cookie().value("metabase.TIMEOUT", "alive"));

        mockMvc.perform(get("/api/session/properties").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.\"has-user-setup\"").value(true))
                .andExpect(jsonPath("$.\"site-name\"").value("DTS Analytics"));
    }

    @Test
    void adminChecklistShouldRequireSuperuserSession() throws Exception {
        mockMvc.perform(get("/api/setup/admin_checklist").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(content().string("You don't have permissions to do that."));

        MvcResult properties = mockMvc.perform(get("/api/session/properties").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        String setupToken = objectMapper.readTree(properties.getResponse().getContentAsString()).get("setup-token").asText();

        MvcResult setupResponse = mockMvc.perform(post("/api/setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "token": "%s",
                                  "prefs": {"site_name": "DTS Analytics", "allow_tracking": false},
                                  "user": {"email": "admin2@example.com", "first_name": "Admin", "last_name": "User", "password": "abc123"}
                                }
                                """
                                        .formatted(setupToken)))
                .andExpect(status().isOk())
                .andReturn();

        String sessionId = objectMapper.readTree(setupResponse.getResponse().getContentAsString()).get("id").asText();
        mockMvc.perform(get("/api/setup/admin_checklist").cookie(new Cookie("metabase.SESSION", sessionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Get connected"))
                .andExpect(jsonPath("$[0].tasks[0].title").value("Add a database"));
    }
}
