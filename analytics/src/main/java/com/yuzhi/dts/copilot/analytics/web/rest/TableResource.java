package com.yuzhi.dts.copilot.analytics.web.rest;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsField;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsTable;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsFieldRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsTableRepository;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.service.ExternalDatabaseDataSourceRegistry;
import com.yuzhi.dts.copilot.analytics.web.support.MetabaseAuth;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/table")
public class TableResource {

    private static final DateTimeFormatter METABASE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    private final AnalyticsSessionService sessionService;
    private final AnalyticsTableRepository tableRepository;
    private final AnalyticsFieldRepository fieldRepository;
    private final ExternalDatabaseDataSourceRegistry dataSourceRegistry;

    public TableResource(
            AnalyticsSessionService sessionService,
            AnalyticsTableRepository tableRepository,
            AnalyticsFieldRepository fieldRepository,
            ExternalDatabaseDataSourceRegistry dataSourceRegistry) {
        this.sessionService = sessionService;
        this.tableRepository = tableRepository;
        this.fieldRepository = fieldRepository;
        this.dataSourceRegistry = dataSourceRegistry;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> list(
            @RequestParam(name = "db", required = false) Long db,
            @RequestParam(name = "db_id", required = false) Long dbId,
            HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        Long databaseId = dbId != null ? dbId : db;
        List<AnalyticsTable> tables;
        if (databaseId != null && databaseId > 0) {
            tables = tableRepository.findAllByDatabaseIdOrderBySchemaNameAscNameAsc(databaseId).stream()
                    .filter(AnalyticsTable::isActive)
                    .toList();
        } else {
            tables = tableRepository.findAll().stream().filter(AnalyticsTable::isActive).toList();
        }
        return ResponseEntity.ok(tables.stream().map(this::toTableSummary).toList());
    }

    @GetMapping(path = "/{tableId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> get(@PathVariable("tableId") long tableId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        Optional<AnalyticsTable> tableOpt = tableRepository.findById(tableId);
        if (tableOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        AnalyticsTable table = tableOpt.get();
        List<Map<String, Object>> fields =
                fieldRepository.findAllByTableIdOrderByPositionAscIdAsc(tableId).stream()
                        .filter(AnalyticsField::isActive)
                        .map(this::toField)
                        .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", table.getId());
        response.put("db_id", table.getDatabaseId());
        response.put("schema", emptyToNull(table.getSchemaName()));
        response.put("name", table.getName());
        response.put("display_name", Optional.ofNullable(table.getDisplayName()).orElse(table.getName()));
        response.put("description", table.getDescription());
        response.put("active", table.isActive());
        response.put("visibility_type", Optional.ofNullable(table.getVisibilityType()).orElse("normal"));
        response.put("field_order", "database");
        response.put("is_upload", false);
        response.put("created_at", formatTimestamp(table.getCreatedAt()));
        response.put("updated_at", formatTimestamp(table.getUpdatedAt()));
        response.put("fields", fields);
        response.put("points_of_interest", null);
        response.put("caveats", null);
        return ResponseEntity.ok(response);
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> update(@PathVariable("id") long id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        Optional<AnalyticsTable> existingOpt = tableRepository.findById(id);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        AnalyticsTable table = existingOpt.get();
        if (body != null) {
            Object displayName = body.get("display_name");
            if (displayName instanceof String s) {
                table.setDisplayName(s);
            }
            Object description = body.get("description");
            if (description instanceof String s) {
                table.setDescription(s);
            }
            Object visibilityType = body.get("visibility_type");
            if (visibilityType instanceof String s) {
                table.setVisibilityType(s);
            }
            Object active = body.get("active");
            if (active instanceof Boolean b) {
                table.setActive(b);
            }
            tableRepository.save(table);
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping(path = "/{tableId}/fks", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> fks(@PathVariable("tableId") long tableId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        Optional<AnalyticsTable> tableOpt = tableRepository.findById(tableId);
        if (tableOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        AnalyticsTable table = tableOpt.get();

        Map<String, AnalyticsField> fkFieldsByName = new LinkedHashMap<>();
        for (AnalyticsField field : fieldRepository.findAllByTableIdOrderByPositionAscIdAsc(tableId)) {
            if (field.getName() != null) {
                fkFieldsByName.put(field.getName(), field);
            }
        }

        List<Map<String, Object>> out = new ArrayList<>();
        try (Connection connection = dataSourceRegistry.get(table.getDatabaseId()).getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            String schema = emptyToNull(table.getSchemaName());
            try (ResultSet rs = meta.getImportedKeys(connection.getCatalog(), schema, table.getName())) {
                while (rs.next()) {
                    String fkSchema = rs.getString("FKTABLE_SCHEM");
                    String fkTable = rs.getString("FKTABLE_NAME");
                    String fkColumn = rs.getString("FKCOLUMN_NAME");
                    String pkSchema = rs.getString("PKTABLE_SCHEM");
                    String pkTable = rs.getString("PKTABLE_NAME");
                    String pkColumn = rs.getString("PKCOLUMN_NAME");
                    String fkName = rs.getString("FK_NAME");

                    if (fkTable == null || fkColumn == null || pkTable == null || pkColumn == null) {
                        continue;
                    }

                    AnalyticsField fkField = fkFieldsByName.get(fkColumn);
                    if (fkField == null || fkField.getId() == null) {
                        continue;
                    }

                    Optional<AnalyticsTable> pkTableOpt =
                            tableRepository.findByDatabaseIdAndSchemaNameAndName(table.getDatabaseId(), normalizeSchema(pkSchema), pkTable);
                    if (pkTableOpt.isEmpty()) {
                        continue;
                    }
                    AnalyticsTable pkTableEntity = pkTableOpt.get();

                    Optional<AnalyticsField> pkFieldOpt = fieldRepository.findByTableIdAndName(pkTableEntity.getId(), pkColumn);
                    if (pkFieldOpt.isEmpty() || pkFieldOpt.get().getId() == null) {
                        continue;
                    }

                    Map<String, Object> fk = new LinkedHashMap<>();
                    fk.put("fk_field_id", fkField.getId());
                    fk.put("fk_target_field_id", pkFieldOpt.get().getId());
                    fk.put("fk_name", fkName);
                    fk.put("fk_table_id", tableId);
                    fk.put("fk_schema", fkSchema);
                    fk.put("fk_table", fkTable);
                    fk.put("fk_column", fkColumn);
                    fk.put("target_table_id", pkTableEntity.getId());
                    fk.put("target_schema", pkSchema);
                    fk.put("target_table", pkTable);
                    fk.put("target_column", pkColumn);
                    out.add(fk);
                }
            }
        } catch (Exception e) {
            return ResponseEntity.accepted().body(Map.of("error", "Error reading foreign keys: " + e.getMessage()));
        }

        return ResponseEntity.ok(out);
    }

    @GetMapping(path = "/{tableId}/query_metadata", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> queryMetadata(@PathVariable("tableId") long tableId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        Optional<AnalyticsTable> tableOpt = tableRepository.findById(tableId);
        if (tableOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        AnalyticsTable table = tableOpt.get();
        List<Map<String, Object>> fields =
                fieldRepository.findAllByTableIdOrderByPositionAscIdAsc(tableId).stream()
                        .filter(AnalyticsField::isActive)
                        .map(this::toField)
                        .toList();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", table.getId());
        response.put("db_id", table.getDatabaseId());
        response.put("schema", emptyToNull(table.getSchemaName()));
        response.put("name", table.getName());
        response.put("display_name", Optional.ofNullable(table.getDisplayName()).orElse(table.getName()));
        response.put("fields", fields);
        response.put("visibility_type", Optional.ofNullable(table.getVisibilityType()).orElse("normal"));
        response.put("created_at", formatTimestamp(table.getCreatedAt()));
        response.put("updated_at", formatTimestamp(table.getUpdatedAt()));
        response.put("field_order", "database");
        return ResponseEntity.ok(response);
    }

    @PostMapping(path = "/{tableId}/rescan_values")
    public ResponseEntity<?> rescanValues(@PathVariable("tableId") long tableId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping(path = "/{tableId}/discard_values")
    public ResponseEntity<?> discardValues(@PathVariable("tableId") long tableId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok().build();
    }

    private Map<String, Object> toTableSummary(AnalyticsTable table) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", table.getId());
        json.put("db_id", table.getDatabaseId());
        json.put("schema", emptyToNull(table.getSchemaName()));
        json.put("name", table.getName());
        json.put("display_name", Optional.ofNullable(table.getDisplayName()).orElse(table.getName()));
        json.put("active", table.isActive());
        json.put("visibility_type", Optional.ofNullable(table.getVisibilityType()).orElse("normal"));
        return json;
    }

    private Map<String, Object> toField(AnalyticsField field) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", field.getId());
        json.put("name", field.getName());
        json.put("display_name", Optional.ofNullable(field.getDisplayName()).orElse(field.getName()));
        json.put("table_id", field.getTableId());
        json.put("db_id", field.getDatabaseId());
        json.put("base_type", field.getBaseType());
        json.put("effective_type", Optional.ofNullable(field.getEffectiveType()).orElse(field.getBaseType()));
        json.put("semantic_type", field.getSemanticType());
        json.put("has_field_values", "list");
        json.put("active", field.isActive());
        json.put("position", field.getPosition());
        json.put("visibility_type", Optional.ofNullable(field.getVisibilityType()).orElse("normal"));
        return json;
    }

    private String formatTimestamp(java.time.Instant instant) {
        if (instant == null) {
            return null;
        }
        return METABASE_TIMESTAMP.format(instant.atZone(ZoneId.systemDefault()).toLocalDateTime());
    }

    private static String emptyToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeSchema(String schema) {
        if (schema == null) {
            return "";
        }
        String trimmed = schema.trim();
        return trimmed.isEmpty() ? "" : trimmed;
    }
}
