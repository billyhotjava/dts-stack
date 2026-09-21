package com.yuzhi.dts.analytics.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
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
class PublicSharingEmbeddingAndRevisionResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void publicSharingAndEmbedAndRevisionsShouldWorkForBasicFlows() throws Exception {
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

        String jdbcUrl = "jdbc:h2:mem:sharing_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
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
                                  "name": "H2 Sharing Test",
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
                                  "name": "Public Card One",
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

        MvcResult cardPublicLinkResponse = mockMvc.perform(post("/api/card/%d/public_link".formatted(cardId)).cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid").isString())
                .andReturn();
        String cardUuid = objectMapper.readTree(cardPublicLinkResponse.getResponse().getContentAsString()).get("uuid").asText();

        mockMvc.perform(get("/api/public/card/%s".formatted(cardUuid)).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) cardId))
                .andExpect(jsonPath("$.public_uuid").value(cardUuid));

        mockMvc.perform(post("/api/public/card/%s/query".formatted(cardUuid))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.data.rows[0][0]").value(1));

        MvcResult createDashboardResponse = mockMvc.perform(post("/api/dashboard")
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "name": "Public Dashboard One",
                                  "description": "demo"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn();
        long dashboardId =
                objectMapper.readTree(createDashboardResponse.getResponse().getContentAsString()).get("id").asLong();

        MvcResult addDashcardResponse = mockMvc.perform(post("/api/dashboard/%d/cards".formatted(dashboardId))
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
                .andReturn();
        long dashcardId = objectMapper.readTree(addDashcardResponse.getResponse().getContentAsString()).get("id").asLong();

        MvcResult dashboardPublicLinkResponse =
                mockMvc.perform(post("/api/dashboard/%d/public_link".formatted(dashboardId)).cookie(sessionCookie))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.uuid").isString())
                        .andReturn();
        String dashboardUuid =
                objectMapper.readTree(dashboardPublicLinkResponse.getResponse().getContentAsString()).get("uuid").asText();

        mockMvc.perform(get("/api/public/dashboard/%s".formatted(dashboardUuid)).cookie(sessionCookie).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) dashboardId))
                .andExpect(jsonPath("$.public_uuid").value(dashboardUuid))
                .andExpect(jsonPath("$.dashcards[0].id").value((int) dashcardId));

        mockMvc.perform(post("/api/public/dashboard/%s/dashcard/%d/card/%d/query".formatted(dashboardUuid, dashcardId, cardId))
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.data.rows[0][0]").value(1));

        mockMvc.perform(put("/api/embed")
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.secret_key").isString());

        MvcResult previewTokenResponse = mockMvc.perform(post("/api/preview_embed")
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "resource": {"question": %d},
                                  "params": {}
                                }
                                """
                                        .formatted(cardId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andReturn();

        String embedToken = objectMapper.readTree(previewTokenResponse.getResponse().getContentAsString()).get("token").asText();

        mockMvc.perform(get("/api/embed/card/%s".formatted(embedToken)).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) cardId));

        mockMvc.perform(post("/api/embed/card/%s/query".formatted(embedToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.data.rows[0][0]").value(1));

        mockMvc.perform(put("/api/card/%d".formatted(cardId))
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Public Card One Updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Public Card One Updated"));

        MvcResult revisionsResponse = mockMvc.perform(get("/api/revision")
                        .cookie(sessionCookie)
                        .param("entity", "card")
                        .param("id", String.valueOf(cardId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].model").value("card"))
                .andExpect(jsonPath("$[1].id").isNumber())
                .andReturn();

        JsonNode revisions = objectMapper.readTree(revisionsResponse.getResponse().getContentAsString());
        long originalRevisionId = revisions.get(1).path("id").asLong();

        mockMvc.perform(post("/api/revision/revert")
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision_id\": %d}".formatted(originalRevisionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        mockMvc.perform(get("/api/card/%d".formatted(cardId)).cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Public Card One"));
    }
}
