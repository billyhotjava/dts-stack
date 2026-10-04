package com.yuzhi.dts.analytics.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BookmarkAndActivityResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void bookmarksAndActivityShouldWorkForCoreModels() throws Exception {
        Cookie sessionCookie = setupAdminAndGetSessionCookie();

        MvcResult createCollectionResponse = mockMvc.perform(post("/api/collection")
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Shared\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long collectionId = objectMapper.readTree(createCollectionResponse.getResponse().getContentAsString()).get("id").asLong();

        String jdbcUrl = "jdbc:h2:mem:bookmark_activity;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
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
                                  "name": "H2 Bookmark Test",
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
                                  "collection_id": %d,
                                  "dataset_query": {"type":"native","native":{"query":"SELECT 1 AS one"},"database": %d},
                                  "display": "table",
                                  "visualization_settings": {}
                                }
                                """
                                        .formatted(collectionId, databaseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn();
        long cardId = objectMapper.readTree(createCardResponse.getResponse().getContentAsString()).get("id").asLong();

        MvcResult createDashboardResponse = mockMvc.perform(post("/api/dashboard")
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"D1\",\"collection_id\":%d}".formatted(collectionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn();
        long dashboardId = objectMapper.readTree(createDashboardResponse.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/bookmark/card/%d".formatted(cardId)).cookie(sessionCookie))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/bookmark/dashboard/%d".formatted(dashboardId)).cookie(sessionCookie))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/bookmark/collection/%d".formatted(collectionId)).cookie(sessionCookie))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/bookmark").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").isNumber());

        mockMvc.perform(get("/api/card").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value((int) cardId))
                .andExpect(jsonPath("$[0].favorite").value(true));

        mockMvc.perform(get("/api/dashboard").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value((int) dashboardId))
                .andExpect(jsonPath("$[0].favorite").value(true));

        mockMvc.perform(get("/api/collection/%d/items".formatted(collectionId)).cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.model=='card' && @.id==%d)][0].favorite".formatted(cardId)).value(true))
                .andExpect(jsonPath("$[?(@.model=='dashboard' && @.id==%d)][0].favorite".formatted(dashboardId)).value(true));

        mockMvc.perform(put("/api/bookmark/ordering")
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                [
                                  {"type":"card","id": %d},
                                  {"type":"dashboard","id": %d},
                                  {"type":"collection","id": %d}
                                ]
                                """
                                        .formatted(cardId, dashboardId, collectionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ordering").value(0));

        mockMvc.perform(get("/api/card/%d".formatted(cardId)).cookie(sessionCookie)).andExpect(status().isOk());
        mockMvc.perform(get("/api/dashboard/%d".formatted(dashboardId)).cookie(sessionCookie)).andExpect(status().isOk());
        mockMvc.perform(get("/api/collection/%d".formatted(collectionId)).cookie(sessionCookie)).andExpect(status().isOk());

        mockMvc.perform(get("/api/activity/recent_views").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.model=='card' && @.model_id==%d)]".formatted(cardId)).isNotEmpty())
                .andExpect(jsonPath("$[?(@.model=='dashboard' && @.model_id==%d)]".formatted(dashboardId)).isNotEmpty())
                .andExpect(jsonPath("$[?(@.model=='collection' && @.model_id==%d)]".formatted(collectionId)).isNotEmpty());

        mockMvc.perform(get("/api/activity/popular_items").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].views").isNumber());
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
