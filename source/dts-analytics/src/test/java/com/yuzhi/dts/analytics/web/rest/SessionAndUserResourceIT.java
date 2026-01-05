package com.yuzhi.dts.analytics.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SessionAndUserResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void sessionLoginLogoutAndCurrentUserShouldWork() throws Exception {
        String setupToken = objectMapper
                .readTree(mockMvc.perform(get("/api/session/properties").accept(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("setup-token")
                .asText();

        MvcResult setupResponse = mockMvc.perform(post("/api/setup")
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
                .andExpect(cookie().exists("metabase.SESSION"))
                .andReturn();

        JsonNode setupJson = objectMapper.readTree(setupResponse.getResponse().getContentAsString());
        String sessionId = setupJson.get("id").asText();

        mockMvc.perform(get("/api/user/current").cookie(new Cookie("metabase.SESSION", sessionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("admin@example.com"))
                .andExpect(jsonPath("$.is_superuser").value(true));

        mockMvc.perform(delete("/api/session").cookie(new Cookie("metabase.SESSION", sessionId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/user/current").cookie(new Cookie("metabase.SESSION", sessionId)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin@example.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().exists("metabase.DEVICE"))
                .andExpect(jsonPath("$.errors.password").value("did not match stored password"));

        mockMvc.perform(post("/api/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin@example.com\",\"password\":\"abc123\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("metabase.SESSION"))
                .andExpect(cookie().value("metabase.TIMEOUT", "alive"))
                .andExpect(jsonPath("$.id").isNotEmpty());
    }
}
