package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.catalog.CatalogTag;
import com.yuzhi.dts.platform.repository.catalog.CatalogTagRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CatalogTagAuditService {

    public static final String TAG_CATEGORY_CREATE = "CATALOG_TAG_CATEGORY_CREATE";
    public static final String TAG_CATEGORY_UPDATE = "CATALOG_TAG_CATEGORY_UPDATE";
    public static final String TAG_CATEGORY_DELETE = "CATALOG_TAG_CATEGORY_DELETE";
    public static final String TAG_CREATE = "CATALOG_TAG_CREATE";
    public static final String TAG_UPDATE = "CATALOG_TAG_UPDATE";
    public static final String TAG_DELETE = "CATALOG_TAG_DELETE";
    public static final String ASSET_TAG_CREATE = "CATALOG_ASSET_TAG_CREATE";
    public static final String ASSET_TAG_DELETE = "CATALOG_ASSET_TAG_DELETE";
    public static final String ASSET_TAG_BATCH_CREATE = "CATALOG_ASSET_TAG_BATCH_CREATE";
    public static final String BUILTIN_INSTALL = "CATALOG_TAG_BUILTIN_INSTALL";

    private static final Logger log = LoggerFactory.getLogger(CatalogTagAuditService.class);

    private final AuditService auditService;
    private final CatalogTagRepository tagRepository;

    public CatalogTagAuditService(AuditService auditService, CatalogTagRepository tagRepository) {
        this.auditService = auditService;
        this.tagRepository = tagRepository;
    }

    public void success(String actionCode, String resourceId, Map<String, ?> payload) {
        writeAudit(actionCode, AuditStage.SUCCESS, resourceId, copyPayload(payload));
    }

    public void failure(
        String actionCode,
        String resourceId,
        Throwable failure,
        Map<String, ?> payload
    ) {
        writeAudit(
            actionCode,
            AuditStage.FAIL,
            resourceId,
            failurePayload(failure, payload)
        );
    }

    public void assetSuccess(
        String actionCode,
        String resourceId,
        List<UUID> tagIds,
        Map<String, ?> payload
    ) {
        writeAudit(
            actionCode,
            AuditStage.SUCCESS,
            resourceId,
            assetPayload(tagIds, payload)
        );
    }

    public void assetFailure(
        String actionCode,
        String resourceId,
        List<UUID> tagIds,
        Throwable failure,
        Map<String, ?> payload
    ) {
        writeAudit(
            actionCode,
            AuditStage.FAIL,
            resourceId,
            failurePayload(failure, assetPayload(tagIds, payload))
        );
    }

    private void writeAudit(
        String actionCode,
        AuditStage stage,
        String resourceId,
        Map<String, Object> payload
    ) {
        try {
            auditService.auditAction(actionCode, stage, resourceId, payload);
        } catch (RuntimeException exception) {
            log.warn(
                "Unable to persist tag business audit action={} stage={} resource={}: {}",
                actionCode,
                stage,
                resourceId,
                exception.getMessage()
            );
            log.debug("Tag business audit persistence failure", exception);
        }
    }

    private Map<String, Object> assetPayload(List<UUID> tagIds, Map<String, ?> details) {
        Map<String, Object> payload = copyPayload(details);
        List<UUID> uniqueTagIds = distinctTagIds(tagIds);
        payload.put("tagCount", uniqueTagIds.size());
        payload.put("tagIds", uniqueTagIds.stream().map(UUID::toString).toList());
        try {
            Map<UUID, String> codesById = new LinkedHashMap<>();
            tagRepository
                .findAllById(uniqueTagIds)
                .forEach(tag -> codesById.put(tag.getId(), tag.getCode()));
            payload.put(
                "tagCodes",
                uniqueTagIds
                    .stream()
                    .map(codesById::get)
                    .filter(StringUtils::hasText)
                    .toList()
            );
            payload.put(
                "tagCodeResolution",
                codesById.size() == uniqueTagIds.size() ? "COMPLETE" : "PARTIAL"
            );
        } catch (RuntimeException exception) {
            log.warn("Unable to resolve tag codes for business audit: {}", exception.getMessage());
            log.debug("Tag code resolution failure", exception);
            payload.put("tagCodes", List.of());
            payload.put("tagCodeResolution", "FAILED");
        }
        return payload;
    }

    private Map<String, Object> failurePayload(Throwable failure, Map<String, ?> details) {
        Map<String, Object> payload = copyPayload(details);
        payload.put("reasonCode", reasonCode(failure));
        String reason = failure == null ? null : failure.getMessage();
        if (StringUtils.hasText(reason)) {
            payload.put("reason", reason);
        }
        return payload;
    }

    private String reasonCode(Throwable failure) {
        if (failure instanceof CatalogAssetTagPermissionException permissionException) {
            return permissionException.reasonCode();
        }
        if (failure instanceof ResponseStatusException responseStatusException) {
            HttpStatus status = HttpStatus.resolve(responseStatusException.getStatusCode().value());
            return status == null
                ? "HTTP_" + responseStatusException.getStatusCode().value()
                : status.name();
        }
        if (failure == null) {
            return "UNKNOWN_FAILURE";
        }
        String simpleName = failure.getClass().getSimpleName();
        if (!StringUtils.hasText(simpleName)) {
            return "INTERNAL_ERROR";
        }
        return simpleName
            .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
            .toUpperCase(Locale.ROOT);
    }

    private List<UUID> distinctTagIds(List<UUID> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }
        Set<UUID> unique = new LinkedHashSet<>();
        for (UUID tagId : tagIds) {
            if (tagId != null) {
                unique.add(tagId);
            }
        }
        return new ArrayList<>(unique);
    }

    private Map<String, Object> copyPayload(Map<String, ?> payload) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (payload != null) {
            copy.putAll(payload);
        }
        return copy;
    }
}
