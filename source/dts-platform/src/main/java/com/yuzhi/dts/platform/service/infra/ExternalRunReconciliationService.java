package com.yuzhi.dts.platform.service.infra;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.infra.InfraExternalRunLog;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ExternalRunReconciliationService {

    public record DailyReport(
        LocalDate date,
        int total,
        int okCount,
        int failedCount,
        int unknownCount,
        List<Map<String, Object>> failedSamples
    ) {}

    private final ObjectMapper objectMapper;

    public ExternalRunReconciliationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> evaluate(InfraExternalRunLog run, Integer rowTolerance, BigDecimal amountTolerance) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("runId", run != null && run.getId() != null ? run.getId().toString() : null);
        payload.put("entryKey", run != null ? run.getEntryKey() : null);
        payload.put("externalRunId", run != null ? run.getExternalRunId() : null);
        payload.put("status", run != null ? run.getStatus() : null);
        payload.put("finishedAt", run != null ? run.getFinishedAt() : null);

        Map<String, Object> metrics = parseMetrics(run != null ? run.getMetricsJson() : null);
        payload.put("metrics", metrics);

        int safeRowTol = rowTolerance != null ? Math.max(0, rowTolerance) : 0;
        BigDecimal safeAmountTol = amountTolerance != null && amountTolerance.compareTo(BigDecimal.ZERO) >= 0 ? amountTolerance : BigDecimal.ZERO;
        payload.put("rowCountTolerance", safeRowTol);
        payload.put("amountTolerance", safeAmountTol);

        List<Map<String, Object>> checks = new ArrayList<>();

        Optional<Long> srcRows = readLong(metrics, "sourceRowCount");
        Optional<Long> tgtRows = readLong(metrics, "targetRowCount");
        if (srcRows.isPresent() || tgtRows.isPresent()) {
            checks.add(compareLong("rowCount", "行数对账", srcRows.orElse(null), tgtRows.orElse(null), safeRowTol));
        }

        Optional<BigDecimal> srcAmount = readDecimal(metrics, "sourceAmount");
        Optional<BigDecimal> tgtAmount = readDecimal(metrics, "targetAmount");
        if (srcAmount.isPresent() || tgtAmount.isPresent()) {
            checks.add(compareDecimal("amount", "金额对账", srcAmount.orElse(null), tgtAmount.orElse(null), safeAmountTol));
        }

        payload.put("checks", checks);

        String overall = computeOverallStatus(checks);
        payload.put("overallStatus", overall);
        payload.put("ok", "OK".equals(overall));
        return payload;
    }

    public DailyReport dailyReport(List<InfraExternalRunLog> runs, LocalDate date) {
        int ok = 0;
        int failed = 0;
        int unknown = 0;
        List<Map<String, Object>> failedSamples = new ArrayList<>();
        if (runs != null) {
            for (InfraExternalRunLog run : runs) {
                Map<String, Object> eval = evaluate(run, 0, BigDecimal.ZERO);
                String status = Objects.toString(eval.get("overallStatus"), "UNKNOWN");
                if ("OK".equals(status)) {
                    ok++;
                } else if ("FAILED".equals(status)) {
                    failed++;
                    if (failedSamples.size() < 20) {
                        failedSamples.add(eval);
                    }
                } else {
                    unknown++;
                }
            }
        }
        return new DailyReport(date, runs != null ? runs.size() : 0, ok, failed, unknown, failedSamples);
    }

    public static Instant dayStartUtc(LocalDate date) {
        LocalDate d = date != null ? date : LocalDate.now(ZoneOffset.UTC);
        return d.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    public static Instant dayEndUtc(LocalDate date) {
        return dayStartUtc(date).plusSeconds(24 * 3600);
    }

    private Map<String, Object> parseMetrics(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            return raw != null ? raw : Map.of();
        } catch (Exception ex) {
            return Map.of("_parseError", safeMessage(ex.getMessage()));
        }
    }

    private Map<String, Object> compareLong(String key, String title, Long left, Long right, int tolerance) {
        Map<String, Object> check = new LinkedHashMap<>();
        check.put("key", key);
        check.put("title", title);
        check.put("left", left);
        check.put("right", right);
        check.put("tolerance", tolerance);
        if (left == null || right == null) {
            check.put("status", "UNKNOWN");
            check.put("message", "缺少对账指标（source/target 任一为空）");
            return check;
        }
        long diff = Math.abs(left - right);
        check.put("diff", diff);
        if (diff <= tolerance) {
            check.put("status", "OK");
        } else {
            check.put("status", "FAILED");
            check.put("message", "行数不一致");
        }
        return check;
    }

    private Map<String, Object> compareDecimal(String key, String title, BigDecimal left, BigDecimal right, BigDecimal tolerance) {
        Map<String, Object> check = new LinkedHashMap<>();
        check.put("key", key);
        check.put("title", title);
        check.put("left", left);
        check.put("right", right);
        check.put("tolerance", tolerance);
        if (left == null || right == null) {
            check.put("status", "UNKNOWN");
            check.put("message", "缺少对账指标（source/target 任一为空）");
            return check;
        }
        BigDecimal diff = left.subtract(right).abs();
        check.put("diff", diff);
        if (diff.compareTo(tolerance) <= 0) {
            check.put("status", "OK");
        } else {
            check.put("status", "FAILED");
            check.put("message", "金额不一致");
        }
        return check;
    }

    private String computeOverallStatus(List<Map<String, Object>> checks) {
        if (checks == null || checks.isEmpty()) {
            return "UNKNOWN";
        }
        boolean hasKnown = false;
        for (Map<String, Object> check : checks) {
            String st = check != null ? Objects.toString(check.get("status"), "") : "";
            if ("FAILED".equalsIgnoreCase(st)) return "FAILED";
            if ("OK".equalsIgnoreCase(st)) hasKnown = true;
        }
        return hasKnown ? "OK" : "UNKNOWN";
    }

    private Optional<Long> readLong(Map<String, Object> metrics, String key) {
        if (metrics == null || !StringUtils.hasText(key)) return Optional.empty();
        Object raw = metrics.get(key);
        if (raw == null) return Optional.empty();
        if (raw instanceof Number n) {
            return Optional.of(n.longValue());
        }
        String s = String.valueOf(raw).trim();
        if (s.isEmpty()) return Optional.empty();
        try {
            if (s.contains(".")) {
                return Optional.of(new BigDecimal(s).longValue());
            }
            return Optional.of(Long.parseLong(s));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private Optional<BigDecimal> readDecimal(Map<String, Object> metrics, String key) {
        if (metrics == null || !StringUtils.hasText(key)) return Optional.empty();
        Object raw = metrics.get(key);
        if (raw == null) return Optional.empty();
        if (raw instanceof BigDecimal bd) {
            return Optional.of(bd);
        }
        if (raw instanceof Number n) {
            return Optional.of(BigDecimal.valueOf(n.doubleValue()));
        }
        String s = String.valueOf(raw).trim();
        if (s.isEmpty()) return Optional.empty();
        try {
            return Optional.of(new BigDecimal(s));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private String safeMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return "UNKNOWN";
        }
        String trimmed = message.trim();
        if (trimmed.length() > 300) {
            return trimmed.substring(0, 300);
        }
        return trimmed;
    }
}

