package com.yuzhi.dts.copilot.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsAnalysisDraft;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsCard;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.service.AnalysisDraftService;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.service.DatasetQueryService;
import com.yuzhi.dts.copilot.analytics.web.support.MetabaseAuth;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analysis-drafts")
public class AnalysisDraftResource {

    private final AnalyticsSessionService sessionService;
    private final AnalysisDraftService analysisDraftService;
    private final ObjectMapper objectMapper;

    public AnalysisDraftResource(
            AnalyticsSessionService sessionService, AnalysisDraftService analysisDraftService, ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.analysisDraftService = analysisDraftService;
        this.objectMapper = objectMapper;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> list(HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        List<Map<String, Object>> items = analysisDraftService.list(user.get().getId()).stream()
                .map(this::toDraftResponse)
                .toList();
        return ResponseEntity.ok(items);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<?> create(@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        AnalyticsAnalysisDraft draft = analysisDraftService.create(user.get().getId(), body);
        return ResponseEntity.ok(toDraftResponse(draft));
    }

    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> get(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        return analysisDraftService.get(user.get().getId(), id)
                .<ResponseEntity<?>>map(draft -> ResponseEntity.ok(toDraftResponse(draft)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping(path = "/{id}/archive", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<?> archive(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        AnalyticsAnalysisDraft draft = analysisDraftService.archive(user.get().getId(), id);
        return ResponseEntity.ok(toDraftResponse(draft));
    }

    @DeleteMapping(path = "/{id}")
    @Transactional
    public ResponseEntity<?> delete(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        analysisDraftService.delete(user.get().getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/{id}/run", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<?> run(
            @PathVariable("id") long id, @RequestBody(required = false) JsonNode body, HttpServletRequest request)
            throws Exception {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        DatasetQueryService.DatasetResult result = analysisDraftService.run(user.get().getId(), id, body);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rows", result.rows());
        payload.put("cols", result.cols());
        payload.put("results_metadata", result.resultsMetadataColumns());
        payload.put("results_timezone", result.resultsTimezone());
        payload.put("row_count", result.rows().size());
        return ResponseEntity.ok(payload);
    }

    @PostMapping(path = "/{id}/save-card", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<?> saveCard(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        AnalyticsCard card = analysisDraftService.saveCard(user.get().getId(), id);
        AnalyticsAnalysisDraft draft =
                analysisDraftService.get(user.get().getId(), id).orElseThrow(() -> new IllegalArgumentException("analysis draft not found"));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("draft", toDraftResponse(draft));
        payload.put("card", toCardSummary(card));
        return ResponseEntity.ok(payload);
    }

    private Map<String, Object> toDraftResponse(AnalyticsAnalysisDraft draft) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", draft.getId());
        response.put("entity_id", draft.getEntityId());
        response.put("title", draft.getTitle());
        response.put("source_type", draft.getSourceType());
        response.put("session_id", draft.getSessionId());
        response.put("message_id", draft.getMessageId());
        response.put("question", draft.getQuestion());
        response.put("database_id", draft.getDatabaseId());
        response.put("sql_text", draft.getSqlText());
        response.put("explanation_text", draft.getExplanationText());
        response.put("suggested_display", draft.getSuggestedDisplay());
        response.put("response_kind", draft.getResponseKind());
        response.put("data_surface", draft.getDataSurface());
        response.put("quality_level", draft.getQualityLevel());
        response.put("quality_notes", draft.getQualityNotes());
        response.put("report_code", draft.getReportCode());
        response.put("status", draft.getStatus());
        response.put("linked_card_id", draft.getLinkedCardId());
        response.put("linked_dashboard_id", draft.getLinkedDashboardId());
        response.put("linked_screen_id", draft.getLinkedScreenId());
        response.put("created_at", draft.getCreatedAt());
        response.put("updated_at", draft.getUpdatedAt());
        return response;
    }

    private Map<String, Object> toCardSummary(AnalyticsCard card) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", card.getId());
        response.put("entity_id", card.getEntityId());
        response.put("name", card.getName());
        response.put("database_id", card.getDatabaseId());
        response.put("display", card.getDisplay());
        response.put("dataset_query", safeJson(card.getDatasetQueryJson()));
        return response;
    }

    private Object safeJson(String value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception ignored) {
            return null;
        }
    }
}
