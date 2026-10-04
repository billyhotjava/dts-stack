package com.yuzhi.dts.copilot.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.service.ExplainabilityService;
import com.yuzhi.dts.copilot.analytics.web.support.MetabaseAuth;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/explain")
public class ExplainabilityResource {

    private final AnalyticsSessionService sessionService;
    private final ExplainabilityService explainabilityService;

    public ExplainabilityResource(
            AnalyticsSessionService sessionService,
            ExplainabilityService explainabilityService) {
        this.sessionService = sessionService;
        this.explainabilityService = explainabilityService;
    }

    @PostMapping(path = "/card/{cardId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> explainCard(
            @PathVariable("cardId") long cardId,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }

        Long metricId = null;
        if (body != null && body.path("metricId").canConvertToLong()) {
            long value = body.path("metricId").asLong();
            metricId = value > 0 ? value : null;
        }
        String metricVersion = body == null ? null : body.path("metricVersion").asText(null);
        String componentId = body == null ? null : body.path("componentId").asText(null);
        JsonNode filterContext = body == null ? null : body.path("filterContext");
        return ResponseEntity.ok(explainabilityService.explainCard(cardId, metricId, metricVersion, filterContext, componentId));
    }
}
