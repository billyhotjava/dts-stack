package com.yuzhi.dts.copilot.analytics.web.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsField;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsTable;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsFieldRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsTableRepository;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.service.ExternalDatabaseDataSourceRegistry;
import com.yuzhi.dts.copilot.analytics.web.support.MetabaseAuth;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/field")
public class FieldResource {

    private static final DateTimeFormatter METABASE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
    private static final long JS_SAFE_INTEGER_MAX = 9_007_199_254_740_991L;

    private final AnalyticsSessionService sessionService;
    private final AnalyticsFieldRepository fieldRepository;
    private final AnalyticsTableRepository tableRepository;
    private final ExternalDatabaseDataSourceRegistry dataSourceRegistry;
    private final ObjectMapper objectMapper;

    public FieldResource(
            AnalyticsSessionService sessionService,
            AnalyticsFieldRepository fieldRepository,
            AnalyticsTableRepository tableRepository,
            ExternalDatabaseDataSourceRegistry dataSourceRegistry,
            ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.fieldRepository = fieldRepository;
        this.tableRepository = tableRepository;
        this.dataSourceRegistry = dataSourceRegistry;
        this.objectMapper = objectMapper;
    }

    @GetMapping(path = "/{fieldId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> get(@PathVariable("fieldId") long fieldId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return fieldRepository.findById(fieldId).<ResponseEntity<?>>map(field -> ResponseEntity.ok(toField(field))).orElseGet(
                () -> ResponseEntity.notFound().build());
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> update(@PathVariable("id") long id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        Optional<AnalyticsField> fieldOpt = fieldRepository.findById(id);
        if (fieldOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        AnalyticsField field = fieldOpt.get();
        if (body != null) {
            Object displayName = body.get("display_name");
            if (displayName instanceof String s) {
                field.setDisplayName(s);
            }
            Object semanticType = body.get("semantic_type");
            if (semanticType instanceof String s) {
                field.setSemanticType(s);
            }
            Object visibilityType = body.get("visibility_type");
            if (visibilityType instanceof String s) {
                field.setVisibilityType(s);
            }
            Object active = body.get("active");
            if (active instanceof Boolean b) {
                field.setActive(b);
            }
            fieldRepository.save(field);
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping(path = "/{fieldId}/values", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> values(@PathVariable("fieldId") long fieldId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        AnalyticsField field = fieldRepository.findById(fieldId).orElse(null);
        if (field == null) {
            return ResponseEntity.notFound().build();
        }
        AnalyticsTable table = tableRepository.findById(field.getTableId()).orElse(null);
        if (table == null || table.getDatabaseId() == null || !table.getDatabaseId().equals(field.getDatabaseId())) {
            return ResponseEntity.notFound().build();
        }

        int limit = 1000;
        List<Object> values = new ArrayList<>();
        boolean hasMore = false;

        String column = quoteIdentifier(field.getName());
        String from = qualifyTable(table.getSchemaName(), table.getName());
        String sql = "SELECT DISTINCT %s AS value FROM %s WHERE %s IS NOT NULL ORDER BY 1 LIMIT %d"
                .formatted(column, from, column, limit + 1);

        try (Connection connection = dataSourceRegistry.get(field.getDatabaseId()).getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Object raw = rs.getObject(1);
                    values.add(normalizeValue(raw));
                    if (values.size() > limit) {
                        hasMore = true;
                        values = values.subList(0, limit);
                        break;
                    }
                }
            }
        } catch (SQLException e) {
            return ResponseEntity.accepted().body(Map.of("error", "Error loading field values: " + e.getMessage()));
        }

        return ResponseEntity.ok(Map.of("field_id", fieldId, "values", values, "has_more_values", hasMore));
    }

    @PostMapping(path = "/{fieldId}/values", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> valuesUpdate(@PathVariable("fieldId") long fieldId, @RequestBody Object ignored, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/{fieldId}/dimension", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateDimension(@PathVariable("fieldId") long fieldId, @RequestBody Object ignored, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping(path = "/{fieldId}/dimension")
    public ResponseEntity<?> deleteDimension(@PathVariable("fieldId") long fieldId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/{fieldId}/rescan_values")
    public ResponseEntity<?> rescan(@PathVariable("fieldId") long fieldId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping(path = "/{fieldId}/discard_values")
    public ResponseEntity<?> discard(@PathVariable("fieldId") long fieldId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping(path = "/{fieldId}/search/{searchFieldId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> search(
            @PathVariable("fieldId") long fieldId,
            @PathVariable("searchFieldId") long searchFieldId,
            HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(List.of());
    }

    @GetMapping(path = "/{fieldId}/remapping/{remappedFieldId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> remapping(
            @PathVariable("fieldId") long fieldId,
            @PathVariable("remappedFieldId") long remappedFieldId,
            HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(new java.util.LinkedHashMap<>());
    }

    private Map<String, Object> toField(AnalyticsField field) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", field.getId());
        json.put("name", field.getName());
        json.put("display_name", Optional.ofNullable(field.getDisplayName()).orElse(field.getName()));
        json.put("description", null);
        json.put("table_id", field.getTableId());
        json.put("db_id", field.getDatabaseId());
        json.put("base_type", field.getBaseType());
        json.put("effective_type", Optional.ofNullable(field.getEffectiveType()).orElse(field.getBaseType()));
        json.put("semantic_type", field.getSemanticType());
        json.put("has_field_values", "list");
        json.put("active", field.isActive());
        json.put("position", field.getPosition());
        json.put("visibility_type", Optional.ofNullable(field.getVisibilityType()).orElse("normal"));
        json.put("fingerprint", parseFingerprint(field.getFingerprintJson()));
        json.put("created_at", formatTimestamp(field.getCreatedAt()));
        json.put("updated_at", formatTimestamp(field.getUpdatedAt()));
        return json;
    }

    private Object parseFingerprint(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (IOException ignored) {
            return null;
        }
    }

    private String formatTimestamp(java.time.Instant instant) {
        if (instant == null) {
            return null;
        }
        return METABASE_TIMESTAMP.format(instant.atZone(ZoneId.systemDefault()).toLocalDateTime());
    }

    private static String qualifyTable(String schema, String name) {
        String tableName = quoteIdentifier(name);
        if (schema == null || schema.isBlank()) {
            return tableName;
        }
        return quoteIdentifier(schema) + "." + tableName;
    }

    private static String quoteIdentifier(String identifier) {
        if (identifier == null) {
            throw new IllegalArgumentException("Identifier cannot be null");
        }
        String escaped = identifier.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private static Object normalizeValue(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Integer || raw instanceof Short || raw instanceof Byte) {
            return raw;
        }
        if (raw instanceof Long value) {
            if (Math.abs(value) > JS_SAFE_INTEGER_MAX) {
                return Long.toString(value);
            }
            return value;
        }
        if (raw instanceof BigInteger value) {
            try {
                long asLong = value.longValueExact();
                if (Math.abs(asLong) > JS_SAFE_INTEGER_MAX) {
                    return value.toString();
                }
                return asLong;
            } catch (ArithmeticException ignore) {
                return value.toString();
            }
        }
        if (raw instanceof java.sql.Date date) {
            LocalDate localDate = date.toLocalDate();
            return localDate.toString();
        }
        if (raw instanceof Timestamp timestamp) {
            Instant instant = timestamp.toInstant();
            return java.time.format.DateTimeFormatter.ISO_INSTANT.format(instant);
        }
        if (raw instanceof Time time) {
            return time.toLocalTime().toString();
        }
        if (raw instanceof Boolean) {
            return raw;
        }
        return raw.toString();
    }
}
