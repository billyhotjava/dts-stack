package com.yuzhi.dts.copilot.analytics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsScreenAssetAuditLog;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsScreenAssetAuditLogRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ScreenAssetAuditService {

    private final AnalyticsScreenAssetAuditLogRepository screenAssetAuditLogRepository;
    private final ObjectMapper objectMapper;

    public ScreenAssetAuditService(
            AnalyticsScreenAssetAuditLogRepository screenAssetAuditLogRepository,
            ObjectMapper objectMapper) {
        this.screenAssetAuditLogRepository = screenAssetAuditLogRepository;
        this.objectMapper = objectMapper;
    }

    public void log(
            String assetType,
            Long assetId,
            Long actorId,
            String action,
            String source,
            String result,
            Object details,
            String requestId) {
        if (assetType == null || assetType.isBlank() || action == null || action.isBlank()) {
            return;
        }
        AnalyticsScreenAssetAuditLog log = new AnalyticsScreenAssetAuditLog();
        log.setAssetType(assetType.trim());
        log.setAssetId(assetId);
        log.setActorId(actorId);
        log.setAction(action.trim());
        log.setSource(trimToNull(source));
        log.setResult(normalizeResult(result));
        log.setDetailsJson(toJson(details));
        log.setRequestId(trimToNull(requestId));
        screenAssetAuditLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public List<AnalyticsScreenAssetAuditLog> listRecent(String assetType, int limit) {
        int normalizedLimit = Math.max(1, Math.min(200, limit));
        List<AnalyticsScreenAssetAuditLog> logs =
                screenAssetAuditLogRepository.findTop200ByAssetTypeOrderByCreatedAtDesc(assetType);
        if (logs.size() <= normalizedLimit) {
            return logs;
        }
        return logs.subList(0, normalizedLimit);
    }

    private String normalizeResult(String result) {
        String value = trimToNull(result);
        return value == null ? "success" : value;
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
