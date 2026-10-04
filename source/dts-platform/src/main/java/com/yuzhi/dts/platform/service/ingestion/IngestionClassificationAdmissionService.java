package com.yuzhi.dts.platform.service.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.security.SecurityLevelCatalog;
import com.yuzhi.dts.platform.domain.catalog.CatalogClassificationSnapshot;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationException;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class IngestionClassificationAdmissionService {

    private final CatalogClassificationService classificationService;
    private final ObjectMapper objectMapper;

    public IngestionClassificationAdmissionService(
        CatalogClassificationService classificationService,
        ObjectMapper objectMapper
    ) {
        this.classificationService = classificationService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> sealEncryptedUpload(Object rawUpload, String declaredLevel) {
        Map<String, Object> upload = mapValue(rawUpload);
        String fileId = text(upload.get("fileId"));
        String fileHash = text(upload.get("fileHash"));
        if (!StringUtils.hasText(fileId) || !StringUtils.hasText(fileHash)) {
            throw new CatalogClassificationException(
                "FILE_UPLOAD_EVIDENCE_INVALID",
                "加密上传结果缺少 fileId/fileHash，不能生成文件密级封存"
            );
        }
        if (!Boolean.TRUE.equals(upload.get("encrypted"))) {
            throw new CatalogClassificationException(
                "FILE_UPLOAD_ENCRYPTION_REQUIRED",
                "文件未完成加密存储，不能进入密级封存流程"
            );
        }

        String subjectKey = "ingestion-upload:" + fileId;
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("fileId", fileId);
        evidence.put("fileName", upload.get("originalName"));
        evidence.put("fileHash", fileHash);
        evidence.put("encrypted", true);
        CatalogClassificationSnapshot snapshot = classificationService.seal(
            new CatalogClassificationService.SealCommand(
                "FILE",
                subjectKey,
                null,
                declaredLevel,
                null,
                null,
                List.of(),
                "FILE_DECLARATION",
                subjectKey,
                fileHash,
                toJson(evidence)
            )
        );

        Map<String, String> fields = new LinkedHashMap<>();
        List<Object> classifiedColumns = new ArrayList<>();
        Object rawColumns = upload.get("columns");
        if (rawColumns instanceof List<?> columns) {
            for (Object rawColumn : columns) {
                Map<String, Object> column = mapValue(rawColumn);
                String name = String.valueOf(column.getOrDefault("name", "")).trim();
                if (!StringUtils.hasText(name)) {
                    continue;
                }
                column.put("classification", declaredLevel);
                classifiedColumns.add(column);
                fields.put(name, declaredLevel);
            }
            upload.put("columns", classifiedColumns);
        }
        Map<String, Object> seal = sealReference(snapshot);
        seal.put("fileFloor", declaredLevel);
        seal.put("fileId", fileId);
        seal.put("fileChecksum", fileHash);
        upload.put("classification", declaredLevel);
        upload.put("classificationSeal", seal);
        upload.put("fieldClassifications", fields);
        return upload;
    }

    public Map<String, Object> attachFileSeal(
        Map<String, Object> resolved,
        Map<String, Object> fileSeal,
        Object rawFieldClassifications,
        Object rawColumns,
        Map<String, Object> sourceConfig,
        boolean required
    ) {
        String subjectType = String.valueOf(fileSeal.getOrDefault("subjectType", "")).trim();
        String subjectKey = String.valueOf(fileSeal.getOrDefault("subjectKey", "")).trim();
        if (!"FILE".equalsIgnoreCase(subjectType) || !StringUtils.hasText(subjectKey)) {
            if (!required) {
                resolved.put("classificationSeal", new LinkedHashMap<>(fileSeal));
                return resolved;
            }
            throw new CatalogClassificationException(
                "FILE_CLASSIFICATION_SEAL_INVALID",
                "文件接入密级封存引用无效"
            );
        }
        CatalogClassificationSnapshot sourceSnapshot = classificationService
            .resolve("FILE", subjectKey)
            .orElseThrow(() ->
                new CatalogClassificationException(
                    "FILE_CLASSIFICATION_SEAL_NOT_FOUND",
                    "文件密级封存不存在或已失效"
                )
        );
        assertCurrentSeal(fileSeal, sourceSnapshot);
        assertManagedUploadEvidence(subjectKey, sourceSnapshot, sourceConfig);
        String fileFloor = SecurityLevelCatalog
            .requireDataLevel(sourceSnapshot.getEffectiveLevel())
            .code();
        Map<String, String> fields = completeFileFieldClassifications(
            rawFieldClassifications,
            rawColumns,
            fileFloor
        );
        if (required && fields.isEmpty()) {
            throw new CatalogClassificationException(
                "CLASSIFICATION_FIELD_SEAL_REQUIRED",
                "文件接入必须先确认并封存字段密级"
            );
        }
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (SecurityLevelCatalog.isDataDowngrade(fileFloor, entry.getValue())) {
                throw new CatalogClassificationException(
                    "CLASSIFICATION_DOWNGRADE_FORBIDDEN",
                    "字段 " + entry.getKey() + " 的密级不能低于文件密级 " + fileFloor
                );
            }
            sealColumn(sourceSnapshot, subjectKey, fileFloor, entry);
        }
        String evidenceSource =
            sourceSnapshot.getId() +
            ":" +
            sourceSnapshot.getRecordVersion() +
            ":" +
            sourceSnapshot.getEvidenceChecksum() +
            ":" +
            new java.util.TreeMap<>(fields);
        CatalogClassificationSnapshot taskSnapshot = classificationService.sealOrRaise(
            new CatalogClassificationService.SealCommand(
                "ASSET",
                "ingestion-file:" + sha256(evidenceSource),
                "DATASET",
                fileFloor,
                null,
                null,
                fields.values(),
                "FILE_DECLARATION",
                subjectKey,
                sha256(evidenceSource),
                toJson(
                    Map.of(
                        "fileSealId",
                        sourceSnapshot.getId(),
                        "fileSubjectKey",
                        subjectKey,
                        "fileFloor",
                        fileFloor,
                        "fieldClassifications",
                        fields
                    )
                )
            )
        );
        Map<String, Object> seal = sealReference(taskSnapshot);
        seal.put("fileFloor", fileFloor);
        seal.put("fileId", text(sourceConfig.get("_fileId")));
        seal.put("fileChecksum", sourceSnapshot.getEvidenceChecksum());
        seal.put("fileSubjectKey", subjectKey);
        resolved.put("classificationSeal", seal);
        resolved.put("fieldClassifications", new LinkedHashMap<>(fields));
        return resolved;
    }

    private void assertManagedUploadEvidence(
        String subjectKey,
        CatalogClassificationSnapshot sourceSnapshot,
        Map<String, Object> sourceConfig
    ) {
        String fileId = text(sourceConfig.get("_fileId"));
        String fileHash = text(sourceConfig.get("_fileHash"));
        if (
            !StringUtils.hasText(fileId) ||
            !subjectKey.equals("ingestion-upload:" + fileId) ||
            !Boolean.TRUE.equals(sourceConfig.get("_encrypted")) ||
            !sourceSnapshot.getEvidenceChecksum().equals(fileHash)
        ) {
            throw new CatalogClassificationException(
                "FILE_UPLOAD_EVIDENCE_MISMATCH",
                "文件任务引用与密级封存不一致，请重新上传并完成密级配置"
            );
        }
    }

    private void sealColumn(
        CatalogClassificationSnapshot sourceSnapshot,
        String subjectKey,
        String fileFloor,
        Map.Entry<String, String> field
    ) {
        classificationService.sealOrRaise(
            new CatalogClassificationService.SealCommand(
                "COLUMN",
                "ingestion-column:" + sha256(subjectKey + ":" + field.getKey()),
                null,
                field.getValue(),
                null,
                null,
                List.of(fileFloor),
                "FILE_DECLARATION",
                subjectKey,
                sha256(
                    sourceSnapshot.getEvidenceChecksum() +
                    ":" +
                    field.getKey() +
                    ":" +
                    field.getValue()
                ),
                toJson(
                    Map.of(
                        "fileSealId",
                        sourceSnapshot.getId(),
                        "fileSubjectKey",
                        subjectKey,
                        "column",
                        field.getKey(),
                        "fileFloor",
                        fileFloor,
                        "classification",
                        field.getValue()
                    )
                )
            )
        );
    }

    private Map<String, String> completeFileFieldClassifications(
        Object rawFieldClassifications,
        Object rawColumns,
        String fileFloor
    ) {
        Map<String, String> requested = new LinkedHashMap<>(
            normalizeFieldClassifications(rawFieldClassifications)
        );
        List<String> columns = new ArrayList<>();
        if (rawColumns instanceof List<?> values) {
            for (Object value : values) {
                Map<String, Object> column = mapValue(value);
                String name = String.valueOf(column.getOrDefault("name", "")).trim();
                if (StringUtils.hasText(name) && !columns.contains(name)) {
                    columns.add(name);
                }
            }
        }
        if (!columns.isEmpty()) {
            List<String> unknown = requested
                .keySet()
                .stream()
                .filter(name -> !columns.contains(name))
                .sorted()
                .toList();
            if (!unknown.isEmpty()) {
                throw new CatalogClassificationException(
                    "CLASSIFICATION_FIELD_UNKNOWN",
                    "字段密级包含解析结果之外的字段: " + String.join(", ", unknown)
                );
            }
            for (String column : columns) {
                requested.putIfAbsent(column, fileFloor);
            }
        }
        return Map.copyOf(requested);
    }

    private void assertCurrentSeal(
        Map<String, Object> reference,
        CatalogClassificationSnapshot snapshot
    ) {
        UUID sealId = parseUuid(reference.get("sealId"));
        Long version = parseLong(reference.get("snapshotVersion"));
        String checksum = String.valueOf(reference.getOrDefault("checksum", "")).trim();
        if (
            sealId == null ||
            !sealId.equals(snapshot.getId()) ||
            version == null ||
            !version.equals(snapshot.getRecordVersion()) ||
            !StringUtils.hasText(checksum) ||
            !checksum.equals(snapshot.getEvidenceChecksum())
        ) {
            throw new CatalogClassificationException(
                "FILE_CLASSIFICATION_SEAL_STALE",
                "密级封存已变化，请重新确认后再创建任务"
            );
        }
    }

    private Map<String, Object> sealReference(CatalogClassificationSnapshot snapshot) {
        Map<String, Object> seal = new LinkedHashMap<>();
        seal.put("sealId", snapshot.getId());
        seal.put("subjectType", snapshot.getSubjectType());
        seal.put("subjectKey", snapshot.getSubjectKey());
        seal.put("assetType", snapshot.getAssetType());
        seal.put("effectiveLevel", snapshot.getEffectiveLevel());
        seal.put("snapshotVersion", snapshot.getRecordVersion() == null ? 0L : snapshot.getRecordVersion());
        seal.put("checksum", snapshot.getEvidenceChecksum());
        seal.put("sealedAt", snapshot.getSealedAt());
        seal.put("propagationStatus", snapshot.getPropagationStatus());
        return seal;
    }

    private Map<String, String> normalizeFieldClassifications(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> fields)) {
            throw new CatalogClassificationException(
                "SOURCE_FIELD_CLASSIFICATION_INVALID",
                "字段密级配置格式无效"
            );
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        fields.forEach((column, level) -> {
            if (!StringUtils.hasText(String.valueOf(column))) {
                throw new CatalogClassificationException(
                    "SOURCE_FIELD_CLASSIFICATION_INVALID",
                    "字段密级配置包含空字段名"
                );
            }
            try {
                normalized.put(
                    String.valueOf(column).trim(),
                    SecurityLevelCatalog.requireDataLevel(level).code()
                );
            } catch (IllegalArgumentException ex) {
                throw new CatalogClassificationException(
                    "SOURCE_FIELD_CLASSIFICATION_INVALID",
                    "字段 " + column + " 的密级无效"
                );
            }
        });
        return Map.copyOf(normalized);
    }

    private Map<String, Object> mapValue(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
        }
        return result;
    }

    private String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new CatalogClassificationException(
                "SOURCE_CLASSIFICATION_INVALID",
                "无法生成数据源密级证据"
            );
        }
    }

    private UUID parseUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.toString().trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Long parseLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                digest.digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception ex) {
            throw new IllegalStateException("无法生成密级证据摘要", ex);
        }
    }
}
