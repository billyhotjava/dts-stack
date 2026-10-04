package com.yuzhi.dts.copilot.analytics.service.elt;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "dts.elt.enabled", havingValue = "true")
public class ProjectFulfillmentSyncJob implements EltSyncJob {

    private static final Logger log = LoggerFactory.getLogger(ProjectFulfillmentSyncJob.class);

    private static final String TARGET_TABLE = "mart_project_fulfillment_daily";

    private static final String QUERY_ACTIVE_PROJECTS = """
            SELECT p.id AS project_id, p.name AS project_name, p.code AS project_code,
                   p.status AS project_status, p.type AS project_type,
                   p.contract_id, p.curing_director_name
            FROM p_project p
            WHERE p.del_flag = '0' AND p.status = 1
            """;

    private static final String QUERY_CONTRACT_CUSTOMER = """
            SELECT c.title AS contract_title, c.settlement_type, cu.name AS customer_name
            FROM p_contract c
            LEFT JOIN p_customer cu ON cu.id = c.customer_id AND cu.del_flag = '0'
            WHERE c.id = ? AND c.del_flag = '0'
            """;

    private static final String QUERY_PROJECT_ROLES = """
            SELECT user_name, project_manage, biz_manage
            FROM p_project_role
            WHERE project_id = ? AND status = 1
            """;

    private static final String QUERY_GREEN_STATS = """
            SELECT COUNT(*) AS green_count,
                   COALESCE(SUM(rent), 0) AS total_monthly_rent
            FROM p_project_green
            WHERE project_id = ? AND del_flag = '0' AND status = 1
            """;

    private static final String QUERY_POSITION_COUNT = """
            SELECT COUNT(*) AS position_count
            FROM p_position
            WHERE project_id = ? AND del_flag = '0' AND status = 1
            """;

    private static final String QUERY_BIZ_TODAY = """
            SELECT biz_type,
                   COUNT(*) AS cnt,
                   COALESCE(SUM(plant_number), 0) AS quantity
            FROM t_flower_biz_info
            WHERE project_id = ? AND del_flag = '0' AND DATE(apply_time) = CURDATE()
            GROUP BY biz_type
            """;

    private static final String QUERY_CURING_TODAY = """
            SELECT COUNT(*) AS curing_count
            FROM t_curing_record
            WHERE project_id = ? AND del_flag = '0' AND DATE(curing_time) = CURDATE()
            """;

    private static final String QUERY_TASK_COUNTS = """
            SELECT status, COUNT(*) AS cnt
            FROM t_daily_task_info
            WHERE project_id = ? AND del_flag = '0'
            GROUP BY status
            """;

    private static final String QUERY_LATEST_ACCOUNTING = """
            SELECT receivable_total_amount, net_receipt_total_amount, year_and_month
            FROM a_month_accounting
            WHERE project_id = ? AND status = 1
            ORDER BY year_and_month DESC
            LIMIT 1
            """;

    private static final String UPSERT_SQL = """
            INSERT INTO mart_project_fulfillment_daily (
                snapshot_date, project_id, project_name, project_code,
                project_status_name, project_type_name,
                customer_name, contract_title, settlement_type_name,
                manager_name, biz_user_name, curing_director_name,
                green_count, position_count, total_monthly_rent,
                add_flower_count, change_flower_count, cut_flower_count, transfer_flower_count,
                add_flower_quantity, change_flower_quantity, cut_flower_quantity,
                curing_count, curing_positions,
                pending_task_count, completed_task_count,
                settlement_month, monthly_receivable, monthly_received, monthly_outstanding,
                sync_batch_id, synced_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
            ON CONFLICT (project_id, snapshot_date) DO UPDATE SET
                project_name = EXCLUDED.project_name,
                project_code = EXCLUDED.project_code,
                project_status_name = EXCLUDED.project_status_name,
                project_type_name = EXCLUDED.project_type_name,
                customer_name = EXCLUDED.customer_name,
                contract_title = EXCLUDED.contract_title,
                settlement_type_name = EXCLUDED.settlement_type_name,
                manager_name = EXCLUDED.manager_name,
                biz_user_name = EXCLUDED.biz_user_name,
                curing_director_name = EXCLUDED.curing_director_name,
                green_count = EXCLUDED.green_count,
                position_count = EXCLUDED.position_count,
                total_monthly_rent = EXCLUDED.total_monthly_rent,
                add_flower_count = EXCLUDED.add_flower_count,
                change_flower_count = EXCLUDED.change_flower_count,
                cut_flower_count = EXCLUDED.cut_flower_count,
                transfer_flower_count = EXCLUDED.transfer_flower_count,
                add_flower_quantity = EXCLUDED.add_flower_quantity,
                change_flower_quantity = EXCLUDED.change_flower_quantity,
                cut_flower_quantity = EXCLUDED.cut_flower_quantity,
                curing_count = EXCLUDED.curing_count,
                curing_positions = EXCLUDED.curing_positions,
                pending_task_count = EXCLUDED.pending_task_count,
                completed_task_count = EXCLUDED.completed_task_count,
                settlement_month = EXCLUDED.settlement_month,
                monthly_receivable = EXCLUDED.monthly_receivable,
                monthly_received = EXCLUDED.monthly_received,
                monthly_outstanding = EXCLUDED.monthly_outstanding,
                sync_batch_id = EXCLUDED.sync_batch_id,
                synced_at = NOW()
            """;

    private final JdbcTemplate businessJdbcTemplate;
    private final JdbcTemplate analyticsJdbcTemplate;

    public ProjectFulfillmentSyncJob(EltDataSourceProvider dataSourceProvider, DataSource dataSource) {
        this.businessJdbcTemplate = dataSourceProvider.getBusinessJdbcTemplate();
        this.analyticsJdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public String getTargetTable() {
        return TARGET_TABLE;
    }

    @Override
    public int sync(Instant lastWatermark, String batchId) {
        LocalDate snapshotDate = LocalDate.now();

        List<Map<String, Object>> projects = businessJdbcTemplate.queryForList(QUERY_ACTIVE_PROJECTS);
        if (projects.isEmpty()) {
            log.info("No active projects found for snapshot");
            return 0;
        }

        List<Object[]> rows = new ArrayList<>(projects.size());

        for (Map<String, Object> project : projects) {
            Object projectId = project.get("project_id");
            Object contractId = project.get("contract_id");

            String customerName = null;
            String contractTitle = null;
            String settlementTypeName = null;
            if (contractId != null) {
                List<Map<String, Object>> contractRows =
                        businessJdbcTemplate.queryForList(QUERY_CONTRACT_CUSTOMER, contractId);
                if (!contractRows.isEmpty()) {
                    Map<String, Object> contract = contractRows.get(0);
                    customerName = (String) contract.get("customer_name");
                    contractTitle = (String) contract.get("contract_title");
                    Object settlementType = contract.get("settlement_type");
                    settlementTypeName = settlementType != null ? settlementType.toString() : null;
                }
            }

            String managerName = null;
            String bizUserName = null;
            List<Map<String, Object>> roles = businessJdbcTemplate.queryForList(QUERY_PROJECT_ROLES, projectId);
            for (Map<String, Object> role : roles) {
                String userName = (String) role.get("user_name");
                if (isTrue(role.get("project_manage"))) {
                    managerName = userName;
                }
                if (isTrue(role.get("biz_manage"))) {
                    bizUserName = userName;
                }
            }

            Map<String, Object> greenStats = businessJdbcTemplate.queryForMap(QUERY_GREEN_STATS, projectId);
            long greenCount = ((Number) greenStats.get("green_count")).longValue();
            BigDecimal totalMonthlyRent = defaultDecimal((BigDecimal) greenStats.get("total_monthly_rent"));

            long positionCount = businessJdbcTemplate.queryForObject(QUERY_POSITION_COUNT, Long.class, projectId);

            int addFlowerCount = 0;
            int changeFlowerCount = 0;
            int cutFlowerCount = 0;
            int transferFlowerCount = 0;
            int addFlowerQuantity = 0;
            int changeFlowerQuantity = 0;
            int cutFlowerQuantity = 0;
            List<Map<String, Object>> bizToday = businessJdbcTemplate.queryForList(QUERY_BIZ_TODAY, projectId);
            for (Map<String, Object> row : bizToday) {
                int bizType = ((Number) row.get("biz_type")).intValue();
                int cnt = ((Number) row.get("cnt")).intValue();
                int quantity = ((Number) row.get("quantity")).intValue();
                switch (bizType) {
                    case 1 -> {
                        changeFlowerCount = cnt;
                        changeFlowerQuantity = quantity;
                    }
                    case 2 -> {
                        addFlowerCount = cnt;
                        addFlowerQuantity = quantity;
                    }
                    case 3 -> {
                        cutFlowerCount = cnt;
                        cutFlowerQuantity = quantity;
                    }
                    case 4 -> transferFlowerCount = cnt;
                    default -> {
                        // Ignore other biz types in the mart snapshot.
                    }
                }
            }

            int curingCount = businessJdbcTemplate.queryForObject(QUERY_CURING_TODAY, Integer.class, projectId);

            int taskTotalCount = 0;
            int completedTaskCount = 0;
            List<Map<String, Object>> taskCounts = businessJdbcTemplate.queryForList(QUERY_TASK_COUNTS, projectId);
            for (Map<String, Object> taskCount : taskCounts) {
                int cnt = ((Number) taskCount.get("cnt")).intValue();
                taskTotalCount += cnt;
                Object statusObj = taskCount.get("status");
                if (statusObj != null && ((Number) statusObj).intValue() == 5) {
                    completedTaskCount = cnt;
                }
            }
            int pendingTaskCount = Math.max(0, taskTotalCount - completedTaskCount);

            BigDecimal monthlyReceivable = BigDecimal.ZERO;
            BigDecimal monthlyReceived = BigDecimal.ZERO;
            BigDecimal monthlyOutstanding = BigDecimal.ZERO;
            String settlementMonth = null;
            List<Map<String, Object>> accountingRows = businessJdbcTemplate.queryForList(
                    QUERY_LATEST_ACCOUNTING, projectId);
            if (!accountingRows.isEmpty()) {
                Map<String, Object> accounting = accountingRows.get(0);
                monthlyReceivable = defaultDecimal((BigDecimal) accounting.get("receivable_total_amount"));
                monthlyReceived = defaultDecimal((BigDecimal) accounting.get("net_receipt_total_amount"));
                monthlyOutstanding = monthlyReceivable.subtract(monthlyReceived);
                settlementMonth = normalizeYearMonth(accounting.get("year_and_month"));
            }

            rows.add(new Object[] {
                    Date.valueOf(snapshotDate),
                    projectId,
                    project.get("project_name"),
                    project.get("project_code"),
                    toStringValue(project.get("project_status")),
                    toStringValue(project.get("project_type")),
                    customerName,
                    contractTitle,
                    settlementTypeName,
                    managerName,
                    bizUserName,
                    project.get("curing_director_name"),
                    greenCount,
                    positionCount,
                    totalMonthlyRent,
                    addFlowerCount,
                    changeFlowerCount,
                    cutFlowerCount,
                    transferFlowerCount,
                    addFlowerQuantity,
                    changeFlowerQuantity,
                    cutFlowerQuantity,
                    curingCount,
                    curingCount,
                    pendingTaskCount,
                    completedTaskCount,
                    settlementMonth,
                    monthlyReceivable,
                    monthlyReceived,
                    monthlyOutstanding,
                    batchId
            });
        }

        List<List<Object[]>> batches = partition(rows, 500);
        for (List<Object[]> batch : batches) {
            analyticsJdbcTemplate.batchUpdate(UPSERT_SQL, batch);
        }

        log.info("Upserted {} project snapshot rows into {}", rows.size(), TARGET_TABLE);
        return rows.size();
    }

    private static boolean isTrue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue() == 1;
        }
        return "1".equals(value.toString()) || "true".equalsIgnoreCase(value.toString());
    }

    private static BigDecimal defaultDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String toStringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private static String normalizeYearMonth(Object value) {
        if (value == null) {
            return null;
        }
        String yearMonth = value.toString().trim();
        if (yearMonth.length() == 6) {
            return yearMonth.substring(0, 4) + "-" + yearMonth.substring(4, 6);
        }
        if (yearMonth.length() == 7) {
            return yearMonth;
        }
        if (yearMonth.length() == 8) {
            return yearMonth.substring(0, 4) + "-" + yearMonth.substring(4, 6);
        }
        return yearMonth;
    }

    private static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }
}
