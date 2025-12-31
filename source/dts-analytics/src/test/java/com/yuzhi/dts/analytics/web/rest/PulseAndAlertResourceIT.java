package com.yuzhi.dts.analytics.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.DtsAnalyticsApp;
import jakarta.servlet.http.Cookie;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
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
class PulseAndAlertResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void pulseAndAlertApisShouldSupportBasicUiCalls() throws Exception {
        Cookie sessionCookie = setupAdminAndGetSessionCookie();

        String jdbcUrl = "jdbc:h2:mem:pulse_alert;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
                Statement statement = connection.createStatement()) {
            statement.execute("create table t (id int primary key, name varchar(100))");
            statement.execute("insert into t (id, name) values (1, 'Alice')");
        }

        MvcResult createDbResponse = mockMvc.perform(post("/api/database")
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "name": "H2 Pulse Test",
                                  "engine": "h2",
                                  "details": {"jdbc-url": "%s", "user": "sa", "password": ""}
                                }
                                """
                                        .formatted(jdbcUrl)))
                .andExpect(status().isOk())
                .andReturn();
        long databaseId = objectMapper.readTree(createDbResponse.getResponse().getContentAsString()).get("id").asLong();

        MvcResult createCardResponse = mockMvc.perform(post("/api/card")
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "name": "Q1",
                                  "dataset_query": {"type":"native","native":{"query":"SELECT 1 AS one"},"database": %d},
                                  "display": "table",
                                  "visualization_settings": {}
                                }
                                """
                                        .formatted(databaseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn();
        long cardId = objectMapper.readTree(createCardResponse.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/pulse/form_input").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channels").isArray())
                .andExpect(jsonPath("$.schedules").isArray());

        MvcResult createPulseResponse = mockMvc.perform(post("/api/pulse")
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "name": "P1",
                                  "cards": [{"id": %d}],
                                  "channels": [{"type":"email","recipients":[{"id":1}],"details":{}}],
                                  "skip_if_empty": false
                                }
                                """
                                        .formatted(cardId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("P1"))
                .andExpect(jsonPath("$.cards").isArray())
                .andReturn();
        long pulseId = objectMapper.readTree(createPulseResponse.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/pulse").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value((int) pulseId));

        mockMvc.perform(get("/api/pulse/%d".formatted(pulseId)).cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) pulseId));

        mockMvc.perform(put("/api/pulse/%d".formatted(pulseId))
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"P1-renamed\",\"cards\":[{\"id\":%d}],\"channels\":[]}".formatted(cardId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("P1-renamed"));

        mockMvc.perform(post("/api/pulse/test")
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        mockMvc.perform(get("/api/pulse/preview_card_info/%d".formatted(cardId)).cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) cardId));

        mockMvc.perform(delete("/api/pulse/%d/subscription".formatted(pulseId)).cookie(sessionCookie))
                .andExpect(status().isNoContent());

        MvcResult createAlertResponse = mockMvc.perform(post("/api/alert")
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "card_id": %d,
                                  "channels": [{"type":"email","recipients":[{"id":1}],"details":{}}],
                                  "alert_condition": null
                                }
                                """
                                        .formatted(cardId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.card_id").value((int) cardId))
                .andReturn();
        long alertId = objectMapper.readTree(createAlertResponse.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/alert").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value((int) alertId));

        mockMvc.perform(get("/api/alert/question/%d".formatted(cardId)).cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].card_id").value((int) cardId));

        mockMvc.perform(get("/api/alert/%d".formatted(alertId)).cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) alertId));

        mockMvc.perform(put("/api/alert/%d".formatted(alertId))
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"card_id\":%d,\"channels\":[]}".formatted(cardId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) alertId));

        mockMvc.perform(delete("/api/alert/%d/subscription".formatted(alertId)).cookie(sessionCookie))
                .andExpect(status().isNoContent());
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

