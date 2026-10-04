package com.yuzhi.dts.copilot.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsAlert;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsAlertSubscription;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsCard;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsAlertRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsAlertSubscriptionRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsCardRepository;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.web.support.MetabaseAuth;
import jakarta.servlet.http.HttpServletRequest;
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
@RequestMapping("/api/alert")
@Transactional
public class AlertResource {

    private final AnalyticsSessionService sessionService;
    private final AnalyticsAlertRepository alertRepository;
    private final AnalyticsAlertSubscriptionRepository subscriptionRepository;
    private final AnalyticsCardRepository cardRepository;
    private final ObjectMapper objectMapper;

    public AlertResource(
            AnalyticsSessionService sessionService,
            AnalyticsAlertRepository alertRepository,
            AnalyticsAlertSubscriptionRepository subscriptionRepository,
            AnalyticsCardRepository cardRepository,
            ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.alertRepository = alertRepository;
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
        return ResponseEntity.ok(alertRepository.findAllByArchivedFalseOrderByIdAsc().stream().map(this::toAlertResponse).toList());
    }

    @GetMapping(path = "/question/{questionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> listForQuestion(@PathVariable("questionId") long questionId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(alertRepository.findAllByArchivedFalseAndCardIdOrderByIdAsc(questionId).stream().map(this::toAlertResponse).toList());
    }

    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> get(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        AnalyticsAlert alert = alertRepository.findById(id).orElse(null);
        if (alert == null || alert.isArchived()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toAlertResponse(alert));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> create(@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }

        Long cardId = body != null && body.path("card_id").canConvertToLong() ? body.path("card_id").asLong() : null;
        if (cardId == null || cardId <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "card_id is required"));
        }
        AnalyticsCard card = cardRepository.findById(cardId).orElse(null);
        if (card == null || card.isArchived()) {
            return ResponseEntity.notFound().build();
        }

        AnalyticsAlert alert = new AnalyticsAlert();
        alert.setCardId(cardId);
        alert.setCreatorId(user.get().getId());
        alert.setArchived(body != null && body.has("archived") && body.path("archived").asBoolean(false));
        alert.setAlertJson(body == null ? "{}" : body.toString());
        alert = alertRepository.save(alert);

        AnalyticsAlertSubscription subscription = subscriptionRepository
                .findByAlertIdAndUserId(alert.getId(), user.get().getId())
                .orElse(null);
        if (subscription == null) {
            AnalyticsAlertSubscription s = new AnalyticsAlertSubscription();
            s.setAlertId(alert.getId());
            s.setUserId(user.get().getId());
            subscriptionRepository.save(s);
        }

        return ResponseEntity.ok(toAlertResponse(alert));
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> update(@PathVariable("id") long id, @RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        AnalyticsAlert alert = alertRepository.findById(id).orElse(null);
        if (alert == null || alert.isArchived()) {
            return ResponseEntity.notFound().build();
        }

        if (body != null && body.has("archived")) {
            alert.setArchived(body.path("archived").asBoolean(false));
        }
        if (body != null && body.has("card_id") && body.path("card_id").canConvertToLong()) {
            long cardId = body.path("card_id").asLong();
            if (cardId <= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "card_id must be a positive integer"));
            }
            if (!cardRepository.existsById(cardId)) {
                return ResponseEntity.notFound().build();
            }
            alert.setCardId(cardId);
        }
        if (body != null) {
            alert.setAlertJson(body.toString());
        }
        alertRepository.save(alert);
        return ResponseEntity.ok(toAlertResponse(alert));
    }

    @DeleteMapping(path = "/{id}/subscription")
    public ResponseEntity<?> unsubscribe(@PathVariable("id") long alertId, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        subscriptionRepository.deleteByAlertIdAndUserId(alertId, user.get().getId());
        return ResponseEntity.noContent().build();
    }

    private ObjectNode toAlertResponse(AnalyticsAlert alert) {
        ObjectNode node = objectMapper.createObjectNode();
        if (alert.getAlertJson() != null && !alert.getAlertJson().isBlank()) {
            try {
                JsonNode parsed = objectMapper.readTree(alert.getAlertJson());
                if (parsed != null && parsed.isObject()) {
                    node = (ObjectNode) parsed;
                }
            } catch (Exception ignored) {
            }
        }

        node.put("id", alert.getId());
        node.put("card_id", alert.getCardId());
        node.put("creator_id", alert.getCreatorId());
        node.put("archived", alert.isArchived());
        node.putPOJO("created_at", alert.getCreatedAt());
        node.putPOJO("updated_at", alert.getUpdatedAt());

        if (!node.has("channels") || !node.get("channels").isArray()) {
            node.putArray("channels");
        }
        if (!node.has("alert_condition")) {
            node.putNull("alert_condition");
        }
        if (!node.has("schedule_type")) {
            node.put("schedule_type", "hourly");
        }
        if (!node.has("parameters") || !node.get("parameters").isArray()) {
            node.putArray("parameters");
        }

        return node;
    }
}

