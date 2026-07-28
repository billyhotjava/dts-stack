package com.yuzhi.dts.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO;
import com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver;
import com.yuzhi.dts.ingestion.service.etl.JdbcMetadataService;
import com.yuzhi.dts.ingestion.service.infra.InfraServiceSettingsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 集成测试：完整的任务创建流程
 * 测试从创建任务到执行的完整流程
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class IngestionTaskIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IngestionSourceResolver sourceResolver;

    @MockBean
    private InfraServiceSettingsRepository infraServiceSettingsRepository;

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void shouldCreateAndManageTaskCompleteFlow() throws Exception {
        mockSourceResolver();
        // Step 1: Create task
        IngestionTaskDTO createdTask = createTask("integration-test-task");
        Long taskId = createdTask.getId();

        // Step 2: Get task list
        mockMvc.perform(get("/api/ingestion/tasks/list")
                .param("page", "0")
                .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content[*].id", hasItem(taskId.intValue())));

        // Step 3: Get task details
        mockMvc.perform(get("/api/ingestion/tasks/{id}", taskId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(taskId))
            .andExpect(jsonPath("$.name").value("integration-test-task"))
            .andExpect(jsonPath("$.sourceType").value("postgresqlreader"));

        // Step 4: Update task
        createdTask.setDescription("Updated description");
        String updateJson = objectMapper.writeValueAsString(createdTask);

        mockMvc.perform(put("/api/ingestion/tasks/{id}", taskId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description").value("Updated description"));

        // Step 5: Admit the draft with immutable classification evidence
        admitTask(taskId);

        // Step 6: Execute task (may fail if Airflow not available, that's ok for test)
        mockMvc.perform(post("/api/ingestion/tasks/{id}/execute", taskId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("running"));

        // Step 7: Get execution history
        mockMvc.perform(get("/api/ingestion/tasks/{id}/executions", taskId)
                .param("page", "0")
                .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());

        // Step 8: Get latest execution
        mockMvc.perform(get("/api/ingestion/tasks/{id}/executions/latest", taskId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taskId").value(taskId));

        // Step 9: Delete task (soft delete)
        mockMvc.perform(delete("/api/ingestion/tasks/{id}", taskId))
            .andExpect(status().isNoContent());

        // Step 10: Verify task is soft-deleted
        mockMvc.perform(get("/api/ingestion/tasks/{id}", taskId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("deleted"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void shouldFilterTasksByStatus() throws Exception {
        mockSourceResolver();
        // Create tasks with different statuses
        createTaskWithStatus("task-draft", "draft");
        createTaskWithStatus("task-active", "active");

        // Filter by status
        mockMvc.perform(get("/api/ingestion/tasks/list")
                .param("status", "draft")
                .param("page", "0")
                .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].status", everyItem(is("draft"))));

        mockMvc.perform(get("/api/ingestion/tasks/list")
                .param("status", "active")
                .param("page", "0")
                .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].status", everyItem(is("active"))));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void shouldHandleInvalidTaskId() throws Exception {
        mockSourceResolver();
        Long invalidId = 999999L;

        // Get non-existent task
        mockMvc.perform(get("/api/ingestion/tasks/{id}", invalidId))
            .andExpect(status().isNotFound());

        // Update non-existent task
        IngestionTaskDTO taskDTO = createTask("task-invalid");
        taskDTO.setId(invalidId);
        String taskJson = objectMapper.writeValueAsString(taskDTO);

        mockMvc.perform(put("/api/ingestion/tasks/{id}", invalidId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(taskJson))
            .andExpect(status().isNotFound());

        // Execute non-existent task
        mockMvc.perform(post("/api/ingestion/tasks/{id}/execute", invalidId))
            .andExpect(status().isNotFound());
    }

    @Test
    void shouldDenyAccessWithoutAuthentication() throws Exception {
        // Attempt to access without authentication
        mockMvc.perform(get("/api/ingestion/tasks/list"))
            .andExpect(status().isForbidden());
    }

    // Helper methods
    private IngestionTaskDTO createTask(String name) throws Exception {
        mockSourceResolver();
        String taskJson = objectMapper.writeValueAsString(buildTaskRequest(name));
        String response = mockMvc.perform(post("/api/ingestion/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(taskJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.task.id").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        JsonNode taskNode = root.path("data").path("task");
        return objectMapper.treeToValue(taskNode, IngestionTaskDTO.class);
    }

    private void createTaskWithStatus(String name, String status) throws Exception {
        IngestionTaskDTO createdTask = createTask(name);
        if ("active".equalsIgnoreCase(status)) {
            admitTask(createdTask.getId());
            return;
        }
        createdTask.setStatus(status);
        String updateJson = objectMapper.writeValueAsString(createdTask);

        mockMvc.perform(put("/api/ingestion/tasks/{id}", createdTask.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson))
            .andExpect(status().isOk());
    }

    private void admitTask(Long taskId) throws Exception {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("classificationSeal", buildClassificationSeal());
        payload.set("fieldClassifications", objectMapper.createObjectNode());

        mockMvc.perform(post("/api/ingestion/tasks/{id}/admit", taskId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("active"));
    }

    private ObjectNode buildTaskRequest(String name) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("name", name);
        payload.put("description", "Integration test task");
        payload.put("draft", true);
        payload.set("classificationSeal", buildClassificationSeal());
        payload.set("fieldClassifications", objectMapper.createObjectNode());

        ObjectNode source = payload.putObject("source");
        source.put("dataSourceId", UUID.randomUUID().toString());

        ObjectNode destination = payload.putObject("destination");
        destination.put("usePlatformDefault", false);
        destination.put("type", "postgresqlwriter");
        destination.set("config", buildJdbcConfig("jdbc:postgresql://localhost:5432/targetdb", "target_table"));

        ObjectNode sync = payload.putObject("sync");
        sync.put("mode", "full_refresh");

        ObjectNode airflow = payload.putObject("airflow");
        airflow.put("enabled", false);

        payload.put("runNow", false);
        return payload;
    }

    private ObjectNode buildClassificationSeal() {
        ObjectNode seal = objectMapper.createObjectNode();
        seal.put("sealId", "integration-seal");
        seal.put("subjectType", "ASSET");
        seal.put("subjectKey", "ingestion-task:integration");
        seal.put("effectiveLevel", "INTERNAL");
        seal.put("snapshotVersion", 1L);
        seal.put("checksum", "0123456789abcdef0123456789abcdef");
        seal.put("sealedAt", "2026-07-28T00:00:00Z");
        return seal;
    }

    private ObjectNode buildJdbcConfig(String jdbcUrl, String table) {
        ObjectNode config = objectMapper.createObjectNode();
        config.put("username", "test");
        config.put("password", "test");
        ArrayNode columns = objectMapper.createArrayNode();
        columns.add("*");
        config.set("column", columns);
        ArrayNode connections = objectMapper.createArrayNode();
        ObjectNode connection = objectMapper.createObjectNode();
        ArrayNode jdbcUrls = objectMapper.createArrayNode();
        jdbcUrls.add(jdbcUrl);
        connection.set("jdbcUrl", jdbcUrls);
        ArrayNode tables = objectMapper.createArrayNode();
        tables.add(table);
        connection.set("table", tables);
        connections.add(connection);
        config.set("connection", connections);
        return config;
    }

    private void mockSourceResolver() {
        Map<String, Object> readerConfig = Map.of(
            "username", "test",
            "password", "test",
            "column", List.of("*"),
            "connection", List.of(Map.of("jdbcUrl", List.of("jdbc:postgresql://localhost:5432/testdb"), "table", List.of("source_table")))
        );
        when(sourceResolver.resolve(any(UUID.class), anyList()))
            .thenReturn(new IngestionSourceResolver.ResolvedSource("postgresqlreader", readerConfig, null));
        when(sourceResolver.resolveJdbcInfo(any(UUID.class)))
            .thenReturn(new JdbcMetadataService.JdbcConnectionInfo(
                "jdbc:postgresql://localhost:5432/testdb", "test", "test", "org.postgresql.Driver", null, Map.of()
            ));
    }
}
