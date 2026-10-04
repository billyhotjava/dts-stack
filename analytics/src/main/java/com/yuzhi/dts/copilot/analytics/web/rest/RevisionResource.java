package com.yuzhi.dts.copilot.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsRevision;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.service.RevisionService;
import com.yuzhi.dts.copilot.analytics.web.support.MetabaseAuth;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/revision")
@Transactional
public class RevisionResource {

    private final AnalyticsSessionService sessionService;
    private final RevisionService revisionService;
    private final ObjectMapper objectMapper;

    public RevisionResource(AnalyticsSessionService sessionService, RevisionService revisionService, ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.revisionService = revisionService;
        this.objectMapper = objectMapper;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> list(
            @RequestParam(name = "entity", required = false) String entity,
            @RequestParam(name = "model", required = false) String model,
            @RequestParam(name = "id", required = false) Long id,
            HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        String resolvedModel = resolveModel(entity, model);
        long modelId = id == null ? 0 : id;

        List<AnalyticsRevision> revisions = revisionService.list(resolvedModel, modelId);
        return ResponseEntity.ok(revisions.stream().map(this::toRevisionItem).toList());
    }

    @GetMapping(path = "/{revisionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> get(@PathVariable("revisionId") long revisionId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        AnalyticsRevision revision = revisionService.find(revisionId).orElse(null);
        if (revision == null) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> map = toRevisionItem(revision);
        map.put("object", parseJsonObject(revision.getObjectJson()));
        return ResponseEntity.ok(map);
    }

    @PostMapping(path = "/revert", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> revert(@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }

        long revisionId = body != null && body.path("revision_id").canConvertToLong()
                ? body.path("revision_id").asLong()
                : body != null && body.path("id").canConvertToLong() ? body.path("id").asLong() : 0;
        if (revisionId <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "revision_id is required"));
        }

        Optional<Map<String, Object>> result = revisionService.revert(revisionId, user.get().getId());
        if (result.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result.get());
    }

    private Map<String, Object> toRevisionItem(AnalyticsRevision revision) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", revision.getId());
        map.put("model", revision.getModel());
        map.put("model_id", revision.getModelId());
        map.put("user_id", revision.getUserId());
        map.put("created_at", revision.getCreatedAt());
        map.put("is_reversion", revision.isReversion());
        return map;
    }

    private Object parseJsonObject(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node == null || node.isNull()) {
                return null;
            }
            return node;
        } catch (Exception e) {
            return null;
        }
    }

    private static String resolveModel(String entity, String model) {
        String candidate = model;
        if (candidate == null || candidate.isBlank()) {
            candidate = entity;
        }
        if (candidate == null) {
            return null;
        }
        String normalized = candidate.trim().toLowerCase();
        if ("question".equals(normalized)) {
            return RevisionService.MODEL_CARD;
        }
        return normalized;
    }
}

