package com.yuzhi.dts.analytics.service.projectcockpit;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ProjectCockpitWarehouseGateway {

    private static final Logger log = LoggerFactory.getLogger(ProjectCockpitWarehouseGateway.class);
    private static final List<String> READY_STATUSES = List.of("MODELED", "READY", "SUCCESS");

    private final ProjectCockpitWarehouseProperties properties;
    private final JdbcTemplate jdbcTemplate;

    public ProjectCockpitWarehouseGateway(
            ProjectCockpitWarehouseProperties properties,
            @Qualifier("projectCockpitWarehouseJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    public ProjectCockpitWarehouseSnapshot loadSnapshot() {
        if (!properties.isEnabled() || jdbcTemplate == null) {
            return ProjectCockpitWarehouseSnapshot.disabled();
        }
        ProjectCockpitBatchSummary batch = tryLoadLatestBatch();
        if (batch != null) {
            if (!batch.modeled()) {
                return ProjectCockpitWarehouseSnapshot.enabled(batch, List.of());
            }
            return ProjectCockpitWarehouseSnapshot.enabled(batch, safeLoadNodeRows());
        }
        List<ProjectCockpitWarehouseNode> nodes = safeLoadNodeRows();
        if (!nodes.isEmpty()) {
            return ProjectCockpitWarehouseSnapshot.enabled(fallbackBatch(nodes), nodes);
        }
        return ProjectCockpitWarehouseSnapshot.enabled(null, List.of());
    }

    private ProjectCockpitBatchSummary tryLoadLatestBatch() {
        try {
            return loadLatestBatch();
        } catch (DataAccessException ex) {
            log.warn("Project cockpit warehouse batch unavailable: {}", ex.getMessage());
            return null;
        }
    }

    private List<ProjectCockpitWarehouseNode> safeLoadNodeRows() {
        try {
            return loadNodeRows();
        } catch (DataAccessException ex) {
            log.warn("Project cockpit warehouse nodes unavailable: {}", ex.getMessage());
            return List.of();
        }
    }

    private ProjectCockpitBatchSummary fallbackBatch(List<ProjectCockpitWarehouseNode> nodes) {
        int totalRows = nodes.size();
        int unmappedSubprojectCount = (int) nodes.stream()
                .map(ProjectCockpitWarehouseNode::subprojectId)
                .filter(value -> value == null || value.isBlank())
                .count();
        int unknownDelayReasonCount = (int) nodes.stream()
                .map(ProjectCockpitWarehouseNode::delayReasonCategory)
                .filter(value -> value == null || value.isBlank() || Objects.equals("unknown", value))
                .count();
        return new ProjectCockpitBatchSummary(
                "",
                "",
                null,
                null,
                "READY",
                totalRows,
                totalRows,
                0,
                0,
                totalRows == 0 ? 0D : 1D,
                unmappedSubprojectCount,
                unknownDelayReasonCount);
    }

    private ProjectCockpitBatchSummary loadLatestBatch() {
        List<ProjectCockpitBatchSummary> rows = jdbcTemplate.query(
                """
                SELECT
                  batch_id,
                  source_file_name,
                  uploaded_at,
                  refreshed_at,
                  status,
                  total_rows,
                  valid_rows,
                  issue_rows,
                  issue_count,
                  coverage_rate,
                  unmapped_subproject_count,
                  unknown_delay_reason_count
                FROM pm_ods_project_progress_batch
                ORDER BY COALESCE(refreshed_at, uploaded_at) DESC, batch_id DESC
                LIMIT 1
                """,
                (rs, rowNum) -> new ProjectCockpitBatchSummary(
                        rs.getString("batch_id"),
                        rs.getString("source_file_name"),
                        toLocalDateTime(rs.getTimestamp("uploaded_at")),
                        toLocalDateTime(rs.getTimestamp("refreshed_at")),
                        rs.getString("status"),
                        rs.getInt("total_rows"),
                        rs.getInt("valid_rows"),
                        rs.getInt("issue_rows"),
                        rs.getInt("issue_count"),
                        rs.getBigDecimal("coverage_rate") == null ? 0D : rs.getBigDecimal("coverage_rate").doubleValue(),
                        rs.getInt("unmapped_subproject_count"),
                        rs.getInt("unknown_delay_reason_count")));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<ProjectCockpitWarehouseNode> loadNodeRows() {
        return jdbcTemplate.query(
                """
                SELECT
                  node_id,
                  project_no,
                  subsystem,
                  node_task,
                  node_type,
                  owner,
                  dept,
                  project_manager,
                  plan_date,
                  actual_date,
                  completion_status,
                  risk_level,
                  delay_days,
                  delay_reason_category,
                  major_project_id,
                  major_project_name,
                  program_id,
                  program_name,
                  subproject_id,
                  subproject_name,
                  incomplete_reason,
                  delay_impact,
                  is_key_node,
                  is_milestone,
                  major_project_owner_dept,
                  major_project_owner_leader,
                  subproject_owner_dept,
                  subproject_owner_user
                FROM biz_dwd_project_node_enriched
                ORDER BY major_project_id, subproject_id, plan_date, node_id
                """,
                (rs, rowNum) -> new ProjectCockpitWarehouseNode(
                        rs.getString("node_id"),
                        rs.getString("project_no"),
                        rs.getString("subsystem"),
                        rs.getString("node_task"),
                        rs.getString("node_type"),
                        rs.getString("owner"),
                        rs.getString("dept"),
                        rs.getString("project_manager"),
                        toLocalDate(rs.getDate("plan_date")),
                        toLocalDate(rs.getDate("actual_date")),
                        rs.getString("completion_status"),
                        rs.getString("risk_level"),
                        rs.getInt("delay_days"),
                        rs.getString("delay_reason_category"),
                        rs.getString("major_project_id"),
                        rs.getString("major_project_name"),
                        rs.getString("program_id"),
                        rs.getString("program_name"),
                        rs.getString("subproject_id"),
                        rs.getString("subproject_name"),
                        rs.getString("incomplete_reason"),
                        rs.getString("delay_impact"),
                        rs.getBoolean("is_key_node"),
                        rs.getBoolean("is_milestone"),
                        rs.getString("major_project_owner_dept"),
                        rs.getString("major_project_owner_leader"),
                        rs.getString("subproject_owner_dept"),
                        rs.getString("subproject_owner_user")));
    }

    private LocalDate toLocalDate(Date value) {
        return value == null ? null : value.toLocalDate();
    }

    private LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    public record ProjectCockpitWarehouseSnapshot(
            boolean warehouseEnabled,
            ProjectCockpitBatchSummary batch,
            List<ProjectCockpitWarehouseNode> nodes) {

        static ProjectCockpitWarehouseSnapshot disabled() {
            return new ProjectCockpitWarehouseSnapshot(false, null, List.of());
        }

        static ProjectCockpitWarehouseSnapshot enabled(ProjectCockpitBatchSummary batch, List<ProjectCockpitWarehouseNode> nodes) {
            return new ProjectCockpitWarehouseSnapshot(true, batch, nodes);
        }

        public boolean ready() {
            return batch != null && batch.modeled() && !nodes.isEmpty();
        }
    }

    public record ProjectCockpitBatchSummary(
            String batchId,
            String sourceFileName,
            LocalDateTime uploadedAt,
            LocalDateTime refreshedAt,
            String status,
            int totalRows,
            int validRows,
            int issueRows,
            int issueCount,
            double coverageRate,
            int unmappedSubprojectCount,
            int unknownDelayReasonCount) {

        public boolean modeled() {
            return READY_STATUSES.contains(Optional.ofNullable(status).orElse("").toUpperCase());
        }
    }

    public record ProjectCockpitWarehouseNode(
            String nodeId,
            String projectNo,
            String subsystem,
            String nodeTask,
            String nodeType,
            String owner,
            String dept,
            String projectManager,
            LocalDate planDate,
            LocalDate actualDate,
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
            String incompleteReason,
            String delayImpact,
            boolean keyNode,
            boolean milestone,
            String majorProjectOwnerDept,
            String majorProjectOwnerLeader,
            String subprojectOwnerDept,
            String subprojectOwnerUser) {}
}
