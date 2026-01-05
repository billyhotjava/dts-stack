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
class SegmentAndMetricResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void segmentsAndMetricsShouldSupportBasicCrud() throws Exception {
        Cookie sessionCookie = setupAdminAndGetSessionCookie();

        MvcResult createSegmentResponse = mockMvc.perform(post("/api/segment")
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"S1\",\"definition\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("S1"))
                .andReturn();
        long segmentId = objectMapper.readTree(createSegmentResponse.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/segment").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value((int) segmentId));

        mockMvc.perform(get("/api/segment/%d".formatted(segmentId)).cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) segmentId));

        mockMvc.perform(put("/api/segment/%d".formatted(segmentId))
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"S1-renamed\",\"definition\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("S1-renamed"));

        mockMvc.perform(delete("/api/segment/%d".formatted(segmentId)).cookie(sessionCookie))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/segment").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        MvcResult createMetricResponse = mockMvc.perform(post("/api/metric")
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"M1\",\"definition\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("M1"))
                .andReturn();
        long metricId = objectMapper.readTree(createMetricResponse.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/metric").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value((int) metricId));

        mockMvc.perform(get("/api/metric/%d".formatted(metricId)).cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) metricId));

        mockMvc.perform(put("/api/metric/%d".formatted(metricId))
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"M1-renamed\",\"definition\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("M1-renamed"));

        mockMvc.perform(delete("/api/metric/%d".formatted(metricId)).cookie(sessionCookie))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/metric").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    private Cookie setupAdminAndGetSessionCookie() throws Exception {
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
        return new Cookie("metabase.SESSION", sessionId);
    }
}
