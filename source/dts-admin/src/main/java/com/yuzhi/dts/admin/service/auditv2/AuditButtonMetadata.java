package com.yuzhi.dts.admin.service.auditv2;

public record AuditButtonMetadata(
    String buttonCode,
    String moduleKey,
    String moduleName,
    String operationCode,
    String operationName,
    AuditOperationKind operationKind,
    boolean allowEmptyTargets
) {
    public AuditButtonMetadata {
        if (buttonCode == null || buttonCode.isBlank()) {
            throw new IllegalArgumentException("buttonCode must not be blank");
        }
        if (moduleKey == null || moduleKey.isBlank()) {
            throw new IllegalArgumentException("moduleKey must not be blank");
        }
        if (operationKind == null) {
            throw new IllegalArgumentException("operationKind must not be null");
        }
    }
}
