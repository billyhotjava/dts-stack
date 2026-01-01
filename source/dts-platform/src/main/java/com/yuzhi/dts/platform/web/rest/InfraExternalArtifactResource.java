package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.infra.InfraExternalArtifact;
import com.yuzhi.dts.platform.repository.infra.InfraExternalArtifactRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.ClassificationUtils;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import jakarta.validation.Valid;
import java.util.Comparator;
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
@RequestMapping("/api/infra/external-artifacts")
@Transactional
public class InfraExternalArtifactResource {

    private static final String INFRA_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).INFRA_MAINTAINERS)";

    private final InfraExternalArtifactRepository repo;
    private final ClassificationUtils classificationUtils;
    private final AuditService audit;

    public InfraExternalArtifactResource(
        InfraExternalArtifactRepository repo,
        ClassificationUtils classificationUtils,
        AuditService audit
    ) {
        this.repo = repo;
        this.classificationUtils = classificationUtils;
        this.audit = audit;
    }

    @GetMapping
    public ApiResponse<List<InfraExternalArtifact>> list(
        @RequestParam(required = false) String entryKey,
        @RequestParam(required = false, name = "type") String artifactType,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false, defaultValue = "true") boolean enabledOnly
    ) {
        String key = trimToNull(entryKey);
        String type = trimToNull(artifactType);
        String kw = trimToNull(keyword);

        List<InfraExternalArtifact> list = repo.search(key, type, kw, enabledOnly);
        list = list.stream().filter(this::canRead).sorted(defaultSort()).toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "查看外部平台作业/任务登记列表");
        payload.put("entryKey", key);
        payload.put("type", type);
        payload.put("keyword", kw);
        payload.put("enabledOnly", enabledOnly);
        payload.put("size", list.size());
        audit.auditAction("INFRA_EXTERNAL_ARTIFACT_VIEW", AuditStage.SUCCESS, "list", payload);
        return ApiResponses.ok(list);
    }

    @GetMapping("/{id}")
    public ApiResponse<InfraExternalArtifact> get(@PathVariable UUID id) {
        InfraExternalArtifact entity = repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not found"));
        if (!canRead(entity)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "forbidden");
        }
        audit.auditAction(
            "INFRA_EXTERNAL_ARTIFACT_VIEW",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of("summary", "查看外部平台作业/任务登记", "id", id.toString())
        );
        return ApiResponses.ok(entity);
    }

    @PostMapping
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<InfraExternalArtifact> create(@Valid @RequestBody InfraExternalArtifact body) {
        InfraExternalArtifact entity = new InfraExternalArtifact();
        apply(entity, body);
        InfraExternalArtifact saved = repo.save(entity);
        audit.auditAction(
            "INFRA_EXTERNAL_ARTIFACT_EDIT",
            AuditStage.SUCCESS,
            saved.getId() != null ? saved.getId().toString() : "new",
            Map.of("summary", "新增外部平台作业/任务登记", "entryKey", saved.getEntryKey(), "type", saved.getArtifactType(), "name", saved.getName())
        );
        return ApiResponses.ok(saved);
    }

    @PutMapping("/{id}")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<InfraExternalArtifact> update(@PathVariable UUID id, @Valid @RequestBody InfraExternalArtifact body) {
        InfraExternalArtifact entity = repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not found"));
        apply(entity, body);
        InfraExternalArtifact saved = repo.save(entity);
        audit.auditAction(
            "INFRA_EXTERNAL_ARTIFACT_EDIT",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of("summary", "更新外部平台作业/任务登记", "id", id.toString(), "entryKey", saved.getEntryKey(), "type", saved.getArtifactType(), "name", saved.getName())
        );
        return ApiResponses.ok(saved);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> delete(@PathVariable UUID id) {
        InfraExternalArtifact entity = repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not found"));
        entity.setEnabled(Boolean.FALSE);
        repo.save(entity);
        audit.auditAction("INFRA_EXTERNAL_ARTIFACT_DELETE", AuditStage.SUCCESS, id.toString(), Map.of("summary", "禁用外部平台作业/任务登记", "id", id.toString()));
        return ApiResponses.ok(Map.of("ok", true));
    }

    @DeleteMapping("/{id}/purge")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> purge(@PathVariable UUID id) {
        InfraExternalArtifact entity = repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not found"));
        repo.delete(entity);
        audit.auditAction("INFRA_EXTERNAL_ARTIFACT_PURGE", AuditStage.SUCCESS, id.toString(), Map.of("summary", "删除外部平台作业/任务登记", "id", id.toString()));
        return ApiResponses.ok(Map.of("ok", true));
    }

    private void apply(InfraExternalArtifact target, InfraExternalArtifact patch) {
        String entryKey = trimToNull(patch.getEntryKey());
        String type = trimToNull(patch.getArtifactType());
        String name = trimToNull(patch.getName());
        if (!StringUtils.hasText(entryKey)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "entryKey required");
        }
        if (!StringUtils.hasText(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "artifactType required");
        }
        if (!StringUtils.hasText(name)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name required");
        }

        target.setEntryKey(entryKey);
        target.setArtifactType(type.toUpperCase(Locale.ROOT));
        target.setName(name);
        target.setExternalId(trimToNull(patch.getExternalId()));
        target.setExternalUrl(trimToNull(patch.getExternalUrl()));
        target.setOwnerDept(trimToNull(patch.getOwnerDept()));
        target.setTags(trimToNull(patch.getTags()));
        target.setStatus(trimToNull(patch.getStatus()));
        target.setLastMessage(trimToNull(patch.getLastMessage()));
        target.setLastSeenAt(patch.getLastSeenAt());
        target.setProps(trimToNull(patch.getProps()));

        String classification = trimToNull(patch.getClassification());
        target.setClassification(classification != null ? classification.toUpperCase(Locale.ROOT) : "INTERNAL");

        if (patch.getEnabled() != null) {
            target.setEnabled(Boolean.TRUE.equals(patch.getEnabled()));
        } else if (target.getEnabled() == null) {
            target.setEnabled(Boolean.TRUE);
        }
    }

    private boolean canRead(InfraExternalArtifact entity) {
        if (entity == null) return false;
        if (!classificationUtils.canAccess(entity.getClassification())) return false;
        if (SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES)) return true;
        String userDept = currentClaim("dept_code");
        String ownerDept = trimToNull(entity.getOwnerDept());
        if (!StringUtils.hasText(ownerDept)) return true;
        if (!StringUtils.hasText(userDept)) return false;
        return ownerDept.equalsIgnoreCase(userDept.trim());
    }

    private Comparator<InfraExternalArtifact> defaultSort() {
        return Comparator
            .comparing((InfraExternalArtifact a) -> a.getEnabled() != null ? a.getEnabled() : Boolean.TRUE)
            .reversed()
            .thenComparing(a -> String.valueOf(a.getEntryKey()).toUpperCase(Locale.ROOT))
            .thenComparing(a -> String.valueOf(a.getArtifactType()).toUpperCase(Locale.ROOT))
            .thenComparing(a -> Objects.toString(a.getName(), "").toUpperCase(Locale.ROOT));
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

