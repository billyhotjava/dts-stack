package com.yuzhi.dts.platform.service.permission;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.permission.AssetPermissionAudit;
import com.yuzhi.dts.platform.domain.permission.AssetPermissionPolicyInjection;
import com.yuzhi.dts.platform.repository.permission.AssetPermissionAuditRepository;
import com.yuzhi.dts.platform.repository.permission.AssetPermissionPolicyInjectionRepository;
import com.yuzhi.dts.platform.security.SecurityUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class AssetPermissionAuditService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AssetPermissionAuditRepository auditRepository;
    private final AssetPermissionPolicyInjectionRepository policyInjectionRepository;

    AssetPermissionAuditService(AssetPermissionAuditRepository auditRepository) {
        this(auditRepository, null);
    }

    public AssetPermissionAuditService(
        AssetPermissionAuditRepository auditRepository,
        AssetPermissionPolicyInjectionRepository policyInjectionRepository
    ) {
        this.auditRepository = auditRepository;
        this.policyInjectionRepository = policyInjectionRepository;
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
        audit.setReasonCode(decision.reasonCode());
        audit.setReasonDetail(decision.reasonDetail());
        audit.setDetail(
            "{" +
            "\"allowed\":" + decision.allowed() +
            ",\"action\":\"" + json(decision.action()) + "\"" +
            ",\"requiredPermission\":\"" + json(decision.requiredPermission()) + "\"" +
            ",\"reason\":\"" + json(decision.reason()) + "\"" +
            ",\"reasonCode\":\"" + json(decision.reasonCode()) + "\"" +
            ",\"reasonDetail\":\"" + json(decision.reasonDetail()) + "\"" +
            ",\"suggestedRemediation\":\"" + json(decision.suggestedRemediation()) + "\"" +
            ",\"deniedAt\":\"" + json(decision.deniedAt() == null ? null : decision.deniedAt().toString()) + "\"" +
            ",\"grantSource\":\"" + json(decision.grantSource()) + "\"" +
            ",\"classificationDecision\":\"" + json(decision.classificationDecision()) + "\"" +
            ",\"assetKey\":\"" + json(decision.assetKey()) + "\"" +
            "}"
        );
        auditRepository.save(audit);
    }

    public void recordPolicyInjection(PolicyInjectionAuditEvent event) {
        if (event == null || policyInjectionRepository == null) {
            return;
        }
        AssetPermissionPolicyInjection audit = new AssetPermissionPolicyInjection();
        audit.setActor(normalizeText(event.actor(), currentOperator()));
        audit.setAssetType(trimToNull(event.assetType()));
        audit.setAssetId(trimToNull(event.assetId()));
        audit.setAction(normalizeText(event.action(), "PREVIEW"));
        audit.setPredicates(toJsonList(event.predicates()));
        audit.setMaskedColumns(toJsonList(event.maskedColumns()));
        audit.setPolicySource(normalizeText(event.policySource(), "platform-permission"));
        audit.setPredicateHash(
            StringUtils.hasText(event.predicateHash())
                ? event.predicateHash().trim()
                : predicateHash(event.predicates(), event.maskedColumns(), audit.getPolicySource())
        );
        audit.setDirection(normalizeText(event.direction(), "PROVIDER").toUpperCase(java.util.Locale.ROOT));
        audit.setPackId(trimToNull(event.packId()));
        policyInjectionRepository.save(audit);
    }

    public static String predicateHash(List<String> predicates, List<String> maskedColumns, String policySource) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("policySource", normalizeText(policySource, "platform-permission"));
        payload.put("predicates", normalizeList(predicates));
        payload.put("maskedColumns", normalizeList(maskedColumns));
        return "sha256:" + sha256(toJson(payload));
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

    private static List<String> normalizeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream().filter(StringUtils::hasText).map(String::trim).toList();
    }

    private static String toJsonList(List<String> values) {
        return toJson(normalizeList(values));
    }

    private static String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("failed to serialize permission policy audit payload", e);
        }
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String normalizeText(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed != null ? trimmed : fallback;
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record PolicyInjectionAuditEvent(
        String actor,
        String assetType,
        String assetId,
        String action,
        List<String> predicates,
        List<String> maskedColumns,
        String policySource,
        String predicateHash,
        String direction,
        String packId
    ) {}
}
