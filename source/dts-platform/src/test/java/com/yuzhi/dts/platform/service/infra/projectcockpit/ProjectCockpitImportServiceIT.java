package com.yuzhi.dts.platform.service.infra.projectcockpit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.IntegrationTest;
import com.yuzhi.dts.platform.domain.infra.InfraExternalExchangeFile;
import com.yuzhi.dts.platform.repository.infra.InfraExternalExchangeFileRepository;
import com.yuzhi.dts.platform.service.infra.ExcelImportService;
import jakarta.transaction.Transactional;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
@Transactional
class ProjectCockpitImportServiceIT {

    @Autowired
    private ExcelImportService excelImportService;

    @Autowired
    private InfraExternalExchangeFileRepository exchangeFileRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @TempDir
    Path tempDir;

    @Test
    void loadProjectCockpitBatchShouldPersistBatchRowsAndIssues() throws Exception {
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
        file.setBatchCode("excel-202603160001");
        file.setStatus("PARSED");
        file.setReceivedAt(Instant.parse("2026-03-16T09:00:00Z"));
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

        var response = excelImportService.loadProjectCockpitBatch(saved.getId(), "tester", "信息科", true);

        assertThat(response.batchCode()).isEqualTo("excel-202603160001");
        assertThat(response.loadedRowCount()).isEqualTo(2);
        assertThat(response.issueCount()).isEqualTo(1);
        assertThat(response.status()).isEqualTo("LOADED");

        assertThat(jdbcTemplate.queryForObject("select count(*) from infra_project_cockpit_batch", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from infra_project_cockpit_row", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("select count(*) from infra_project_cockpit_issue", Integer.class)).isEqualTo(1);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select count(*) from infra_project_cockpit_row where parse_status = 'PARSED_WITH_WARNINGS'",
                                Integer.class))
                .isEqualTo(1);
    }
}
