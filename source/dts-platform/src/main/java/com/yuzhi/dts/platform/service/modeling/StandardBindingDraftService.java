package com.yuzhi.dts.platform.service.modeling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.modeling.StandardBindingDraft;
import com.yuzhi.dts.platform.repository.modeling.StandardBindingDraftRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class StandardBindingDraftService {

    private static final int MAX_FIELDS = 500;
    private static final String STATUS_DRAFT = "DRAFT";

    private final StandardBindingDraftRepository repository;
    private final ObjectMapper objectMapper;

    public StandardBindingDraftService(StandardBindingDraftRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public StandardBindingDraftDto create(StandardBindingDraftRequest request) {
        List<StandardBindingDraftField> fields = normalizeFields(request != null ? request.fields() : null);
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("字段落标草稿至少需要一个字段");
        }
        StandardBindingDraftPayload payload = new StandardBindingDraftPayload(
            fields,
            normalizeMetadata(request != null ? request.metadata() : null)
        );

        StandardBindingDraft entity = new StandardBindingDraft();
        entity.setSource(defaultText(request != null ? request.source() : null, "metadata-elements"));
        entity.setTitle(defaultText(request != null ? request.title() : null, "字段落标草稿"));
        entity.setStatus(STATUS_DRAFT);
        entity.setFieldCount(fields.size());
        entity.setPayloadJson(writePayload(payload));

        return toDto(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public StandardBindingDraftDto get(UUID id) {
        return repository.findById(id).map(this::toDto).orElseThrow(() -> new EntityNotFoundException("字段落标草稿不存在"));
    }

    private StandardBindingDraftDto toDto(StandardBindingDraft entity) {
        StandardBindingDraftPayload payload = readPayload(entity.getPayloadJson());
        return new StandardBindingDraftDto(
            entity.getId(),
            entity.getSource(),
            entity.getTitle(),
            entity.getStatus(),
            entity.getFieldCount(),
            entity.getCreatedDate(),
            entity.getCreatedBy(),
            payload.fields(),
            payload.metadata()
        );
    }

    private List<StandardBindingDraftField> normalizeFields(List<StandardBindingDraftField> input) {
        List<StandardBindingDraftField> fields = new ArrayList<>();
        if (input == null) {
            return fields;
        }
        for (int i = 0; i < input.size() && fields.size() < MAX_FIELDS; i++) {
            StandardBindingDraftField field = input.get(i);
            if (field == null) {
                continue;
            }
            String columnName = normalizeIdentifier(firstText(field.columnName(), field.standardCode()), "field_" + (i + 1));
            if (!StringUtils.hasText(columnName)) {
                continue;
            }
            fields.add(
                new StandardBindingDraftField(
                    columnName,
                    field.standardId(),
                    defaultText(field.standardCode(), columnName),
                    defaultText(firstText(field.standardName(), field.columnName()), columnName),
                    defaultText(field.dataType(), "STRING").toUpperCase(java.util.Locale.ROOT),
                    field.nullable(),
                    trimToNull(field.codeSet()),
                    trimToNull(field.securityLevel()),
                    trimToNull(field.description()),
                    trimToNull(field.domain()),
                    trimToNull(field.sourceSystem()),
                    field.isPk()
                )
            );
        }
        return fields;
    }

    private Map<String, Object> normalizeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        return new LinkedHashMap<>(metadata);
    }

    private String writePayload(StandardBindingDraftPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("字段落标草稿序列化失败", e);
        }
    }

    private StandardBindingDraftPayload readPayload(String payloadJson) {
        if (!StringUtils.hasText(payloadJson)) {
            return new StandardBindingDraftPayload(List.of(), Map.of());
        }
        try {
            return objectMapper.readValue(payloadJson, StandardBindingDraftPayload.class);
        } catch (Exception e) {
            return new StandardBindingDraftPayload(List.of(), Map.of("parseError", true));
        }
    }

    private String normalizeIdentifier(String value, String fallback) {
        String normalized = defaultText(value, fallback)
            .toLowerCase(java.util.Locale.ROOT)
            .replaceAll("[^a-z0-9_]+", "_")
            .replaceAll("^_+|_+$", "");
        if (!StringUtils.hasText(normalized)) {
            normalized = fallback;
        }
        return normalized.matches("^[a-z_].*") ? normalized : "f_" + normalized;
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private String defaultText(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed != null ? trimmed : fallback;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    public record StandardBindingDraftRequest(
        String source,
        String title,
        List<StandardBindingDraftField> fields,
        Map<String, Object> metadata
    ) {}

    public record StandardBindingDraftDto(
        UUID id,
        String source,
        String title,
        String status,
        Integer fieldCount,
        Instant createdAt,
        String createdBy,
        List<StandardBindingDraftField> fields,
        Map<String, Object> metadata
    ) {}

    public record StandardBindingDraftField(
        String columnName,
        UUID standardId,
        String standardCode,
        String standardName,
        String dataType,
        Boolean nullable,
        String codeSet,
        String securityLevel,
        String description,
        String domain,
        String sourceSystem,
        Boolean isPk
    ) {}

    private record StandardBindingDraftPayload(List<StandardBindingDraftField> fields, Map<String, Object> metadata) {}
}
