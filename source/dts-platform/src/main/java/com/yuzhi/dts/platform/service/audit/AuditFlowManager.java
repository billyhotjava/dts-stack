package com.yuzhi.dts.platform.service.audit;

import com.yuzhi.dts.common.audit.AuditStage;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AuditFlowManager {

    private static final ThreadLocal<FlowContext> CONTEXT = new ThreadLocal<>();

    private final AuditService auditService;

    public AuditFlowManager(AuditService auditService) {
        this.auditService = auditService;
    }

    public FlowContext begin(String actionCode, String flowId) {
        String resolvedAction = StringUtils.hasText(actionCode) ? actionCode : "UNDEFINED";
        String resolvedFlow = StringUtils.hasText(flowId) ? flowId : UUID.randomUUID().toString();
        FlowContext context = new FlowContext(resolvedAction, resolvedFlow, Instant.now());
        CONTEXT.set(context);
        auditService.auditAction(resolvedAction, AuditStage.BEGIN, resolvedFlow, Map.of("flowId", resolvedFlow));
        return context;
    }

    public FlowContext begin(String actionCode) {
        return begin(actionCode, UUID.randomUUID().toString());
    }

    public FlowContext attach(String actionCode, String flowId) {
        String resolvedAction = StringUtils.hasText(actionCode) ? actionCode : "UNDEFINED";
        String resolvedFlow = StringUtils.hasText(flowId) ? flowId : UUID.randomUUID().toString();
        FlowContext context = new FlowContext(resolvedAction, resolvedFlow, Instant.now());
        CONTEXT.set(context);
        return context;
    }

    public Optional<FlowContext> current() {
        return Optional.ofNullable(CONTEXT.get());
    }

    public void appendSupportingCall(String method, String uri, Map<String, Object> extra) {
        FlowContext ctx = CONTEXT.get();
        if (ctx == null) {
            return;
        }
        ctx.addSupportingCall(method, uri, extra);
    }

    public void completeSuccess(Object summary) {
        FlowContext ctx = CONTEXT.get();
        if (ctx == null) {
            return;
        }
        try {
            auditService.auditAction(ctx.actionCode(), AuditStage.SUCCESS, ctx.flowId(), ctx.buildPayload(summary, null));
        } finally {
            CONTEXT.remove();
        }
    }

    public void completeFailure(Throwable error) {
        completeFailure(error, null);
    }

    public void completeFailure(Throwable error, Object summary) {
        FlowContext ctx = CONTEXT.get();
        if (ctx == null) {
            return;
        }
        try {
            Map<String, Object> payload = ctx.buildPayload(summary, error);
            auditService.auditAction(ctx.actionCode(), AuditStage.FAIL, ctx.flowId(), payload);
        } finally {
            CONTEXT.remove();
        }
    }

    public void clear() {
        CONTEXT.remove();
    }

    public static final class FlowContext {
        private final String actionCode;
        private final String flowId;
        private final Instant startedAt;
        private final List<Map<String, Object>> supportingCalls = new ArrayList<>();

        FlowContext(String actionCode, String flowId, Instant startedAt) {
            this.actionCode = actionCode;
            this.flowId = flowId;
            this.startedAt = startedAt;
        }

        public String actionCode() {
            return actionCode;
        }

        public String flowId() {
            return flowId;
        }

        public Instant startedAt() {
            return startedAt;
        }

        public List<Map<String, Object>> supportingCalls() {
            return Collections.unmodifiableList(supportingCalls);
        }

        void addSupportingCall(String method, String uri, Map<String, Object> extra) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("method", method);
            entry.put("uri", uri);
            if (extra != null && !extra.isEmpty()) {
                entry.put("extra", new HashMap<>(extra));
            }
            supportingCalls.add(entry);
        }

        Map<String, Object> buildPayload(Object summary, Throwable error) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("flowId", flowId);
            payload.put("startedAt", startedAt.toString());
            payload.put("durationMs", Duration.between(startedAt, Instant.now()).toMillis());
            if (!supportingCalls.isEmpty()) {
                payload.put("supportingCalls", supportingCalls);
            }
            if (summary != null) {
                payload.put("summary", summary);
            }
            if (error != null) {
                payload.put("error", Map.of("type", error.getClass().getSimpleName(), "message", error.getMessage()));
            }
            return payload;
        }
    }
}
