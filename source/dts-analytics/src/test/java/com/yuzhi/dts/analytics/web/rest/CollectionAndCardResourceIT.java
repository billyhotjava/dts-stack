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
class CollectionAndCardResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void collectionsAndCardsShouldSupportBasicUiFlows() throws Exception {
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

        mockMvc.perform(get("/api/collection/root").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("root"));

        mockMvc.perform(get("/api/collection").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("root"))
                .andExpect(jsonPath("$[1].id").isNumber())
                .andExpect(jsonPath("$[1].personal_owner_id").value(1));

        mockMvc.perform(get("/api/collection/tree").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].children").isArray());

        mockMvc.perform(post("/api/collection")
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Shared\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Shared"));

        mockMvc.perform(get("/api/card").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        String jdbcUrl = "jdbc:h2:mem:card_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
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
                                  "name": "H2 Card Test",
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
                .andExpect(jsonPath("$.query_type").value("native"))
                .andExpect(jsonPath("$.database_id").value((int) databaseId))
                .andExpect(jsonPath("$.result_metadata").isArray())
                .andReturn();

        long cardId = objectMapper.readTree(createCardResponse.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/card/%d/query".formatted(cardId))
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.data.rows[0][0]").value(1));
    }
}

