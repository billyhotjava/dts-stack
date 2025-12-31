package com.yuzhi.dts.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.yuzhi.dts.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.analytics.service.DatasetQueryService;
import com.yuzhi.dts.analytics.service.MbqlToSqlService;
import com.yuzhi.dts.analytics.web.support.MetabaseAuth;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dataset")
public class DatasetResource {

    private final AnalyticsSessionService sessionService;
    private final DatasetQueryService datasetQueryService;
    private final MbqlToSqlService mbqlToSqlService;

    public DatasetResource(
            AnalyticsSessionService sessionService, DatasetQueryService datasetQueryService, MbqlToSqlService mbqlToSqlService) {
        this.sessionService = sessionService;
        this.datasetQueryService = datasetQueryService;
        this.mbqlToSqlService = mbqlToSqlService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> run(@RequestBody JsonNode body, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }

        if (body == null || !body.isObject()) {
            return ResponseEntity.badRequest().body(Map.of("errors", Map.of("query", "Invalid dataset query")));
        }

        long databaseId = body.path("database").asLong(0);
        String type = body.path("type").asText(null);
        if (type == null || type.isBlank()) {
            if (body.has("native")) {
                type = "native";
            } else if (body.has("query")) {
                type = "query";
            }
        }

        if (databaseId <= 0) {
            return ResponseEntity.badRequest().body(Map.of("errors", Map.of("database", "database is required")));
        }

        DatasetQueryService.DatasetConstraints constraints = parseConstraints(body);
        OffsetDateTime startedAt = OffsetDateTime.now();
        long startedMillis = System.currentTimeMillis();

        try {
            String sql;
            Map<String, Object> jsonQuery = new LinkedHashMap<>();
            jsonQuery.put("database", databaseId);
            jsonQuery.put(
                    "middleware",
                    Map.of(
                            "js-int-to-string?", true,
                            "add-default-userland-constraints?", true));

            if ("native".equalsIgnoreCase(type)) {
                JsonNode nativeQuery = body.path("native");
                sql = nativeQuery.path("query").asText(null);
                if (sql == null || sql.isBlank()) {
                    return ResponseEntity.badRequest().body(Map.of("errors", Map.of("query", "native.query is required")));
                }
                jsonQuery.put("type", "native");
                jsonQuery.put("native", Map.of("query", sql));
            } else if ("query".equalsIgnoreCase(type)) {
                JsonNode mbql = body.get("query");
                MbqlToSqlService.TranslationResult translated = mbqlToSqlService.translateSelect(databaseId, mbql, constraints);
                sql = translated.sql();
                jsonQuery.put("type", "query");
                jsonQuery.put("query", mbql);
            } else {
                return ResponseEntity.badRequest()
                        .body(Map.of("errors", Map.of("type", "Only native and query (MBQL) dataset types are supported")));
            }

            DatasetQueryService.DatasetResult result = datasetQueryService.runNative(databaseId, sql, constraints);
            long runningTimeMs = System.currentTimeMillis() - startedMillis;

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("rows", result.rows());
            data.put("cols", result.cols());
            data.put("native_form", Map.of("query", sql));
            data.put("results_timezone", result.resultsTimezone());
            data.put("results_metadata", Map.of("columns", result.resultsMetadataColumns()));
            data.put("insights", null);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", data);
            response.put("database_id", databaseId);
            response.put("started_at", startedAt);
            response.put("json_query", jsonQuery);
            response.put("average_execution_time", null);
            response.put("status", "completed");
            response.put("context", body.path("context").asText("ad-hoc"));
            response.put("row_count", result.rows().size());
            response.put("running_time", runningTimeMs);

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("errors", Map.of("query", e.getMessage())));
        } catch (SQLException e) {
            Map<String, Object> via = new LinkedHashMap<>();
            via.put("status", "failed");
            via.put("class", e.getClass().toString());
            via.put("error", "Error executing query: " + e.getMessage());
            via.put("stacktrace", List.of());
            via.put("card_id", null);
            via.put("context", body.path("context").asText("ad-hoc"));

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("database_id", databaseId);
            response.put("started_at", startedAt);
            response.put("via", List.of(via));
            response.put("card_id", null);
            response.put("context", body.path("context").asText("ad-hoc"));
            response.put("error", e.getMessage());
            response.put("row_count", 0);
            response.put("running_time", 0);
            response.put("data", Map.of("rows", List.of(), "cols", List.of()));

            return ResponseEntity.accepted().body(response);
        }
    }

    @PostMapping(path = "/native", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> nativeQuery(@RequestBody JsonNode body, HttpServletRequest request) {
        return run(body, request);
    }

    @PostMapping(path = "/pivot", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> pivot(@RequestBody JsonNode body, HttpServletRequest request) {
        return run(body, request);
    }

    @PostMapping(path = "/duration", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> duration(@RequestBody JsonNode body, HttpServletRequest request) {
        return run(body, request);
    }

    private static DatasetQueryService.DatasetConstraints parseConstraints(JsonNode body) {
        int maxResults = 2000;
        int timeoutSeconds = 60;
        String timezone = ZoneId.systemDefault().getId();

        JsonNode constraintsNode = body.path("constraints");
        if (constraintsNode != null && constraintsNode.isObject()) {
            JsonNode maxResultsNode = constraintsNode.get("max-results");
            if (maxResultsNode != null && maxResultsNode.canConvertToInt()) {
                maxResults = maxResultsNode.asInt();
            }
        }

        JsonNode queryTimeoutNode = body.path("query_timeout");
        if (queryTimeoutNode != null && queryTimeoutNode.canConvertToInt()) {
            timeoutSeconds = queryTimeoutNode.asInt();
        }

        JsonNode requestedTz = body.path("requested_timezone");
        if (requestedTz != null && requestedTz.isTextual() && !requestedTz.asText().isBlank()) {
            timezone = requestedTz.asText();
        }

        if (maxResults <= 0) {
            maxResults = 2000;
        }
        if (timeoutSeconds <= 0) {
            timeoutSeconds = (int) Duration.ofSeconds(60).toSeconds();
        }

        return new DatasetQueryService.DatasetConstraints(maxResults, timeoutSeconds, timezone);
    }
}
