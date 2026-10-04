package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.infra.InfraExternalExchangeFile;
import com.yuzhi.dts.platform.repository.infra.InfraExternalExchangeFileRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.ClassificationUtils;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/infra/exchange-files")
@Transactional
public class InfraExternalExchangeFileResource {

    private static final String INFRA_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).INFRA_MAINTAINERS)";

    private final InfraExternalExchangeFileRepository repo;
    private final ClassificationUtils classificationUtils;
    private final AuditService audit;

    public InfraExternalExchangeFileResource(
        InfraExternalExchangeFileRepository repo,
        ClassificationUtils classificationUtils,
        AuditService audit
    ) {
        this.repo = repo;
        this.classificationUtils = classificationUtils;
        this.audit = audit;
    }

    @GetMapping
    public ApiResponse<List<InfraExternalExchangeFile>> list(
        @RequestParam(required = false) String entryKey,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false, defaultValue = "true") boolean enabledOnly,
        @RequestParam(required = false, defaultValue = "200") int limit
    ) {
        String key = trimToNull(entryKey);
        String st = trimToNull(status);
        String kw = trimToNull(keyword);
        int safeLimit = Math.max(1, Math.min(limit, 500));

        List<InfraExternalExchangeFile> list = repo.search(key, st, kw, enabledOnly).stream().filter(this::canRead).limit(safeLimit).toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "查看数据交换文件台账");
        payload.put("entryKey", key);
        payload.put("status", st);
        payload.put("keyword", kw);
        payload.put("enabledOnly", enabledOnly);
        payload.put("limit", safeLimit);
        payload.put("size", list.size());
        audit.auditAction("INFRA_EXTERNAL_EXCHANGE_FILE_VIEW", AuditStage.SUCCESS, "list", payload);
        return ApiResponses.ok(list);
    }

    @GetMapping("/{id}")
    public ApiResponse<InfraExternalExchangeFile> get(@PathVariable UUID id) {
        InfraExternalExchangeFile entity = repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not found"));
        if (!canRead(entity)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "forbidden");
        }
        audit.auditAction("INFRA_EXTERNAL_EXCHANGE_FILE_VIEW", AuditStage.SUCCESS, id.toString(), Map.of("summary", "查看数据交换文件详情", "id", id.toString()));
        return ApiResponses.ok(entity);
    }

    @PostMapping
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<InfraExternalExchangeFile> create(@Valid @RequestBody InfraExternalExchangeFile body) {
        InfraExternalExchangeFile entity = new InfraExternalExchangeFile();
        apply(entity, body, true);
        if (entity.getReceivedAt() == null) {
            entity.setReceivedAt(Instant.now());
        }
        InfraExternalExchangeFile saved = repo.save(entity);
        audit.auditAction(
            "INFRA_EXTERNAL_EXCHANGE_FILE_EDIT",
            AuditStage.SUCCESS,
            saved.getId() != null ? saved.getId().toString() : "new",
            Map.of("summary", "新增数据交换文件台账", "entryKey", saved.getEntryKey(), "fileName", saved.getFileName(), "status", saved.getStatus())
        );
        return ApiResponses.ok(saved);
    }

    @PutMapping("/{id}")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<InfraExternalExchangeFile> update(@PathVariable UUID id, @Valid @RequestBody InfraExternalExchangeFile body) {
        InfraExternalExchangeFile entity = repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not found"));
        apply(entity, body, false);
        InfraExternalExchangeFile saved = repo.save(entity);
        audit.auditAction(
            "INFRA_EXTERNAL_EXCHANGE_FILE_EDIT",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of("summary", "更新数据交换文件台账", "id", id.toString(), "entryKey", saved.getEntryKey(), "fileName", saved.getFileName(), "status", saved.getStatus())
        );
        return ApiResponses.ok(saved);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> delete(@PathVariable UUID id) {
        InfraExternalExchangeFile entity = repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not found"));
        entity.setEnabled(Boolean.FALSE);
        repo.save(entity);
        audit.auditAction("INFRA_EXTERNAL_EXCHANGE_FILE_DELETE", AuditStage.SUCCESS, id.toString(), Map.of("summary", "禁用数据交换文件台账", "id", id.toString()));
        return ApiResponses.ok(Map.of("ok", true));
    }

    private void apply(InfraExternalExchangeFile target, InfraExternalExchangeFile patch, boolean creating) {
        String entryKey = trimToNull(patch.getEntryKey());
        String fileName = trimToNull(patch.getFileName());
        if (!StringUtils.hasText(entryKey)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "entryKey required");
        }
        if (!StringUtils.hasText(fileName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fileName required");
        }

        target.setEntryKey(entryKey);
        target.setDirectoryArtifactId(patch.getDirectoryArtifactId());
        target.setFileName(fileName);
        target.setFilePath(trimToNull(patch.getFilePath()));
        target.setFileSize(patch.getFileSize());
        target.setChecksum(trimToNull(patch.getChecksum()));
        target.setBatchCode(trimToNull(patch.getBatchCode()));
        target.setSourceSystem(trimToNull(patch.getSourceSystem()));
        target.setErrorMessage(trimToNull(patch.getErrorMessage()));
        target.setExternalRef(trimToNull(patch.getExternalRef()));
        target.setOwnerDept(trimToNull(patch.getOwnerDept()));
        target.setProps(trimToNull(patch.getProps()));

        String status = trimToNull(patch.getStatus());
        if (StringUtils.hasText(status)) {
            target.setStatus(status.toUpperCase(Locale.ROOT));
        } else if (creating && !StringUtils.hasText(target.getStatus())) {
            target.setStatus("RECEIVED");
        }

        String classification = trimToNull(patch.getClassification());
        target.setClassification(classification != null ? classification.toUpperCase(Locale.ROOT) : "INTERNAL");

        if (patch.getReceivedAt() != null) {
            target.setReceivedAt(patch.getReceivedAt());
        } else if (creating && target.getReceivedAt() == null) {
            target.setReceivedAt(Instant.now());
        }
        if (patch.getProcessedAt() != null) {
            target.setProcessedAt(patch.getProcessedAt());
        }

        if (patch.getEnabled() != null) {
            target.setEnabled(Boolean.TRUE.equals(patch.getEnabled()));
        } else if (creating) {
            target.setEnabled(Boolean.TRUE);
        }
    }

    private boolean canRead(InfraExternalExchangeFile entity) {
        if (entity == null) return false;
        if (!classificationUtils.canAccess(entity.getClassification())) return false;
        if (SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES)) return true;
        String userDept = currentClaim("dept_code");
        String ownerDept = trimToNull(entity.getOwnerDept());
        if (!StringUtils.hasText(ownerDept)) return true;
        if (!StringUtils.hasText(userDept)) return false;
        return ownerDept.equalsIgnoreCase(userDept.trim());
    }

    private String currentClaim(String name) {
        try {
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
                Object v = token.getToken().getClaims().get(name);
                return firstTextValue(v);
            }
            if (auth != null && auth.getPrincipal() instanceof org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal principal) {
                Object v = principal.getAttribute(name);
                return firstTextValue(v);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String firstTextValue(Object raw) {
        Object flattened = flatten(raw);
        if (flattened == null) return null;
        String text = flattened.toString();
        return text == null || text.isBlank() ? null : text.trim();
    }

    private Object flatten(Object raw) {
        if (raw == null) return null;
        if (raw instanceof java.util.Collection<?> c) {
            return c.stream().filter(Objects::nonNull).findFirst().orElse(null);
        }
        if (raw.getClass().isArray()) {
            int len = java.lang.reflect.Array.getLength(raw);
            for (int i = 0; i < len; i++) {
                Object e = java.lang.reflect.Array.get(raw, i);
                if (e != null) return e;
            }
            return null;
        }
        return raw;
    }

    private String trimToNull(String s) {
        if (!StringUtils.hasText(s)) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}

