package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTerm;
import com.yuzhi.dts.platform.domain.modeling.ModelingPlan;
import com.yuzhi.dts.platform.domain.modeling.ModelingTemplate;
import com.yuzhi.dts.platform.repository.modeling.ModelingGlossaryTermRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingPlanRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingTemplateRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.DepartmentUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.DataStandardSecurity;
import com.yuzhi.dts.platform.service.security.OrganizationVisibilityService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/modeling")
@Transactional
public class ModelingAuxResource {

    private static final String MODELING_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).CATALOG_MAINTAINERS)";

    private final ModelingPlanRepository planRepo;
    private final ModelingGlossaryTermRepository glossaryRepo;
    private final ModelingTemplateRepository templateRepo;
    private final AuditService auditService;
    private final DataStandardSecurity security;
    private final OrganizationVisibilityService organizationVisibilityService;

    public ModelingAuxResource(
        ModelingPlanRepository planRepo,
        ModelingGlossaryTermRepository glossaryRepo,
        ModelingTemplateRepository templateRepo,
        AuditService auditService,
        DataStandardSecurity security,
        OrganizationVisibilityService organizationVisibilityService
    ) {
        this.planRepo = planRepo;
        this.glossaryRepo = glossaryRepo;
        this.templateRepo = templateRepo;
        this.auditService = auditService;
        this.security = security;
        this.organizationVisibilityService = organizationVisibilityService;
    }

    @GetMapping("/plans")
    public ApiResponse<List<ModelingPlan>> listPlans(
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDeptHeader
    ) {
        String activeDept = security.resolveActiveDept(activeDeptHeader);
        boolean instituteScope = security.hasInstituteScope();
        String kw = StringUtils.trimToNull(keyword);
        List<ModelingPlan> list = planRepo
            .findAll()
            .stream()
            .filter(plan -> isOwnerDeptVisible(plan != null ? plan.getOwnerDept() : null, activeDept, instituteScope))
            .filter(plan -> kw == null || matchKeyword(plan.getName(), kw) || matchKeyword(plan.getDomain(), kw) || matchKeyword(plan.getOwner(), kw))
            .sorted(Comparator.comparing(plan -> String.valueOf(plan.getName()).toLowerCase(Locale.ROOT)))
            .toList();
        auditService.audit("READ", "modeling.plan", "list");
        return ApiResponses.ok(list);
    }

    @GetMapping("/plans/{id}")
    public ApiResponse<ModelingPlan> getPlan(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDeptHeader
    ) {
        String activeDept = security.resolveActiveDept(activeDeptHeader);
        boolean instituteScope = security.hasInstituteScope();
        ModelingPlan plan = planRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("数据规划不存在"));
        if (!isOwnerDeptVisible(plan.getOwnerDept(), activeDept, instituteScope)) {
            throw new AccessDeniedException("当前账号无权访问该数据规划");
        }
        auditService.audit("READ", "modeling.plan", id.toString());
        return ApiResponses.ok(plan);
    }

    @PostMapping("/plans")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ModelingPlan> createPlan(
        @Valid @RequestBody ModelingPlan request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDeptHeader
    ) {
        ModelingPlan plan = new ModelingPlan();
        applyPlanUpsert(plan, request, activeDeptHeader);
        ModelingPlan saved = planRepo.save(plan);
        auditService.audit("CREATE", "modeling.plan", saved.getId().toString());
        return ApiResponses.ok(saved);
    }

    @PutMapping("/plans/{id}")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ModelingPlan> updatePlan(
        @PathVariable UUID id,
        @Valid @RequestBody ModelingPlan request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDeptHeader
    ) {
        ModelingPlan plan = planRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("数据规划不存在"));
        applyPlanUpsert(plan, request, activeDeptHeader);
        ModelingPlan saved = planRepo.save(plan);
        auditService.audit("UPDATE", "modeling.plan", id.toString());
        return ApiResponses.ok(saved);
    }

    @DeleteMapping("/plans/{id}")
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.OP_ADMIN + "')")
    public ApiResponse<Boolean> deletePlan(@PathVariable UUID id) {
        planRepo.deleteById(id);
        auditService.audit("DELETE", "modeling.plan", id.toString());
        return ApiResponses.ok(Boolean.TRUE);
    }

    @GetMapping("/glossary/terms")
    public ApiResponse<List<ModelingGlossaryTerm>> listGlossaryTerms(
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDeptHeader
    ) {
        String activeDept = security.resolveActiveDept(activeDeptHeader);
        boolean instituteScope = security.hasInstituteScope();
        String kw = StringUtils.trimToNull(keyword);
        List<ModelingGlossaryTerm> list = glossaryRepo
            .findAll()
            .stream()
            .filter(term -> isOwnerDeptVisible(term != null ? term.getOwnerDept() : null, activeDept, instituteScope))
            .filter(term -> kw == null || matchKeyword(term.getName(), kw) || matchKeyword(term.getCode(), kw) || matchKeyword(term.getAliases(), kw))
            .sorted(Comparator.comparing(term -> String.valueOf(term.getName()).toLowerCase(Locale.ROOT)))
            .toList();
        auditService.audit("READ", "modeling.glossary", "list");
        return ApiResponses.ok(list);
    }

    @PostMapping("/glossary/terms")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ModelingGlossaryTerm> createGlossaryTerm(
        @Valid @RequestBody ModelingGlossaryTerm request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDeptHeader
    ) {
        ModelingGlossaryTerm term = new ModelingGlossaryTerm();
        applyGlossaryUpsert(term, request, activeDeptHeader);
        ModelingGlossaryTerm saved = glossaryRepo.save(term);
        auditService.audit("CREATE", "modeling.glossary", saved.getId().toString());
        return ApiResponses.ok(saved);
    }

    @PutMapping("/glossary/terms/{id}")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ModelingGlossaryTerm> updateGlossaryTerm(
        @PathVariable UUID id,
        @Valid @RequestBody ModelingGlossaryTerm request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDeptHeader
    ) {
        ModelingGlossaryTerm term = glossaryRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("术语不存在"));
        applyGlossaryUpsert(term, request, activeDeptHeader);
        ModelingGlossaryTerm saved = glossaryRepo.save(term);
        auditService.audit("UPDATE", "modeling.glossary", id.toString());
        return ApiResponses.ok(saved);
    }

    @DeleteMapping("/glossary/terms/{id}")
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.OP_ADMIN + "')")
    public ApiResponse<Boolean> deleteGlossaryTerm(@PathVariable UUID id) {
        glossaryRepo.deleteById(id);
        auditService.audit("DELETE", "modeling.glossary", id.toString());
        return ApiResponses.ok(Boolean.TRUE);
    }

    @GetMapping("/templates")
    public ApiResponse<List<ModelingTemplate>> listTemplates() {
        List<ModelingTemplate> list = templateRepo
            .findAll()
            .stream()
            .sorted(Comparator.comparing(template -> String.valueOf(template.getName()).toLowerCase(Locale.ROOT)))
            .toList();
        auditService.audit("READ", "modeling.template", "list");
        return ApiResponses.ok(list);
    }

    @PostMapping("/templates")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ModelingTemplate> createTemplate(@Valid @RequestBody ModelingTemplate request) {
        ModelingTemplate template = new ModelingTemplate();
        applyTemplateUpsert(template, request);
        ModelingTemplate saved = templateRepo.save(template);
        auditService.audit("CREATE", "modeling.template", saved.getId().toString());
        return ApiResponses.ok(saved);
    }

    @PutMapping("/templates/{id}")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ModelingTemplate> updateTemplate(@PathVariable UUID id, @Valid @RequestBody ModelingTemplate request) {
        ModelingTemplate template = templateRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("模型模板不存在"));
        applyTemplateUpsert(template, request);
        ModelingTemplate saved = templateRepo.save(template);
        auditService.audit("UPDATE", "modeling.template", id.toString());
        return ApiResponses.ok(saved);
    }

    @DeleteMapping("/templates/{id}")
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.OP_ADMIN + "')")
    public ApiResponse<Boolean> deleteTemplate(@PathVariable UUID id) {
        templateRepo.deleteById(id);
        auditService.audit("DELETE", "modeling.template", id.toString());
        return ApiResponses.ok(Boolean.TRUE);
    }

    private void applyPlanUpsert(ModelingPlan plan, ModelingPlan request, String activeDeptHeader) {
        if (plan == null || request == null) return;
        plan.setName(StringUtils.trimToNull(request.getName()));
        plan.setDomain(StringUtils.trimToNull(request.getDomain()));
        plan.setScope(StringUtils.trimToNull(request.getScope()));
        plan.setStatus(StringUtils.trimToNull(request.getStatus()));
        plan.setVersion(StringUtils.trimToNull(request.getVersion()));
        plan.setVersionNotes(StringUtils.trimToNull(request.getVersionNotes()));
        plan.setOwner(StringUtils.trimToNull(request.getOwner()));
        plan.setTags(StringUtils.trimToNull(request.getTags()));
        plan.setContent(StringUtils.trimToNull(request.getContent()));

        String requestedOwnerDept = StringUtils.trimToNull(request.getOwnerDept());
        if (security.hasInstituteScope()) {
            plan.setOwnerDept(requestedOwnerDept);
            return;
        }
        String activeDept = security.resolveActiveDept(activeDeptHeader);
        if (!org.springframework.util.StringUtils.hasText(activeDept)) {
            throw new AccessDeniedException("当前账号未配置所属部门，无法执行该操作");
        }
        if (requestedOwnerDept != null && !DepartmentUtils.matches(requestedOwnerDept, activeDept)) {
            throw new AccessDeniedException("仅允许设置为当前登录部门的数据规划");
        }
        plan.setOwnerDept(activeDept.trim());
    }

    private void applyGlossaryUpsert(ModelingGlossaryTerm term, ModelingGlossaryTerm request, String activeDeptHeader) {
        if (term == null || request == null) return;
        term.setCode(StringUtils.trimToNull(request.getCode()));
        term.setName(StringUtils.trimToNull(request.getName()));
        term.setAliases(StringUtils.trimToNull(request.getAliases()));
        term.setDomain(StringUtils.trimToNull(request.getDomain()));
        term.setDefinition(StringUtils.trimToNull(request.getDefinition()));
        term.setStatus(StringUtils.trimToNull(request.getStatus()));
        term.setOwner(StringUtils.trimToNull(request.getOwner()));
        term.setTags(StringUtils.trimToNull(request.getTags()));

        String requestedOwnerDept = StringUtils.trimToNull(request.getOwnerDept());
        if (security.hasInstituteScope()) {
            term.setOwnerDept(requestedOwnerDept);
            return;
        }
        String activeDept = security.resolveActiveDept(activeDeptHeader);
        if (!org.springframework.util.StringUtils.hasText(activeDept)) {
            throw new AccessDeniedException("当前账号未配置所属部门，无法执行该操作");
        }
        if (requestedOwnerDept != null && !DepartmentUtils.matches(requestedOwnerDept, activeDept)) {
            throw new AccessDeniedException("仅允许设置为当前登录部门的术语");
        }
        term.setOwnerDept(activeDept.trim());
    }

    private void applyTemplateUpsert(ModelingTemplate template, ModelingTemplate request) {
        if (template == null || request == null) return;
        template.setName(StringUtils.trimToNull(request.getName()));
        template.setLayer(StringUtils.trimToNull(request.getLayer()));
        template.setStatus(StringUtils.trimToNull(request.getStatus()));
        template.setNamingRule(StringUtils.trimToNull(request.getNamingRule()));
        template.setFieldsTemplate(StringUtils.trimToNull(request.getFieldsTemplate()));
        template.setReviewChecklist(StringUtils.trimToNull(request.getReviewChecklist()));
    }

    private boolean isOwnerDeptVisible(String ownerDept, String activeDept, boolean instituteScope) {
        String trimmedOwner = StringUtils.trimToNull(ownerDept);
        if (trimmedOwner == null) return true;
        if (instituteScope) return true;
        if (organizationVisibilityService.isRoot(trimmedOwner)) return true;
        if (StringUtils.isBlank(activeDept)) return false;
        return DepartmentUtils.matches(trimmedOwner, activeDept);
    }

    private boolean matchKeyword(String value, String keyword) {
        if (!org.springframework.util.StringUtils.hasText(value) || !org.springframework.util.StringUtils.hasText(keyword)) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT).contains(keyword.trim().toLowerCase(Locale.ROOT));
    }
}

