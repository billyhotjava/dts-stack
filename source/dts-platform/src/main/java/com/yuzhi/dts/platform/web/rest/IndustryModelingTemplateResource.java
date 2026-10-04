package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.template.IndustryModelingTemplateCatalog;
import com.yuzhi.dts.platform.service.modeling.template.IndustryModelingTemplateContract.IndustryModelingTemplate;
import com.yuzhi.dts.platform.service.modeling.template.IndustryModelingTemplateInstaller;
import com.yuzhi.dts.platform.service.modeling.template.IndustryModelingTemplateInstaller.InstallationResult;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/governance/modeling-templates")
@Transactional
public class IndustryModelingTemplateResource {

    private static final String GOVERNANCE_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).DATA_MAINTAINER_ROLES)";

    private final IndustryModelingTemplateCatalog catalog;
    private final IndustryModelingTemplateInstaller installer;
    private final AuditService audit;

    public IndustryModelingTemplateResource(
        IndustryModelingTemplateCatalog catalog,
        IndustryModelingTemplateInstaller installer,
        AuditService audit
    ) {
        this.catalog = catalog;
        this.installer = installer;
        this.audit = audit;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<List<IndustryModelingTemplate>> listTemplates() {
        return ApiResponses.ok(catalog.listTemplates());
    }

    @GetMapping("/{templateId}")
    @Transactional(readOnly = true)
    public ApiResponse<IndustryModelingTemplate> getTemplate(@PathVariable String templateId) {
        return ApiResponses.ok(requireTemplate(templateId));
    }

    @PostMapping("/{templateId}/install")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<InstallationResult> installTemplate(@PathVariable String templateId, @RequestParam UUID domainId) {
        IndustryModelingTemplate template = requireTemplate(templateId);
        InstallationResult result = installer.install(domainId, template);
        audit.auditAction(
            "MODELING_TEMPLATE_INSTALL",
            AuditStage.SUCCESS,
            template.templateId(),
            Map.of(
                "domainId",
                domainId.toString(),
                "templateVersion",
                template.version(),
                "status",
                result.status().name(),
                "createdProcesses",
                result.createdProcesses(),
                "createdDimensions",
                result.createdDimensions()
            )
        );
        return ApiResponses.ok(result);
    }

    private IndustryModelingTemplate requireTemplate(String templateId) {
        try {
            return catalog.requireTemplate(templateId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        }
    }
}
