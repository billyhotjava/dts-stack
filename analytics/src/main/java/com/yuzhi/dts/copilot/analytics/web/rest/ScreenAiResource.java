package com.yuzhi.dts.copilot.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.service.ScreenAiGenerationService;
import com.yuzhi.dts.copilot.analytics.web.support.MetabaseAuth;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/screens/ai")
@Transactional(readOnly = true)
public class ScreenAiResource {

    private final AnalyticsSessionService sessionService;
    private final ScreenAiGenerationService screenAiGenerationService;

    public ScreenAiResource(
            AnalyticsSessionService sessionService,
            ScreenAiGenerationService screenAiGenerationService) {
        this.sessionService = sessionService;
        this.screenAiGenerationService = screenAiGenerationService;
    }

    /**
     * Legacy-compatible endpoint. Prefer /api/screens/ai/generate.
     */
    @PostMapping(path = "/draft", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> draft(@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthorized");
        }

        String prompt = body == null ? null : trimToNull(body.path("prompt").asText(null));
        if (prompt == null) {
            return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body("prompt is required");
        }

        Integer width = null;
        if (body != null && body.has("width") && body.path("width").canConvertToInt()) {
            width = body.path("width").asInt();
        }

        Integer height = null;
        if (body != null && body.has("height") && body.path("height").canConvertToInt()) {
            height = body.path("height").asInt();
        }

        ObjectNode generated;
        try {
            generated = screenAiGenerationService.generate(prompt, width, height);
        } catch (RuntimeException ex) {
            return ResponseEntity.status(503).contentType(MediaType.TEXT_PLAIN).body(ex.getMessage());
        }
        ObjectNode response = generated.deepCopy();

        JsonNode screenSpec = generated.path("screenSpec");
        if (screenSpec.isObject()) {
            response.setAll((ObjectNode) screenSpec);
        }

        response.put("requestPrompt", prompt);
        response.put("deprecated", true);
        response.put("deprecatedEndpoint", "/api/screens/ai/draft");
        response.put("recommendedEndpoint", "/api/screens/ai/generate");
        response.putPOJO("generatedBy", user.get().getId());
        return ResponseEntity.ok(response);
    }

    private String trimToNull(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
