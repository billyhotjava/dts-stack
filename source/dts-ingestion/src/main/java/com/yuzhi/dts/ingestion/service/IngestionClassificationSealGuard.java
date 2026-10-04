package com.yuzhi.dts.ingestion.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.yuzhi.dts.common.security.SecurityLevelCatalog;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class IngestionClassificationSealGuard {

    private static final Set<String> LEVELS = Set.of("PUBLIC", "INTERNAL", "SECRET", "CONFIDENTIAL");

    public void requireProductionSeal(IngestionTask task) {
        if (task == null) {
            throw new IllegalStateException("CLASSIFICATION_SEAL_REQUIRED: ingestion task is required");
        }
        JsonNode seal = task.getClassificationSeal();
        if (seal == null || !seal.isObject()) {
            JsonNode fields = task.getFieldClassifications();
            if (fields == null || fields.isNull() || (fields.isObject() && fields.isEmpty())) {
                return;
            }
            throw new IllegalStateException(
                "CLASSIFICATION_SEAL_REQUIRED: 任务存在字段密级但缺少密级封存，不能执行生产写入"
            );
        }
        requireText(seal, "sealId");
        requireText(seal, "subjectType");
        requireText(seal, "subjectKey");
        String effectiveLevel = requireText(seal, "effectiveLevel").toUpperCase();
        if (!LEVELS.contains(effectiveLevel)) {
            throw new IllegalStateException("CLASSIFICATION_SEAL_INVALID: effectiveLevel 无效");
        }
        JsonNode version = seal.get("snapshotVersion");
        if (version == null || !version.canConvertToLong() || version.longValue() < 0) {
            throw new IllegalStateException("CLASSIFICATION_SEAL_INVALID: snapshotVersion 无效");
        }
        String checksum = requireText(seal, "checksum");
        if (checksum.length() < 16 || checksum.length() > 128) {
            throw new IllegalStateException("CLASSIFICATION_SEAL_INVALID: checksum 无效");
        }
        try {
            Instant.parse(requireText(seal, "sealedAt"));
        } catch (DateTimeParseException ex) {
            throw new IllegalStateException("CLASSIFICATION_SEAL_INVALID: sealedAt 无效", ex);
        }
        validateFieldClassifications(task.getFieldClassifications(), seal, effectiveLevel);
    }

    private void validateFieldClassifications(
        JsonNode fieldClassifications,
        JsonNode seal,
        String effectiveLevel
    ) {
        String subjectType = requireText(seal, "subjectType").toUpperCase();
        String explicitFileFloor = optionalDataLevel(seal, "fileFloor");
        String fileFloor =
            "FILE".equals(subjectType) && !StringUtils.hasText(explicitFileFloor)
                ? effectiveLevel
                : explicitFileFloor;
        if (fieldClassifications == null || fieldClassifications.isNull()) {
            if (StringUtils.hasText(fileFloor)) {
                throw new IllegalStateException(
                    "CLASSIFICATION_FIELD_SEAL_REQUIRED: 文件接入缺少字段密级封存"
                );
            }
            return;
        }
        if (!fieldClassifications.isObject()) {
            throw new IllegalStateException(
                "CLASSIFICATION_FIELD_SEAL_INVALID: fieldClassifications 必须为对象"
            );
        }
        List<String> levels = new ArrayList<>();
        fieldClassifications.fields().forEachRemaining(entry -> {
            if (!StringUtils.hasText(entry.getKey()) || entry.getValue() == null || !entry.getValue().isTextual()) {
                throw new IllegalStateException(
                    "CLASSIFICATION_FIELD_SEAL_INVALID: 字段密级条目无效"
                );
            }
            String fieldLevel;
            try {
                fieldLevel = SecurityLevelCatalog.requireDataLevel(entry.getValue().asText()).code();
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException(
                    "CLASSIFICATION_FIELD_SEAL_INVALID: 字段 " + entry.getKey() + " 的密级无效",
                    ex
                );
            }
            if (
                StringUtils.hasText(fileFloor) &&
                SecurityLevelCatalog.isDataDowngrade(fileFloor, fieldLevel)
            ) {
                throw new IllegalStateException(
                    "CLASSIFICATION_DOWNGRADE_FORBIDDEN: 文件字段 " +
                    entry.getKey() +
                    " 的密级不能低于文件密级 " +
                    fileFloor
                );
            }
            levels.add(fieldLevel);
        });
        if (StringUtils.hasText(fileFloor) && levels.isEmpty()) {
            throw new IllegalStateException(
                "CLASSIFICATION_FIELD_SEAL_REQUIRED: 文件接入缺少字段密级封存"
            );
        }
        String highestFieldLevel = SecurityLevelCatalog.maxDataCode(levels);
        if (
            StringUtils.hasText(highestFieldLevel) &&
            SecurityLevelCatalog.isDataDowngrade(highestFieldLevel, effectiveLevel)
        ) {
            throw new IllegalStateException(
                "CLASSIFICATION_SEAL_STALE: 资产有效密级低于字段最高密级 " + highestFieldLevel
            );
        }
    }

    private String optionalDataLevel(JsonNode seal, String field) {
        JsonNode value = seal.get(field);
        if (value == null || value.isNull() || !StringUtils.hasText(value.asText())) {
            return null;
        }
        try {
            return SecurityLevelCatalog.requireDataLevel(value.asText()).code();
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                "CLASSIFICATION_SEAL_INVALID: " + field + " 无效",
                ex
            );
        }
    }

    private String requireText(JsonNode seal, String field) {
        JsonNode value = seal.get(field);
        String text = value == null || value.isNull() ? null : value.asText();
        if (!StringUtils.hasText(text)) {
            throw new IllegalStateException("CLASSIFICATION_SEAL_INVALID: " + field + " 缺失");
        }
        return text.trim();
    }
}
