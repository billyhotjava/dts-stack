package com.yuzhi.dts.platform.web.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema;
import com.yuzhi.dts.platform.domain.modeling.DataStandard;
import com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTerm;
import com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTermReview;
import com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTermVersion;
import com.yuzhi.dts.platform.domain.modeling.ModelingTemplate;
import com.yuzhi.dts.platform.domain.modeling.ModelingTemplateVersion;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorDefinitionRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.repository.modeling.DataStandardRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingGlossaryTermRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingGlossaryTermReviewRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingGlossaryTermVersionRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingTemplateRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingTemplateVersionRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.DepartmentUtils;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetIdentity;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetKey;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetType;
import com.yuzhi.dts.platform.service.catalog.CodeAssetGrantWriter;
import com.yuzhi.dts.platform.service.catalog.CodeAssetLifecycleMapper;
import com.yuzhi.dts.platform.service.modeling.ModelingAssetReferenceService;
import com.yuzhi.dts.platform.service.modeling.DataStandardSecurity;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import com.yuzhi.dts.platform.service.security.OrganizationVisibilityService;
import com.yuzhi.dts.platform.web.rest.errors.BadRequestAlertException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
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
    private static final Pattern STD_CODE_PATTERN = Pattern.compile("(?i)(?:\\bSTD\\b|标准)\\s*[:：]\\s*([A-Za-z0-9_\\-\\.]+)");

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
    private final CatalogTableSchemaRepository catalogTableRepo;
    private final CatalogColumnSchemaRepository catalogColumnRepo;
    private final AccessChecker catalogAccessChecker;
    private final ModelingAssetReferenceService referenceService;
    private final CodeAssetGrantWriter codeAssetGrantWriter;

    public ModelingAuxResource(
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
        GovIndicatorDefinitionRepository indicatorRepository,
        CatalogTableSchemaRepository catalogTableRepo,
        CatalogColumnSchemaRepository catalogColumnRepo,
        AccessChecker catalogAccessChecker,
        ModelingAssetReferenceService referenceService,
        CodeAssetGrantWriter codeAssetGrantWriter
    ) {
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
        this.catalogTableRepo = catalogTableRepo;
        this.catalogColumnRepo = catalogColumnRepo;
        this.catalogAccessChecker = catalogAccessChecker;
        this.referenceService = referenceService;
        this.codeAssetGrantWriter = codeAssetGrantWriter;
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
        auditService.auditAction("MODELING_GLOSSARY_READ", AuditStage.SUCCESS, "list", null);
        return ApiResponses.ok(list);
    }

    @GetMapping("/glossary/terms/{id}")
    public ApiResponse<ModelingGlossaryTerm> getGlossaryTerm(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDeptHeader
    ) {
        ModelingGlossaryTerm term = getReadableTerm(id, activeDeptHeader);
        auditService.auditAction("MODELING_GLOSSARY_READ", AuditStage.SUCCESS, id.toString(), null);
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
        syncGlossaryCodeAssetGrant(saved, activeDeptHeader);
        auditService.auditAction("MODELING_GLOSSARY_CREATE", AuditStage.SUCCESS, saved.getId().toString(), null);
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
        syncGlossaryCodeAssetGrant(saved, activeDeptHeader);
        auditService.auditAction("MODELING_GLOSSARY_UPDATE", AuditStage.SUCCESS, id.toString(), null);
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
        syncGlossaryCodeAssetGrant(saved, activeDeptHeader);

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
        syncGlossaryCodeAssetGrant(saved, activeDeptHeader);

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
        Map<String, Object> payload = referenceService.glossaryReferences(term);
        int impactCount = referenceService.countReferences(payload);
        auditService.auditAction(
            "MODELING_GLOSSARY_REFERENCE_VIEW",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of("summary", "查看术语引用关系", "impactCount", impactCount)
        );
        return ApiResponses.ok(payload);
    }

    @DeleteMapping("/glossary/terms/{id}")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<Boolean> deleteGlossaryTerm(@PathVariable UUID id) {
        ModelingGlossaryTerm term = glossaryRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("术语不存在"));
        Map<String, Object> payload = referenceService.glossaryReferences(term);
        int impactCount = referenceService.countReferences(payload);
        if (impactCount > 0) {
            String summary = referenceService.summarizeReferences(payload, 5);
            throw new org.springframework.web.server.ResponseStatusException(
                HttpStatus.CONFLICT,
                "存在引用依赖，无法删除（影响对象 " + impactCount + " 个）" + (summary.isBlank() ? "" : "：" + summary)
            );
        }
        glossaryReviewRepo.deleteByTerm(term);
        glossaryVersionRepo.deleteByTerm(term);
        glossaryRepo.deleteById(id);
        auditService.auditAction(
            "MODELING_GLOSSARY_DELETE",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of("summary", "删除术语", "impactCount", impactCount)
        );
        return ApiResponses.ok(Boolean.TRUE);
    }

    @GetMapping("/templates")
    public ApiResponse<List<ModelingTemplate>> listTemplates() {
        List<ModelingTemplate> list = templateRepo
            .findAll()
            .stream()
            .sorted(Comparator.comparing(template -> String.valueOf(template.getName()).toLowerCase(Locale.ROOT)))
            .toList();
        auditService.auditAction("MODELING_TEMPLATE_READ", AuditStage.SUCCESS, "list", null);
        return ApiResponses.ok(list);
    }

    /**
     * 获取所有模型分层类型（从标准模板中提取去重）
     * 返回格式：[{layer, name, description}]
     */
    @GetMapping("/templates/layers")
    public ApiResponse<List<Map<String, Object>>> listTemplateLayers() {
        List<ModelingTemplate> templates = templateRepo.findAll();
        Set<String> seenLayers = new LinkedHashSet<>();
        List<Map<String, Object>> result = new ArrayList<>();

        // 按 layer 分组，取第一个模板的名称作为描述
        for (ModelingTemplate template : templates) {
            String layer = StringUtils.trimToNull(template.getLayer());
            if (layer == null || seenLayers.contains(layer.toUpperCase(Locale.ROOT))) {
                continue;
            }
            seenLayers.add(layer.toUpperCase(Locale.ROOT));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("layer", layer.toUpperCase(Locale.ROOT));
            row.put("name", template.getName());
            row.put("description", getLayerDescription(layer));
            result.add(row);
        }

        // 如果没有模板数据，返回默认分层
        if (result.isEmpty()) {
            result.add(Map.of("layer", "ODS", "name", "ODS", "description", "操作数据层（原始数据）"));
            result.add(Map.of("layer", "STG", "name", "STG", "description", "结构化层（仅清洗+类型转换，不含业务派生）"));
            result.add(Map.of("layer", "DWD", "name", "DWD", "description", "明细数据层（业务语义+维度打标）"));
            result.add(Map.of("layer", "DWS", "name", "DWS", "description", "汇总数据层（轻度聚合）"));
            result.add(Map.of("layer", "ADS", "name", "ADS", "description", "应用数据层（报表数据）"));
        } else {
            // 若已有模板但缺少 STG（常见：标准模板库未及时更新），补齐 STG 让前端下拉可选
            if (!seenLayers.contains("STG")) {
                result.add(Map.of(
                    "layer", "STG",
                    "name", "STG",
                    "description", "结构化层（仅清洗+类型转换，不含业务派生）"
                ));
            }
        }

        auditService.auditAction("MODELING_TEMPLATE_LAYERS_READ", AuditStage.SUCCESS, "list", null);
        return ApiResponses.ok(result);
    }

    private String getLayerDescription(String layer) {
        if (layer == null) return "";
        return switch (layer.toUpperCase(Locale.ROOT)) {
            case "ODS" -> "操作数据层（原始数据）";
            case "STG" -> "结构化层（仅清洗+类型转换，不含业务派生）";
            case "DWD" -> "明细数据层（业务语义+维度打标）";
            case "DWS" -> "汇总数据层（轻度聚合）";
            case "ADS" -> "应用数据层（报表数据）";
            default -> "";
        };
    }

    @GetMapping("/templates/{id}")
    public ApiResponse<ModelingTemplate> getTemplate(@PathVariable UUID id) {
        ModelingTemplate template = templateRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("模型模板不存在"));
        auditService.auditAction("MODELING_TEMPLATE_READ", AuditStage.SUCCESS, id.toString(), null);
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
        auditService.auditAction("MODELING_TEMPLATE_CREATE", AuditStage.SUCCESS, saved.getId().toString(), null);
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
        auditService.auditAction("MODELING_TEMPLATE_UPDATE", AuditStage.SUCCESS, id.toString(), null);
        return ApiResponses.ok(saved);
    }

    @DeleteMapping("/templates/{id}")
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.OP_ADMIN + "')")
    public ApiResponse<Boolean> deleteTemplate(@PathVariable UUID id) {
        ModelingTemplate template = templateRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("模型模板不存在"));
        Map<String, Object> payload = referenceService.templateReferences(template);
        int impactCount = referenceService.countReferences(payload);
        if (impactCount > 0) {
            String summary = referenceService.summarizeReferences(payload, 5);
            throw new org.springframework.web.server.ResponseStatusException(
                HttpStatus.CONFLICT,
                "存在引用依赖，无法删除（影响对象 " + impactCount + " 个）" + (summary.isBlank() ? "" : "：" + summary)
            );
        }
        templateRepo.delete(template);
        auditService.auditAction(
            "MODELING_TEMPLATE_DELETE",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of("summary", "删除模型模板", "impactCount", impactCount)
        );
        return ApiResponses.ok(Boolean.TRUE);
    }

    @GetMapping("/templates/{id}/references")
    public ApiResponse<Map<String, Object>> getTemplateReferences(@PathVariable UUID id) {
        ModelingTemplate template = templateRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("模型模板不存在"));
        Map<String, Object> payload = referenceService.templateReferences(template);
        int impactCount = referenceService.countReferences(payload);
        auditService.auditAction(
            "MODELING_TEMPLATE_REFERENCE_VIEW",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of("summary", "查看模型模板引用关系", "impactCount", impactCount)
        );
        return ApiResponses.ok(payload);
    }

    public record TemplateFieldSpec(String name, String dataType, Boolean nullable, String standardCode, String comment) {}

    public record TemplateValidationIssue(
        String severity,
        String code,
        String columnName,
        String message,
        String expected,
        String actual
    ) {}

    @GetMapping("/templates/{id}/validate")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> validateTemplateAgainstTable(
        @PathVariable UUID id,
        @RequestParam UUID tableId,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDeptHeader
    ) {
        ModelingTemplate template = templateRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("模型模板不存在"));
        CatalogTableSchema table = catalogTableRepo.findById(tableId).orElseThrow(() -> new EntityNotFoundException("数据表不存在"));
        CatalogDataset dataset = table.getDataset();

        String activeDept = security.resolveActiveDept(activeDeptHeader);
        if (dataset != null) {
            if (!catalogAccessChecker.canRead(dataset) || !catalogAccessChecker.departmentAllowed(dataset, activeDept)) {
                throw new AccessDeniedException("当前账号无权访问该数据表");
            }
        }

        List<CatalogColumnSchema> columns = catalogColumnRepo.findByTable(table);
        Map<String, CatalogColumnSchema> byName = new LinkedHashMap<>();
        for (CatalogColumnSchema c : columns) {
            if (c == null) continue;
            String name = org.springframework.util.StringUtils.hasText(c.getName()) ? c.getName().trim().toLowerCase(Locale.ROOT) : null;
            if (name != null && !byName.containsKey(name)) {
                byName.put(name, c);
            }
        }

        Map<UUID, DataStandard> standardsById = loadStandardsById(columns);
        List<TemplateFieldSpec> specs = parseTemplateFieldSpecs(template.getFieldsTemplate());

        int requiredFields = specs.size();
        int missingFields = 0;
        int typeMismatches = 0;
        int nullableMismatches = 0;
        int standardMismatches = 0;
        int unmappedStandards = 0;

        List<TemplateValidationIssue> issues = new ArrayList<>();

        // Table naming rule (regex support)
        String namingRule = StringUtils.trimToNull(template.getNamingRule());
        Pattern namingPattern = compileNamingPattern(namingRule);
        if (namingPattern != null) {
            String tableName = StringUtils.trimToNull(table.getName());
            if (tableName != null && !namingPattern.matcher(tableName).matches()) {
                issues.add(new TemplateValidationIssue(
                    "WARN",
                    "TABLE_NAME_RULE",
                    tableName,
                    "表名不符合模板命名规则",
                    namingRule,
                    tableName
                ));
            }
        }

        for (TemplateFieldSpec spec : specs) {
            if (spec == null || !org.springframework.util.StringUtils.hasText(spec.name())) {
                continue;
            }
            String expectedName = spec.name().trim();
            CatalogColumnSchema col = byName.get(expectedName.toLowerCase(Locale.ROOT));
            if (col == null) {
                missingFields++;
                issues.add(new TemplateValidationIssue(
                    "ERROR",
                    "MISSING_FIELD",
                    expectedName,
                    "缺少必备字段",
                    expectedName,
                    null
                ));
                continue;
            }

            String actualType = StringUtils.trimToNull(col.getDataType());
            String expectedType = StringUtils.trimToNull(spec.dataType());
            if (expectedType != null && actualType != null && !isTypeCompatible(expectedType, actualType)) {
                typeMismatches++;
                issues.add(new TemplateValidationIssue(
                    "ERROR",
                    "TYPE_MISMATCH",
                    col.getName(),
                    "字段类型不符合模板定义",
                    expectedType,
                    actualType
                ));
            }

            Boolean expectedNullable = spec.nullable();
            Boolean actualNullable = col.getNullable();
            if (expectedNullable != null) {
                boolean exp = Boolean.TRUE.equals(expectedNullable);
                boolean act = actualNullable == null || Boolean.TRUE.equals(actualNullable);
                if (exp != act) {
                    nullableMismatches++;
                    issues.add(new TemplateValidationIssue(
                        "ERROR",
                        "NULLABLE_MISMATCH",
                        col.getName(),
                        "字段可空性不符合模板定义",
                        String.valueOf(exp),
                        String.valueOf(act)
                    ));
                }
            }

            String expectedStandardCode = StringUtils.trimToNull(spec.standardCode());
            if (expectedStandardCode != null) {
                UUID standardId = col.getStandardId();
                if (standardId == null) {
                    unmappedStandards++;
                    issues.add(new TemplateValidationIssue(
                        "WARN",
                        "STANDARD_UNMAPPED",
                        col.getName(),
                        "字段未绑定数据元（字段标准）",
                        expectedStandardCode,
                        null
                    ));
                } else {
                    DataStandard standard = standardsById.get(standardId);
                    String actualStandardCode = standard != null ? StringUtils.trimToNull(standard.getCode()) : null;
                    if (actualStandardCode == null || !actualStandardCode.equalsIgnoreCase(expectedStandardCode)) {
                        standardMismatches++;
                        issues.add(new TemplateValidationIssue(
                            "WARN",
                            "STANDARD_MISMATCH",
                            col.getName(),
                            "字段绑定的数据元与模板期望不一致",
                            expectedStandardCode,
                            actualStandardCode
                        ));
                    }
                }
            } else {
                // If template didn't specify standard but comment hints exist, encourage mapping.
                String hinted = extractStdCodeHint(StringUtils.trimToNull(col.getComment()));
                if (hinted != null && col.getStandardId() == null) {
                    issues.add(new TemplateValidationIssue(
                        "INFO",
                        "STD_HINT_UNMAPPED",
                        col.getName(),
                        "注释包含 STD 编码但未绑定数据元，可在元数据页面使用“自动匹配”",
                        hinted,
                        null
                    ));
                }
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("requiredFields", requiredFields);
        summary.put("missingFields", missingFields);
        summary.put("typeMismatches", typeMismatches);
        summary.put("nullableMismatches", nullableMismatches);
        summary.put("unmappedStandards", unmappedStandards);
        summary.put("standardMismatches", standardMismatches);
        summary.put("issueCount", issues.size());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("templateId", id.toString());
        payload.put("templateName", template.getName());
        payload.put("layer", template.getLayer());
        payload.put("version", template.getVersion());
        payload.put("tableId", tableId.toString());
        payload.put("tableName", table.getName());
        payload.put("datasetId", dataset != null && dataset.getId() != null ? dataset.getId().toString() : null);
        payload.put("namingRule", namingRule);
        payload.put("summary", summary);
        payload.put("issues", issues);

        auditService.auditAction(
            "MODELING_TEMPLATE_VALIDATE",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of(
                "summary",
                "校验模型模板与数据表结构",
                "templateId",
                id.toString(),
                "tableId",
                tableId.toString(),
                "issueCount",
                issues.size()
            )
        );
        return ApiResponses.ok(payload);
    }

    private void applyGlossaryUpsert(ModelingGlossaryTerm term, ModelingGlossaryTerm request, String activeDeptHeader) {
        if (term == null || request == null) return;
        term.setCode(StringUtils.trimToNull(request.getCode()));
        term.setName(StringUtils.trimToNull(request.getName()));
        term.setAliases(StringUtils.trimToNull(request.getAliases()));
        term.setDomain(StringUtils.trimToNull(request.getDomain()));
        term.setDefinition(StringUtils.trimToNull(request.getDefinition()));
        term.setVersion(StringUtils.trimToNull(request.getVersion()));
        term.setVersionNotes(StringUtils.trimToNull(request.getVersionNotes()));
        // Treat as dictionary ledger: no "owner" field.
        term.setOwner(null);
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
        template.setMetadataStandardIds(StringUtils.trimToNull(request.getMetadataStandardIds()));
        template.setReviewChecklist(StringUtils.trimToNull(request.getReviewChecklist()));
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
        // Glossary/indicator terms are treated as a simple ledger: no status workflow.
        term.setStatus("ACTIVE");
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

    private void syncGlossaryCodeAssetGrant(ModelingGlossaryTerm term, String activeDeptHeader) {
        if (codeAssetGrantWriter == null || term == null || term.getId() == null) {
            return;
        }
        String naturalKey = org.springframework.util.StringUtils.hasText(term.getCode()) ? term.getCode().trim() : term.getId().toString();
        CatalogAssetIdentity identity = new CatalogAssetIdentity(
            CatalogAssetType.GLOSSARY_TERM,
            CatalogAssetKey.codeAsset(CatalogAssetType.GLOSSARY_TERM, "default", naturalKey),
            term.getId().toString(),
            "glossary-term:" + naturalKey
        );
        codeAssetGrantWriter.upsertCodeAsset(
            identity,
            firstText(term.getOwnerDept(), security.resolveActiveDept(activeDeptHeader)),
            SecurityUtils.getCurrentUserLogin().orElse("dts-platform"),
            "INTERNAL",
            CodeAssetLifecycleMapper.fromGlossaryStatus(term.getStatus())
        );
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
        view.put("version", term.getVersion());
        view.put("versionNotes", term.getVersionNotes());
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
        view.put("metadataStandardIds", template.getMetadataStandardIds());
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

    private List<TemplateFieldSpec> parseTemplateFieldSpecs(String raw) {
        String text = StringUtils.trimToNull(raw);
        if (text == null) {
            return List.of();
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            try {
                Object parsed = objectMapper.readValue(trimmed, Object.class);
                if (parsed instanceof List<?> list) {
                    return parseTemplateFieldSpecsFromList(list);
                }
                if (parsed instanceof Map<?, ?> map) {
                    Object fields = map.get("fields");
                    if (fields instanceof List<?> list) {
                        return parseTemplateFieldSpecsFromList(list);
                    }
                }
            } catch (Exception ignored) {}
        }
        return parseTemplateFieldSpecsFromText(trimmed);
    }

    private List<TemplateFieldSpec> parseTemplateFieldSpecsFromList(List<?> list) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        List<TemplateFieldSpec> specs = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            String name = StringUtils.trimToNull(Objects.toString(m.get("name"), null));
            if (name == null) continue;
            String dataType = StringUtils.trimToNull(Objects.toString(m.get("dataType"), null));
            Boolean nullable = parseNullable(m.get("nullable"));
            String standardCode = StringUtils.trimToNull(Objects.toString(m.get("standardCode"), null));
            String comment = StringUtils.trimToNull(Objects.toString(m.get("comment"), null));
            specs.add(new TemplateFieldSpec(name, dataType, nullable, standardCode, comment));
        }
        return specs;
    }

    private List<TemplateFieldSpec> parseTemplateFieldSpecsFromText(String text) {
        List<String> lines = Arrays.stream(text.split("\\r?\\n"))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .filter(s -> !s.startsWith("#"))
            .filter(s -> !s.startsWith("//"))
            .toList();
        if (lines.isEmpty()) {
            return List.of();
        }

        String header = lines.getFirst();
        boolean hasHeader = header.toLowerCase(Locale.ROOT).contains("name") && header.toLowerCase(Locale.ROOT).contains("datatype");
        List<String> dataLines = hasHeader ? lines.subList(1, lines.size()) : lines;

        List<TemplateFieldSpec> specs = new ArrayList<>();
        for (String line : dataLines) {
            if (!org.springframework.util.StringUtils.hasText(line)) continue;
            String[] parts = line.split("[,\\t]");
            String name = parts.length > 0 ? StringUtils.trimToNull(parts[0]) : null;
            if (name == null) continue;
            String dataType = parts.length > 1 ? StringUtils.trimToNull(parts[1]) : null;
            Boolean nullable = parts.length > 2 ? parseNullable(parts[2]) : null;
            String standardCode = parts.length > 3 ? StringUtils.trimToNull(parts[3]) : null;
            specs.add(new TemplateFieldSpec(name, dataType, nullable, standardCode, null));
        }
        return specs;
    }

    private Boolean parseNullable(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Boolean b) return b;
        String s = StringUtils.trimToNull(String.valueOf(raw));
        if (s == null) return null;
        String v = s.trim().toLowerCase(Locale.ROOT);
        if (v.equals("true") || v.equals("1") || v.equals("yes") || v.equals("y") || v.equals("是")) return Boolean.TRUE;
        if (v.equals("false") || v.equals("0") || v.equals("no") || v.equals("n") || v.equals("否")) return Boolean.FALSE;
        return null;
    }

    private Pattern compileNamingPattern(String namingRule) {
        String raw = StringUtils.trimToNull(namingRule);
        if (raw == null) return null;
        String candidate = raw;
        if (candidate.startsWith("regex:")) {
            candidate = StringUtils.trimToNull(candidate.substring("regex:".length()));
        } else if (candidate.startsWith("/") && candidate.endsWith("/") && candidate.length() > 2) {
            candidate = candidate.substring(1, candidate.length() - 1);
        }
        if (!org.springframework.util.StringUtils.hasText(candidate)) {
            return null;
        }
        try {
            return Pattern.compile(candidate);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String extractStdCodeHint(String comment) {
        if (!org.springframework.util.StringUtils.hasText(comment)) {
            return null;
        }
        var m = STD_CODE_PATTERN.matcher(comment);
        if (m.find()) {
            String code = StringUtils.trimToNull(m.group(1));
            return code;
        }
        return null;
    }

    private Map<UUID, DataStandard> loadStandardsById(List<CatalogColumnSchema> columns) {
        if (columns == null || columns.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        Set<UUID> ids = new LinkedHashSet<>();
        for (CatalogColumnSchema c : columns) {
            if (c == null) continue;
            if (c.getStandardId() != null) {
                ids.add(c.getStandardId());
            }
        }
        if (ids.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        Map<UUID, DataStandard> map = new LinkedHashMap<>();
        for (DataStandard standard : dataStandardRepository.findAllById(ids)) {
            if (standard != null && standard.getId() != null) {
                map.put(standard.getId(), standard);
            }
        }
        return map;
    }

    private boolean isTypeCompatible(String expectedType, String actualType) {
        String expected = normalizeDataType(expectedType);
        String actual = normalizeDataType(actualType);
        if (expected == null || actual == null) {
            return true;
        }
        return expected.equals(actual);
    }

    private String normalizeDataType(String rawType) {
        if (!org.springframework.util.StringUtils.hasText(rawType)) {
            return null;
        }
        String t = rawType.trim().toLowerCase(Locale.ROOT);
        int paren = t.indexOf('(');
        if (paren > 0) {
            t = t.substring(0, paren).trim();
        }
        if (t.isEmpty()) {
            return null;
        }
        return switch (t) {
            case "varchar", "char", "character", "character varying", "string", "text" -> "string";
            case "bigint", "int8", "long" -> "bigint";
            case "int", "integer", "int4" -> "int";
            case "smallint", "int2", "short" -> "smallint";
            case "double", "float8" -> "double";
            case "float", "float4", "real" -> "float";
            case "decimal", "numeric" -> "decimal";
            case "boolean", "bool" -> "boolean";
            case "timestamp", "datetime" -> "timestamp";
            default -> t;
        };
    }

    private static String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (org.springframework.util.StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
