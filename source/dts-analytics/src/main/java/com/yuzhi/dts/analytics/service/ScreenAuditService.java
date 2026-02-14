package com.yuzhi.dts.analytics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.domain.AnalyticsScreenAuditLog;
import com.yuzhi.dts.analytics.repository.AnalyticsScreenAuditLogRepository;
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
        if (screenId == null || action == null || action.isBlank()) {
            return;
        }

        AnalyticsScreenAuditLog log = new AnalyticsScreenAuditLog();
        log.setScreenId(screenId);
        log.setActorId(actorId);
        log.setAction(action);
        log.setBeforeJson(toJson(before));
        log.setAfterJson(toJson(after));
        log.setRequestId(trimToNull(requestId));
        screenAuditLogRepository.save(log);
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
