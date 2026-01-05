package com.yuzhi.dts.analytics.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class SettingsAndPermissionsResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void settingsAndPermissionsShouldRequireAuthAndReturnMetabaseLikeShapes() throws Exception {
        mockMvc.perform(get("/api/setting")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/permissions/group")).andExpect(status().isUnauthorized());

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

        mockMvc.perform(get("/api/setting").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.key=='site-name')].value").isNotEmpty());

        mockMvc.perform(put("/api/setting/site-name").cookie(sessionCookie).contentType(MediaType.APPLICATION_JSON).content("\"Renamed\""))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/session/properties").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.\"site-name\"").value("Renamed"));

        mockMvc.perform(get("/api/permissions/group").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Administrators"));

        mockMvc.perform(get("/api/permissions/graph").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(0));
    }
}
