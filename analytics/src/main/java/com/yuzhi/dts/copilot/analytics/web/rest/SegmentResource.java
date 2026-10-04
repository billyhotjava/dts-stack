package com.yuzhi.dts.copilot.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsSegment;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsSegmentRepository;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.web.support.MetabaseAuth;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/segment")
@Transactional
public class SegmentResource {

    private final AnalyticsSessionService sessionService;
    private final AnalyticsSegmentRepository segmentRepository;
    private final ObjectMapper objectMapper;

    public SegmentResource(AnalyticsSessionService sessionService, AnalyticsSegmentRepository segmentRepository, ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.segmentRepository = segmentRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> list(HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(segmentRepository.findAllByArchivedFalseOrderByIdAsc().stream().map(this::toSegmentResponse).toList());
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> create(@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        String name = body == null ? null : trimToNull(body.path("name").asText(null));
        if (name == null) {
            return ResponseEntity.badRequest().body(Map.of("errors", Map.of("name", "value must be a non-blank string.")));
        }

        AnalyticsSegment segment = new AnalyticsSegment();
        segment.setName(name);
        segment.setDescription(body != null && body.has("description") ? body.path("description").isNull() ? null : body.path("description").asText(null) : null);
        segment.setCreatorId(user.get().getId());
        segment.setArchived(body != null && body.has("archived") && body.path("archived").asBoolean(false));
        segment.setSegmentJson(body == null ? "{}" : body.toString());
        segment = segmentRepository.save(segment);
        return ResponseEntity.ok(toSegmentResponse(segment));
    }

    @GetMapping(path = "/{segmentId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> get(@PathVariable("segmentId") long segmentId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        AnalyticsSegment segment = segmentRepository.findById(segmentId).orElse(null);
        if (segment == null || segment.isArchived()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toSegmentResponse(segment));
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> update(@PathVariable("id") long id, @RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        AnalyticsSegment segment = segmentRepository.findById(id).orElse(null);
        if (segment == null || segment.isArchived()) {
            return ResponseEntity.notFound().build();
        }

        if (body != null && body.has("name")) {
            String name = trimToNull(body.path("name").asText(null));
            if (name == null) {
                return ResponseEntity.badRequest().body(Map.of("errors", Map.of("name", "value must be a non-blank string.")));
            }
            segment.setName(name);
        }
        if (body != null && body.has("description")) {
            segment.setDescription(body.path("description").isNull() ? null : body.path("description").asText(null));
        }
        if (body != null && body.has("archived")) {
            segment.setArchived(body.path("archived").asBoolean(false));
        }
        if (body != null) {
            segment.setSegmentJson(body.toString());
        }
        segmentRepository.save(segment);
        return ResponseEntity.ok(toSegmentResponse(segment));
    }

    @DeleteMapping(path = "/{segmentId}")
    public ResponseEntity<?> delete(@PathVariable("segmentId") long segmentId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        AnalyticsSegment segment = segmentRepository.findById(segmentId).orElse(null);
        if (segment == null) {
            return ResponseEntity.notFound().build();
        }
        segment.setArchived(true);
        segmentRepository.save(segment);
        return ResponseEntity.noContent().build();
    }

    private ObjectNode toSegmentResponse(AnalyticsSegment segment) {
        ObjectNode node = objectMapper.createObjectNode();
        if (segment.getSegmentJson() != null && !segment.getSegmentJson().isBlank()) {
            try {
                JsonNode parsed = objectMapper.readTree(segment.getSegmentJson());
                if (parsed != null && parsed.isObject()) {
                    node = (ObjectNode) parsed;
                }
            } catch (Exception ignored) {
            }
        }
        node.put("id", segment.getId());
        node.put("name", segment.getName());
        node.put("creator_id", segment.getCreatorId());
        node.put("archived", segment.isArchived());
        node.putPOJO("created_at", segment.getCreatedAt());
        node.putPOJO("updated_at", segment.getUpdatedAt());
        if (!node.has("definition") || !node.get("definition").isObject()) {
            node.set("definition", objectMapper.createObjectNode());
        }
        if (!node.has("table_id")) {
            node.putNull("table_id");
        }
        if (!node.has("description")) {
            node.putNull("description");
        }
        return node;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}

