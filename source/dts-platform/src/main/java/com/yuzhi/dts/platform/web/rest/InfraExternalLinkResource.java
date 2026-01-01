package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.domain.service.InfraExternalLink;
import com.yuzhi.dts.platform.repository.service.InfraExternalLinkRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import jakarta.validation.Valid;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/infra/external-links")
@Transactional
public class InfraExternalLinkResource {

    private static final String INFRA_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).INFRA_MAINTAINERS)";

    private final InfraExternalLinkRepository repo;
    private final AuditService auditService;

    public InfraExternalLinkResource(InfraExternalLinkRepository repo, AuditService auditService) {
        this.repo = repo;
        this.auditService = auditService;
    }

    @GetMapping
    public ApiResponse<List<InfraExternalLink>> list() {
        List<InfraExternalLink> list = repo
            .findAll()
            .stream()
            .sorted(Comparator.comparing(link -> String.valueOf(link.getEntryKey()).toUpperCase(Locale.ROOT)))
            .toList();
        auditService.audit("READ", "infra.externalLink", "list");
        return ApiResponses.ok(list);
    }

    @GetMapping("/{entryKey}")
    public ApiResponse<InfraExternalLink> get(@PathVariable String entryKey) {
        InfraExternalLink link = repo.findByEntryKeyIgnoreCase(entryKey).orElse(null);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "查看外部链接配置");
        payload.put("entryKey", entryKey);
        auditService.recordAuxiliary("READ", "infra.externalLink", "infra.externalLink", entryKey, payload);
        return ApiResponses.ok(link);
    }

    @PutMapping("/{entryKey}")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<InfraExternalLink> upsert(@PathVariable String entryKey, @Valid @RequestBody InfraExternalLink patch) {
        String normalizedKey = StringUtils.hasText(entryKey) ? entryKey.trim() : "";
        if (normalizedKey.isEmpty()) {
            throw new IllegalArgumentException("entryKey 不能为空");
        }
        InfraExternalLink link = repo.findByEntryKeyIgnoreCase(normalizedKey).orElseGet(InfraExternalLink::new);
        link.setEntryKey(normalizedKey);
        link.setName(StringUtils.hasText(patch.getName()) ? patch.getName().trim() : null);
        link.setUrl(StringUtils.hasText(patch.getUrl()) ? patch.getUrl().trim() : null);
        link.setDescription(StringUtils.hasText(patch.getDescription()) ? patch.getDescription().trim() : null);
        link.setEnabled(patch.getEnabled() != null ? patch.getEnabled() : Boolean.TRUE);
        InfraExternalLink saved = repo.save(link);
        auditService.audit("UPDATE", "infra.externalLink", normalizedKey);
        return ApiResponses.ok(saved);
    }

    @DeleteMapping("/{entryKey}")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<Boolean> delete(@PathVariable String entryKey) {
        String normalizedKey = StringUtils.hasText(entryKey) ? entryKey.trim() : "";
        if (!normalizedKey.isEmpty()) {
            repo.findByEntryKeyIgnoreCase(normalizedKey).ifPresent(repo::delete);
            auditService.audit("DELETE", "infra.externalLink", normalizedKey);
        }
        return ApiResponses.ok(Boolean.TRUE);
    }
}

