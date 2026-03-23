package com.yuzhi.dts.analytics.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.analytics.domain.AnalyticsSetting;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.repository.AnalyticsSettingRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsUserRepository;
import com.yuzhi.dts.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.analytics.service.projectcockpit.ProjectCockpitTopicBindingGateway;
import jakarta.servlet.http.Cookie;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;

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
    private AnalyticsSettingRepository settingRepository;

    @Autowired
    private AnalyticsSessionService sessionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private ProjectCockpitTopicBindingGateway topicBindingGateway;

    @BeforeEach
    void setUpWarehouseTables() {
        given(topicBindingGateway.currentProjectManagementState()).willReturn(
                new ProjectCockpitTopicBindingGateway.TopicBindingState(
                        true,
                        "已绑定",
                        "pm_ods",
                        "project_subject_domain",
                        "ods",
                        "pm_upload_20260316",
                        "项目管理专题当前绑定到 ods.pm_upload_20260316。"));
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
                .andExpect(jsonPath("$.hero.title").value("项目看板"))
                .andExpect(jsonPath("$.dataState.message", Matchers.containsString("暂无正式数据")))
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
                .andExpect(jsonPath("$.filters.programs").doesNotExist())
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
                .andExpect(jsonPath("$.missingChecklist[*].id", Matchers.hasItem("topic-binding-project-management")))
                .andExpect(jsonPath("$.missingChecklist[*].id", Matchers.hasItem("master-data-placeholder")))
                .andExpect(jsonPath("$.dataSources[*].name", Matchers.hasItem("topic_binding_project_subject_domain")))
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
    void projectCockpitShouldReturnEmptySummaryForUnknownProject() throws Exception {
        seedModeledBatch();
        seedNodeRows();
        Cookie sessionCookie = authenticate();

        mockMvc.perform(get("/api/project-cockpit/summary")
                        .cookie(sessionCookie)
                        .param("majorProjectId", "project-missing"))
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

    @Test
    void projectCockpitShouldExposeCommandCenterScreenEndpoints() throws Exception {
        seedModeledBatch();
        seedNodeRows();
        Cookie sessionCookie = authenticate();

        mockMvc.perform(get("/api/project-cockpit/screen/header").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("科研项目管理指挥大屏"))
                .andExpect(jsonPath("$.filters.current.programId").doesNotExist())
                .andExpect(jsonPath("$.filters.current.majorProjectId").value(""))
                .andExpect(jsonPath("$.dataState.ready").value(true));

        mockMvc.perform(get("/api/project-cockpit/screen/overview").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screenKey").value("overview"))
                .andExpect(jsonPath("$.kpis").isArray())
                .andExpect(jsonPath("$.kpis.length()").value(Matchers.greaterThan(0)));

        mockMvc.perform(get("/api/project-cockpit/screen/execution").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screenKey").value("execution"))
                .andExpect(jsonPath("$.milestoneKpis").isArray())
                .andExpect(jsonPath("$.ganttTasks").isArray());

        mockMvc.perform(get("/api/project-cockpit/screen/risk").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screenKey").value("risk"))
                .andExpect(jsonPath("$.changeKpis").isArray())
                .andExpect(jsonPath("$.riskBreakdown").isArray());
    }

    @Test
    void projectCockpitShouldReturnPublishedPeriodSettings() throws Exception {
        savePublishedPeriod("2026-03-01", "2026-03-31", "admin@example.com");
        Cookie sessionCookie = authenticate();

        mockMvc.perform(get("/api/project-cockpit/settings").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodStart").value("2026-03-01"))
                .andExpect(jsonPath("$.periodEnd").value("2026-03-31"))
                .andExpect(jsonPath("$.updatedBy").value("admin@example.com"))
                .andExpect(jsonPath("$.updatedAt").isString())
                .andExpect(jsonPath("$.canPublish").value(true));
    }

    @Test
    void projectCockpitShouldAllowSuperuserToPublishPeriodSettings() throws Exception {
        Cookie sessionCookie = authenticate();

        mockMvc.perform(put("/api/project-cockpit/settings")
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "periodStart": "2026-03-01",
                                  "periodEnd": "2026-03-31"
                                }
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/project-cockpit/settings").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodStart").value("2026-03-01"))
                .andExpect(jsonPath("$.periodEnd").value("2026-03-31"))
                .andExpect(jsonPath("$.updatedBy").value("admin@example.com"))
                .andExpect(jsonPath("$.canPublish").value(true));
    }

    @Test
    void projectCockpitShouldRejectNonSuperuserPublishingPeriodSettings() throws Exception {
        Cookie sessionCookie = authenticateRegularUser();

        mockMvc.perform(put("/api/project-cockpit/settings")
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "periodStart": "2026-03-01",
                                  "periodEnd": "2026-03-31"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void projectCockpitScreenMetricsShouldFollowProject3Definitions() throws Exception {
        seedModeledBatch();
        seedProject3MetricRows();
        Cookie sessionCookie = authenticate();

        mockMvc.perform(get("/api/project-cockpit/screen/overview")
                        .cookie(sessionCookie)
                        .param("dateFrom", "2026-03-01")
                        .param("dateTo", "2026-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kpis[0].key").value("periodNodeTotalCount"))
                .andExpect(jsonPath("$.kpis[0].value").value("8"))
                .andExpect(jsonPath("$.kpis[2].key").value("dueNodeCount"))
                .andExpect(jsonPath("$.kpis[2].value").value("7"))
                .andExpect(jsonPath("$.kpis[3].key").value("outsideCompletedCount"))
                .andExpect(jsonPath("$.kpis[3].value").value("1"))
                .andExpect(jsonPath("$.kpis[7].key").value("completedNodeCount"))
                .andExpect(jsonPath("$.kpis[7].value").value("3"))
                .andExpect(jsonPath("$.kpis[8].key").value("completionRate"))
                .andExpect(jsonPath("$.kpis[8].value").value("37.5"))
                .andExpect(jsonPath("$.kpis[9].key").value("onTimeRate"))
                .andExpect(jsonPath("$.kpis[9].value").value("12.5"))
                .andExpect(jsonPath("$.kpis[10].key").value("overdueCompletionRate"))
                .andExpect(jsonPath("$.kpis[10].value").value("22.22"));

        mockMvc.perform(get("/api/project-cockpit/screen/execution")
                        .cookie(sessionCookie)
                        .param("dateFrom", "2026-03-01")
                        .param("dateTo", "2026-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incompleteKpis[0].key").value("incompleteHighRiskCount"))
                .andExpect(jsonPath("$.incompleteKpis[0].value").value("3"))
                .andExpect(jsonPath("$.incompleteKpis[1].key").value("incompleteMidRiskCount"))
                .andExpect(jsonPath("$.incompleteKpis[1].value").value("1"))
                .andExpect(jsonPath("$.incompleteKpis[2].key").value("incompleteMilestoneCount"))
                .andExpect(jsonPath("$.incompleteKpis[2].value").value("1"))
                .andExpect(jsonPath("$.incompleteKpis[3].key").value("incompleteMajorCount"))
                .andExpect(jsonPath("$.incompleteKpis[3].value").value("2"))
                .andExpect(jsonPath("$.incompleteKpis[4].key").value("incompleteImportantCount"))
                .andExpect(jsonPath("$.incompleteKpis[4].value").value("1"))
                .andExpect(jsonPath("$.milestoneKpis[0].key").value("milestoneOnTimeCount"))
                .andExpect(jsonPath("$.milestoneKpis[0].value").value("1"))
                .andExpect(jsonPath("$.milestoneKpis[1].key").value("milestoneOverdueCompletedCount"))
                .andExpect(jsonPath("$.milestoneKpis[1].value").value("1"))
                .andExpect(jsonPath("$.milestoneKpis[2].key").value("milestonePendingCount"))
                .andExpect(jsonPath("$.milestoneKpis[2].value").value("1"))
                .andExpect(jsonPath("$.milestoneKpis[3].key").value("milestoneCompletionRate"))
                .andExpect(jsonPath("$.milestoneKpis[3].value").value("66.67"))
                .andExpect(jsonPath("$.milestoneKpis[4].key").value("highRiskNodeCount"))
                .andExpect(jsonPath("$.milestoneKpis[4].value").value("3"))
                .andExpect(jsonPath("$.milestoneKpis[5].key").value("midRiskNodeCount"))
                .andExpect(jsonPath("$.milestoneKpis[5].value").value("3"))
                .andExpect(jsonPath("$.milestoneKpis[6].key").value("milestoneTotalCount"))
                .andExpect(jsonPath("$.milestoneKpis[6].value").value("4"))
                .andExpect(jsonPath("$.milestoneKpis[7].key").value("majorNodeCount"))
                .andExpect(jsonPath("$.milestoneKpis[7].value").value("2"))
                .andExpect(jsonPath("$.milestoneKpis[8].key").value("importantNodeCount"))
                .andExpect(jsonPath("$.milestoneKpis[8].value").value("1"));

        mockMvc.perform(get("/api/project-cockpit/screen/risk")
                        .cookie(sessionCookie)
                        .param("dateFrom", "2026-03-01")
                        .param("dateTo", "2026-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changeKpis[0].key").value("abnormalPendingNonGeneralCount"))
                .andExpect(jsonPath("$.changeKpis[0].value").value("1"))
                .andExpect(jsonPath("$.changeKpis[1].key").value("overdueIncompleteUnchangedNonGeneralCount"))
                .andExpect(jsonPath("$.changeKpis[1].value").value("1"))
                .andExpect(jsonPath("$.changeKpis[2].key").value("overdueIncompleteChangedNonGeneralCount"))
                .andExpect(jsonPath("$.changeKpis[2].value").value("2"))
                .andExpect(jsonPath("$.changeKpis[3].key").value("overdueCompletedUnchangedNonGeneralCount"))
                .andExpect(jsonPath("$.changeKpis[3].value").value("1"))
                .andExpect(jsonPath("$.changeKpis[4].key").value("abnormalRate"))
                .andExpect(jsonPath("$.changeKpis[4].value").value("33.33"))
                .andExpect(jsonPath("$.changeKpis[5].key").value("overdueRate"))
                .andExpect(jsonPath("$.changeKpis[5].value").value("50.0"));
    }

    @Test
    void projectCockpitSummaryMetricsShouldFollowPublishedPeriodDefinitions() throws Exception {
        seedModeledBatch();
        seedProject3MetricRows();
        Cookie sessionCookie = authenticate();

        mockMvc.perform(get("/api/project-cockpit/summary")
                        .cookie(sessionCookie)
                        .param("dateFrom", "2026-03-01")
                        .param("dateTo", "2026-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kpis[0].key").value("majorProjectCount"))
                .andExpect(jsonPath("$.kpis[0].value").value("1"))
                .andExpect(jsonPath("$.kpis[1].key").value("subprojectCount"))
                .andExpect(jsonPath("$.kpis[1].value").value("4"))
                .andExpect(jsonPath("$.kpis[2].key").value("completionRate"))
                .andExpect(jsonPath("$.kpis[2].value").value("37.5"))
                .andExpect(jsonPath("$.kpis[3].key").value("overdueNodeCount"))
                .andExpect(jsonPath("$.kpis[3].value").value("6"))
                .andExpect(jsonPath("$.kpis[4].key").value("highRiskNodeCount"))
                .andExpect(jsonPath("$.kpis[4].value").value("3"))
                .andExpect(jsonPath("$.kpis[5].key").value("milestoneCompletionRate"))
                .andExpect(jsonPath("$.kpis[5].value").value("66.67"));
    }

    @Test
    void projectCockpitShouldFallbackToPublishedPeriodWhenDatesAreOmitted() throws Exception {
        seedModeledBatch();
        seedProject3MetricRows();
        savePublishedPeriod("2026-03-01", "2026-03-31", "admin@example.com");
        Cookie sessionCookie = authenticate();

        mockMvc.perform(get("/api/project-cockpit/screen/overview").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filters.current.dateFrom").value("2026-03-01"))
                .andExpect(jsonPath("$.filters.current.dateTo").value("2026-03-31"))
                .andExpect(jsonPath("$.kpis[0].key").value("periodNodeTotalCount"))
                .andExpect(jsonPath("$.kpis[0].value").value("8"))
                .andExpect(jsonPath("$.kpis[2].key").value("dueNodeCount"))
                .andExpect(jsonPath("$.kpis[2].value").value("7"))
                .andExpect(jsonPath("$.kpis[3].key").value("outsideCompletedCount"))
                .andExpect(jsonPath("$.kpis[3].value").value("1"))
                .andExpect(jsonPath("$.kpis[7].key").value("completedNodeCount"))
                .andExpect(jsonPath("$.kpis[7].value").value("3"))
                .andExpect(jsonPath("$.kpis[8].key").value("completionRate"))
                .andExpect(jsonPath("$.kpis[8].value").value("37.5"))
                .andExpect(jsonPath("$.kpis[9].key").value("onTimeRate"))
                .andExpect(jsonPath("$.kpis[9].value").value("12.5"))
                .andExpect(jsonPath("$.kpis[10].key").value("overdueCompletionRate"))
                .andExpect(jsonPath("$.kpis[10].value").value("22.22"));
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
                "sub-aurora-02",
                "总装联试子项目",
                "总体技术部",
                "张总",
                "总装二科",
                "李工"
        );
    }

    private void seedProject3MetricRows() {
        insertNodeDetailed(
                "metric-1",
                "SYS-M1",
                "方案评审",
                "里程碑节点",
                "2026-03-05",
                "2026-03-05",
                "按时完成",
                "低",
                0,
                "normal",
                "major-metrics",
                "项目指标主线",
                "sub-metrics-01",
                "里程碑子项目",
                "总体技术部",
                "张总",
                "软件一科",
                "王工",
                true,
                true
        );
        insertNodeDetailed(
                "metric-2",
                "SYS-M1",
                "初样交付",
                "里程碑节点",
                "2026-03-07",
                "2026-03-10",
                "超期已完成未变更",
                "中",
                3,
                "change",
                "major-metrics",
                "项目指标主线",
                "sub-metrics-01",
                "里程碑子项目",
                "总体技术部",
                "张总",
                "软件一科",
                "王工",
                true,
                true
        );
        insertNodeDetailed(
                "metric-3",
                "SYS-M1",
                "联试启动",
                "里程碑节点",
                "2026-03-12",
                null,
                "正常待完成",
                "中",
                0,
                "coordination",
                "major-metrics",
                "项目指标主线",
                "sub-metrics-01",
                "里程碑子项目",
                "总体技术部",
                "张总",
                "软件一科",
                "王工",
                true,
                true
        );
        insertNodeDetailed(
                "metric-4",
                "SYS-M2",
                "重大攻关",
                "重大节点",
                "2026-03-13",
                null,
                "不正常待变更",
                "高",
                5,
                "technical",
                "major-metrics",
                "项目指标主线",
                "sub-metrics-02",
                "重大节点子项目",
                "总体技术部",
                "张总",
                "总装二科",
                "李工",
                true,
                false
        );
        insertNodeDetailed(
                "metric-5",
                "SYS-M2",
                "关键器件到货",
                "重大节点",
                "2026-03-14",
                null,
                "超期未完成未变更",
                "高",
                7,
                "supplier",
                "major-metrics",
                "项目指标主线",
                "sub-metrics-02",
                "重大节点子项目",
                "总体技术部",
                "张总",
                "总装二科",
                "李工",
                true,
                false
        );
        insertNodeDetailed(
                "metric-6",
                "SYS-M3",
                "接口联调",
                "重要节点",
                "2026-03-15",
                null,
                "超期未完成已变更",
                "中",
                4,
                "coordination",
                "major-metrics",
                "项目指标主线",
                "sub-metrics-03",
                "重要节点子项目",
                "总体技术部",
                "张总",
                "测试三科",
                "赵工",
                true,
                false
        );
        insertNodeDetailed(
                "metric-7",
                "SYS-M4",
                "日报归档",
                "一般节点",
                "2026-03-16",
                null,
                "超期未完成未变更",
                "低",
                2,
                "archive",
                "major-metrics",
                "项目指标主线",
                "sub-metrics-04",
                "一般节点子项目",
                "总体技术部",
                "张总",
                "文档一科",
                "钱工",
                false,
                false
        );
        insertNodeDetailed(
                "metric-8",
                "SYS-M1",
                "正样评审",
                "里程碑节点",
                "2026-03-18",
                null,
                "超期未完成已变更",
                "高",
                6,
                "change",
                "major-metrics",
                "项目指标主线",
                "sub-metrics-01",
                "里程碑子项目",
                "总体技术部",
                "张总",
                "软件一科",
                "王工",
                true,
                true
        );
        insertNodeDetailed(
                "metric-9",
                "SYS-M5",
                "周期外完成",
                "一般节点",
                "2026-02-20",
                "2026-03-06",
                "超期已完成已变更",
                "低",
                10,
                "change",
                "major-metrics",
                "项目指标主线",
                "sub-metrics-05",
                "周期外子项目",
                "总体技术部",
                "张总",
                "综合保障科",
                "周工",
                false,
                false
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
            String subprojectId,
            String subprojectName,
            String majorOwnerDept,
            String majorOwnerLeader,
            String subOwnerDept,
            String subOwnerUser) {
        insertNodeDetailed(
                nodeId,
                subsystem,
                nodeTask,
                "里程碑节点",
                planDate,
                actualDate,
                completionStatus,
                riskLevel,
                delayDays,
                delayReasonCategory,
                majorProjectId,
                majorProjectName,
                subprojectId,
                subprojectName,
                majorOwnerDept,
                majorOwnerLeader,
                subOwnerDept,
                subOwnerUser,
                true,
                true
        );
    }

    private void insertNodeDetailed(
            String nodeId,
            String subsystem,
            String nodeTask,
            String nodeType,
            String planDate,
            String actualDate,
            String completionStatus,
            String riskLevel,
            int delayDays,
            String delayReasonCategory,
            String majorProjectId,
            String majorProjectName,
            String subprojectId,
            String subprojectName,
            String majorOwnerDept,
            String majorOwnerLeader,
            String subOwnerDept,
            String subOwnerUser,
            boolean isKeyNode,
            boolean isMilestone) {
        jdbcTemplate.update(
                """
                INSERT INTO biz_dwd_project_node_enriched (
                    node_id, project_no, subsystem, node_task, node_type, owner, dept, project_manager,
                    plan_date, actual_date, completion_status, risk_level, delay_days, delay_reason_category,
                    major_project_id, major_project_name, subproject_id, subproject_name,
                    incomplete_reason, delay_impact, is_key_node, is_milestone,
                    major_project_owner_dept, major_project_owner_leader, subproject_owner_dept, subproject_owner_user
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                nodeId,
                "P-" + subprojectId,
                subsystem,
                nodeTask,
                nodeType,
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
                subprojectId,
                subprojectName,
                delayDays > 0 ? "接口联调资源冲突" : "",
                delayDays > 0 ? "影响联试窗口" : "",
                isKeyNode,
                isMilestone,
                majorOwnerDept,
                majorOwnerLeader,
                subOwnerDept,
                subOwnerUser
        );
    }

    private Cookie authenticate() {
        return authenticate("admin@example.com", "Admin", true);
    }

    private Cookie authenticateRegularUser() {
        return authenticate("analyst@example.com", "Analyst", false);
    }

    private Cookie authenticate(String email, String firstName, boolean superuser) {
        AnalyticsUser user = userRepository.findByEmailIgnoreCase(email).orElseGet(() -> {
            AnalyticsUser row = new AnalyticsUser();
            row.setEmail(email);
            row.setFirstName(firstName);
            row.setLastName("User");
            row.setPasswordHash("test-hash");
            row.setSuperuser(superuser);
            row.setActive(true);
            return userRepository.save(row);
        });
        String sessionId = sessionService.createSession(user.getId()).toString();
        return new Cookie("metabase.SESSION", sessionId);
    }

    private void savePublishedPeriod(String periodStart, String periodEnd, String updatedBy) {
        AnalyticsSetting setting = settingRepository.findById("project-cockpit-period").orElseGet(() -> {
            AnalyticsSetting row = new AnalyticsSetting();
            row.setSettingKey("project-cockpit-period");
            return row;
        });
        setting.setSettingValue("""
                {
                  "periodStart": "%s",
                  "periodEnd": "%s",
                  "updatedBy": "%s"
                }
                """.formatted(periodStart, periodEnd, updatedBy));
        settingRepository.save(setting);
    }
}
