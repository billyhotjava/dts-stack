package com.yuzhi.dts.copilot.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsMetric;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsMetricRepository;
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
@RequestMapping("/api/metric")
@Transactional
public class MetricResource {

    private final AnalyticsSessionService sessionService;
    private final AnalyticsMetricRepository metricRepository;
    private final ObjectMapper objectMapper;

    public MetricResource(AnalyticsSessionService sessionService, AnalyticsMetricRepository metricRepository, ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.metricRepository = metricRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> list(HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(metricRepository.findAllByArchivedFalseOrderByIdAsc().stream().map(this::toMetricResponse).toList());
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

        AnalyticsMetric metric = new AnalyticsMetric();
        metric.setName(name);
        metric.setDescription(body != null && body.has("description") ? body.path("description").isNull() ? null : body.path("description").asText(null) : null);
        metric.setCreatorId(user.get().getId());
        metric.setArchived(body != null && body.has("archived") && body.path("archived").asBoolean(false));
        metric.setMetricJson(body == null ? "{}" : body.toString());
        metric = metricRepository.save(metric);
        return ResponseEntity.ok(toMetricResponse(metric));
    }

    @GetMapping(path = "/{metricId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> get(@PathVariable("metricId") long metricId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        AnalyticsMetric metric = metricRepository.findById(metricId).orElse(null);
        if (metric == null || metric.isArchived()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toMetricResponse(metric));
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> update(@PathVariable("id") long id, @RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        AnalyticsMetric metric = metricRepository.findById(id).orElse(null);
        if (metric == null || metric.isArchived()) {
            return ResponseEntity.notFound().build();
        }

        if (body != null && body.has("name")) {
            String name = trimToNull(body.path("name").asText(null));
            if (name == null) {
                return ResponseEntity.badRequest().body(Map.of("errors", Map.of("name", "value must be a non-blank string.")));
            }
            metric.setName(name);
        }
        if (body != null && body.has("description")) {
            metric.setDescription(body.path("description").isNull() ? null : body.path("description").asText(null));
        }
        if (body != null && body.has("archived")) {
            metric.setArchived(body.path("archived").asBoolean(false));
        }
        if (body != null) {
            metric.setMetricJson(body.toString());
        }
        metricRepository.save(metric);
        return ResponseEntity.ok(toMetricResponse(metric));
    }

    @DeleteMapping(path = "/{metricId}")
    public ResponseEntity<?> delete(@PathVariable("metricId") long metricId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        AnalyticsMetric metric = metricRepository.findById(metricId).orElse(null);
        if (metric == null) {
            return ResponseEntity.notFound().build();
        }
        metric.setArchived(true);
        metricRepository.save(metric);
        return ResponseEntity.noContent().build();
    }

    private ObjectNode toMetricResponse(AnalyticsMetric metric) {
        ObjectNode node = objectMapper.createObjectNode();
        if (metric.getMetricJson() != null && !metric.getMetricJson().isBlank()) {
            try {
                JsonNode parsed = objectMapper.readTree(metric.getMetricJson());
                if (parsed != null && parsed.isObject()) {
                    node = (ObjectNode) parsed;
                }
            } catch (Exception ignored) {
            }
        }
        node.put("id", metric.getId());
        node.put("name", metric.getName());
        node.put("creator_id", metric.getCreatorId());
        node.put("archived", metric.isArchived());
        node.putPOJO("created_at", metric.getCreatedAt());
        node.putPOJO("updated_at", metric.getUpdatedAt());
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

