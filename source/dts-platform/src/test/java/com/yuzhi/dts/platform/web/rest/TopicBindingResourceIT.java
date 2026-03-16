package com.yuzhi.dts.platform.web.rest;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.IntegrationTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@IntegrationTest
@Transactional
class TopicBindingResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(username = "tester", authorities = {"ROLE_INST_DATA_OWNER"})
    void listTemplatesShouldReturnDefaultTemplates() throws Exception {
        mockMvc
            .perform(get("/api/topic-bindings/templates"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[?(@.templateCode=='project-management')]").exists())
            .andExpect(jsonPath("$.data[?(@.templateCode=='plm-overview')]").exists());
    }

    @Test
    @WithMockUser(username = "tester", authorities = {"ROLE_INST_DATA_OWNER"})
    void statusShouldReturnBindingRowsAndMissingRequired() throws Exception {
        mockMvc
            .perform(get("/api/topic-bindings/status").param("selector", "tag:project-management"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.relevantTemplateCodes[0]").value("project-management"))
            .andExpect(jsonPath("$.data.missingRequired[0]").value("project-management.project_subject_domain"))
            .andExpect(jsonPath("$.data.rows[0].templateCode").value("project-management"))
            .andExpect(jsonPath("$.data.rows[0].entityCode").value("project_subject_domain"))
            .andExpect(jsonPath("$.data.rows[0].bound").value(false));
    }

    @Test
    @WithMockUser(username = "tester", authorities = {"ROLE_INST_DATA_OWNER"})
    void bindOdsTableShouldCreateGlobalBinding() throws Exception {
        mockMvc
            .perform(
                post("/api/topic-bindings/ods")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            java.util.Map.of(
                                "templateCode",
                                "project-management",
                                "entityCode",
                                "project_subject_domain",
                                "schemaName",
                                "ods",
                                "tableName",
                                "pm_upload_20260316",
                                "notes",
                                "bind from import"
                            )
                        )
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.templateCode").value("project-management"))
            .andExpect(jsonPath("$.data.entityCode").value("project_subject_domain"))
            .andExpect(jsonPath("$.data.bindingMode").value("ODS_TABLE"))
            .andExpect(jsonPath("$.data.scopeKey").value("GLOBAL"))
            .andExpect(jsonPath("$.data.schemaName").value("ods"))
            .andExpect(jsonPath("$.data.tableName").value("pm_upload_20260316"))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }
}
