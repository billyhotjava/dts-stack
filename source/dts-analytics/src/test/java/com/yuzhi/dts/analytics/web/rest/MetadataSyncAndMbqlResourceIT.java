package com.yuzhi.dts.analytics.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
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
class MetadataSyncAndMbqlResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void syncSchemaShouldPopulateMetadataAndMbqlDatasetShouldWork() throws Exception {
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

        String jdbcUrl = "jdbc:h2:mem:metadata_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
                Statement statement = connection.createStatement()) {
            statement.execute("create table parent (id int primary key, name varchar(100))");
            statement.execute("create table child (id int primary key, parent_id int, constraint fk_child_parent foreign key (parent_id) references parent(id))");
            statement.execute("insert into parent (id, name) values (1, 'Alice')");
            statement.execute("insert into parent (id, name) values (2, 'Bob')");
            statement.execute("insert into child (id, parent_id) values (10, 1)");
        }

        MvcResult createDbResponse = mockMvc.perform(post("/api/database")
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "name": "H2 Metadata",
                                  "engine": "h2",
                                  "details": {"jdbc-url": "%s", "user": "sa", "password": ""}
                                }
                                """
                                        .formatted(jdbcUrl)))
                .andExpect(status().isOk())
                .andReturn();
        long databaseId = objectMapper.readTree(createDbResponse.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/database/%d/sync_schema".formatted(databaseId)).cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created_tables").value(2))
                .andExpect(jsonPath("$.created_fields").value(5));

        MvcResult metadataResult = mockMvc.perform(get("/api/database/%d/metadata".formatted(databaseId))
                        .cookie(sessionCookie)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tables.length()").value(2))
                .andReturn();

        JsonNode metadata = objectMapper.readTree(metadataResult.getResponse().getContentAsString());
        JsonNode parentTable = null;
        JsonNode childTable = null;
        for (JsonNode table : metadata.get("tables")) {
            if ("PARENT".equals(table.get("name").asText())) {
                parentTable = table;
            } else if ("CHILD".equals(table.get("name").asText())) {
                childTable = table;
            }
        }
        if (parentTable == null || childTable == null) {
            throw new IllegalStateException("Expected parent/child tables in metadata response");
        }

        long parentTableId = parentTable.get("id").asLong();
        long childTableId = childTable.get("id").asLong();

        long parentIdFieldId = parentTable.get("fields").get(0).get("id").asLong();
        long parentNameFieldId = parentTable.get("fields").get(1).get("id").asLong();

        long childParentIdFieldId = 0;
        for (JsonNode field : childTable.get("fields")) {
            if ("PARENT_ID".equals(field.get("name").asText())) {
                childParentIdFieldId = field.get("id").asLong();
            }
        }
        if (childParentIdFieldId <= 0) {
            throw new IllegalStateException("Expected CHILD.PARENT_ID field in metadata response");
        }

        mockMvc.perform(get("/api/table/%d".formatted(parentTableId)).cookie(sessionCookie).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) parentTableId))
                .andExpect(jsonPath("$.fields.length()").value(2));

        mockMvc.perform(get("/api/table/%d/query_metadata".formatted(parentTableId))
                        .cookie(sessionCookie)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.length()").value(2));

        mockMvc.perform(get("/api/field/%d".formatted(parentIdFieldId)).cookie(sessionCookie).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("ID"))
                .andExpect(jsonPath("$.base_type").value("type/Integer"));

        mockMvc.perform(get("/api/field/%d/values".formatted(parentNameFieldId)).cookie(sessionCookie).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.field_id").value((int) parentNameFieldId))
                .andExpect(jsonPath("$.values.length()").value(2))
                .andExpect(jsonPath("$.has_more_values").value(false));

        mockMvc.perform(get("/api/table/%d/fks".formatted(childTableId)).cookie(sessionCookie).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fk_field_id").value((int) childParentIdFieldId))
                .andExpect(jsonPath("$[0].fk_target_field_id").value((int) parentIdFieldId));

        mockMvc.perform(post("/api/dataset")
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "database": %d,
                                  "type": "query",
                                  "query": {
                                    "source-table": %d,
                                    "fields": [["field", %d, null], ["field", %d, null]],
                                    "limit": 10
                                  }
                                }
                                """
                                        .formatted(databaseId, parentTableId, parentIdFieldId, parentNameFieldId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.data.rows[0][0]").value(1))
                .andExpect(jsonPath("$.data.rows[0][1]").value("Alice"));
    }
}
