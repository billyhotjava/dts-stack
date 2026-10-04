package com.yuzhi.dts.copilot.analytics.service.elt;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
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
public class FieldOperationSyncJob implements EltSyncJob {

    private static final Logger log = LoggerFactory.getLogger(FieldOperationSyncJob.class);

    private static final String TARGET_TABLE = "fact_field_operation_event";

    private static final Map<Integer, String> BIZ_TYPE_MAP = Map.ofEntries(
            Map.entry(1, "换花"),
            Map.entry(2, "加花"),
            Map.entry(3, "减花"),
            Map.entry(4, "调花"),
            Map.entry(5, "售花"),
            Map.entry(6, "坏账"),
            Map.entry(7, "销售"),
            Map.entry(8, "内购"),
            Map.entry(10, "辅料"),
            Map.entry(11, "加盆架"),
            Map.entry(12, "减盆架"));

    private static final Map<Integer, String> STATUS_MAP = Map.ofEntries(
            Map.entry(-1, "作废"),
            Map.entry(1, "审核中"),
            Map.entry(2, "备货中"),
            Map.entry(3, "核算中"),
            Map.entry(4, "待结算"),
            Map.entry(5, "已完成"),
            Map.entry(20, "草稿"),
            Map.entry(21, "驳回"));

    private static final Map<Integer, String> URGENT_MAP = Map.of(
            1, "是",
            2, "否");

    private static final Map<Integer, String> BEAR_COST_TYPE_MAP = Map.of(
            1, "养护人",
            2, "领导",
            3, "公司",
            4, "客户");

    private static final String QUERY_BIZ = """
            SELECT bi.id,
                   bi.code,
                   bi.biz_type,
                   bi.status,
                   bi.urgent,
                   bi.project_id,
                   COALESCE(bi.project_name, p.name) AS project_name,
                   c.name AS customer_name,
                   bi.apply_use_name,
                   bi.curing_user_name,
                   bi.bear_cost_type,
                   bi.apply_time,
                   bi.finish_time,
                   bi.biz_total_rent,
                   bi.biz_total_cost,
                   bi.update_time,
                   bi.project_manage_name AS manager_name,
                   (
                       SELECT COALESCE(SUM(item.plant_number), 0)
                       FROM t_flower_biz_item item
                       WHERE item.flower_biz_id = bi.id
                   ) AS total_plant_number
            FROM t_flower_biz_info bi
            LEFT JOIN p_project p ON bi.project_id = p.id
            LEFT JOIN p_contract ct ON p.contract_id = ct.id
            LEFT JOIN p_customer c ON ct.customer_id = c.id
            WHERE bi.del_flag = '0' AND bi.update_time > ?
            ORDER BY bi.update_time ASC
            """;

    private static final String UPSERT_SQL = """
            INSERT INTO fact_field_operation_event (
                biz_id, biz_code,
                event_date, event_month, event_year,
                biz_type_name, biz_status_name, is_urgent,
                project_id, project_name, customer_name, manager_name,
                apply_user_name, curing_user_name, bear_cost_type_name,
                plant_number, total_rent, total_cost,
                apply_time, finish_time,
                source_updated_at, sync_batch_id, synced_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
            ON CONFLICT (biz_id) DO UPDATE SET
                biz_code = EXCLUDED.biz_code,
                event_date = EXCLUDED.event_date,
                event_month = EXCLUDED.event_month,
                event_year = EXCLUDED.event_year,
                biz_type_name = EXCLUDED.biz_type_name,
                biz_status_name = EXCLUDED.biz_status_name,
                is_urgent = EXCLUDED.is_urgent,
                project_id = EXCLUDED.project_id,
                project_name = EXCLUDED.project_name,
                customer_name = EXCLUDED.customer_name,
                manager_name = EXCLUDED.manager_name,
                apply_user_name = EXCLUDED.apply_user_name,
                curing_user_name = EXCLUDED.curing_user_name,
                bear_cost_type_name = EXCLUDED.bear_cost_type_name,
                plant_number = EXCLUDED.plant_number,
                total_rent = EXCLUDED.total_rent,
                total_cost = EXCLUDED.total_cost,
                apply_time = EXCLUDED.apply_time,
                finish_time = EXCLUDED.finish_time,
                source_updated_at = EXCLUDED.source_updated_at,
                sync_batch_id = EXCLUDED.sync_batch_id,
                synced_at = NOW()
            """;

    private final JdbcTemplate businessJdbcTemplate;
    private final JdbcTemplate analyticsJdbcTemplate;

    public FieldOperationSyncJob(EltDataSourceProvider dataSourceProvider, DataSource dataSource) {
        this.businessJdbcTemplate = dataSourceProvider.getBusinessJdbcTemplate();
        this.analyticsJdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public String getTargetTable() {
        return TARGET_TABLE;
    }

    @Override
    public int sync(Instant lastWatermark, String batchId) {
        Timestamp watermarkTs = Timestamp.from(lastWatermark);
        List<Object[]> rows = businessJdbcTemplate.query(QUERY_BIZ, (rs, rowNum) -> mapRow(rs, batchId), watermarkTs);

        if (rows.isEmpty()) {
            log.info("No new rows in t_flower_biz_info since {}", lastWatermark);
            return 0;
        }

        List<List<Object[]>> batches = partition(rows, 500);
        for (List<Object[]> batch : batches) {
            analyticsJdbcTemplate.batchUpdate(UPSERT_SQL, batch);
        }

        log.info("Upserted {} rows into {}", rows.size(), TARGET_TABLE);
        return rows.size();
    }

    private Object[] mapRow(ResultSet rs, String batchId) throws SQLException {
        int bizType = rs.getInt("biz_type");
        int status = rs.getInt("status");
        int urgent = rs.getInt("urgent");
        int bearCostType = rs.getInt("bear_cost_type");

        Timestamp eventTimestamp = firstNonNull(
                rs.getTimestamp("apply_time"),
                rs.getTimestamp("finish_time"),
                rs.getTimestamp("update_time"));
        LocalDate eventDate = eventTimestamp.toLocalDateTime().toLocalDate();

        return new Object[] {
                rs.getLong("id"),
                rs.getString("code"),
                java.sql.Date.valueOf(eventDate),
                String.format("%04d-%02d", eventDate.getYear(), eventDate.getMonthValue()),
                eventDate.getYear(),
                BIZ_TYPE_MAP.getOrDefault(bizType, String.valueOf(bizType)),
                STATUS_MAP.getOrDefault(status, String.valueOf(status)),
                URGENT_MAP.getOrDefault(urgent, String.valueOf(urgent)),
                rs.getObject("project_id"),
                rs.getString("project_name"),
                rs.getString("customer_name"),
                rs.getString("manager_name"),
                rs.getString("apply_use_name"),
                rs.getString("curing_user_name"),
                BEAR_COST_TYPE_MAP.getOrDefault(bearCostType, String.valueOf(bearCostType)),
                rs.getInt("total_plant_number"),
                defaultDecimal(rs.getBigDecimal("biz_total_rent")),
                defaultDecimal(rs.getBigDecimal("biz_total_cost")),
                rs.getTimestamp("apply_time"),
                rs.getTimestamp("finish_time"),
                rs.getTimestamp("update_time"),
                batchId
        };
    }

    private static Timestamp firstNonNull(Timestamp... values) {
        for (Timestamp value : values) {
            if (value != null) {
                return value;
            }
        }
        return Timestamp.from(Instant.now());
    }

    private static BigDecimal defaultDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }
}
