package com.yuzhi.dts.admin.service.audit;

import com.yuzhi.dts.admin.service.auditv2.AuditOperationType;
import com.yuzhi.dts.admin.service.auditv2.AuditResultStatus;
import com.yuzhi.dts.admin.service.auditv2.ButtonCodes;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.util.StringUtils;

/**
 * 角色及角色授权相关的审计上下文。
 */
public final class RoleAuditContext {

    private final String actor;
    private final Operation operation;
    private final AuditResultStatus result;
    private final Long roleId;
    private final String roleName;
    private final Long assignmentId;
    private final Long changeRequestId;
    private final String changeRequestRef;
    private final String summary;
    private final String operationName;
    private final Map<String, Object> detail;
    private final String clientIp;
    private final String clientAgent;
    private final String requestUri;
    private final String httpMethod;
    private final boolean allowEmptyTargets;

    private RoleAuditContext(Builder builder) {
        this.actor = builder.actor;
        this.operation = builder.operation;
        this.result = builder.result;
        this.roleId = builder.roleId;
        this.roleName = builder.roleName;
        this.assignmentId = builder.assignmentId;
        this.changeRequestId = builder.changeRequestId;
        this.changeRequestRef = builder.changeRequestRef;
        this.summary = builder.summary;
        this.operationName = builder.operationName;
        this.detail = Map.copyOf(builder.detail);
        this.clientIp = builder.clientIp;
        this.clientAgent = builder.clientAgent;
        this.requestUri = builder.requestUri;
        this.httpMethod = builder.httpMethod;
        this.allowEmptyTargets = builder.allowEmptyTargets;
    }

    public String actor() {
        return actor;
    }

    public Operation operation() {
        return operation;
    }

    public AuditResultStatus result() {
        return result;
    }

    public Optional<Long> roleId() {
        return Optional.ofNullable(roleId);
    }

    public Optional<String> roleName() {
        return Optional.ofNullable(roleName);
    }

    public Optional<Long> assignmentId() {
        return Optional.ofNullable(assignmentId);
    }

    public Optional<Long> changeRequestId() {
        return Optional.ofNullable(changeRequestId);
    }

    public Optional<String> changeRequestRef() {
        return Optional.ofNullable(changeRequestRef);
    }

    public Optional<String> summary() {
        return Optional.ofNullable(summary);
    }

    public Optional<String> operationName() {
        return Optional.ofNullable(operationName);
    }

    public Map<String, Object> detail() {
        return detail;
    }

    public Optional<String> clientIp() {
        return Optional.ofNullable(clientIp);
    }

    public Optional<String> clientAgent() {
        return Optional.ofNullable(clientAgent);
    }

    public Optional<String> requestUri() {
        return Optional.ofNullable(requestUri);
    }

    public Optional<String> httpMethod() {
        return Optional.ofNullable(httpMethod);
    }

    public boolean allowEmptyTargets() {
        return allowEmptyTargets;
    }

    public static Builder builder(String actor, Operation operation) {
        return new Builder(actor, operation);
    }

    public enum Operation {
        ASSIGN_ROLE(ButtonCodes.ROLE_ASSIGNMENT_CREATE, AuditOperationType.GRANT),
        REVOKE_ROLE(ButtonCodes.ROLE_ASSIGNMENT_CREATE, AuditOperationType.REVOKE),
        ROLE_CREATE(ButtonCodes.ROLE_CREATE, AuditOperationType.CREATE),
        ROLE_UPDATE(ButtonCodes.ROLE_UPDATE, AuditOperationType.UPDATE),
        ROLE_DELETE(ButtonCodes.ROLE_DELETE, AuditOperationType.DELETE),
        ROLE_ENABLE(ButtonCodes.ROLE_UPDATE, AuditOperationType.ENABLE),
        ROLE_DISABLE(ButtonCodes.ROLE_UPDATE, AuditOperationType.DISABLE),
        ROLE_LIST(ButtonCodes.ROLE_LIST, AuditOperationType.READ);

        private final String buttonCode;
        private final AuditOperationType operationType;

        Operation(String buttonCode, AuditOperationType operationType) {
            this.buttonCode = buttonCode;
            this.operationType = operationType;
        }

        public String buttonCode() {
            return buttonCode;
        }

        public AuditOperationType operationType() {
            return operationType;
        }
    }

    public static final class Builder {
        private final String actor;
        private final Operation operation;
        private AuditResultStatus result = AuditResultStatus.SUCCESS;
        private Long roleId;
        private String roleName;
        private Long assignmentId;
        private Long changeRequestId;
        private String changeRequestRef;
        private String summary;
        private String operationName;
        private final Map<String, Object> detail = new LinkedHashMap<>();
        private String clientIp;
        private String clientAgent;
        private String requestUri;
        private String httpMethod;
        private boolean allowEmptyTargets;

        private Builder(String actor, Operation operation) {
            if (!StringUtils.hasText(actor)) {
                throw new IllegalArgumentException("actor must not be blank");
            }
            this.actor = actor.trim();
            this.operation = Objects.requireNonNull(operation, "operation");
        }

        public Builder result(AuditResultStatus result) {
            if (result != null) {
                this.result = result;
            }
            return this;
        }

        public Builder role(Long id, String name) {
            this.roleId = id;
            this.roleName = name;
            return this;
        }

        public Builder assignmentId(Long assignmentId) {
            this.assignmentId = assignmentId;
            return this;
        }

        public Builder changeRequest(Long id, String ref) {
            this.changeRequestId = id;
            this.changeRequestRef = ref;
            return this;
        }

        public Builder summary(String summary) {
            this.summary = summary;
            return this;
        }

        public Builder operationName(String operationName) {
            this.operationName = operationName;
            return this;
        }

        public Builder detail(String key, Object value) {
            if (StringUtils.hasText(key) && value != null) {
                detail.put(key, value);
            }
            return this;
        }

        public Builder detail(Map<String, Object> payload) {
            if (payload != null) {
                payload.forEach((key, value) -> {
                    if (StringUtils.hasText(key) && value != null) {
                        detail.put(key, value);
                    }
                });
            }
            return this;
        }

        public Builder client(String clientIp, String clientAgent, String requestUri, String httpMethod) {
            this.clientIp = clientIp;
            this.clientAgent = clientAgent;
            this.requestUri = requestUri;
            this.httpMethod = httpMethod;
            return this;
        }

        public Builder allowEmptyTargets(boolean allowEmptyTargets) {
            this.allowEmptyTargets = allowEmptyTargets;
            return this;
        }

        public RoleAuditContext build() {
            return new RoleAuditContext(this);
        }
    }
}
