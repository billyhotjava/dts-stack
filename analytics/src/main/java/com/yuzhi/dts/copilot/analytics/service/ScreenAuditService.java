package com.yuzhi.dts.copilot.analytics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsScreenAuditLog;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsScreenAuditLogRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ScreenAuditService {

    private final AnalyticsScreenAuditLogRepository screenAuditLogRepository;
    private final ObjectMapper objectMapper;

    public ScreenAuditService(AnalyticsScreenAuditLogRepository screenAuditLogRepository, ObjectMapper objectMapper) {
        this.screenAuditLogRepository = screenAuditLogRepository;
        this.objectMapper = objectMapper;
    }

    public void log(Long screenId, Long actorId, String action, Object before, Object after, String requestId) {
        logAndReturn(screenId, actorId, action, before, after, requestId);
    }

    public AnalyticsScreenAuditLog logAndReturn(
            Long screenId,
            Long actorId,
            String action,
            Object before,
            Object after,
            String requestId) {
        if (screenId == null || action == null || action.isBlank()) {
            return null;
        }

        AnalyticsScreenAuditLog log = new AnalyticsScreenAuditLog();
        log.setScreenId(screenId);
        log.setActorId(actorId);
        log.setAction(action);
        log.setBeforeJson(toJson(before));
        log.setAfterJson(toJson(after));
        log.setRequestId(trimToNull(requestId));
        return screenAuditLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public List<AnalyticsScreenAuditLog> listByScreenId(Long screenId, int limit) {
        if (screenId == null) {
            return List.of();
        }
        int safeLimit = Math.max(1, Math.min(limit, 1000));
        List<AnalyticsScreenAuditLog> all = screenAuditLogRepository.findAllByScreenIdOrderByCreatedAtDesc(screenId);
        if (all.size() <= safeLimit) {
            return all;
        }
        return new ArrayList<>(all.subList(0, safeLimit));
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

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
