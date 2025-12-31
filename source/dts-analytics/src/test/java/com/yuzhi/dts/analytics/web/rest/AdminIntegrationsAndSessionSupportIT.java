package com.yuzhi.dts.analytics.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.DtsAnalyticsApp;
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

@SpringBootTest(classes = DtsAnalyticsApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AdminIntegrationsAndSessionSupportIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminSettingsEmailSlackGoogleLdapAndLoginHistoryShouldWork() throws Exception {
        String setupToken = objectMapper
                .readTree(mockMvc.perform(get("/api/session/properties").accept(MediaType.APPLICATION_JSON))
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
                .andReturn();

        String setupSessionId = objectMapper.readTree(setupResponse.getResponse().getContentAsString()).get("id").asText();
        Cookie setupSessionCookie = new Cookie("metabase.SESSION", setupSessionId);

        mockMvc.perform(get("/api/email").cookie(setupSessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.\"email-smtp-host\"").exists());

        mockMvc.perform(put("/api/email")
                        .cookie(setupSessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email-smtp-host\":\"smtp.local\",\"email-smtp-port\":587}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.\"email-smtp-host\"").value("smtp.local"))
                .andExpect(jsonPath("$.\"email-smtp-port\"").value(587));

        mockMvc.perform(post("/api/email/test").cookie(setupSessionCookie).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/slack/settings").cookie(setupSessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.\"slack-token\"").exists());

        mockMvc.perform(get("/api/google/settings").cookie(setupSessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.\"google-auth-enabled\"").exists());

        mockMvc.perform(get("/api/ldap/settings").cookie(setupSessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.\"ldap-enabled\"").exists());

        mockMvc.perform(post("/api/session/forgot_password").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"admin@example.com\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/session/password_reset_token_valid")).andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/session/reset_password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"bad\",\"password\":\"abc123\"}"))
                .andExpect(status().isBadRequest());

        MvcResult loginResponse = mockMvc.perform(post("/api/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin@example.com\",\"password\":\"abc123\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResponse.getResponse().getContentAsString());
        Cookie loginCookie = new Cookie("metabase.SESSION", loginJson.get("id").asText());

        mockMvc.perform(get("/api/login-history/current").cookie(loginCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}

