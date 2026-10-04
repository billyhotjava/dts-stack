package com.yuzhi.dts.ingestion.web.rest;

import com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.util.StringUtils;

final class IngestionTaskAdmissionAuditMetadata {

    private static final int MAX_ERROR_LENGTH = 200;

    private IngestionTaskAdmissionAuditMetadata() {}

    static Map<String, Object> success(Long taskId, String operator, IngestionTaskDTO admitted) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("summary", "完成密级封存与生产准入");
        metadata.put("taskId", taskId);
        metadata.put("operator", operator);
        if (admitted != null) {
            putIfNotNull(metadata, "revisionNumber", admitted.getRevisionNumber());
            putIfNotNull(metadata, "planChecksum", admitted.getEffectiveConfigChecksum());
            putIfNotNull(
                metadata,
                "targetDatasetId",
                admitted.getTargetDatasetId() == null ? null : admitted.getTargetDatasetId().toString()
            );
        }
        return metadata;
    }

    static Map<String, Object> failure(Long taskId, String operator, Throwable error) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("summary", "密级封存与生产准入失败");
        metadata.put("taskId", taskId);
        metadata.put("operator", operator);
        String message = trimMessage(error == null ? null : error.getMessage());
        if (StringUtils.hasText(message)) {
            metadata.put("error", message);
        }
        return metadata;
    }

    private static String trimMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }
        String trimmed = message.trim();
        return trimmed.length() > MAX_ERROR_LENGTH ? trimmed.substring(0, MAX_ERROR_LENGTH) : trimmed;
    }

    private static void putIfNotNull(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }
}
