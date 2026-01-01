package com.yuzhi.dts.platform.web.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import com.yuzhi.dts.platform.domain.modeling.DataStandard;
import com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTerm;
import com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTermReview;
import com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTermVersion;
import com.yuzhi.dts.platform.domain.modeling.ModelingPlan;
import com.yuzhi.dts.platform.domain.modeling.ModelingPlanReview;
import com.yuzhi.dts.platform.domain.modeling.ModelingPlanVersion;
import com.yuzhi.dts.platform.domain.modeling.ModelingTemplate;
import com.yuzhi.dts.platform.domain.modeling.ModelingTemplateVersion;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorDefinitionRepository;
import com.yuzhi.dts.platform.repository.modeling.DataStandardRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingGlossaryTermRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingGlossaryTermReviewRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingGlossaryTermVersionRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingPlanRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingPlanReviewRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingPlanVersionRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingTemplateRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingTemplateVersionRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.DepartmentUtils;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.DataStandardSecurity;
import com.yuzhi.dts.platform.service.security.OrganizationVisibilityService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
    private final ModelingPlanVersionRepository planVersionRepo;
    private final ModelingPlanReviewRepository planReviewRepo;
    private final ModelingGlossaryTermRepository glossaryRepo;
    private final ModelingGlossaryTermVersionRepository glossaryVersionRepo;
    private final ModelingGlossaryTermReviewRepository glossaryReviewRepo;
    private final ModelingTemplateRepository templateRepo;
    private final ModelingTemplateVersionRepository templateVersionRepo;
    private final AuditService auditService;
    private final DataStandardSecurity security;
    private final OrganizationVisibilityService organizationVisibilityService;
    private final ObjectMapper objectMapper;
    private final DataStandardRepository dataStandardRepository;
    private final GovIndicatorDefinitionRepository indicatorRepository;

    public ModelingAuxResource(
        ModelingPlanRepository planRepo,
        ModelingPlanVersionRepository planVersionRepo,
        ModelingPlanReviewRepository planReviewRepo,
        ModelingGlossaryTermRepository glossaryRepo,
        ModelingGlossaryTermVersionRepository glossaryVersionRepo,
        ModelingGlossaryTermReviewRepository glossaryReviewRepo,
        ModelingTemplateRepository templateRepo,
        ModelingTemplateVersionRepository templateVersionRepo,
        AuditService auditService,
        DataStandardSecurity security,
        OrganizationVisibilityService organizationVisibilityService,
        ObjectMapper objectMapper,
        DataStandardRepository dataStandardRepository,
        GovIndicatorDefinitionRepository indicatorRepository
    ) {
        this.planRepo = planRepo;
        this.planVersionRepo = planVersionRepo;
        this.planReviewRepo = planReviewRepo;
        this.glossaryRepo = glossaryRepo;
        this.glossaryVersionRepo = glossaryVersionRepo;
        this.glossaryReviewRepo = glossaryReviewRepo;
        this.templateRepo = templateRepo;
        this.templateVersionRepo = templateVersionRepo;
        this.auditService = auditService;
        this.security = security;
        this.organizationVisibilityService = organizationVisibilityService;
        this.objectMapper = objectMapper;
        this.dataStandardRepository = dataStandardRepository;
        this.indicatorRepository = indicatorRepository;
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
        ensurePlanDefaults(plan);
        ModelingPlan saved = planRepo.save(plan);
        upsertPlanVersionSnapshot(saved, saved.getVersionNotes(), null);
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
        ensurePlanDefaults(plan);
        ModelingPlan saved = planRepo.save(plan);
        upsertPlanVersionSnapshot(saved, saved.getVersionNotes(), null);
        auditService.audit("UPDATE", "modeling.plan", id.toString());
        return ApiResponses.ok(saved);
    }

    @GetMapping("/plans/{id}/versions")
    public ApiResponse<List<ModelingPlanVersion>> listPlanVersions(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDeptHeader
    ) {
        ModelingPlan plan = getReadablePlan(id, activeDeptHeader);
        List<ModelingPlanVersion> list = planVersionRepo.findByPlanOrderByCreatedDateDesc(plan);
        auditService.auditAction(
            "MODELING_PLAN_VERSION_VIEW",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of("summary", "查看数据规划版本", "planName", plan.getName())
        );
        return ApiResponses.ok(list);
    }

    public record ModelingPlanPublishRequest(String version, String changeSummary) {}

    @PostMapping("/plans/{id}/publish")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ModelingPlan> publishPlan(
        @PathVariable UUID id,
        @RequestBody(required = false) ModelingPlanPublishRequest body,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDeptHeader
    ) {
        ModelingPlan plan = getWritablePlan(id, activeDeptHeader);
        Map<String, Object> before = toPlanAuditView(plan);

        String version = StringUtils.trimToNull(body != null ? body.version() : null);
        if (version != null) {
            plan.setVersion(version);
        }
        ensurePlanDefaults(plan);
        plan.setStatus("PUBLISHED");
        if (StringUtils.isNotBlank(body != null ? body.changeSummary() : null)) {
            plan.setVersionNotes(StringUtils.trimToNull(body.changeSummary()));
        }
        ModelingPlan saved = planRepo.save(plan);
        upsertPlanVersionSnapshot(saved, StringUtils.trimToNull(body != null ? body.changeSummary() : null), "PUBLISHED");

        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("before", before);
        auditPayload.put("after", toPlanAuditView(saved));
        auditPayload.put("targetId", id.toString());
        auditPayload.put("targetName", saved.getName());
        auditPayload.put("operationType", "PUBLISH");
        auditPayload.put("summary", "发布数据规划：" + saved.getName());
        auditPayload.put("version", saved.getVersion());
        auditService.auditAction("MODELING_PLAN_PUBLISH", AuditStage.SUCCESS, id.toString(), auditPayload);
        return ApiResponses.ok(saved);
    }

    @GetMapping("/plans/{id}/reviews")
    public ApiResponse<List<ModelingPlanReview>> listPlanReviews(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDeptHeader
    ) {
        ModelingPlan plan = getReadablePlan(id, activeDeptHeader);
        List<ModelingPlanReview> list = planReviewRepo.findByPlanOrderByCreatedDateDesc(plan);
        auditService.auditAction("MODELING_PLAN_REVIEW_VIEW", AuditStage.SUCCESS, id.toString(), Map.of("summary", "查看数据规划评审记录"));
        return ApiResponses.ok(list);
    }

    public record ModelingPlanReviewSubmitRequest(String version, String notes) {}

    @PostMapping("/plans/{id}/reviews/submit")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ModelingPlanReview> submitPlanReview(
        @PathVariable UUID id,
        @RequestBody(required = false) ModelingPlanReviewSubmitRequest body,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDeptHeader
    ) {
        ModelingPlan plan = getWritablePlan(id, activeDeptHeader);
        String version = StringUtils.trimToNull(body != null ? body.version() : null);
        if (version == null) {
            version = StringUtils.trimToNull(plan.getVersion());
        }
        ModelingPlanReview review = new ModelingPlanReview();
        review.setPlan(plan);
        review.setVersion(version);
        review.setStatus("SUBMITTED");
        review.setReviewNotes(StringUtils.trimToNull(body != null ? body.notes() : null));
        ModelingPlanReview saved = planReviewRepo.save(review);
        auditService.auditAction(
            "MODELING_PLAN_REVIEW_SUBMIT",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of("summary", "提交数据规划评审：" + plan.getName(), "version", version)
        );
        return ApiResponses.ok(saved);
    }

    public record ModelingPlanReviewDecisionRequest(String notes) {}

    @PostMapping("/plans/{id}/reviews/{reviewId}/approve")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ModelingPlan> approvePlanReview(
        @PathVariable UUID id,
        @PathVariable UUID reviewId,
        @RequestBody(required = false) ModelingPlanReviewDecisionRequest body,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDeptHeader
    ) {
        ModelingPlan plan = getWritablePlan(id, activeDeptHeader);
        ModelingPlanReview review = planReviewRepo.findById(reviewId).orElseThrow(() -> new EntityNotFoundException("评审记录不存在"));
        if (review.getPlan() == null || !Objects.equals(review.getPlan().getId(), plan.getId())) {
            throw new AccessDeniedException("评审记录不属于当前数据规划");
        }
        String reviewer = SecurityUtils.getCurrentUserLogin().orElse("unknown");
        review.setReviewer(reviewer);
        review.setReviewedAt(Instant.now());
        review.setStatus("APPROVED");
        if (StringUtils.isNotBlank(body != null ? body.notes() : null)) {
            review.setReviewNotes(StringUtils.trimToNull(body.notes()));
        }
        planReviewRepo.save(review);

        Map<String, Object> before = toPlanAuditView(plan);
        plan.setStatus("PUBLISHED");
        ensurePlanDefaults(plan);
        ModelingPlan saved = planRepo.save(plan);
        upsertPlanVersionSnapshot(saved, StringUtils.trimToNull(body != null ? body.notes() : null), "PUBLISHED");

        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("before", before);
        auditPayload.put("after", toPlanAuditView(saved));
        auditPayload.put("targetId", id.toString());
        auditPayload.put("targetName", saved.getName());
        auditPayload.put("operationType", "REVIEW_APPROVE");
        auditPayload.put("summary", "通过数据规划评审并发布：" + saved.getName());
        auditPayload.put("reviewId", reviewId.toString());
        auditPayload.put("reviewer", reviewer);
        auditService.auditAction("MODELING_PLAN_REVIEW_DECIDE", AuditStage.SUCCESS, id.toString(), auditPayload);
        return ApiResponses.ok(saved);
    }

    @PostMapping("/plans/{id}/reviews/{reviewId}/reject")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ModelingPlanReview> rejectPlanReview(
        @PathVariable UUID id,
        @PathVariable UUID reviewId,
        @RequestBody(required = false) ModelingPlanReviewDecisionRequest body,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDeptHeader
    ) {
        ModelingPlan plan = getWritablePlan(id, activeDeptHeader);
        ModelingPlanReview review = planReviewRepo.findById(reviewId).orElseThrow(() -> new EntityNotFoundException("评审记录不存在"));
        if (review.getPlan() == null || !Objects.equals(review.getPlan().getId(), plan.getId())) {
            throw new AccessDeniedException("评审记录不属于当前数据规划");
        }
        String reviewer = SecurityUtils.getCurrentUserLogin().orElse("unknown");
        review.setReviewer(reviewer);
        review.setReviewedAt(Instant.now());
        review.setStatus("REJECTED");
        if (StringUtils.isNotBlank(body != null ? body.notes() : null)) {
            review.setReviewNotes(StringUtils.trimToNull(body.notes()));
        }
        ModelingPlanReview saved = planReviewRepo.save(review);
        auditService.auditAction(
            "MODELING_PLAN_REVIEW_DECIDE",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of("summary", "驳回数据规划评审：" + plan.getName(), "reviewId", reviewId.toString(), "reviewer", reviewer)
        );
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

    @GetMapping("/glossary/terms/{id}")
    public ApiResponse<ModelingGlossaryTerm> getGlossaryTerm(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDeptHeader
    ) {
        ModelingGlossaryTerm term = getReadableTerm(id, activeDeptHeader);
        auditService.audit("READ", "modeling.glossary", id.toString());
        return ApiResponses.ok(term);
    }

    @PostMapping("/glossary/terms")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ModelingGlossaryTerm> createGlossaryTerm(
        @Valid @RequestBody ModelingGlossaryTerm request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDeptHeader
    ) {
        ModelingGlossaryTerm term = new ModelingGlossaryTerm();
        applyGlossaryUpsert(term, request, activeDeptHeader);
        ensureGlossaryDefaults(term);
        ModelingGlossaryTerm saved = glossaryRepo.save(term);
        upsertGlossaryVersionSnapshot(saved, saved.getVersionNotes(), null);
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
        ensureGlossaryDefaults(term);
        ModelingGlossaryTerm saved = glossaryRepo.save(term);
        upsertGlossaryVersionSnapshot(saved, saved.getVersionNotes(), null);
        auditService.audit("UPDATE", "modeling.glossary", id.toString());
        return ApiResponses.ok(saved);
    }

    @GetMapping("/glossary/terms/{id}/versions")
    public ApiResponse<List<ModelingGlossaryTermVersion>> listGlossaryTermVersions(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDeptHeader
    ) {
        ModelingGlossaryTerm term = getReadableTerm(id, activeDeptHeader);
        List<ModelingGlossaryTermVersion> list = glossaryVersionRepo.findByTermOrderByCreatedDateDesc(term);
        auditService.auditAction("MODELING_GLOSSARY_VERSION_VIEW", AuditStage.SUCCESS, id.toString(), Map.of("summary", "查看术语版本"));
        return ApiResponses.ok(list);
    }

    public record ModelingGlossaryPublishRequest(String version, String changeSummary) {}

    @PostMapping("/glossary/terms/{id}/publish")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ModelingGlossaryTerm> publishGlossaryTerm(
        @PathVariable UUID id,
        @RequestBody(required = false) ModelingGlossaryPublishRequest body,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDeptHeader
    ) {
        ModelingGlossaryTerm term = getWritableTerm(id, activeDeptHeader);
        Map<String, Object> before = toGlossaryAuditView(term);
        String version = StringUtils.trimToNull(body != null ? body.version() : null);
        if (version != null) {
            term.setVersion(version);
        }
        ensureGlossaryDefaults(term);
        term.setStatus("ACTIVE");
        if (StringUtils.isNotBlank(body != null ? body.changeSummary() : null)) {
            term.setVersionNotes(StringUtils.trimToNull(body.changeSummary()));
        }
        ModelingGlossaryTerm saved = glossaryRepo.save(term);
        upsertGlossaryVersionSnapshot(saved, StringUtils.trimToNull(body != null ? body.changeSummary() : null), "ACTIVE");

        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("before", before);
        auditPayload.put("after", toGlossaryAuditView(saved));
        auditPayload.put("targetId", id.toString());
        auditPayload.put("targetName", saved.getName());
        auditPayload.put("operationType", "PUBLISH");
        auditPayload.put("summary", "发布术语：" + saved.getName());
        auditPayload.put("version", saved.getVersion());
        auditService.auditAction("MODELING_GLOSSARY_PUBLISH", AuditStage.SUCCESS, id.toString(), auditPayload);
        return ApiResponses.ok(saved);
    }

    @GetMapping("/glossary/terms/{id}/reviews")
    public ApiResponse<List<ModelingGlossaryTermReview>> listGlossaryTermReviews(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDeptHeader
    ) {
        ModelingGlossaryTerm term = getReadableTerm(id, activeDeptHeader);
        List<ModelingGlossaryTermReview> list = glossaryReviewRepo.findByTermOrderByCreatedDateDesc(term);
        auditService.auditAction("MODELING_GLOSSARY_REVIEW_VIEW", AuditStage.SUCCESS, id.toString(), Map.of("summary", "查看术语评审记录"));
        return ApiResponses.ok(list);
    }

    public record ModelingGlossaryReviewSubmitRequest(String version, String notes) {}

    @PostMapping("/glossary/terms/{id}/reviews/submit")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ModelingGlossaryTermReview> submitGlossaryTermReview(
        @PathVariable UUID id,
        @RequestBody(required = false) ModelingGlossaryReviewSubmitRequest body,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDeptHeader
    ) {
        ModelingGlossaryTerm term = getWritableTerm(id, activeDeptHeader);
        String version = StringUtils.trimToNull(body != null ? body.version() : null);
        if (version == null) {
            version = StringUtils.trimToNull(term.getVersion());
        }
        ModelingGlossaryTermReview review = new ModelingGlossaryTermReview();
        review.setTerm(term);
        review.setVersion(version);
        review.setStatus("SUBMITTED");
        review.setReviewNotes(StringUtils.trimToNull(body != null ? body.notes() : null));
        ModelingGlossaryTermReview saved = glossaryReviewRepo.save(review);
        auditService.auditAction(
            "MODELING_GLOSSARY_REVIEW_SUBMIT",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of("summary", "提交术语评审：" + term.getName(), "version", version)
        );
        return ApiResponses.ok(saved);
    }

    public record ModelingGlossaryReviewDecisionRequest(String notes) {}

    @PostMapping("/glossary/terms/{id}/reviews/{reviewId}/approve")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ModelingGlossaryTerm> approveGlossaryTermReview(
        @PathVariable UUID id,
        @PathVariable UUID reviewId,
        @RequestBody(required = false) ModelingGlossaryReviewDecisionRequest body,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDeptHeader
    ) {
        ModelingGlossaryTerm term = getWritableTerm(id, activeDeptHeader);
        ModelingGlossaryTermReview review = glossaryReviewRepo.findById(reviewId).orElseThrow(() -> new EntityNotFoundException("评审记录不存在"));
        if (review.getTerm() == null || !Objects.equals(review.getTerm().getId(), term.getId())) {
            throw new AccessDeniedException("评审记录不属于当前术语");
        }
        String reviewer = SecurityUtils.getCurrentUserLogin().orElse("unknown");
        review.setReviewer(reviewer);
        review.setReviewedAt(Instant.now());
        review.setStatus("APPROVED");
        if (StringUtils.isNotBlank(body != null ? body.notes() : null)) {
            review.setReviewNotes(StringUtils.trimToNull(body.notes()));
        }
        glossaryReviewRepo.save(review);

        Map<String, Object> before = toGlossaryAuditView(term);
        term.setStatus("ACTIVE");
        ensureGlossaryDefaults(term);
        ModelingGlossaryTerm saved = glossaryRepo.save(term);
        upsertGlossaryVersionSnapshot(saved, StringUtils.trimToNull(body != null ? body.notes() : null), "ACTIVE");

        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("before", before);
        auditPayload.put("after", toGlossaryAuditView(saved));
        auditPayload.put("targetId", id.toString());
        auditPayload.put("targetName", saved.getName());
        auditPayload.put("operationType", "REVIEW_APPROVE");
        auditPayload.put("summary", "通过术语评审并发布：" + saved.getName());
        auditPayload.put("reviewId", reviewId.toString());
        auditPayload.put("reviewer", reviewer);
        auditService.auditAction("MODELING_GLOSSARY_REVIEW_DECIDE", AuditStage.SUCCESS, id.toString(), auditPayload);
        return ApiResponses.ok(saved);
    }

    @PostMapping("/glossary/terms/{id}/reviews/{reviewId}/reject")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ModelingGlossaryTermReview> rejectGlossaryTermReview(
        @PathVariable UUID id,
        @PathVariable UUID reviewId,
        @RequestBody(required = false) ModelingGlossaryReviewDecisionRequest body,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDeptHeader
    ) {
        ModelingGlossaryTerm term = getWritableTerm(id, activeDeptHeader);
        ModelingGlossaryTermReview review = glossaryReviewRepo.findById(reviewId).orElseThrow(() -> new EntityNotFoundException("评审记录不存在"));
        if (review.getTerm() == null || !Objects.equals(review.getTerm().getId(), term.getId())) {
            throw new AccessDeniedException("评审记录不属于当前术语");
        }
        String reviewer = SecurityUtils.getCurrentUserLogin().orElse("unknown");
        review.setReviewer(reviewer);
        review.setReviewedAt(Instant.now());
        review.setStatus("REJECTED");
        if (StringUtils.isNotBlank(body != null ? body.notes() : null)) {
            review.setReviewNotes(StringUtils.trimToNull(body.notes()));
        }
        ModelingGlossaryTermReview saved = glossaryReviewRepo.save(review);
        auditService.auditAction(
            "MODELING_GLOSSARY_REVIEW_DECIDE",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of("summary", "驳回术语评审：" + term.getName(), "reviewId", reviewId.toString(), "reviewer", reviewer)
        );
        return ApiResponses.ok(saved);
    }

    @GetMapping("/glossary/terms/{id}/references")
    public ApiResponse<Map<String, Object>> getGlossaryTermReferences(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDeptHeader
    ) {
        ModelingGlossaryTerm term = getReadableTerm(id, activeDeptHeader);
        String needle = StringUtils.trimToNull(term.getCode());
        if (needle == null) {
            needle = StringUtils.trimToNull(term.getName());
        }
        if (needle == null) {
            needle = id.toString();
        }
        String kw = needle.toLowerCase(Locale.ROOT);

        List<Map<String, Object>> standards = dataStandardRepository
            .findAll()
            .stream()
            .filter(s -> matchTermRef(s, kw))
            .limit(50)
            .map(s -> Map.of("type", "DATA_STANDARD", "id", s.getId().toString(), "code", s.getCode(), "name", s.getName()))
            .toList();

        List<Map<String, Object>> indicators = indicatorRepository
            .findAll()
            .stream()
            .filter(i -> matchTermRef(i, kw))
            .limit(50)
            .map(i -> Map.of("type", "INDICATOR", "id", i.getId().toString(), "code", i.getCode(), "name", i.getName()))
            .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("termId", id.toString());
        payload.put("needle", needle);
        payload.put("standardCount", standards.size());
        payload.put("indicatorCount", indicators.size());
        payload.put("standards", standards);
        payload.put("indicators", indicators);
        auditService.auditAction("MODELING_GLOSSARY_REFERENCE_VIEW", AuditStage.SUCCESS, id.toString(), Map.of("summary", "查看术语引用关系"));
        return ApiResponses.ok(payload);
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

    @GetMapping("/templates/{id}")
    public ApiResponse<ModelingTemplate> getTemplate(@PathVariable UUID id) {
        ModelingTemplate template = templateRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("模型模板不存在"));
        auditService.audit("READ", "modeling.template", id.toString());
        return ApiResponses.ok(template);
    }

    @GetMapping("/templates/{id}/versions")
    public ApiResponse<List<ModelingTemplateVersion>> listTemplateVersions(@PathVariable UUID id) {
        ModelingTemplate template = templateRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("模型模板不存在"));
        List<ModelingTemplateVersion> list = templateVersionRepo.findByTemplateOrderByCreatedDateDesc(template);
        auditService.auditAction("MODELING_TEMPLATE_VERSION_VIEW", AuditStage.SUCCESS, id.toString(), Map.of("summary", "查看模型模板版本"));
        return ApiResponses.ok(list);
    }

    public record ModelingTemplatePublishRequest(String version, String changeSummary) {}

    @PostMapping("/templates/{id}/publish")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ModelingTemplate> publishTemplate(@PathVariable UUID id, @RequestBody(required = false) ModelingTemplatePublishRequest body) {
        ModelingTemplate template = templateRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("模型模板不存在"));
        Map<String, Object> before = toTemplateAuditView(template);
        String version = StringUtils.trimToNull(body != null ? body.version() : null);
        if (version != null) {
            template.setVersion(version);
        }
        ensureTemplateDefaults(template);
        template.setStatus("ACTIVE");
        if (StringUtils.isNotBlank(body != null ? body.changeSummary() : null)) {
            template.setVersionNotes(StringUtils.trimToNull(body.changeSummary()));
        }
        ModelingTemplate saved = templateRepo.save(template);
        upsertTemplateVersionSnapshot(saved, StringUtils.trimToNull(body != null ? body.changeSummary() : null), "ACTIVE");
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("before", before);
        auditPayload.put("after", toTemplateAuditView(saved));
        auditPayload.put("targetId", id.toString());
        auditPayload.put("targetName", saved.getName());
        auditPayload.put("operationType", "PUBLISH");
        auditPayload.put("summary", "发布模型模板：" + saved.getName());
        auditPayload.put("version", saved.getVersion());
        auditService.auditAction("MODELING_TEMPLATE_PUBLISH", AuditStage.SUCCESS, id.toString(), auditPayload);
        return ApiResponses.ok(saved);
    }

    @PostMapping("/templates")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ModelingTemplate> createTemplate(@Valid @RequestBody ModelingTemplate request) {
        ModelingTemplate template = new ModelingTemplate();
        applyTemplateUpsert(template, request);
        ensureTemplateDefaults(template);
        ModelingTemplate saved = templateRepo.save(template);
        upsertTemplateVersionSnapshot(saved, saved.getVersionNotes(), null);
        auditService.audit("CREATE", "modeling.template", saved.getId().toString());
        return ApiResponses.ok(saved);
    }

    @PutMapping("/templates/{id}")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ModelingTemplate> updateTemplate(@PathVariable UUID id, @Valid @RequestBody ModelingTemplate request) {
        ModelingTemplate template = templateRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("模型模板不存在"));
        applyTemplateUpsert(template, request);
        ensureTemplateDefaults(template);
        ModelingTemplate saved = templateRepo.save(template);
        upsertTemplateVersionSnapshot(saved, saved.getVersionNotes(), null);
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
        term.setVersion(StringUtils.trimToNull(request.getVersion()));
        term.setVersionNotes(StringUtils.trimToNull(request.getVersionNotes()));
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
        template.setVersion(StringUtils.trimToNull(request.getVersion()));
        template.setVersionNotes(StringUtils.trimToNull(request.getVersionNotes()));
        template.setNamingRule(StringUtils.trimToNull(request.getNamingRule()));
        template.setFieldsTemplate(StringUtils.trimToNull(request.getFieldsTemplate()));
        template.setReviewChecklist(StringUtils.trimToNull(request.getReviewChecklist()));
    }

    private ModelingPlan getReadablePlan(UUID id, String activeDeptHeader) {
        String activeDept = security.resolveActiveDept(activeDeptHeader);
        boolean instituteScope = security.hasInstituteScope();
        ModelingPlan plan = planRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("数据规划不存在"));
        if (!isOwnerDeptVisible(plan.getOwnerDept(), activeDept, instituteScope)) {
            throw new AccessDeniedException("当前账号无权访问该数据规划");
        }
        return plan;
    }

    private ModelingPlan getWritablePlan(UUID id, String activeDeptHeader) {
        ModelingPlan plan = getReadablePlan(id, activeDeptHeader);
        if (security.hasInstituteScope()) {
            return plan;
        }
        String activeDept = security.resolveActiveDept(activeDeptHeader);
        if (!org.springframework.util.StringUtils.hasText(activeDept)) {
            throw new AccessDeniedException("当前账号未配置所属部门，无法执行该操作");
        }
        String ownerDept = StringUtils.trimToNull(plan.getOwnerDept());
        if (ownerDept != null && !DepartmentUtils.matches(ownerDept, activeDept)) {
            throw new AccessDeniedException("仅允许维护当前登录部门的数据规划");
        }
        return plan;
    }

    private void ensurePlanDefaults(ModelingPlan plan) {
        if (plan == null) return;
        if (!org.springframework.util.StringUtils.hasText(plan.getStatus())) {
            plan.setStatus("DRAFT");
        } else {
            plan.setStatus(normalizePlanStatus(plan.getStatus()));
        }
        if (!org.springframework.util.StringUtils.hasText(plan.getVersion())) {
            plan.setVersion("v1");
        } else {
            plan.setVersion(plan.getVersion().trim());
        }
    }

    private String normalizePlanStatus(String status) {
        if (!org.springframework.util.StringUtils.hasText(status)) {
            return "DRAFT";
        }
        String s = status.trim().toUpperCase(Locale.ROOT);
        if ("PUBLISHED".equals(s) || "ARCHIVED".equals(s) || "DRAFT".equals(s)) {
            return s;
        }
        return "DRAFT";
    }

    private void upsertPlanVersionSnapshot(ModelingPlan plan, String changeSummary, String statusOverride) {
        if (plan == null || !org.springframework.util.StringUtils.hasText(plan.getVersion())) {
            return;
        }
        ModelingPlanVersion snapshot = planVersionRepo
            .findByPlanAndVersion(plan, plan.getVersion())
            .orElseGet(ModelingPlanVersion::new);
        snapshot.setPlan(plan);
        snapshot.setVersion(plan.getVersion());
        String effectiveStatus = org.springframework.util.StringUtils.hasText(statusOverride)
            ? normalizePlanStatus(statusOverride)
            : normalizePlanStatus(plan.getStatus());
        snapshot.setStatus(effectiveStatus);
        snapshot.setChangeSummary(StringUtils.trimToNull(changeSummary));
        if (org.springframework.util.StringUtils.hasText(statusOverride)) {
            snapshot.setReleasedAt("PUBLISHED".equals(effectiveStatus) ? Instant.now() : null);
        }
        snapshot.setSnapshotJson(serializePlanSnapshot(plan));
        planVersionRepo.save(snapshot);
    }

    private String serializePlanSnapshot(ModelingPlan plan) {
        try {
            return objectMapper.writeValueAsString(toPlanAuditView(plan));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize plan snapshot", e);
        }
    }

    private Map<String, Object> toPlanAuditView(ModelingPlan plan) {
        if (plan == null) {
            return Map.of();
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", plan.getId());
        view.put("name", plan.getName());
        view.put("domain", plan.getDomain());
        view.put("scope", plan.getScope());
        view.put("status", plan.getStatus());
        view.put("version", plan.getVersion());
        view.put("versionNotes", plan.getVersionNotes());
        view.put("owner", plan.getOwner());
        view.put("ownerDept", plan.getOwnerDept());
        view.put("tags", plan.getTags());
        return view;
    }

    private ModelingGlossaryTerm getReadableTerm(UUID id, String activeDeptHeader) {
        String activeDept = security.resolveActiveDept(activeDeptHeader);
        boolean instituteScope = security.hasInstituteScope();
        ModelingGlossaryTerm term = glossaryRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("术语不存在"));
        if (!isOwnerDeptVisible(term.getOwnerDept(), activeDept, instituteScope)) {
            throw new AccessDeniedException("当前账号无权访问该术语");
        }
        return term;
    }

    private ModelingGlossaryTerm getWritableTerm(UUID id, String activeDeptHeader) {
        ModelingGlossaryTerm term = getReadableTerm(id, activeDeptHeader);
        if (security.hasInstituteScope()) {
            return term;
        }
        String activeDept = security.resolveActiveDept(activeDeptHeader);
        if (!org.springframework.util.StringUtils.hasText(activeDept)) {
            throw new AccessDeniedException("当前账号未配置所属部门，无法执行该操作");
        }
        String ownerDept = StringUtils.trimToNull(term.getOwnerDept());
        if (ownerDept != null && !DepartmentUtils.matches(ownerDept, activeDept)) {
            throw new AccessDeniedException("仅允许维护当前登录部门的术语");
        }
        return term;
    }

    private void ensureGlossaryDefaults(ModelingGlossaryTerm term) {
        if (term == null) return;
        if (!org.springframework.util.StringUtils.hasText(term.getStatus())) {
            term.setStatus("ACTIVE");
        } else {
            term.setStatus(normalizeGlossaryStatus(term.getStatus()));
        }
        if (!org.springframework.util.StringUtils.hasText(term.getVersion())) {
            term.setVersion("v1");
        } else {
            term.setVersion(term.getVersion().trim());
        }
    }

    private String normalizeGlossaryStatus(String status) {
        if (!org.springframework.util.StringUtils.hasText(status)) {
            return "ACTIVE";
        }
        String s = status.trim().toUpperCase(Locale.ROOT);
        if ("ACTIVE".equals(s) || "ARCHIVED".equals(s) || "DRAFT".equals(s)) {
            return s;
        }
        return "ACTIVE";
    }

    private void upsertGlossaryVersionSnapshot(ModelingGlossaryTerm term, String changeSummary, String statusOverride) {
        if (term == null || !org.springframework.util.StringUtils.hasText(term.getVersion())) {
            return;
        }
        ModelingGlossaryTermVersion snapshot = glossaryVersionRepo
            .findByTermAndVersion(term, term.getVersion())
            .orElseGet(ModelingGlossaryTermVersion::new);
        snapshot.setTerm(term);
        snapshot.setVersion(term.getVersion());
        String effectiveStatus = org.springframework.util.StringUtils.hasText(statusOverride)
            ? normalizeGlossaryStatus(statusOverride)
            : normalizeGlossaryStatus(term.getStatus());
        snapshot.setStatus(effectiveStatus);
        snapshot.setChangeSummary(StringUtils.trimToNull(changeSummary));
        if (org.springframework.util.StringUtils.hasText(statusOverride)) {
            snapshot.setReleasedAt("ACTIVE".equals(effectiveStatus) ? Instant.now() : null);
        }
        snapshot.setSnapshotJson(serializeGlossarySnapshot(term));
        glossaryVersionRepo.save(snapshot);
    }

    private String serializeGlossarySnapshot(ModelingGlossaryTerm term) {
        try {
            return objectMapper.writeValueAsString(toGlossaryAuditView(term));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize glossary snapshot", e);
        }
    }

    private Map<String, Object> toGlossaryAuditView(ModelingGlossaryTerm term) {
        if (term == null) {
            return Map.of();
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", term.getId());
        view.put("code", term.getCode());
        view.put("name", term.getName());
        view.put("aliases", term.getAliases());
        view.put("domain", term.getDomain());
        view.put("definition", term.getDefinition());
        view.put("status", term.getStatus());
        view.put("version", term.getVersion());
        view.put("versionNotes", term.getVersionNotes());
        view.put("owner", term.getOwner());
        view.put("ownerDept", term.getOwnerDept());
        view.put("tags", term.getTags());
        return view;
    }

    private boolean matchTermRef(DataStandard standard, String keyword) {
        if (standard == null) return false;
        return (
            matchKeyword(standard.getName(), keyword) ||
            matchKeyword(standard.getCode(), keyword) ||
            matchKeyword(standard.getDescription(), keyword) ||
            matchKeyword(standard.getTags(), keyword)
        );
    }

    private boolean matchTermRef(GovIndicatorDefinition indicator, String keyword) {
        if (indicator == null) return false;
        return (
            matchKeyword(indicator.getName(), keyword) ||
            matchKeyword(indicator.getCode(), keyword) ||
            matchKeyword(indicator.getDefinition(), keyword) ||
            matchKeyword(indicator.getExpressionSql(), keyword) ||
            matchKeyword(indicator.getTags(), keyword)
        );
    }

    private void ensureTemplateDefaults(ModelingTemplate template) {
        if (template == null) return;
        if (!org.springframework.util.StringUtils.hasText(template.getStatus())) {
            template.setStatus("ACTIVE");
        } else {
            template.setStatus(normalizeTemplateStatus(template.getStatus()));
        }
        if (!org.springframework.util.StringUtils.hasText(template.getVersion())) {
            template.setVersion("v1");
        } else {
            template.setVersion(template.getVersion().trim());
        }
    }

    private String normalizeTemplateStatus(String status) {
        if (!org.springframework.util.StringUtils.hasText(status)) {
            return "ACTIVE";
        }
        String s = status.trim().toUpperCase(Locale.ROOT);
        if ("ACTIVE".equals(s) || "ARCHIVED".equals(s) || "DRAFT".equals(s)) {
            return s;
        }
        return "ACTIVE";
    }

    private void upsertTemplateVersionSnapshot(ModelingTemplate template, String changeSummary, String statusOverride) {
        if (template == null || !org.springframework.util.StringUtils.hasText(template.getVersion())) {
            return;
        }
        ModelingTemplateVersion snapshot = templateVersionRepo
            .findByTemplateAndVersion(template, template.getVersion())
            .orElseGet(ModelingTemplateVersion::new);
        snapshot.setTemplate(template);
        snapshot.setVersion(template.getVersion());
        String effectiveStatus = org.springframework.util.StringUtils.hasText(statusOverride)
            ? normalizeTemplateStatus(statusOverride)
            : normalizeTemplateStatus(template.getStatus());
        snapshot.setStatus(effectiveStatus);
        snapshot.setChangeSummary(StringUtils.trimToNull(changeSummary));
        if (org.springframework.util.StringUtils.hasText(statusOverride)) {
            snapshot.setReleasedAt("ACTIVE".equals(effectiveStatus) ? Instant.now() : null);
        }
        snapshot.setSnapshotJson(serializeTemplateSnapshot(template));
        templateVersionRepo.save(snapshot);
    }

    private String serializeTemplateSnapshot(ModelingTemplate template) {
        try {
            return objectMapper.writeValueAsString(toTemplateAuditView(template));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize template snapshot", e);
        }
    }

    private Map<String, Object> toTemplateAuditView(ModelingTemplate template) {
        if (template == null) {
            return Map.of();
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", template.getId());
        view.put("name", template.getName());
        view.put("layer", template.getLayer());
        view.put("status", template.getStatus());
        view.put("version", template.getVersion());
        view.put("versionNotes", template.getVersionNotes());
        view.put("namingRule", template.getNamingRule());
        view.put("fieldsTemplate", template.getFieldsTemplate());
        view.put("reviewChecklist", template.getReviewChecklist());
        return view;
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
