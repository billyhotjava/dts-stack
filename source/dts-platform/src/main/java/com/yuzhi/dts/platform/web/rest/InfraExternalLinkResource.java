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
import com.yuzhi.dts.common.audit.AuditStage;
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
        auditService.auditAction("INFRA_EXTERNAL_LINK_VIEW", AuditStage.SUCCESS, "list", Map.of("summary", "查看外部平台入口配置列表"));
        return ApiResponses.ok(list);
    }

    @GetMapping("/{entryKey}")
    public ApiResponse<InfraExternalLink> get(@PathVariable String entryKey) {
        String normalizedKey = StringUtils.hasText(entryKey) ? entryKey.trim() : "";
        InfraExternalLink link = repo.findByEntryKeyIgnoreCase(normalizedKey).orElse(null);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "查看外部平台入口配置");
        payload.put("entryKey", normalizedKey);
        payload.put("enabled", link != null ? link.getEnabled() : null);
        payload.put("url", link != null ? link.getUrl() : null);
        auditService.auditAction("INFRA_EXTERNAL_LINK_VIEW", AuditStage.SUCCESS, normalizedKey, payload);
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
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "更新外部平台入口配置");
        payload.put("entryKey", normalizedKey);
        payload.put("enabled", saved.getEnabled());
        payload.put("url", saved.getUrl());
        auditService.auditAction("INFRA_EXTERNAL_LINK_EDIT", AuditStage.SUCCESS, normalizedKey, payload);
        return ApiResponses.ok(saved);
    }

    @DeleteMapping("/{entryKey}")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<Boolean> delete(@PathVariable String entryKey) {
        String normalizedKey = StringUtils.hasText(entryKey) ? entryKey.trim() : "";
        if (!normalizedKey.isEmpty()) {
            repo.findByEntryKeyIgnoreCase(normalizedKey).ifPresent(repo::delete);
            auditService.auditAction(
                "INFRA_EXTERNAL_LINK_DELETE",
                AuditStage.SUCCESS,
                normalizedKey,
                Map.of("summary", "删除外部平台入口配置", "entryKey", normalizedKey)
            );
        }
        return ApiResponses.ok(Boolean.TRUE);
    }
}
