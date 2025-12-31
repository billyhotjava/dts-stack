package com.yuzhi.dts.analytics.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class DashboardAndSearchResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void dashboardCardQueryCollectionItemsAndSearchShouldWork() throws Exception {
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

        String jdbcUrl = "jdbc:h2:mem:dashboard_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
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
                                  "name": "H2 Test",
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
                                  "name": "Card One",
                                  "dataset_query": {
                                    "database": %d,
                                    "type": "native",
                                    "native": {"query": "SELECT 1 AS one"},
                                    "parameters": []
                                  },
                                  "display": "table",
                                  "visualization_settings": {}
                                }
                                """
                                        .formatted(databaseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn();
        long cardId = objectMapper.readTree(createCardResponse.getResponse().getContentAsString()).get("id").asLong();

        MvcResult createDashboardResponse = mockMvc.perform(post("/api/dashboard")
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "name": "Dash One",
                                  "description": "demo"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn();
        long dashboardId =
                objectMapper.readTree(createDashboardResponse.getResponse().getContentAsString()).get("id").asLong();

        MvcResult addCardResponse = mockMvc.perform(post("/api/dashboard/%d/cards".formatted(dashboardId))
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "card_id": %d,
                                  "row": 0,
                                  "col": 0,
                                  "size_x": 4,
                                  "size_y": 3,
                                  "parameter_mappings": [],
                                  "visualization_settings": {}
                                }
                                """
                                        .formatted(cardId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.card.id").value((int) cardId))
                .andReturn();
        long dashcardId = objectMapper.readTree(addCardResponse.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/dashboard/%d/dashcard/%d/card/%d/query".formatted(dashboardId, dashcardId, cardId))
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.database_id").value((int) databaseId))
                .andExpect(jsonPath("$.data.rows[0][0]").value(1));

        mockMvc.perform(get("/api/collection/root/items").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.model == 'dashboard')].id").isNotEmpty())
                .andExpect(jsonPath("$[?(@.model == 'card')].id").isNotEmpty());

        mockMvc.perform(get("/api/search").param("q", "Dash One").param("models", "dashboard").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data[0].model").value("dashboard"))
                .andExpect(jsonPath("$.data[0].id").value((int) dashboardId));
    }
}

