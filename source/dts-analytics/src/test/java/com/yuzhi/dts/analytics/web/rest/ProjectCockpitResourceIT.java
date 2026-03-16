package com.yuzhi.dts.analytics.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.repository.AnalyticsUserRepository;
import com.yuzhi.dts.analytics.service.AnalyticsSessionService;
import jakarta.servlet.http.Cookie;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ProjectCockpitResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AnalyticsUserRepository userRepository;

    @Autowired
    private AnalyticsSessionService sessionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpWarehouseTables() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS pm_ods_project_progress_batch");
        jdbcTemplate.execute("DROP TABLE IF EXISTS biz_dwd_project_node_enriched");
        jdbcTemplate.execute(
                """
                CREATE TABLE pm_ods_project_progress_batch (
                    batch_id VARCHAR(128) PRIMARY KEY,
                    source_file_name VARCHAR(255),
                    uploaded_at TIMESTAMP,
                    refreshed_at TIMESTAMP,
                    status VARCHAR(64),
                    total_rows INT,
                    valid_rows INT,
                    issue_rows INT,
                    issue_count INT,
                    coverage_rate DECIMAL(10, 4),
                    unmapped_subproject_count INT,
                    unknown_delay_reason_count INT
                )
                """
        );
        jdbcTemplate.execute(
                """
                CREATE TABLE biz_dwd_project_node_enriched (
                    node_id VARCHAR(128) PRIMARY KEY,
                    project_no VARCHAR(128),
                    subsystem VARCHAR(255),
                    node_task VARCHAR(255),
                    node_type VARCHAR(64),
                    owner VARCHAR(128),
                    dept VARCHAR(128),
                    project_manager VARCHAR(128),
                    plan_date DATE,
                    actual_date DATE,
                    completion_status VARCHAR(64),
                    risk_level VARCHAR(32),
                    delay_days INT,
                    delay_reason_category VARCHAR(64),
                    major_project_id VARCHAR(128),
                    major_project_name VARCHAR(255),
                    program_id VARCHAR(128),
                    program_name VARCHAR(255),
                    subproject_id VARCHAR(128),
                    subproject_name VARCHAR(255),
                    incomplete_reason VARCHAR(255),
                    delay_impact VARCHAR(255),
                    is_key_node BOOLEAN,
                    is_milestone BOOLEAN,
                    major_project_owner_dept VARCHAR(128),
                    major_project_owner_leader VARCHAR(128),
                    subproject_owner_dept VARCHAR(128),
                    subproject_owner_user VARCHAR(128)
                )
                """
        );
    }

    @Test
    void projectCockpitShouldReturnFormalEmptyStateWhenWarehouseHasNoBatch() throws Exception {
        Cookie sessionCookie = authenticate();

        mockMvc.perform(get("/api/project-cockpit/summary").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hero.title").value("项目看板系统"))
                .andExpect(jsonPath("$.hero.subtitle", Matchers.containsString("暂无正式数据")))
                .andExpect(jsonPath("$.dataState.ready").value(false))
                .andExpect(jsonPath("$.kpis[0].key").value("majorProjectCount"));
    }

    @Test
    void projectCockpitShouldFallbackToEnrichedDataWhenBatchTableIsMissing() throws Exception {
        jdbcTemplate.execute("DROP TABLE IF EXISTS pm_ods_project_progress_batch");
        seedNodeRows();
        Cookie sessionCookie = authenticate();

        mockMvc.perform(get("/api/project-cockpit/summary").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataState.ready").value(true))
                .andExpect(jsonPath("$.dataState.status").value("READY"))
                .andExpect(jsonPath("$.kpis[0].value").value("1"));

        mockMvc.perform(get("/api/project-cockpit/data-support").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batch.status").value("READY"))
                .andExpect(jsonPath("$.batch.totalRows").value(3))
                .andExpect(jsonPath("$.dataState.ready").value(true));
    }

    @Test
    void projectCockpitShouldTreatLoadedBatchWithModeledRowsAsReady() throws Exception {
        seedBatch("LOADED");
        seedNodeRows();
        Cookie sessionCookie = authenticate();

        mockMvc.perform(get("/api/project-cockpit/summary").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataState.ready").value(true))
                .andExpect(jsonPath("$.dataState.status").value("READY"))
                .andExpect(jsonPath("$.kpis[0].value").value("1"));

        mockMvc.perform(get("/api/project-cockpit/data-support").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batch.status").value("READY"))
                .andExpect(jsonPath("$.dataState.ready").value(true));
    }

    @Test
    void projectCockpitShouldExposeWarehouseBackedSummaryTreeAndSupportMetadata() throws Exception {
        seedModeledBatch();
        seedNodeRows();
        Cookie sessionCookie = authenticate();

        mockMvc.perform(get("/api/project-cockpit/summary").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataState.ready").value(true))
                .andExpect(jsonPath("$.filters.programs[0].value").value("program-a"))
                .andExpect(jsonPath("$.kpis[0].value").value("1"));

        mockMvc.perform(get("/api/project-cockpit/major-project-tree")
                        .cookie(sessionCookie)
                        .param("majorProjectId", "major-aurora"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedMajorProjectId").value("major-aurora"))
                .andExpect(jsonPath("$.tree[0].level").value("major"))
                .andExpect(jsonPath("$.tree[0].children[0].level").value("subproject"));

        mockMvc.perform(get("/api/project-cockpit/data-support").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batch.batchId").value("batch-20260316-001"))
                .andExpect(jsonPath("$.batch.status").value("MODELED"))
                .andExpect(jsonPath("$.quality.issueCount").value(12))
                .andExpect(jsonPath("$.missingChecklist[*].id", Matchers.hasItem("master-data-placeholder")))
                .andExpect(jsonPath("$.dataSources[*].name", Matchers.hasItem("project_master_data_placeholder")))
                .andExpect(jsonPath("$.dataState.ready").value(true));
    }

    @Test
    void projectCockpitShouldFilterRiskAttributionByRiskLevel() throws Exception {
        seedModeledBatch();
        seedNodeRows();
        Cookie sessionCookie = authenticate();

        mockMvc.perform(get("/api/project-cockpit/risk-attribution")
                        .cookie(sessionCookie)
                        .param("riskLevel", "高"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskBreakdown[0].name").value("高"))
                .andExpect(jsonPath("$.delayedProjects[0].riskLevel").value("高"));
    }

    @Test
    void projectCockpitShouldReturnEmptySummaryForUnknownProgram() throws Exception {
        seedModeledBatch();
        seedNodeRows();
        Cookie sessionCookie = authenticate();

        mockMvc.perform(get("/api/project-cockpit/summary")
                        .cookie(sessionCookie)
                        .param("programId", "program-missing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataState.ready").value(true))
                .andExpect(jsonPath("$.kpis[0].value").value("0"))
                .andExpect(jsonPath("$.ranking").isArray())
                .andExpect(jsonPath("$.ranking.length()").value(0));
    }

    @Test
    void projectCockpitShouldExposeOtherThemeEndpoints() throws Exception {
        seedModeledBatch();
        seedNodeRows();
        Cookie sessionCookie = authenticate();

        mockMvc.perform(get("/api/project-cockpit/trends").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekly").isArray());

        mockMvc.perform(get("/api/project-cockpit/execution").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ganttTasks").isArray());

        mockMvc.perform(get("/api/project-cockpit/data-support").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batch").isMap())
                .andExpect(jsonPath("$.glossary").isArray());
    }

    private void seedModeledBatch() {
        seedBatch("MODELED");
    }

    private void seedBatch(String status) {
        jdbcTemplate.update(
                """
                INSERT INTO pm_ods_project_progress_batch (
                    batch_id, source_file_name, uploaded_at, refreshed_at, status,
                    total_rows, valid_rows, issue_rows, issue_count, coverage_rate,
                    unmapped_subproject_count, unknown_delay_reason_count
                ) VALUES (?, ?, TIMESTAMP '2026-03-16 09:00:00', TIMESTAMP '2026-03-16 09:10:00', ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "batch-20260316-001",
                "project-cockpit-test-batch-2000.xlsx",
                status,
                2000,
                1836,
                164,
                12,
                0.9180,
                4,
                9
        );
    }

    private void seedNodeRows() {
        insertNode(
                "node-1",
                "SYS-A",
                "需求评审",
                "2026-03-03",
                "2026-03-02",
                "按时完成",
                "低",
                0,
                "normal",
                "major-aurora",
                "北斗融合主线",
                "program-a",
                "项目群A",
                "sub-aurora-01",
                "导航底座子项目",
                "总体技术部",
                "张总",
                "软件一科",
                "王工"
        );
        insertNode(
                "node-2",
                "SYS-A",
                "接口联调",
                "2026-03-08",
                null,
                "超期未完成未变更",
                "高",
                5,
                "coordination",
                "major-aurora",
                "北斗融合主线",
                "program-a",
                "项目群A",
                "sub-aurora-01",
                "导航底座子项目",
                "总体技术部",
                "张总",
                "软件一科",
                "王工"
        );
        insertNode(
                "node-3",
                "SYS-B",
                "整机联试",
                "2026-03-10",
                null,
                "正常待完成",
                "中",
                0,
                "test",
                "major-aurora",
                "北斗融合主线",
                "program-a",
                "项目群A",
                "sub-aurora-02",
                "总装联试子项目",
                "总体技术部",
                "张总",
                "总装二科",
                "李工"
        );
    }

    private void insertNode(
            String nodeId,
            String subsystem,
            String nodeTask,
            String planDate,
            String actualDate,
            String completionStatus,
            String riskLevel,
            int delayDays,
            String delayReasonCategory,
            String majorProjectId,
            String majorProjectName,
            String programId,
            String programName,
            String subprojectId,
            String subprojectName,
            String majorOwnerDept,
            String majorOwnerLeader,
            String subOwnerDept,
            String subOwnerUser) {
        jdbcTemplate.update(
                """
                INSERT INTO biz_dwd_project_node_enriched (
                    node_id, project_no, subsystem, node_task, node_type, owner, dept, project_manager,
                    plan_date, actual_date, completion_status, risk_level, delay_days, delay_reason_category,
                    major_project_id, major_project_name, program_id, program_name, subproject_id, subproject_name,
                    incomplete_reason, delay_impact, is_key_node, is_milestone,
                    major_project_owner_dept, major_project_owner_leader, subproject_owner_dept, subproject_owner_user
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                nodeId,
                "P-" + subprojectId,
                subsystem,
                nodeTask,
                "里程碑",
                subOwnerUser,
                subOwnerDept,
                "项目经理-" + subprojectId,
                java.sql.Date.valueOf(planDate),
                actualDate == null ? null : java.sql.Date.valueOf(actualDate),
                completionStatus,
                riskLevel,
                delayDays,
                delayReasonCategory,
                majorProjectId,
                majorProjectName,
                programId,
                programName,
                subprojectId,
                subprojectName,
                delayDays > 0 ? "接口联调资源冲突" : "",
                delayDays > 0 ? "影响联试窗口" : "",
                Boolean.TRUE,
                Boolean.TRUE,
                majorOwnerDept,
                majorOwnerLeader,
                subOwnerDept,
                subOwnerUser
        );
    }

    private Cookie authenticate() {
        AnalyticsUser admin = userRepository.findByEmailIgnoreCase("admin@example.com").orElseGet(() -> {
            AnalyticsUser row = new AnalyticsUser();
            row.setEmail("admin@example.com");
            row.setFirstName("Admin");
            row.setLastName("User");
            row.setPasswordHash("test-hash");
            row.setSuperuser(true);
            row.setActive(true);
            return userRepository.save(row);
        });
        String sessionId = sessionService.createSession(admin.getId()).toString();
        return new Cookie("metabase.SESSION", sessionId);
    }
}
