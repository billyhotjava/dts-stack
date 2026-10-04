package com.yuzhi.dts.analytics.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class AdminPeopleResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminPeopleApisShouldSupportCreateUpdateDeactivateAndPassword() throws Exception {
        mockMvc.perform(get("/api/user")).andExpect(status().isUnauthorized());

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

        String sessionId = objectMapper.readTree(setupResponse.getResponse().getContentAsString()).get("id").asText();
        Cookie sessionCookie = new Cookie("metabase.SESSION", sessionId);

        mockMvc.perform(post("/api/user")
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "email": "analyst@example.com",
                                  "first_name": "Data",
                                  "last_name": "Analyst",
                                  "password": "abc123",
                                  "is_superuser": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("analyst@example.com"))
                .andExpect(jsonPath("$.is_superuser").value(false))
                .andExpect(jsonPath("$.group_ids").isArray());

        mockMvc.perform(get("/api/user").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email=='admin@example.com')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.email=='analyst@example.com')]").isNotEmpty());

        mockMvc.perform(put("/api/user/2/password")
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"abc123\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/user/2").cookie(sessionCookie)).andExpect(status().isNoContent());

        mockMvc.perform(put("/api/user/2/reactivate").cookie(sessionCookie)).andExpect(status().isNoContent());

        mockMvc.perform(post("/api/user/2/send_invite").cookie(sessionCookie)).andExpect(status().isNoContent());
    }
}
