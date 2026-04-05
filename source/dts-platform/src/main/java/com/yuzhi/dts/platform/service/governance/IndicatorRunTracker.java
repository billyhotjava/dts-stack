package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorRun;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorDefinitionRepository;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorRunRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class IndicatorRunTracker {

    private static final Logger LOG = LoggerFactory.getLogger(IndicatorRunTracker.class);

    private final GovIndicatorDefinitionRepository indicatorRepo;
    private final GovIndicatorRunRepository runRepo;
    private final DataSource dataSource;

    public IndicatorRunTracker(
        GovIndicatorDefinitionRepository indicatorRepo,
        GovIndicatorRunRepository runRepo,
        DataSource dataSource
    ) {
        this.indicatorRepo = indicatorRepo;
        this.runRepo = runRepo;
        this.dataSource = dataSource;
    }

    /**
     * dbt run 完成后调用，采集所有已提交/已发布指标的最新计算值。
     */
    public void captureResults(String dbtRunId) {
        List<GovIndicatorDefinition> indicators = indicatorRepo
            .findByStatusIn(List.of("COMMITTED", "PUBLISHED"));

        for (GovIndicatorDefinition def : indicators) {
            if (def.getTargetModelName() == null) continue;
            try {
                captureOne(def, dbtRunId);
            } catch (Exception e) {
                LOG.warn("Failed to capture indicator {}: {}", def.getCode(), e.getMessage());
            }
        }
    }

    private void captureOne(GovIndicatorDefinition def, String dbtRunId) {
        long start = System.currentTimeMillis();

        String targetTable = def.getTargetModelName();
        String code = def.getCode();
        // 校验表名和列名安全（只允许合法标识符）
        if (!targetTable.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) return;
        if (code == null || !code.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) return;

        BigDecimal currentValue = null;
        int rowsProcessed = 0;
        String status = "SUCCESS";
        String errorMsg = null;

        try (Connection conn = dataSource.getConnection()) {
            // 查最新周期的聚合值
            String sql = "SELECT " + code + " FROM " + targetTable
                + " ORDER BY report_period DESC LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    currentValue = rs.getBigDecimal(1);
                }
            }

            // 查行数
            String countSql = "SELECT count(*) FROM " + targetTable;
            try (PreparedStatement ps = conn.prepareStatement(countSql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    rowsProcessed = rs.getInt(1);
                }
            }
        } catch (Exception e) {
            status = "FAILED";
            errorMsg = e.getMessage();
            LOG.warn("Failed to query indicator {} from {}: {}", def.getCode(), targetTable, e.getMessage());
        }

        long duration = System.currentTimeMillis() - start;

        // 获取上次运行的 computedValue 作为 previousValue
        Optional<GovIndicatorRun> lastRun = runRepo.findTopByIndicatorIdOrderByRunAtDesc(def.getId());
        BigDecimal previousValue = lastRun.map(GovIndicatorRun::getComputedValue).orElse(null);

        // 计算变化率
        BigDecimal changeRate = null;
        if (currentValue != null && previousValue != null && previousValue.compareTo(BigDecimal.ZERO) != 0) {
            changeRate = currentValue.subtract(previousValue)
                .divide(previousValue.abs(), 4, RoundingMode.HALF_UP);
        }

        // T03: 预警判定
        String alertLevel = determineAlertLevel(def, currentValue, previousValue);
        String alertReason = buildAlertReason(def, currentValue, alertLevel);

        // 写入记录
        GovIndicatorRun run = new GovIndicatorRun();
        run.setIndicatorId(def.getId());
        run.setRunAt(Instant.now());
        run.setStatus(status);
        run.setComputedValue(currentValue);
        run.setPreviousValue(previousValue);
        run.setChangeRate(changeRate);
        run.setRowsProcessed(rowsProcessed);
        run.setDurationMs((int) duration);
        run.setDbtRunId(dbtRunId);
        run.setAlertLevel(alertLevel);
        run.setAlertReason(alertReason);
        run.setThresholdHit(!"GREEN".equals(alertLevel));

        runRepo.save(run);
    }

    // ---- T03: 预警判定 ----

    private String determineAlertLevel(GovIndicatorDefinition def, BigDecimal value, BigDecimal prev) {
        if (value == null) return "RED";

        BigDecimal min = def.getThresholdMin();
        BigDecimal max = def.getThresholdMax();

        // 阈值判断
        boolean belowMin = min != null && value.compareTo(min) < 0;
        boolean aboveMax = max != null && value.compareTo(max) > 0;

        if (belowMin || aboveMax) {
            // 计算偏离度
            BigDecimal threshold = belowMin ? min : max;
            BigDecimal deviation = value.subtract(threshold).abs()
                .divide(threshold.abs().max(BigDecimal.ONE), 4, RoundingMode.HALF_UP);
            return deviation.compareTo(new BigDecimal("0.1")) > 0 ? "RED" : "YELLOW";
        }

        // 连续3次同向偏移检查
        List<GovIndicatorRun> recent = runRepo.findTop3ByIndicatorIdOrderByRunAtDesc(def.getId());
        if (isConsecutiveDrift(recent, def.getDirection())) {
            return "YELLOW";
        }

        return "GREEN";
    }

    private boolean isConsecutiveDrift(List<GovIndicatorRun> recent, String direction) {
        if (recent.size() < 3) return false;
        boolean allDecreasing = true;
        boolean allIncreasing = true;
        for (int i = 0; i < recent.size() - 1; i++) {
            BigDecimal curr = recent.get(i).getComputedValue();
            BigDecimal prev = recent.get(i + 1).getComputedValue();
            if (curr == null || prev == null) return false;
            if (curr.compareTo(prev) >= 0) allDecreasing = false;
            if (curr.compareTo(prev) <= 0) allIncreasing = false;
        }
        // HIGHER_BETTER 连续下降是预警，LOWER_BETTER 连续上升是预警
        if ("HIGHER_BETTER".equals(direction)) return allDecreasing;
        if ("LOWER_BETTER".equals(direction)) return allIncreasing;
        return false;
    }

    private String buildAlertReason(GovIndicatorDefinition def, BigDecimal value, String alertLevel) {
        if ("GREEN".equals(alertLevel)) return null;
        if (value == null) return "指标计算失败";
        if (def.getThresholdMin() != null && value.compareTo(def.getThresholdMin()) < 0) {
            return String.format("当前值 %s 低于阈值下限 %s", value, def.getThresholdMin());
        }
        if (def.getThresholdMax() != null && value.compareTo(def.getThresholdMax()) > 0) {
            return String.format("当前值 %s 高于阈值上限 %s", value, def.getThresholdMax());
        }
        return "连续 3 期同向偏移";
    }
}
