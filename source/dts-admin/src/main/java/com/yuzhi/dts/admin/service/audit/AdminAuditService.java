package com.yuzhi.dts.admin.service.audit;

import com.yuzhi.dts.admin.security.SecurityUtils;
import com.yuzhi.dts.admin.service.auditv2.AdminAuditOperation;
import com.yuzhi.dts.admin.service.auditv2.AuditActionRequest;
import com.yuzhi.dts.admin.service.auditv2.AuditOperationKind;
import com.yuzhi.dts.admin.service.auditv2.AuditOperationType;
import com.yuzhi.dts.admin.service.auditv2.AuditResultStatus;
import com.yuzhi.dts.admin.service.auditv2.AuditV2Service;
import com.yuzhi.dts.common.audit.ChangeSnapshot;
import com.yuzhi.dts.common.net.IpAddressUtils;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 管理端审计落库统一入口，确保菜单和角色相关操作只生成一条主语清晰的日志。
 */
@Service
public class AdminAuditService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuditService.class);

    private final AuditV2Service auditV2Service;

    public AdminAuditService(AuditV2Service auditV2Service) {
        this.auditV2Service = auditV2Service;
    }

    public void logMenuAction(MenuAuditContext context) {
        if (context == null || !StringUtils.hasText(context.actor())) {
            return;
        }
        try {
            AuditOperationType opType = context.operationTypeOverride().orElse(context.operation().operationType());
            String summary = determineSummary(context.summary(), context.operationName(), context.operation());
            Map<String, Object> detail = enrichDetail(context.detail(), summary, opType);

            AuditActionRequest.Builder builder = AuditActionRequest
                .builder(context.actor(), context.operation().buttonCode())
                .actorRoles(SecurityUtils.getCurrentUserAuthorities())
                .summary(summary)
                .result(Optional.ofNullable(context.result()).orElse(AuditResultStatus.SUCCESS));

            applyClient(builder, context.clientIp().orElse(null), context.clientAgent().orElse(null), context.requestUri().orElse(null), context.httpMethod().orElse(null));
            applyOperationOverride(builder, resolveMenuOperation(context.operation()), context.operationName().orElse(summary), opType);
            ChangeSnapshot snapshot = resolveChangeSnapshot(detail);
            if (snapshot != null) {
                builder.changeSnapshot(snapshot, "PORTAL_MENU");
            }
            boolean hasTarget = applyTargetsForMenu(builder, context);
            if (!hasTarget && context.allowEmptyTargets()) {
                builder.allowEmptyTargets();
            }
            if (!detail.isEmpty()) {
                builder.detail("detail", detail);
            }
            auditV2Service.record(builder.build());
        } catch (Exception ex) {
            log.warn("Failed to record menu audit log: {}", ex.getMessage());
            log.debug("Menu audit context failure", ex);
        }
    }

    public void logRoleAction(RoleAuditContext context) {
        if (context == null || !StringUtils.hasText(context.actor())) {
            return;
        }
        try {
            String summary = determineSummary(context.summary(), context.operationName(), context.operation());
            Map<String, Object> detail = enrichDetail(context.detail(), summary, context.operation().operationType());

            AuditActionRequest.Builder builder = AuditActionRequest
                .builder(context.actor(), context.operation().buttonCode())
                .actorRoles(SecurityUtils.getCurrentUserAuthorities())
                .summary(summary)
                .result(Optional.ofNullable(context.result()).orElse(AuditResultStatus.SUCCESS));

            applyClient(builder, context.clientIp().orElse(null), context.clientAgent().orElse(null), context.requestUri().orElse(null), context.httpMethod().orElse(null));
            applyOperationOverride(builder, resolveRoleOperation(context.operation()), context.operationName().orElse(summary), context.operation().operationType());
            ChangeSnapshot snapshot = resolveChangeSnapshot(detail);
            if (snapshot != null) {
                builder.changeSnapshot(snapshot, resolveRoleResourceType(context));
            }
            boolean hasTarget = applyTargetsForRole(builder, context);
            if (!hasTarget && context.allowEmptyTargets()) {
                builder.allowEmptyTargets();
            }
            if (!detail.isEmpty()) {
                builder.detail("detail", detail);
            }
            auditV2Service.record(builder.build());
        } catch (Exception ex) {
            log.warn("Failed to record role audit log: {}", ex.getMessage());
            log.debug("Role audit context failure", ex);
        }
    }

    private Map<String, Object> enrichDetail(Map<String, Object> origin, String summary, AuditOperationType type) {
        Map<String, Object> detail = new LinkedHashMap<>();
        if (origin != null) {
            detail.putAll(origin);
        }
        if (StringUtils.hasText(summary) && !detail.containsKey("summary")) {
            detail.put("summary", summary);
        }
        if (type != null && !detail.containsKey("operationType")) {
            detail.put("operationType", type.getCode());
            detail.put("operationTypeText", type.getDisplayName());
        }
        return detail;
    }

    private boolean applyTargetsForMenu(AuditActionRequest.Builder builder, MenuAuditContext context) {
        boolean hasTarget = false;
        if (context.menuId().isPresent()) {
            builder.target("portal_menu", context.menuId().get(), context.menuName().orElseGet(() -> String.valueOf(context.menuId().get())));
            hasTarget = true;
        }
        if (context.changeRequestId().isPresent()) {
            String ref = context.changeRequestRef().orElseGet(() -> "CR-" + context.changeRequestId().get());
            builder.changeRequestRef(ref);
            if (!hasTarget) {
                builder.target("change_request", context.changeRequestId().get(), ref);
                hasTarget = true;
            }
        }
        return hasTarget;
    }

    private boolean applyTargetsForRole(AuditActionRequest.Builder builder, RoleAuditContext context) {
        boolean hasTarget = false;
        if (context.roleId().isPresent()) {
            builder.target("admin_custom_role", context.roleId().get(), context.roleName().orElse(String.valueOf(context.roleId().get())));
            hasTarget = true;
        } else if (context.assignmentId().isPresent()) {
            builder.target("admin_role_assignment", context.assignmentId().get(), String.valueOf(context.assignmentId().get()));
            hasTarget = true;
        }
        if (context.changeRequestId().isPresent()) {
            String ref = context.changeRequestRef().orElseGet(() -> "CR-" + context.changeRequestId().get());
            builder.changeRequestRef(ref);
            if (!hasTarget) {
                builder.target("change_request", context.changeRequestId().get(), ref);
                hasTarget = true;
            }
        }
        return hasTarget;
    }

    private void applyOperationOverride(AuditActionRequest.Builder builder, Optional<AdminAuditOperation> operation, String operationName, AuditOperationType type) {
        if (operation.isPresent()) {
            AdminAuditOperation op = operation.get();
            AuditOperationKind kind = mapOperationKind(Optional.ofNullable(type).orElse(op.type()));
            builder.operationOverride(op.code(), StringUtils.hasText(operationName) ? operationName : op.defaultName(), kind);
            builder.moduleOverride(op.moduleKey(), op.moduleLabel());
        } else if (type != null) {
            AuditOperationKind kind = mapOperationKind(type);
            if (kind != null && StringUtils.hasText(operationName)) {
                builder.operationOverride(type.getCode(), operationName, kind);
            }
        }
    }

    private Optional<AdminAuditOperation> resolveMenuOperation(MenuAuditContext.Operation operation) {
        return switch (operation) {
            case CREATE -> Optional.of(AdminAuditOperation.ADMIN_MENU_CREATE);
            case UPDATE -> Optional.of(AdminAuditOperation.ADMIN_MENU_UPDATE);
            case ENABLE -> Optional.of(AdminAuditOperation.ADMIN_MENU_ENABLE);
            case DISABLE -> Optional.of(AdminAuditOperation.ADMIN_MENU_DISABLE);
            case DELETE -> Optional.of(AdminAuditOperation.ADMIN_MENU_DISABLE);
            case RESTORE -> Optional.of(AdminAuditOperation.ADMIN_MENU_ENABLE);
            case SUBMIT_APPROVAL, APPROVAL_DECISION, VISIBILITY, LIST -> Optional.of(AdminAuditOperation.ADMIN_MENU_UPDATE);
        };
    }

    private Optional<AdminAuditOperation> resolveRoleOperation(RoleAuditContext.Operation operation) {
        return switch (operation) {
            case ASSIGN_ROLE -> Optional.of(AdminAuditOperation.ADMIN_ROLE_ASSIGNMENT_CREATE);
            case REVOKE_ROLE -> Optional.of(AdminAuditOperation.ADMIN_ROLE_ASSIGNMENT_CREATE);
            case ROLE_CREATE -> Optional.of(AdminAuditOperation.ADMIN_ROLE_CREATE);
            case ROLE_UPDATE -> Optional.of(AdminAuditOperation.ADMIN_ROLE_UPDATE);
            case ROLE_DELETE -> Optional.of(AdminAuditOperation.ADMIN_ROLE_DELETE);
            case ROLE_ENABLE -> Optional.of(AdminAuditOperation.ADMIN_ROLE_UPDATE);
            case ROLE_DISABLE -> Optional.of(AdminAuditOperation.ADMIN_ROLE_UPDATE);
            case ROLE_LIST -> Optional.of(AdminAuditOperation.ADMIN_ROLE_VIEW);
        };
    }

    private void applyClient(AuditActionRequest.Builder builder, String clientIp, String agent, String uri, String method) {
        String resolvedIp = IpAddressUtils.resolveClientIp(clientIp);
        builder.client(resolvedIp, agent);
        builder.request(StringUtils.hasText(uri) ? uri : "/", StringUtils.hasText(method) ? method : "GET");
    }

    private String determineSummary(Optional<String> explicitSummary, Optional<String> operationName, Enum<?> operation) {
        if (explicitSummary.isPresent() && StringUtils.hasText(explicitSummary.get())) {
            return explicitSummary.get();
        }
        if (operationName.isPresent() && StringUtils.hasText(operationName.get())) {
            return operationName.get();
        }
        return operation != null ? operation.name() : "操作";
    }

    private AuditOperationKind mapOperationKind(AuditOperationType type) {
        if (type == null || type == AuditOperationType.UNKNOWN) {
            return null;
        }
        return switch (type) {
            case CREATE -> AuditOperationKind.CREATE;
            case UPDATE -> AuditOperationKind.UPDATE;
            case DELETE -> AuditOperationKind.DELETE;
            case ENABLE -> AuditOperationKind.ENABLE;
            case DISABLE -> AuditOperationKind.DISABLE;
            case GRANT -> AuditOperationKind.GRANT;
            case REVOKE -> AuditOperationKind.REVOKE;
            case EXECUTE -> AuditOperationKind.EXECUTE;
            case APPROVE -> AuditOperationKind.APPROVE;
            case REJECT -> AuditOperationKind.REJECT;
            case READ, LIST -> AuditOperationKind.QUERY;
            case REFRESH -> AuditOperationKind.REFRESH;
            case UPLOAD -> AuditOperationKind.UPLOAD;
            case TEST -> AuditOperationKind.TEST;
            case CLEAN -> AuditOperationKind.CLEAN;
            case EXPORT -> AuditOperationKind.EXPORT;
            case LOGIN -> AuditOperationKind.LOGIN;
            case LOGOUT -> AuditOperationKind.LOGOUT;
            case PUBLISH -> AuditOperationKind.UPDATE;
            case REQUEST -> AuditOperationKind.OTHER;
            default -> null;
        };
    }

    private ChangeSnapshot resolveChangeSnapshot(Map<String, Object> detail) {
        if (detail == null || detail.isEmpty()) {
            return null;
        }
        ChangeSnapshot snapshot = extractSnapshot(detail.get("changeSnapshot"));
        Map<String, Object> before = toMap(detail.get("before"));
        if (before.isEmpty()) {
            before = toMap(detail.get("originalValue"));
        }
        Map<String, Object> after = toMap(detail.get("after"));
        if (after.isEmpty()) {
            after = firstNonEmpty(
                () -> toMap(detail.get("afterValue")),
                () -> toMap(detail.get("updatedValue")),
                () -> toMap(detail.get("payload")),
                () -> toMap(detail.get("request"))
            );
        }
        if (snapshot == null) {
            if (before.isEmpty() && after.isEmpty()) {
                return null;
            }
            snapshot = ChangeSnapshot.of(before, after);
        } else {
            Map<String, Object> mergedBefore = before.isEmpty() ? snapshot.getBefore() : before;
            Map<String, Object> mergedAfter = after.isEmpty() ? snapshot.getAfter() : after;
            snapshot = ChangeSnapshot.of(mergedBefore, mergedAfter);
        }
        return snapshotHasContent(snapshot) ? snapshot : null;
    }

    private String resolveRoleResourceType(RoleAuditContext context) {
        if (context == null || context.operation() == null) {
            return null;
        }
        return switch (context.operation()) {
            case ASSIGN_ROLE, REVOKE_ROLE -> "ROLE_ASSIGNMENT";
            case ROLE_CREATE, ROLE_UPDATE, ROLE_DELETE, ROLE_ENABLE, ROLE_DISABLE -> "ROLE";
            case ROLE_LIST -> null;
        };
    }

    private ChangeSnapshot extractSnapshot(Object candidate) {
        if (candidate == null) {
            return null;
        }
        if (candidate instanceof ChangeSnapshot snapshot) {
            return snapshot;
        }
        if (candidate instanceof Map<?, ?> map) {
            return ChangeSnapshot.fromMap(map);
        }
        return null;
    }

    private Map<String, Object> toMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((k, v) -> {
                if (k != null) {
                    copy.put(String.valueOf(k), v);
                }
            });
            return copy;
        }
        if (value instanceof ChangeSnapshot snapshot) {
            return snapshot.toMap();
        }
        return new LinkedHashMap<>();
    }

    @SafeVarargs
    private Map<String, Object> firstNonEmpty(Supplier<Map<String, Object>>... suppliers) {
        if (suppliers == null) {
            return new LinkedHashMap<>();
        }
        for (Supplier<Map<String, Object>> supplier : suppliers) {
            if (supplier == null) {
                continue;
            }
            Map<String, Object> candidate = supplier.get();
            if (candidate != null && !candidate.isEmpty()) {
                return candidate;
            }
        }
        return new LinkedHashMap<>();
    }

    private boolean snapshotHasContent(ChangeSnapshot snapshot) {
        return snapshot != null && (snapshot.hasChanges() || !snapshot.getBefore().isEmpty() || !snapshot.getAfter().isEmpty());
    }
}
