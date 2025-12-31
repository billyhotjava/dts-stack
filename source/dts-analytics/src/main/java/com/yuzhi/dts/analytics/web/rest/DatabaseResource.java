package com.yuzhi.dts.analytics.web.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.domain.AnalyticsDatabase;
import com.yuzhi.dts.analytics.repository.AnalyticsDatabaseRepository;
import com.yuzhi.dts.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.analytics.web.support.MetabaseAuth;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/database")
@Transactional
public class DatabaseResource {

    private static final DateTimeFormatter METABASE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    private final AnalyticsSessionService sessionService;
    private final AnalyticsDatabaseRepository databaseRepository;
    private final ObjectMapper objectMapper;

    public DatabaseResource(
            AnalyticsSessionService sessionService, AnalyticsDatabaseRepository databaseRepository, ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.databaseRepository = databaseRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> list(HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }

        List<Map<String, Object>> data = databaseRepository.findAll().stream().map(this::toDatabaseListItem).toList();
        return ResponseEntity.ok(Map.of("data", data, "total", data.size()));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> create(@RequestBody DatabaseRequest request, HttpServletRequest servletRequest) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, servletRequest);
        if (auth.isPresent()) {
            return auth.get();
        }

        Map<String, String> errors = new LinkedHashMap<>();
        String name = request == null ? null : trimToNull(request.name());
        if (name == null) {
            errors.put("name", "value must be a non-blank string.");
        }
        String engine = request == null ? null : trimToNull(request.engine());
        if (engine == null) {
            errors.put("engine", "value must be a valid database engine.");
        }
        JsonNode details = request == null ? null : request.details();
        if (details == null || !details.isObject()) {
            errors.put("details", "value must be a map.");
        }
        if (!errors.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("errors", errors));
        }

        AnalyticsDatabase db = new AnalyticsDatabase();
        db.setName(name);
        db.setEngine(engine);
        db.setDetailsJson(details.toString());
        db.setDescription(request.description());
        db.setSample(Boolean.TRUE.equals(request.isSample()));
        db.setTimezone(Optional.ofNullable(request.timezone()).orElse(ZoneId.systemDefault().getId()));
        db.setMetadataSyncSchedule(Optional.ofNullable(request.metadataSyncSchedule()).orElse("0 50 * * * ? *"));
        db.setCacheFieldValuesSchedule(Optional.ofNullable(request.cacheFieldValuesSchedule()).orElse("0 50 0 * * ? *"));
        db.setAutoRunQueries(request.autoRunQueries() == null || request.autoRunQueries());
        db.setFullSync(request.isFullSync() == null || request.isFullSync());
        db.setOnDemand(request.isOnDemand() != null && request.isOnDemand());
        db = databaseRepository.save(db);

        return ResponseEntity.ok(toDatabaseGet(db, true));
    }

    @GetMapping(path = "/{dbId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> get(@PathVariable("dbId") long dbId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return databaseRepository.findById(dbId).<ResponseEntity<?>>map(db -> ResponseEntity.ok(toDatabaseGet(db, true))).orElseGet(
                () -> ResponseEntity.notFound().build());
    }

    @GetMapping(path = "/{dbId}/metadata", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> metadata(@PathVariable("dbId") long dbId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return databaseRepository.findById(dbId)
                .<ResponseEntity<?>>map(db -> {
                    Map<String, Object> response = new LinkedHashMap<>(toDatabaseGet(db, true));
                    response.put("tables", List.of());
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping(path = "/{dbId}/schemas", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> schemas(@PathVariable("dbId") long dbId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        if (!databaseRepository.existsById(dbId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(List.of());
    }

    @GetMapping(path = "/{dbId}/schema/{schemaName}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> schemaTables(
            @PathVariable("dbId") long dbId, @PathVariable("schemaName") String schemaName, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        if (!databaseRepository.existsById(dbId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(List.of());
    }

    @GetMapping(path = "/{dbId}/fields", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> fields(@PathVariable("dbId") long dbId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        if (!databaseRepository.existsById(dbId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(List.of());
    }

    @GetMapping(path = "/{dbId}/idfields", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> idfields(@PathVariable("dbId") long dbId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        if (!databaseRepository.existsById(dbId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(List.of());
    }

    @GetMapping(path = "/{dbId}/autocomplete_suggestions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> autocompleteSuggestions(
            @PathVariable("dbId") long dbId,
            @RequestParam(name = "matchStyle", required = false) String matchStyle,
            @RequestParam(name = "query", required = false) String query,
            HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        if (!databaseRepository.existsById(dbId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(List.of());
    }

    @GetMapping(path = "/{dbId}/card_autocomplete_suggestions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> cardAutocompleteSuggestions(@PathVariable("dbId") long dbId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        if (!databaseRepository.existsById(dbId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(List.of());
    }

    @PostMapping(path = "/{dbId}/sync_schema", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> syncSchema(@PathVariable("dbId") long dbId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        if (!databaseRepository.existsById(dbId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of());
    }

    @PostMapping(path = "/{dbId}/dismiss_spinner", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> dismissSpinner(@PathVariable("dbId") long dbId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        if (!databaseRepository.existsById(dbId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of());
    }

    @PostMapping(path = "/{dbId}/rescan_values", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> rescanValues(@PathVariable("dbId") long dbId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        if (!databaseRepository.existsById(dbId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of());
    }

    @PostMapping(path = "/{dbId}/discard_values", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> discardValues(@PathVariable("dbId") long dbId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        if (!databaseRepository.existsById(dbId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of());
    }

    @PostMapping(path = "/{dbId}/persist", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> persist(@PathVariable("dbId") long dbId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        if (!databaseRepository.existsById(dbId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of());
    }

    @PostMapping(path = "/{dbId}/unpersist", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> unpersist(@PathVariable("dbId") long dbId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        if (!databaseRepository.existsById(dbId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of());
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> update(@PathVariable("id") long id, @RequestBody DatabaseRequest request, HttpServletRequest servletRequest) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, servletRequest);
        if (auth.isPresent()) {
            return auth.get();
        }

        Optional<AnalyticsDatabase> existing = databaseRepository.findById(id);
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        AnalyticsDatabase db = existing.get();
        if (trimToNull(request.name()) != null) {
            db.setName(trimToNull(request.name()));
        }
        if (trimToNull(request.engine()) != null) {
            db.setEngine(trimToNull(request.engine()));
        }
        if (request.details() != null && request.details().isObject()) {
            db.setDetailsJson(request.details().toString());
        }
        if (request.description() != null) {
            db.setDescription(request.description());
        }
        if (request.timezone() != null) {
            db.setTimezone(request.timezone());
        }
        if (request.metadataSyncSchedule() != null) {
            db.setMetadataSyncSchedule(request.metadataSyncSchedule());
        }
        if (request.cacheFieldValuesSchedule() != null) {
            db.setCacheFieldValuesSchedule(request.cacheFieldValuesSchedule());
        }
        if (request.autoRunQueries() != null) {
            db.setAutoRunQueries(request.autoRunQueries());
        }
        if (request.isFullSync() != null) {
            db.setFullSync(request.isFullSync());
        }
        if (request.isOnDemand() != null) {
            db.setOnDemand(request.isOnDemand());
        }

        db = databaseRepository.save(db);
        return ResponseEntity.ok(toDatabaseGet(db, true));
    }

    @DeleteMapping(path = "/{dbId}")
    public ResponseEntity<?> delete(@PathVariable("dbId") long dbId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        if (!databaseRepository.existsById(dbId)) {
            return ResponseEntity.notFound().build();
        }
        databaseRepository.deleteById(dbId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/sample_database", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> addSampleDatabase(HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }

        AnalyticsDatabase db = new AnalyticsDatabase();
        db.setName("Sample Database");
        db.setEngine("h2");
        db.setDetailsJson("{\"db\":\"file:/plugins/sample-database.db\"}");
        db.setDescription("Some example data for you to play around with as you embark on your Metabase journey.");
        db.setSample(true);
        db.setTimezone("UTC");
        db.setMetadataSyncSchedule("0 50 * * * ? *");
        db.setCacheFieldValuesSchedule("0 50 0 * * ? *");
        db.setAutoRunQueries(true);
        db.setFullSync(true);
        db.setOnDemand(false);
        db = databaseRepository.save(db);
        return ResponseEntity.ok(toDatabaseGet(db, true));
    }

    @PostMapping(path = "/validate", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> validateConnection(@RequestBody DatabaseRequest request, HttpServletRequest servletRequest) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, servletRequest);
        if (auth.isPresent()) {
            return auth.get();
        }

        Map<String, String> errors = new LinkedHashMap<>();
        String engine = request == null ? null : trimToNull(request.engine());
        if (engine == null) {
            errors.put("engine", "value must be a valid database engine.");
        }
        JsonNode details = request == null ? null : request.details();
        if (details == null || !details.isObject()) {
            errors.put("details", "value must be a map.");
        }
        if (!errors.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("errors", errors));
        }

        return ResponseEntity.ok(Map.of());
    }

    private Map<String, Object> toDatabaseListItem(AnalyticsDatabase db) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", db.getId());
        item.put("name", db.getName());
        item.put("engine", db.getEngine());
        item.put("description", db.getDescription());
        item.put("details", parseDetails(db.getDetailsJson()));
        item.put("settings", null);
        item.put("caveats", null);
        item.put("points_of_interest", null);
        item.put("features", List.of());
        item.put("created_at", formatTimestamp(db.getCreatedAt()));
        item.put("updated_at", formatTimestamp(db.getUpdatedAt()));
        item.put("timezone", db.getTimezone());
        item.put("auto_run_queries", db.isAutoRunQueries());
        item.put("metadata_sync_schedule", db.getMetadataSyncSchedule());
        item.put("cache_field_values_schedule", db.getCacheFieldValuesSchedule());
        item.put("cache_ttl", null);
        item.put("is_full_sync", db.isFullSync());
        item.put("is_on_demand", db.isOnDemand());
        item.put("is_sample", db.isSample());
        item.put("initial_sync_status", "complete");
        item.put("native_permissions", "write");
        item.put("options", null);
        item.put("creator_id", null);
        return item;
    }

    private Map<String, Object> toDatabaseGet(AnalyticsDatabase db, boolean canManage) {
        Map<String, Object> item = new LinkedHashMap<>(toDatabaseListItem(db));
        item.put("can-manage", canManage);
        item.put("schedules", null);
        item.remove("native_permissions");
        return item;
    }

    private Object parseDetails(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readTree(json);
        } catch (IOException ignored) {
            return Map.of();
        }
    }

    private String formatTimestamp(java.time.Instant instant) {
        if (instant == null) {
            return null;
        }
        return METABASE_TIMESTAMP.format(instant.atZone(ZoneId.systemDefault()).toLocalDateTime());
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record DatabaseRequest(
            @JsonProperty("name") String name,
            @JsonProperty("engine") String engine,
            @JsonProperty("details") JsonNode details,
            @JsonProperty("description") String description,
            @JsonProperty("is_sample") Boolean isSample,
            @JsonProperty("timezone") String timezone,
            @JsonProperty("metadata_sync_schedule") String metadataSyncSchedule,
            @JsonProperty("cache_field_values_schedule") String cacheFieldValuesSchedule,
            @JsonProperty("auto_run_queries") Boolean autoRunQueries,
            @JsonProperty("is_full_sync") Boolean isFullSync,
            @JsonProperty("is_on_demand") Boolean isOnDemand) {}
}
