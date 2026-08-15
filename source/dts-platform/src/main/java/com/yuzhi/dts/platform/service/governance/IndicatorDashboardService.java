package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorRun;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorDefinitionRepository;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorRunRepository;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IndicatorDashboardService {

    private static final Logger log = LoggerFactory.getLogger(IndicatorDashboardService.class);
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");
    private static final int MAX_DRILLDOWN_ROWS = 500;

    private final GovIndicatorDefinitionRepository definitionRepo;
    private final GovIndicatorRunRepository runRepo;
    private final DataSource dataSource;

    public IndicatorDashboardService(
        GovIndicatorDefinitionRepository definitionRepo,
        GovIndicatorRunRepository runRepo,
        DataSource dataSource
    ) {
        this.definitionRepo = definitionRepo;
        this.runRepo = runRepo;
        this.dataSource = dataSource;
    }

    /**
     * Dashboard overview: published indicators with latest value, trend, and alert summary.
     */
    public Map<String, Object> getDashboard(String domain, int days) {
        List<GovIndicatorDefinition> definitions;
        if (StringUtils.hasText(domain)) {
            definitions = definitionRepo.findByDomainAndStatusNot(domain.trim(), "ARCHIVED");
        } else {
            definitions = definitionRepo.findByStatusIn(List.of("PUBLISHED", "DRAFT"));
        }

        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        int redCount = 0;
        int yellowCount = 0;
        int greenCount = 0;
        int noDataCount = 0;

        List<Map<String, Object>> cards = new ArrayList<>();
        for (GovIndicatorDefinition def : definitions) {
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("id", def.getId());
            card.put("code", def.getCode());
            card.put("name", def.getName());
            card.put("domain", def.getDomain());
            card.put("category", def.getCategory());
            card.put("unit", def.getUnit());
            card.put("direction", def.getDirection());
            card.put("thresholdMin", def.getThresholdMin());
            card.put("thresholdMax", def.getThresholdMax());
            card.put("status", def.getStatus());

            Optional<GovIndicatorRun> latestOpt = runRepo.findTopByIndicatorIdOrderByRunAtDesc(def.getId());
            latestOpt.ifPresentOrElse(latest -> {
                card.put("currentValue", latest.getComputedValue());
                card.put("changeRate", latest.getChangeRate());
                card.put("alertLevel", latest.getAlertLevel());
                card.put("lastRunAt", latest.getRunAt());
            }, () -> {
                card.put("currentValue", null);
                card.put("changeRate", null);
                card.put("alertLevel", null);
                card.put("lastRunAt", null);
            });
            String alert = (String) card.get("alertLevel");
            if ("RED".equals(alert)) redCount++;
            else if ("YELLOW".equals(alert)) yellowCount++;
            else if (alert != null) greenCount++;
            else noDataCount++;

            // Trend data within the time window
            List<GovIndicatorRun> runs = runRepo.findByIndicatorIdAndRunAtBetween(
                def.getId(), since, Instant.now()
            );
            List<Map<String, Object>> trend = new ArrayList<>();
            for (GovIndicatorRun r : runs) {
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("date", r.getRunAt().toString());
                point.put("value", r.getComputedValue());
                trend.add(point);
            }
            card.put("trend", trend);

            cards.add(card);
        }

        Map<String, Object> alertSummary = new LinkedHashMap<>();
        alertSummary.put("red", redCount);
        alertSummary.put("yellow", yellowCount);
        alertSummary.put("green", greenCount);
        alertSummary.put("noData", noDataCount);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("indicators", cards);
        result.put("alertSummary", alertSummary);
        result.put("total", definitions.size());
        return result;
    }

    /**
     * Indicator detail with run history.
     */
    public Map<String, Object> getDetail(UUID id, int days) {
        GovIndicatorDefinition def = definitionRepo.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Indicator not found: " + id));

        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        List<GovIndicatorRun> runs = runRepo.findByIndicatorIdAndRunAtBetween(id, since, Instant.now());

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("id", def.getId());
        meta.put("code", def.getCode());
        meta.put("name", def.getName());
        meta.put("definition", def.getDefinition());
        meta.put("domain", def.getDomain());
        meta.put("category", def.getCategory());
        meta.put("unit", def.getUnit());
        meta.put("direction", def.getDirection());
        meta.put("thresholdMin", def.getThresholdMin());
        meta.put("thresholdMax", def.getThresholdMax());
        meta.put("aggregationType", def.getAggregationType());
        meta.put("sourceTable", def.getSourceTable());
        meta.put("measureField", def.getMeasureField());
        meta.put("owner", def.getOwner());
        meta.put("ownerDept", def.getOwnerDept());
        meta.put("status", def.getStatus());

        Optional<GovIndicatorRun> latestOpt = runRepo.findTopByIndicatorIdOrderByRunAtDesc(id);
        latestOpt.ifPresent(latest -> {
            meta.put("currentValue", latest.getComputedValue());
            meta.put("changeRate", latest.getChangeRate());
            meta.put("alertLevel", latest.getAlertLevel());
            meta.put("lastRunAt", latest.getRunAt());
        });

        List<Map<String, Object>> trend = new ArrayList<>();
        for (GovIndicatorRun r : runs) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", r.getRunAt().toString());
            point.put("value", r.getComputedValue());
            point.put("alertLevel", r.getAlertLevel());
            trend.add(point);
        }

        List<Map<String, Object>> history = new ArrayList<>();
        List<GovIndicatorRun> allRuns = runRepo.findByIndicatorIdOrderByRunAtDesc(id);
        for (GovIndicatorRun r : allRuns) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", r.getId());
            row.put("runAt", r.getRunAt());
            row.put("status", r.getStatus());
            row.put("computedValue", r.getComputedValue());
            row.put("previousValue", r.getPreviousValue());
            row.put("changeRate", r.getChangeRate());
            row.put("alertLevel", r.getAlertLevel());
            row.put("alertReason", r.getAlertReason());
            row.put("rowsProcessed", r.getRowsProcessed());
            row.put("durationMs", r.getDurationMs());
            row.put("requestId", r.getDbtRunId());
            row.put("errorMessage", r.getErrorMessage());
            history.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("indicator", meta);
        result.put("trend", trend);
        result.put("history", history);
        return result;
    }

    /**
     * Drilldown into ADS table by dimension.
     */
    public List<Map<String, Object>> drilldown(UUID id, String dimension, String period) {
        if (!SAFE_IDENTIFIER.matcher(dimension).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Invalid dimension name: " + dimension);
        }

        GovIndicatorDefinition def = definitionRepo.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Indicator not found: " + id));

        String table = def.getSourceTable();
        if (!StringUtils.hasText(table)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Indicator has no source table configured");
        }

        // Validate table name as well
        if (!SAFE_IDENTIFIER.matcher(table.replace(".", "_").replace("-", "_")).matches()
            || !table.matches("^[a-zA-Z_][a-zA-Z0-9_.\\-]*$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Invalid source table name: " + table);
        }

        String measureField = StringUtils.hasText(def.getMeasureField()) ? def.getMeasureField() : "value";
        if (!SAFE_IDENTIFIER.matcher(measureField).matches()) {
            measureField = "value";
        }

        String aggType = StringUtils.hasText(def.getAggregationType()) ? def.getAggregationType().toUpperCase() : "SUM";
        String aggFn = switch (aggType) {
            case "AVG" -> "AVG";
            case "COUNT" -> "COUNT";
            case "MIN" -> "MIN";
            case "MAX" -> "MAX";
            default -> "SUM";
        };

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(dimension).append(" AS dimension, ");
        sql.append(aggFn).append("(").append(measureField).append(") AS metric_value, ");
        sql.append("COUNT(*) AS row_count ");
        sql.append("FROM ").append(table).append(" ");

        boolean hasPeriod = StringUtils.hasText(period);
        String dateColumn = StringUtils.hasText(def.getDateColumn()) ? def.getDateColumn() : "created_date";
        if (!SAFE_IDENTIFIER.matcher(dateColumn).matches()) {
            dateColumn = "created_date";
        }

        if (hasPeriod) {
            sql.append("WHERE DATE_TRUNC('month', ").append(dateColumn).append(") = DATE_TRUNC('month', CAST(? AS DATE)) ");
        }

        sql.append("GROUP BY ").append(dimension).append(" ");
        sql.append("ORDER BY metric_value DESC ");
        sql.append("LIMIT ").append(MAX_DRILLDOWN_ROWS);

        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            if (hasPeriod) {
                ps.setString(1, period);
            }
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData rsmd = rs.getMetaData();
                int colCount = rsmd.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        row.put(rsmd.getColumnLabel(i), rs.getObject(i));
                    }
                    rows.add(row);
                }
            }
        } catch (SQLException e) {
            log.warn("Drilldown query failed for indicator {}: {}", id, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Drilldown query failed: " + e.getMessage());
        }
        return rows;
    }
}
