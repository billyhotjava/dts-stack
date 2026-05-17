package com.yuzhi.dts.platform.service.permission;

import com.yuzhi.dts.platform.domain.permission.AssetPermissionAudit;
import com.yuzhi.dts.platform.repository.permission.AssetPermissionAuditRepository;
import com.yuzhi.dts.platform.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AssetPermissionAuditService {

    private final AssetPermissionAuditRepository auditRepository;

    public AssetPermissionAuditService(AssetPermissionAuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    public void recordGrant(String assetType, String assetId, String targetUser,
                            String permission, String oaReference) {
        recordGrant(assetType, assetId, targetUser, permission, oaReference, currentOperator());
    }

    public void recordGrant(String assetType, String assetId, String targetUser,
                            String permission, String oaReference, String operator) {
        AssetPermissionAudit audit = new AssetPermissionAudit();
        audit.setAction("GRANT");
        audit.setAssetType(assetType);
        audit.setAssetId(assetId);
        audit.setTargetUser(targetUser);
        audit.setPermission(permission);
        audit.setOperator(operator);
        audit.setOaReference(oaReference);
        auditRepository.save(audit);
    }

    public void recordRevoke(String assetType, String assetId, String targetUser,
                             String permission) {
        AssetPermissionAudit audit = new AssetPermissionAudit();
        audit.setAction("REVOKE");
        audit.setAssetType(assetType);
        audit.setAssetId(assetId);
        audit.setTargetUser(targetUser);
        audit.setPermission(permission);
        audit.setOperator(currentOperator());
        auditRepository.save(audit);
    }

    public void recordOwnershipChange(String assetType, String assetId,
                                       String oldDeptCode, String newDeptCode) {
        AssetPermissionAudit audit = new AssetPermissionAudit();
        audit.setAction("CHANGE_OWNERSHIP");
        audit.setAssetType(assetType);
        audit.setAssetId(assetId);
        audit.setOperator(currentOperator());
        audit.setDetail("{\"oldDept\":\"" + oldDeptCode + "\",\"newDept\":\"" + newDeptCode + "\"}");
        auditRepository.save(audit);
    }

    public void recordDecision(AssetPermissionService.PermissionDecision decision, String targetUser, String operator) {
        if (decision == null) {
            return;
        }
        AssetPermissionAudit audit = new AssetPermissionAudit();
        audit.setAction(decision.allowed() ? "CHECK_ALLOW" : "CHECK_DENY");
        audit.setAssetType(decision.assetType());
        audit.setAssetId(decision.assetId());
        audit.setTargetUser(targetUser);
        audit.setPermission(decision.permission());
        audit.setOperator(normalizeOperator(operator));
        audit.setDetail(
            "{" +
            "\"allowed\":" + decision.allowed() +
            ",\"action\":\"" + json(decision.action()) + "\"" +
            ",\"requiredPermission\":\"" + json(decision.requiredPermission()) + "\"" +
            ",\"reason\":\"" + json(decision.reason()) + "\"" +
            ",\"grantSource\":\"" + json(decision.grantSource()) + "\"" +
            ",\"classificationDecision\":\"" + json(decision.classificationDecision()) + "\"" +
            ",\"assetKey\":\"" + json(decision.assetKey()) + "\"" +
            "}"
        );
        auditRepository.save(audit);
    }

    private String currentOperator() {
        return SecurityUtils.getCurrentUserLogin().orElse("system");
    }

    private String normalizeOperator(String operator) {
        if (operator == null || operator.isBlank()) {
            return currentOperator();
        }
        return operator.trim();
    }

    private String json(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
