package com.yuzhi.dts.copilot.analytics.web.rest;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.service.CopilotAgentChatClient;
import com.yuzhi.dts.copilot.analytics.service.CopilotChatDataSourceResolver;
import com.yuzhi.dts.copilot.analytics.service.elt.EltWatermarkService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/copilot/chat")
public class CopilotChatResource {

    private static final List<String> MART_TABLES = List.of(
            "mart_project_fulfillment_daily",
            "fact_field_operation_event");

    private final AnalyticsSessionService sessionService;
    private final CopilotAgentChatClient copilotAgentChatClient;
    private final CopilotChatDataSourceResolver chatDataSourceResolver;
    private final ObjectProvider<EltWatermarkService> eltWatermarkServiceProvider;

    public CopilotChatResource(
            AnalyticsSessionService sessionService,
            CopilotAgentChatClient copilotAgentChatClient,
            CopilotChatDataSourceResolver chatDataSourceResolver,
            ObjectProvider<EltWatermarkService> eltWatermarkServiceProvider) {
        this.sessionService = sessionService;
        this.copilotAgentChatClient = copilotAgentChatClient;
        this.chatDataSourceResolver = chatDataSourceResolver;
        this.eltWatermarkServiceProvider = eltWatermarkServiceProvider;
    }

    @PostMapping(path = "/send", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> sendMessage(@RequestBody ChatSendRequest body, HttpServletRequest request) {
        AnalyticsUser user = resolveUser(request);
        if (user == null) {
            return unauthenticated();
        }
        final Long datasourceId;
        try {
            datasourceId = chatDataSourceResolver.resolveSelectedDatasourceId(
                    body == null ? null : body.datasourceId());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
        return proxy(() -> copilotAgentChatClient.sendMessage(
                resolveCopilotUserId(user),
                body == null ? null : body.sessionId(),
                body == null ? null : body.userMessage(),
                datasourceId,
                buildMartHealthSnapshot(),
                body == null ? null : body.assumptionOverrides(),
                body == null ? null : body.clarificationAnswers()));
    }

    @PostMapping(path = "/send-stream", consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public StreamingResponseBody sendMessageStream(@RequestBody ChatSendRequest body, HttpServletRequest request) {
        AnalyticsUser user = resolveUser(request);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated");
        }
        final Long datasourceId;
        try {
            datasourceId = chatDataSourceResolver.resolveSelectedDatasourceId(
                    body == null ? null : body.datasourceId());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }

        return outputStream -> {
            try {
                copilotAgentChatClient.sendMessageStream(
                        resolveCopilotUserId(user),
                        body == null ? null : body.sessionId(),
                        body == null ? null : body.userMessage(),
                        datasourceId,
                        buildMartHealthSnapshot(),
                        body == null ? null : body.assumptionOverrides(),
                        body == null ? null : body.clarificationAnswers(),
                        outputStream);
            } catch (Exception ex) {
                if (AgentChatInterrupts.isInterrupted(ex)) {
                    AgentChatInterrupts.restoreInterruptFlag(ex);
                    return;
                }
                String errMsg = ex.getMessage() != null ? ex.getMessage() : "unknown";
                String errJson = new com.fasterxml.jackson.databind.ObjectMapper()
                        .createObjectNode().put("error", errMsg).toString();
                outputStream.write(("event: error\ndata: " + errJson + "\n\n")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                outputStream.flush();
            }
        };
    }

    @GetMapping(path = "/sessions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> listSessions(
            @RequestParam(required = false, defaultValue = "50") int limit,
            HttpServletRequest request) {
        AnalyticsUser user = resolveUser(request);
        if (user == null) {
            return unauthenticated();
        }
        int safeLimit = Math.max(limit, 0);
        return proxy(() -> copilotAgentChatClient.listSessions(resolveCopilotUserId(user), safeLimit));
    }

    @GetMapping(path = "/{sessionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getSession(@PathVariable String sessionId, HttpServletRequest request) {
        AnalyticsUser user = resolveUser(request);
        if (user == null) {
            return unauthenticated();
        }
        return proxy(() -> copilotAgentChatClient.getSession(resolveCopilotUserId(user), sessionId));
    }

    @GetMapping(path = "/route-telemetry", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getRouteTelemetry(
            @RequestParam(required = false, defaultValue = "7") int days,
            @RequestParam(required = false, defaultValue = "10") int limit,
            HttpServletRequest request) {
        AnalyticsUser user = resolveUser(request);
        if (user == null) {
            return unauthenticated();
        }
        return proxy(() -> copilotAgentChatClient.getRouteTelemetry(days, limit));
    }

    @DeleteMapping(path = "/{sessionId}")
    public ResponseEntity<?> deleteSession(@PathVariable String sessionId, HttpServletRequest request) {
        AnalyticsUser user = resolveUser(request);
        if (user == null) {
            return unauthenticated();
        }
        try {
            copilotAgentChatClient.deleteSession(resolveCopilotUserId(user), sessionId);
            return ResponseEntity.noContent().build();
        } catch (RestClientException ex) {
            return proxyFailure(ex);
        }
    }

    private AnalyticsUser resolveUser(HttpServletRequest request) {
        return sessionService.resolveUser(request).orElse(null);
    }

    private String resolveCopilotUserId(AnalyticsUser user) {
        if (StringUtils.hasText(user.getUsername())) {
            return user.getUsername().trim();
        }
        String fullName = ((user.getFirstName() == null ? "" : user.getFirstName()) + " " + (user.getLastName() == null ? "" : user.getLastName())).trim();
        if (StringUtils.hasText(fullName)) {
            return fullName;
        }
        return String.valueOf(user.getId());
    }

    private ResponseEntity<?> proxy(ResponseSupplier supplier) {
        try {
            return ResponseEntity.ok(supplier.get());
        } catch (RestClientException ex) {
            return proxyFailure(ex);
        }
    }

    private ResponseEntity<Map<String, Object>> proxyFailure(RestClientException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Copilot chat service unavailable");
        body.put("detail", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
    }

    private ResponseEntity<String> unauthenticated() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.TEXT_PLAIN)
                .body("Unauthenticated");
    }

    private Map<String, Boolean> buildMartHealthSnapshot() {
        EltWatermarkService watermarkService = eltWatermarkServiceProvider.getIfAvailable();
        if (watermarkService == null) {
            return Map.of();
        }
        Map<String, Boolean> snapshot = new LinkedHashMap<>();
        for (String table : MART_TABLES) {
            snapshot.put(table, watermarkService.isHealthy(table));
        }
        return snapshot;
    }

    public record ChatSendRequest(
            String sessionId,
            String userMessage,
            String datasourceId,
            Map<String, String> assumptionOverrides,
            Map<String, String> clarificationAnswers) {}

    @FunctionalInterface
    private interface ResponseSupplier {
        Object get();
    }

    private static final class AgentChatInterrupts {
        private static boolean isInterrupted(Throwable throwable) {
            Throwable current = throwable;
            while (current != null) {
                if (current instanceof InterruptedException) {
                    return true;
                }
                current = current.getCause();
            }
            return false;
        }

        private static void restoreInterruptFlag(Throwable throwable) {
            if (isInterrupted(throwable)) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
