package com.yuzhi.dts.copilot.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsCard;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsPulse;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsPulseSubscription;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsCardRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsPulseRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsPulseSubscriptionRepository;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pulse")
@Transactional
public class PulseResource {

    private final AnalyticsSessionService sessionService;
    private final AnalyticsPulseRepository pulseRepository;
    private final AnalyticsPulseSubscriptionRepository subscriptionRepository;
    private final AnalyticsCardRepository cardRepository;
    private final ObjectMapper objectMapper;

    public PulseResource(
            AnalyticsSessionService sessionService,
            AnalyticsPulseRepository pulseRepository,
            AnalyticsPulseSubscriptionRepository subscriptionRepository,
            AnalyticsCardRepository cardRepository,
            ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.pulseRepository = pulseRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.cardRepository = cardRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> list(HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(pulseRepository.findAllByArchivedFalseOrderByIdAsc().stream().map(this::toPulseResponse).toList());
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

        AnalyticsPulse pulse = new AnalyticsPulse();
        pulse.setName(name);
        pulse.setCreatorId(user.get().getId());
        pulse.setArchived(body != null && body.has("archived") && body.path("archived").asBoolean(false));
        pulse.setCollectionId(body != null && body.has("collection_id") && body.path("collection_id").canConvertToLong()
                ? body.path("collection_id").asLong()
                : null);
        pulse.setPulseJson(body == null ? "{}" : body.toString());
        pulse = pulseRepository.save(pulse);

        AnalyticsPulseSubscription subscription = subscriptionRepository
                .findByPulseIdAndUserId(pulse.getId(), user.get().getId())
                .orElse(null);
        if (subscription == null) {
            AnalyticsPulseSubscription s = new AnalyticsPulseSubscription();
            s.setPulseId(pulse.getId());
            s.setUserId(user.get().getId());
            subscriptionRepository.save(s);
        }

        return ResponseEntity.ok(toPulseResponse(pulse));
    }

    @GetMapping(path = "/{pulseId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> get(@PathVariable("pulseId") long pulseId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        AnalyticsPulse pulse = pulseRepository.findById(pulseId).orElse(null);
        if (pulse == null || pulse.isArchived()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toPulseResponse(pulse));
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> update(@PathVariable("id") long id, @RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        AnalyticsPulse pulse = pulseRepository.findById(id).orElse(null);
        if (pulse == null || pulse.isArchived()) {
            return ResponseEntity.notFound().build();
        }

        if (body != null && body.has("name")) {
            String name = trimToNull(body.path("name").asText(null));
            if (name == null) {
                return ResponseEntity.badRequest().body(Map.of("errors", Map.of("name", "value must be a non-blank string.")));
            }
            pulse.setName(name);
        }
        if (body != null && body.has("archived")) {
            pulse.setArchived(body.path("archived").asBoolean(false));
        }
        if (body != null && body.has("collection_id")) {
            pulse.setCollectionId(body.path("collection_id").isNull()
                    ? null
                    : body.path("collection_id").canConvertToLong() ? body.path("collection_id").asLong() : pulse.getCollectionId());
        }
        if (body != null) {
            pulse.setPulseJson(body.toString());
        }
        pulseRepository.save(pulse);
        return ResponseEntity.ok(toPulseResponse(pulse));
    }

    @PostMapping(path = "/test", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> test(@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @GetMapping(path = "/form_input", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> formInput(HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }

        List<Map<String, Object>> channels = List.of(Map.of(
                "type",
                "email",
                "name",
                "Email",
                "configured",
                true,
                "details_fields",
                List.of(),
                "recipients",
                List.of()));

        List<Map<String, Object>> schedules = List.of(
                Map.of("schedule_type", "hourly"),
                Map.of("schedule_type", "daily"),
                Map.of("schedule_type", "weekly"),
                Map.of("schedule_type", "monthly"));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("channels", channels);
        response.put("schedules", schedules);
        return ResponseEntity.ok(response);
    }

    @GetMapping(path = "/preview_card_info/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> previewCardInfo(@PathVariable("id") long cardId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        AnalyticsCard card = cardRepository.findById(cardId).orElse(null);
        if (card == null || card.isArchived()) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", card.getId());
        map.put("name", card.getName());
        map.put("description", card.getDescription());
        map.put("collection_id", card.getCollectionId());
        map.put("database_id", card.getDatabaseId());
        map.put("display", card.getDisplay());
        map.put("archived", card.isArchived());
        return ResponseEntity.ok(map);
    }

    @DeleteMapping(path = "/{id}/subscription")
    public ResponseEntity<?> unsubscribe(@PathVariable("id") long pulseId, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        subscriptionRepository.deleteByPulseIdAndUserId(pulseId, user.get().getId());
        return ResponseEntity.noContent().build();
    }

    private ObjectNode toPulseResponse(AnalyticsPulse pulse) {
        ObjectNode node = objectMapper.createObjectNode();
        if (pulse.getPulseJson() != null && !pulse.getPulseJson().isBlank()) {
            try {
                JsonNode parsed = objectMapper.readTree(pulse.getPulseJson());
                if (parsed != null && parsed.isObject()) {
                    node = (ObjectNode) parsed;
                }
            } catch (Exception ignored) {
            }
        }
        node.put("id", pulse.getId());
        node.put("name", pulse.getName());
        node.put("creator_id", pulse.getCreatorId());
        if (pulse.getCollectionId() == null) {
            node.putNull("collection_id");
        } else {
            node.put("collection_id", pulse.getCollectionId());
        }
        node.put("archived", pulse.isArchived());
        node.putPOJO("created_at", pulse.getCreatedAt());
        node.putPOJO("updated_at", pulse.getUpdatedAt());

        if (!node.has("channels") || !node.get("channels").isArray()) {
            node.set("channels", objectMapper.createArrayNode());
        }
        if (!node.has("cards") || !node.get("cards").isArray()) {
            node.set("cards", objectMapper.createArrayNode());
        }
        if (!node.has("skip_if_empty")) {
            node.put("skip_if_empty", false);
        }
        if (!node.has("alert_condition")) {
            node.putNull("alert_condition");
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
