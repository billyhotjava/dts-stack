package com.yuzhi.dts.admin.service.audit;

import com.yuzhi.dts.admin.service.auditv2.AuditOperationType;
import com.yuzhi.dts.admin.service.auditv2.AuditResultStatus;
import com.yuzhi.dts.admin.service.auditv2.ButtonCodes;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.util.StringUtils;

/**
 * 菜单相关操作的审计上下文。该类限定一条日志只描述一个菜单主体，并允许附带审批单引用。
 */
public final class MenuAuditContext {

    private final String actor;
    private final Operation operation;
    private final AuditResultStatus result;
    private final Long menuId;
    private final String menuName;
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

    private MenuAuditContext(Builder builder) {
        this.actor = builder.actor;
        this.operation = builder.operation;
        this.result = builder.result;
        this.menuId = builder.menuId;
        this.menuName = builder.menuName;
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

    public Optional<Long> menuId() {
        return Optional.ofNullable(menuId);
    }

    public Optional<String> menuName() {
        return Optional.ofNullable(menuName);
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
        CREATE(ButtonCodes.PORTAL_MENU_CREATE, AuditOperationType.CREATE),
        UPDATE(ButtonCodes.PORTAL_MENU_UPDATE, AuditOperationType.UPDATE),
        ENABLE(ButtonCodes.PORTAL_MENU_UPDATE, AuditOperationType.UPDATE),
        DISABLE(ButtonCodes.PORTAL_MENU_UPDATE, AuditOperationType.UPDATE),
        DELETE(ButtonCodes.PORTAL_MENU_DELETE, AuditOperationType.DELETE),
        RESTORE(ButtonCodes.PORTAL_MENU_UPDATE, AuditOperationType.UPDATE),
        SUBMIT_APPROVAL(ButtonCodes.PORTAL_MENU_UPDATE, AuditOperationType.CREATE),
        APPROVAL_DECISION(ButtonCodes.APPROVAL_APPROVE, AuditOperationType.APPROVE),
        VISIBILITY(ButtonCodes.PORTAL_MENU_UPDATE, AuditOperationType.UPDATE),
        LIST(ButtonCodes.PORTAL_MENU_VIEW, AuditOperationType.READ);

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
        private Long menuId;
        private String menuName;
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

        public Builder menu(Long id, String name) {
            this.menuId = id;
            this.menuName = name;
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

        public Builder client(HttpServletRequest request, String fallbackUri, String fallbackMethod) {
            if (request != null) {
                this.clientIp = request.getHeader("X-Forwarded-For");
                this.clientAgent = request.getHeader("User-Agent");
                this.requestUri = request.getRequestURI();
                this.httpMethod = request.getMethod();
            } else {
                this.requestUri = fallbackUri;
                this.httpMethod = fallbackMethod;
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

        public Builder allowEmptyTargets(boolean allow) {
            this.allowEmptyTargets = allow;
            return this;
        }

        public MenuAuditContext build() {
            return new MenuAuditContext(this);
        }
    }
}
