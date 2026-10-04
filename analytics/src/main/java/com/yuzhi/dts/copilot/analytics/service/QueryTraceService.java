package com.yuzhi.dts.copilot.analytics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsQueryTrace;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsQueryTraceRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class QueryTraceService {

    private static final int MAX_SQL_LENGTH = 20000;
    private static final int MAX_CONTEXT_LENGTH = 10000;

    private final AnalyticsQueryTraceRepository queryTraceRepository;
    private final ObjectMapper objectMapper;

    public QueryTraceService(AnalyticsQueryTraceRepository queryTraceRepository, ObjectMapper objectMapper) {
        this.queryTraceRepository = queryTraceRepository;
        this.objectMapper = objectMapper;
    }

    public void log(
            String chain,
            Long cardId,
            Long databaseId,
            Long metricId,
            String metricVersion,
            String sqlText,
            String status,
            String errorCode,
            String requestId,
            Long actorUserId,
            String dept,
            String classification,
            long durationMs,
            Object context) {
        String safeChain = trimToNull(chain);
        String safeStatus = trimToNull(status);
        if (safeChain == null || safeStatus == null) {
            return;
        }

        AnalyticsQueryTrace trace = new AnalyticsQueryTrace();
        trace.setChain(safeChain);
        trace.setCardId(cardId);
        trace.setDatabaseId(databaseId);
        trace.setMetricId(metricId);
        trace.setMetricVersion(trimToNull(metricVersion));
        trace.setSqlText(truncate(trimToNull(sqlText), MAX_SQL_LENGTH));
        trace.setStatus(safeStatus);
        trace.setErrorCode(trimToNull(errorCode));
        trace.setRequestId(trimToNull(requestId));
        trace.setActorUserId(actorUserId);
        trace.setDept(trimToNull(dept));
        trace.setClassification(trimToNull(classification));
        trace.setDurationMs(Math.max(durationMs, 0));
        trace.setContextJson(truncate(toJson(context), MAX_CONTEXT_LENGTH));
        queryTraceRepository.save(trace);
    }

    @Transactional(readOnly = true)
    public List<AnalyticsQueryTrace> listRecent(Long metricId, Long cardId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        PageRequest page = PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (metricId != null && metricId > 0) {
            return queryTraceRepository.findAllByMetricId(metricId, page).getContent();
        }
        if (cardId != null && cardId > 0) {
            return queryTraceRepository.findAllByCardId(cardId, page).getContent();
        }
        return queryTraceRepository.findAll(page).getContent();
    }

    @Transactional(readOnly = true)
    public List<String> listMetricVersions(Long metricId) {
        if (metricId == null || metricId <= 0) {
            return List.of();
        }
        return queryTraceRepository.findDistinctMetricVersions(metricId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> summarizeFailures(int days, int topN, String chain) {
        int safeDays = Math.max(1, Math.min(days, 30));
        int safeTopN = Math.max(1, Math.min(topN, 20));
        Instant since = Instant.now().minus(Duration.ofDays(safeDays));
        String safeChain = trimToNull(chain);

        long total = queryTraceRepository.countAllSince(since, safeChain);
        long success = queryTraceRepository.countByStatusSince(since, "success", safeChain);
        long failed = Math.max(0, total - success);
        double failureRate = total <= 0 ? 0.0 : ((double) failed / (double) total);

        List<Object[]> topRows = queryTraceRepository.summarizeFailedErrorCodesSince(
                since, safeChain, PageRequest.of(0, safeTopN));
        Map<String, Long> categoryCounters = new TreeMap<>();
        List<Map<String, Object>> topErrorCodes = topRows.stream().map(row -> {
            Object rawCode = row != null && row.length > 0 ? row[0] : null;
            String code = rawCode == null ? null : trimToNull(rawCode.toString());
            Number count = row != null && row.length > 1 && row[1] instanceof Number ? (Number) row[1] : 0;
            String category = classifyErrorCategory(code);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", code == null ? "UNKNOWN" : code);
            item.put("count", count.longValue());
            item.put("retryableHint", isRetryableCode(code));
            item.put("category", category);
            categoryCounters.merge(category, count.longValue(), Long::sum);
            return item;
        }).toList();

        List<Map<String, Object>> topErrorCategories = categoryCounters.entrySet().stream()
                .sorted((left, right) -> Long.compare(right.getValue(), left.getValue()))
                .limit(Math.min(5, safeTopN))
                .map(entry -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("category", entry.getKey());
                    item.put("count", entry.getValue());
                    return item;
                })
                .toList();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("since", since);
        summary.put("windowDays", safeDays);
        summary.put("chain", safeChain);
        summary.put("total", total);
        summary.put("success", success);
        summary.put("failed", failed);
        summary.put("failureRate", failureRate);
        summary.put("topErrorCodes", topErrorCodes);
        summary.put("topErrorCategories", topErrorCategories);
        return summary;
    }

    private boolean isRetryableCode(String errorCode) {
        if (errorCode == null || errorCode.isBlank()) {
            return false;
        }
        String code = errorCode.trim().toUpperCase();
        return code.contains("TIMEOUT")
                || code.contains("CONNECT")
                || code.contains("UNAVAILABLE")
                || code.contains("ROLLBACK");
    }

    private String classifyErrorCategory(String errorCode) {
        if (errorCode == null || errorCode.isBlank()) {
            return "runtime";
        }
        String code = errorCode.trim().toUpperCase();
        if (code.contains("SYNTAX") || code.contains("PARSE")) {
            return "syntax";
        }
        if (code.contains("TIMEOUT")) {
            return "timeout";
        }
        if (code.contains("CONNECT") || code.contains("REFUSED") || code.contains("UNAVAILABLE")) {
            return "connection";
        }
        if (code.contains("AUTH") || code.contains("PERMISSION") || code.contains("FORBIDDEN")) {
            return "permission";
        }
        if (code.contains("TABLE")
                || code.contains("COLUMN")
                || code.contains("SCHEMA")
                || code.contains("FIELD")
                || code.contains("MISSING")
                || code.contains("NOT_FOUND")) {
            return "schema";
        }
        return "runtime";
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private String truncate(String value, int maxLen) {
        if (value == null || value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
