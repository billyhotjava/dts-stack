package com.yuzhi.dts.analytics.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class DatabaseResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void databaseCrudEndpointsShouldMatchMetabaseShapes() throws Exception {
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
                .andReturn();
        String sessionId = objectMapper.readTree(setupResponse.getResponse().getContentAsString()).get("id").asText();
        Cookie sessionCookie = new Cookie("metabase.SESSION", sessionId);

        mockMvc.perform(get("/api/database").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.total").value(0));

        mockMvc.perform(post("/api/database")
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test DB\",\"engine\":\"postgres\",\"details\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Test DB"))
                .andExpect(jsonPath("$.engine").value("postgres"))
                .andExpect(jsonPath("$.details").isMap());

        mockMvc.perform(get("/api/database").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Test DB"));
    }
}
