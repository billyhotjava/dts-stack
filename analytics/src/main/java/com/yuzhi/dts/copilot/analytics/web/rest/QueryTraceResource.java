package com.yuzhi.dts.copilot.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsQueryTrace;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.service.QueryTraceService;
import com.yuzhi.dts.copilot.analytics.web.support.MetabaseAuth;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/query-trace")
public class QueryTraceResource {

    private final AnalyticsSessionService sessionService;
    private final QueryTraceService queryTraceService;
    private final ObjectMapper objectMapper;

    public QueryTraceResource(
            AnalyticsSessionService sessionService,
            QueryTraceService queryTraceService,
            ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.queryTraceService = queryTraceService;
        this.objectMapper = objectMapper;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> list(
            @RequestParam(name = "metricId", required = false) Long metricId,
            @RequestParam(name = "cardId", required = false) Long cardId,
            @RequestParam(name = "limit", required = false, defaultValue = "100") int limit,
            HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }

        List<AnalyticsQueryTrace> list = queryTraceService.listRecent(metricId, cardId, limit);
        return ResponseEntity.ok(list.stream().map(this::toResponse).toList());
    }

    @GetMapping(path = "/metric/{metricId}/versions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> metricVersions(@PathVariable("metricId") long metricId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(queryTraceService.listMetricVersions(metricId));
    }

    @GetMapping(path = "/failure-summary", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> failureSummary(
            @RequestParam(name = "days", required = false, defaultValue = "7") int days,
            @RequestParam(name = "topN", required = false, defaultValue = "10") int topN,
            @RequestParam(name = "chain", required = false) String chain,
            HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(queryTraceService.summarizeFailures(days, topN, chain));
    }

    private Map<String, Object> toResponse(AnalyticsQueryTrace trace) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", trace.getId());
        map.put("chain", trace.getChain());
        map.put("cardId", trace.getCardId());
        map.put("databaseId", trace.getDatabaseId());
        map.put("metricId", trace.getMetricId());
        map.put("metricVersion", trace.getMetricVersion());
        map.put("status", trace.getStatus());
        map.put("errorCode", trace.getErrorCode());
        map.put("requestId", trace.getRequestId());
        map.put("actorUserId", trace.getActorUserId());
        map.put("dept", trace.getDept());
        map.put("classification", trace.getClassification());
        map.put("durationMs", trace.getDurationMs());
        map.put("sqlText", trace.getSqlText());
        map.put("context", parseJson(trace.getContextJson()));
        map.put("createdAt", trace.getCreatedAt());
        return map;
    }

    private JsonNode parseJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            return null;
        }
    }
}
