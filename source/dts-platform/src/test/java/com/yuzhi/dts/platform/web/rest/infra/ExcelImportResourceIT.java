package com.yuzhi.dts.platform.web.rest.infra;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.IntegrationTest;
import com.yuzhi.dts.platform.domain.infra.InfraExternalExchangeFile;
import com.yuzhi.dts.platform.repository.infra.InfraExternalExchangeFileRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@IntegrationTest
class ExcelImportResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InfraExternalExchangeFileRepository exchangeFileRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @TempDir
    Path tempDir;

    @Test
    @WithMockUser(username = "tester", authorities = {"ROLE_INST_DATA_OWNER"})
    void projectCockpitLoadShouldPersistBatch() throws Exception {
        Path csvPath = tempDir.resolve("data.csv");
        Files.writeString(
            csvPath,
            """
            project_no,subsystem,node_task,owner,dept,project_manager,plan_date,actual_date,completion_status,risk_level,incomplete_reason
            PJ-001,导航处理,方案评审,张三,总体技术部,王工,2026-03-01,2026-03-01,按时完成,低,
            PJ-001,导航处理,接口联调,李四,软件一科,王工,2026-03-05,,超期未完成未变更,高,接口联调资源冲突
            """
        );
        Path errorPath = tempDir.resolve("error.csv");
        Files.writeString(errorPath, "3,计划日期格式异常\n");

        InfraExternalExchangeFile file = new InfraExternalExchangeFile();
        file.setEntryKey("EXCEL_IMPORT");
        file.setFileName("project-cockpit-test.csv");
        file.setFilePath(tempDir.resolve("source.csv").toString());
        file.setBatchCode("excel-202603160002");
        file.setStatus("PARSED");
        file.setReceivedAt(Instant.parse("2026-03-16T09:30:00Z"));
        file.setOwnerDept("信息科");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("csvPath", csvPath.toString());
        props.put("errorPath", errorPath.toString());
        props.put("delimiter", ",");
        props.put("sheetName", "project_progress_raw");
        props.put("rowCount", 2);
        props.put("errorCount", 1);
        file.setProps(objectMapper.writeValueAsString(props));
        InfraExternalExchangeFile saved = exchangeFileRepository.saveAndFlush(file);

        mockMvc
            .perform(
                post("/api/infra/excel-import/project-cockpit/load")
                    .with(csrf())
                    .header("X-Active-Dept", "信息科")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(Map.of("fileId", saved.getId())))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.batchCode").value("excel-202603160002"))
            .andExpect(jsonPath("$.data.loadedRowCount").value(2))
            .andExpect(jsonPath("$.data.issueCount").value(1))
            .andExpect(jsonPath("$.data.status").value("LOADED"));

        org.assertj.core.api.Assertions.assertThat(
                jdbcTemplate.queryForObject("select count(*) from infra_project_cockpit_batch", Integer.class)
            )
            .isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(
                jdbcTemplate.queryForObject("select count(*) from infra_project_cockpit_row", Integer.class)
            )
            .isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(
                jdbcTemplate.queryForObject("select count(*) from infra_project_cockpit_issue", Integer.class)
            )
            .isEqualTo(1);
    }
}
