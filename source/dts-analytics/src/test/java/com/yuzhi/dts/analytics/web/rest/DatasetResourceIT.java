package com.yuzhi.dts.analytics.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class DatasetResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void datasetNativeQueryShouldReturnMetabaseLikeShape() throws Exception {
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

        String jdbcUrl = "jdbc:h2:mem:dataset_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
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

        mockMvc.perform(post("/api/dataset")
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "database": %d,
                                  "type": "native",
                                  "native": {"query": "SELECT 1 AS one"},
                                  "parameters": []
                                }
                                """
                                        .formatted(databaseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.database_id").value((int) databaseId))
                .andExpect(jsonPath("$.data.rows[0][0]").value(1))
                .andExpect(jsonPath("$.data.cols[0].name").value("ONE"))
                .andExpect(jsonPath("$.data.cols[0].base_type").value("type/Integer"))
                .andExpect(jsonPath("$.data.results_metadata.columns[0].fingerprint.global['distinct-count']").value(1))
                .andExpect(jsonPath("$.data.results_metadata.columns[0].fingerprint.global['nil%']").value(0.0));
    }
}
