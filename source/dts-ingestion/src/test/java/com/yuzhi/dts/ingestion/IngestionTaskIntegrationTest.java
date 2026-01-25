package com.yuzhi.dts.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
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

    @Test
    @WithMockUser(authorities = "INFRA_MAINTAINERS")
    void shouldCreateAndManageTaskCompleteFlow() throws Exception {
        // Step 1: Create task
        IngestionTaskDTO taskDTO = createTestTaskDTO();
        String taskJson = objectMapper.writeValueAsString(taskDTO);

        String createResponse = mockMvc.perform(post("/api/ingestion/tasks/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(taskJson))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.name").value("integration-test-task"))
            .andExpect(jsonPath("$.status").value("draft"))
            .andExpect(jsonPath("$.createdBy").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();

        IngestionTaskDTO createdTask = objectMapper.readValue(createResponse, IngestionTaskDTO.class);
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
            .andExpect(jsonPath("$.sourceType").value("mysqlreader"));

        // Step 4: Update task
        taskDTO.setId(taskId);
        taskDTO.setDescription("Updated description");
        String updateJson = objectMapper.writeValueAsString(taskDTO);

        mockMvc.perform(put("/api/ingestion/tasks/{id}", taskId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description").value("Updated description"));

        // Step 5: Execute task (may fail if Airflow not available, that's ok for test)
        mockMvc.perform(post("/api/ingestion/tasks/{id}/execute", taskId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("running"));

        // Step 6: Get execution history
        mockMvc.perform(get("/api/ingestion/tasks/{id}/executions", taskId)
                .param("page", "0")
                .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());

        // Step 7: Get latest execution
        mockMvc.perform(get("/api/ingestion/tasks/{id}/executions/latest", taskId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taskId").value(taskId));

        // Step 8: Delete task (soft delete)
        mockMvc.perform(delete("/api/ingestion/tasks/{id}", taskId))
            .andExpect(status().isNoContent());

        // Step 9: Verify task is soft-deleted
        mockMvc.perform(get("/api/ingestion/tasks/{id}", taskId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("deleted"));
    }

    @Test
    @WithMockUser(authorities = "INFRA_MAINTAINERS")
    void shouldFilterTasksByStatus() throws Exception {
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
    @WithMockUser(authorities = "INFRA_MAINTAINERS")
    void shouldHandleInvalidTaskId() throws Exception {
        Long invalidId = 999999L;

        // Get non-existent task
        mockMvc.perform(get("/api/ingestion/tasks/{id}", invalidId))
            .andExpect(status().isNotFound());

        // Update non-existent task
        IngestionTaskDTO taskDTO = createTestTaskDTO();
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
            .andExpect(status().isUnauthorized());
    }

    // Helper methods
    private IngestionTaskDTO createTestTaskDTO() {
        IngestionTaskDTO dto = new IngestionTaskDTO();
        dto.setName("integration-test-task");
        dto.setDescription("Integration test task");
        dto.setSourceType("mysqlreader");
        dto.setDestinationType("postgresqlwriter");
        dto.setSyncMode("full_refresh");
        dto.setAirflowEnabled(false);

        // Source config
        ObjectNode sourceConfig = objectMapper.createObjectNode();
        sourceConfig.put("host", "localhost");
        sourceConfig.put("port", 3306);
        sourceConfig.put("database", "testdb");
        sourceConfig.put("username", "test");
        sourceConfig.put("password", "test");
        dto.setSourceConfig(sourceConfig);

        // Destination config
        ObjectNode destConfig = objectMapper.createObjectNode();
        destConfig.put("host", "localhost");
        destConfig.put("port", 5432);
        destConfig.put("database", "targetdb");
        destConfig.put("username", "test");
        destConfig.put("password", "test");
        dto.setDestinationConfig(destConfig);

        // Table mapping
        ArrayNode tableMapping = objectMapper.createArrayNode();
        ObjectNode mapping = objectMapper.createObjectNode();
        mapping.put("source", "users");
        mapping.put("target", "ods_users");
        tableMapping.add(mapping);
        dto.setTableMapping(tableMapping);

        return dto;
    }

    private void createTaskWithStatus(String name, String status) throws Exception {
        IngestionTaskDTO dto = createTestTaskDTO();
        dto.setName(name);
        dto.setStatus(status);
        String taskJson = objectMapper.writeValueAsString(dto);

        mockMvc.perform(post("/api/ingestion/tasks/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(taskJson))
            .andExpect(status().isCreated());
    }
}
