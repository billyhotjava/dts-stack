package com.yuzhi.dts.platform.service.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.iam.IamAssetActionPolicy;
import com.yuzhi.dts.platform.domain.iam.IamAssetActionPolicyRequest;
import com.yuzhi.dts.platform.repository.iam.IamAssetActionPolicyRepository;
import com.yuzhi.dts.platform.repository.iam.IamAssetActionPolicyRequestRepository;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.security.policy.AssetAction;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class AssetActionPolicyService {

    private static final String PENDING = "PENDING";
    private static final String APPROVED = "APPROVED";
    private static final String REJECTED = "REJECTED";

    private final IamAssetActionPolicyRepository policyRepository;
    private final IamAssetActionPolicyRequestRepository requestRepository;
    private final ObjectMapper objectMapper;

    public AssetActionPolicyService(
        IamAssetActionPolicyRepository policyRepository,
        IamAssetActionPolicyRequestRepository requestRepository,
        ObjectMapper objectMapper
    ) {
        this.policyRepository = policyRepository;
        this.requestRepository = requestRepository;
        this.objectMapper = objectMapper;
    }

    public MatrixView matrix(String subjectType, String subjectId, String resourceType, String resourceId) {
        String canonicalSubjectType = canonicalSubjectType(subjectType);
        String canonicalResourceType = canonicalResourceType(resourceType);
        String canonicalSubjectId = required(subjectId, "subjectId");
        String canonicalResourceId = required(resourceId, "resourceId");
        Map<String, IamAssetActionPolicy> byAction = new LinkedHashMap<>();
        policyRepository
            .findBySubjectTypeIgnoreCaseAndSubjectIdAndResourceTypeIgnoreCaseAndResourceIdOrderByActionAsc(
                canonicalSubjectType,
                canonicalSubjectId,
                canonicalResourceType,
                canonicalResourceId
            )
            .forEach(policy -> byAction.put(AssetAction.from(policy.getAction()).code(), policy));
        List<ActionCell> actions = new ArrayList<>();
        Instant now = Instant.now();
        for (AssetAction action : AssetAction.values()) {
            IamAssetActionPolicy policy = byAction.get(action.code());
            actions.add(
                new ActionCell(
                    action.code(),
                    action.displayName(),
                    policy != null ? policy.getEffect() : "NONE",
                    policy != null ? policy.getValidFrom() : null,
                    policy != null ? policy.getValidTo() : null,
                    policyStatus(policy, now)
                )
            );
        }
        IamAssetActionPolicyRequest pending = requestRepository
            .findFirstBySubjectTypeIgnoreCaseAndSubjectIdAndResourceTypeIgnoreCaseAndResourceIdAndStatusIgnoreCaseOrderByCreatedDateDesc(
                canonicalSubjectType,
                canonicalSubjectId,
                canonicalResourceType,
                canonicalResourceId,
                PENDING
            )
            .orElse(null);
        return new MatrixView(
            canonicalSubjectType,
            canonicalSubjectId,
            canonicalResourceType,
            canonicalResourceId,
            actions,
            pending
        );
    }

    public List<IamAssetActionPolicyRequest> listRequests(String status) {
        String canonicalStatus = StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : PENDING;
        if (!List.of(PENDING, APPROVED, REJECTED).contains(canonicalStatus)) {
            throw badRequest("Unsupported request status: " + status);
        }
        return requestRepository.findByStatusIgnoreCaseOrderByCreatedDateDesc(canonicalStatus);
    }

    @Transactional
    public IamAssetActionPolicyRequest requestChange(PolicyChangeRequestCommand command) {
        if (command == null) {
            throw badRequest("Request body is required");
        }
        String subjectType = canonicalSubjectType(command.subjectType());
        String subjectId = required(command.subjectId(), "subjectId");
        String resourceType = canonicalResourceType(command.resourceType());
        String resourceId = required(command.resourceId(), "resourceId");
        String actor = currentActor();
        if (!StringUtils.hasText(command.reason())) {
            throw badRequest("reason is required");
        }
        if (command.validFrom() != null && command.validTo() != null && command.validTo().isBefore(command.validFrom())) {
            throw badRequest("validTo must not be before validFrom");
        }
        if (
            requestRepository.existsBySubjectTypeIgnoreCaseAndSubjectIdAndResourceTypeIgnoreCaseAndResourceIdAndStatusIgnoreCase(
                subjectType,
                subjectId,
                resourceType,
                resourceId,
                PENDING
            )
        ) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "asset_action_policy_request_pending");
        }

        Map<String, String> changes = canonicalChanges(command.desiredEffects());
        Map<String, String> before = new LinkedHashMap<>();
        for (String action : changes.keySet()) {
            String effect = policyRepository
                .findBySubjectTypeIgnoreCaseAndSubjectIdAndResourceTypeIgnoreCaseAndResourceIdAndActionIgnoreCase(
                    subjectType,
                    subjectId,
                    resourceType,
                    resourceId,
                    action
                )
                .map(IamAssetActionPolicy::getEffect)
                .orElse("NONE");
            before.put(action, effect);
        }

        Instant now = Instant.now();
        IamAssetActionPolicyRequest request = new IamAssetActionPolicyRequest();
        request.setSubjectType(subjectType);
        request.setSubjectId(subjectId);
        request.setSubjectName(trimToNull(command.subjectName()));
        request.setResourceType(resourceType);
        request.setResourceId(resourceId);
        request.setResourceName(trimToNull(command.resourceName()));
        request.setChangesJson(writeJson(changes));
        request.setBeforeSnapshotJson(writeJson(before));
        request.setValidFrom(command.validFrom());
        request.setValidTo(command.validTo());
        request.setReason(command.reason().trim());
        request.setStatus(PENDING);
        request.setRequestedBy(actor);
        request.setCreatedBy(actor);
        request.setCreatedDate(now);
        request.setLastModifiedBy(actor);
        request.setLastModifiedDate(now);
        try {
            return requestRepository.saveAndFlush(request);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "asset_action_policy_request_pending",
                exception
            );
        }
    }

    @Transactional
    public IamAssetActionPolicyRequest decide(UUID requestId, DecisionCommand command) {
        if (requestId == null || command == null) {
            throw badRequest("requestId and decision are required");
        }
        IamAssetActionPolicyRequest request = requestRepository
            .findForUpdate(requestId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "asset_action_policy_request_not_found"));
        if (!PENDING.equalsIgnoreCase(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "asset_action_policy_request_not_pending");
        }
        String actor = currentActor();
        if (actor.equalsIgnoreCase(request.getRequestedBy())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "separation_of_duties");
        }
        String decision = required(command.decision(), "decision").toUpperCase(Locale.ROOT);
        Instant now = Instant.now();
        if ("APPROVE".equals(decision)) {
            applyApprovedChanges(request, actor, now);
            request.setStatus(APPROVED);
        } else if ("REJECT".equals(decision)) {
            request.setStatus(REJECTED);
        } else {
            throw badRequest("Unsupported decision: " + command.decision());
        }
        request.setDecidedBy(actor);
        request.setDecidedAt(now);
        request.setDecisionNotes(trimToNull(command.notes()));
        request.setLastModifiedBy(actor);
        request.setLastModifiedDate(now);
        requestRepository.save(request);
        return request;
    }

    private void applyApprovedChanges(IamAssetActionPolicyRequest request, String actor, Instant now) {
        Map<String, String> changes = readJson(request.getChangesJson());
        for (Map.Entry<String, String> change : changes.entrySet()) {
            Optional<IamAssetActionPolicy> existing = policyRepository.findBySubjectTypeIgnoreCaseAndSubjectIdAndResourceTypeIgnoreCaseAndResourceIdAndActionIgnoreCase(
                request.getSubjectType(),
                request.getSubjectId(),
                request.getResourceType(),
                request.getResourceId(),
                change.getKey()
            );
            if ("NONE".equals(change.getValue())) {
                existing.ifPresent(policyRepository::delete);
                continue;
            }
            IamAssetActionPolicy policy = existing.orElseGet(IamAssetActionPolicy::new);
            policy.setSubjectType(request.getSubjectType());
            policy.setSubjectId(request.getSubjectId());
            policy.setSubjectName(request.getSubjectName());
            policy.setResourceType(request.getResourceType());
            policy.setResourceId(request.getResourceId());
            policy.setResourceName(request.getResourceName());
            policy.setAction(change.getKey());
            policy.setEffect(change.getValue());
            policy.setSource("MANUAL");
            policy.setValidFrom(request.getValidFrom());
            policy.setValidTo(request.getValidTo());
            if (policy.getCreatedDate() == null) {
                policy.setCreatedBy(actor);
                policy.setCreatedDate(now);
            }
            policy.setLastModifiedBy(actor);
            policy.setLastModifiedDate(now);
            policyRepository.save(policy);
        }
    }

    private static Map<String, String> canonicalChanges(Map<String, String> desiredEffects) {
        if (desiredEffects == null || desiredEffects.isEmpty()) {
            throw badRequest("desiredEffects must contain at least one action");
        }
        Map<String, String> changes = new LinkedHashMap<>();
        desiredEffects.forEach((rawAction, rawEffect) -> {
            AssetAction action;
            try {
                action = AssetAction.from(rawAction);
            } catch (IllegalArgumentException exception) {
                throw badRequest(exception.getMessage());
            }
            String effect = required(rawEffect, "effect").toUpperCase(Locale.ROOT);
            if (!List.of("ALLOW", "DENY", "NONE").contains(effect)) {
                throw badRequest("Unsupported policy effect: " + rawEffect);
            }
            changes.put(action.code(), effect);
        });
        return changes;
    }

    private Map<String, String> readJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {});
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "asset_action_policy_request_corrupt", exception);
        }
    }

    private String writeJson(Map<String, String> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize action-policy request", exception);
        }
    }

    private static String canonicalSubjectType(String raw) {
        String value = required(raw, "subjectType").toUpperCase(Locale.ROOT);
        value = switch (value) {
            case "DEPT", "ORG" -> "DEPARTMENT";
            default -> value;
        };
        if (!List.of("ROLE", "DEPARTMENT", "USER").contains(value)) {
            throw badRequest("Unsupported subjectType: " + raw);
        }
        return value;
    }

    private static String canonicalResourceType(String raw) {
        String value = required(raw, "resourceType").toUpperCase(Locale.ROOT);
        if (!List.of("CATALOG", "TABLE", "DATASET").contains(value)) {
            throw badRequest("Unsupported resourceType: " + raw);
        }
        return value;
    }

    private static String required(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw badRequest(field + " is required");
        }
        return value.trim();
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String policyStatus(IamAssetActionPolicy policy, Instant now) {
        if (policy == null) {
            return "UNCONFIGURED";
        }
        if (policy.getValidFrom() != null && policy.getValidFrom().isAfter(now)) {
            return "SCHEDULED";
        }
        if (policy.getValidTo() != null && policy.getValidTo().isBefore(now)) {
            return "EXPIRED";
        }
        return "ACTIVE";
    }

    private static String currentActor() {
        return SecurityUtils
            .getCurrentUserLogin()
            .filter(StringUtils::hasText)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "authentication_required"));
    }

    private static ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, Objects.toString(reason, "invalid_request"));
    }

    public record PolicyChangeRequestCommand(
        String subjectType,
        String subjectId,
        String subjectName,
        String resourceType,
        String resourceId,
        String resourceName,
        Map<String, String> desiredEffects,
        Instant validFrom,
        Instant validTo,
        String reason
    ) {}

    public record DecisionCommand(String decision, String notes) {}

    public record ActionCell(
        String action,
        String displayName,
        String effect,
        Instant validFrom,
        Instant validTo,
        String policyStatus
    ) {}

    public record MatrixView(
        String subjectType,
        String subjectId,
        String resourceType,
        String resourceId,
        List<ActionCell> actions,
        IamAssetActionPolicyRequest pendingRequest
    ) {}
}
