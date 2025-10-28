package com.yuzhi.dts.admin.service.auditv2;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.util.StringUtils;

public record AuditActionRequest(
    Instant occurredAt,
    String actorId,
    String actorName,
    List<String> actorRoles,
    String buttonCode,
    String moduleKeyOverride,
    String moduleNameOverride,
    String operationCodeOverride,
    String operationNameOverride,
    AuditOperationKind operationKindOverride,
    AuditResultStatus result,
    String summary,
    String changeRequestRef,
    String clientIp,
    String clientAgent,
    String requestUri,
    String httpMethod,
    Map<String, Object> metadata,
    Map<String, Object> attributes,
    List<AuditTarget> targets,
    List<AuditDetail> details,
    boolean allowEmptyTargets
) {
    public AuditActionRequest {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(buttonCode, "buttonCode");
        actorRoles = actorRoles == null ? List.of() : List.copyOf(actorRoles);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        targets = targets == null ? List.of() : List.copyOf(targets);
        details = details == null ? List.of() : List.copyOf(details);
    }

    public static Builder builder(String actorId, String buttonCode) {
        return new Builder(actorId, buttonCode);
    }

    public record AuditTarget(String table, Object id, String label) {}

    public record AuditDetail(String key, Object value) {}

    public static final class Builder {

        private Instant occurredAt;
        private final String actorId;
        private final String buttonCode;
        private String actorName;
        private final Set<String> actorRoles = new LinkedHashSet<>();
        private String moduleKeyOverride;
        private String moduleNameOverride;
        private String operationCodeOverride;
        private String operationNameOverride;
        private AuditOperationKind operationKindOverride;
        private AuditResultStatus result = AuditResultStatus.SUCCESS;
        private String summary;
        private String changeRequestRef;
        private String clientIp;
        private String clientAgent;
        private String requestUri;
        private String httpMethod;
        private final Map<String, Object> metadata = new LinkedHashMap<>();
        private final Map<String, Object> attributes = new LinkedHashMap<>();
        private final List<AuditTarget> targets = new ArrayList<>();
        private final List<AuditDetail> details = new ArrayList<>();
        private boolean allowEmptyTargets;

        private Builder(String actorId, String buttonCode) {
            if (!StringUtils.hasText(actorId)) {
                throw new IllegalArgumentException("actorId must not be blank");
            }
            if (!StringUtils.hasText(buttonCode)) {
                throw new IllegalArgumentException("buttonCode must not be blank");
            }
            this.actorId = actorId.trim();
            this.buttonCode = buttonCode.trim().toUpperCase();
        }

        public Builder occurredAt(Instant occurredAt) {
            this.occurredAt = occurredAt;
            return this;
        }

        public Builder actorName(String actorName) {
            this.actorName = actorName;
            return this;
        }

        public Builder actorRoles(Collection<String> roles) {
            if (roles != null) {
                roles.stream().filter(StringUtils::hasText).map(r -> r.trim().toUpperCase()).forEach(actorRoles::add);
            }
            return this;
        }

        public Builder moduleOverride(String moduleKey, String moduleName) {
            this.moduleKeyOverride = moduleKey;
            this.moduleNameOverride = moduleName;
            return this;
        }

        public Builder operationOverride(String code, String name, AuditOperationKind kind) {
            this.operationCodeOverride = code;
            this.operationNameOverride = name;
            this.operationKindOverride = kind;
            return this;
        }

        public Builder result(AuditResultStatus result) {
            if (result != null) {
                this.result = result;
            }
            return this;
        }

        public Builder summary(String summary) {
            this.summary = summary;
            return this;
        }

        public Builder changeRequestRef(String changeRequestRef) {
            this.changeRequestRef = changeRequestRef;
            return this;
        }

        public Builder client(String ip, String agent) {
            this.clientIp = ip;
            this.clientAgent = agent;
            return this;
        }

        public Builder request(String uri, String method) {
            this.requestUri = uri;
            this.httpMethod = method;
            return this;
        }

        public Builder metadata(String key, Object value) {
            if (StringUtils.hasText(key) && value != null) {
                metadata.put(key.trim(), value);
            }
            return this;
        }

        public Builder attribute(String key, Object value) {
            if (StringUtils.hasText(key) && value != null) {
                attributes.put(key.trim(), value);
            }
            return this;
        }

        public Builder target(String table, Object id, String label) {
            if (StringUtils.hasText(table) && id != null) {
                targets.add(new AuditTarget(table.trim(), id, label));
            }
            return this;
        }

        public Builder detail(String key, Object value) {
            if (StringUtils.hasText(key) && value != null) {
                details.add(new AuditDetail(key.trim(), value));
            }
            return this;
        }

        public Builder allowEmptyTargets() {
            this.allowEmptyTargets = true;
            return this;
        }

        public AuditActionRequest build() {
            return new AuditActionRequest(
                occurredAt,
                actorId,
                actorName,
                List.copyOf(actorRoles),
                buttonCode,
                moduleKeyOverride,
                moduleNameOverride,
                operationCodeOverride,
                operationNameOverride,
                operationKindOverride,
                result,
                summary,
                changeRequestRef,
                clientIp,
                clientAgent,
                requestUri,
                httpMethod,
                Map.copyOf(metadata),
                Map.copyOf(attributes),
                List.copyOf(targets),
                List.copyOf(details),
                allowEmptyTargets
            );
        }
    }
}
