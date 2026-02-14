package com.yuzhi.dts.analytics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.domain.AnalyticsQueryTrace;
import com.yuzhi.dts.analytics.repository.AnalyticsQueryTraceRepository;
import java.util.List;
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
