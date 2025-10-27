package com.yuzhi.dts.platform.service.governance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.governance.GovComplianceBatch;
import com.yuzhi.dts.platform.domain.governance.GovComplianceBatchItem;
import com.yuzhi.dts.platform.domain.governance.GovRule;
import com.yuzhi.dts.platform.domain.governance.GovRuleBinding;
import com.yuzhi.dts.platform.domain.governance.GovRuleVersion;
import com.yuzhi.dts.platform.repository.governance.GovComplianceBatchItemRepository;
import com.yuzhi.dts.platform.repository.governance.GovComplianceBatchRepository;
import com.yuzhi.dts.platform.repository.governance.GovQualityRunRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleVersionRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.DepartmentUtils;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.governance.dto.ComplianceBatchDto;
import com.yuzhi.dts.platform.service.governance.dto.ComplianceBatchItemDto;
import com.yuzhi.dts.platform.service.governance.request.ComplianceBatchRequest;
import com.yuzhi.dts.platform.service.governance.request.ComplianceItemUpdateRequest;
import com.yuzhi.dts.platform.service.governance.request.QualityRunTriggerRequest;
import com.yuzhi.dts.platform.service.security.OrganizationVisibilityService;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@Service
@Transactional
public class ComplianceService {

    private static final Logger log = LoggerFactory.getLogger(ComplianceService.class);
    private static final Set<String> PASS_STATUSES = Set.of("PASSED", "SUCCESS", "COMPLIANT");
    private static final Set<String> FAIL_STATUSES = Set.of("FAILED", "NON_COMPLIANT", "BREACHED");
    private static final Set<String> WAIVE_STATUSES = Set.of("WAIVED", "ACCEPTED_RISK");

    private final GovComplianceBatchRepository batchRepository;
    private final GovComplianceBatchItemRepository itemRepository;
    private final GovRuleRepository ruleRepository;
    private final GovRuleVersionRepository versionRepository;
    private final QualityRunService qualityRunService;
    private final GovQualityRunRepository qualityRunRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final OrganizationVisibilityService organizationVisibilityService;

    public ComplianceService(
        GovComplianceBatchRepository batchRepository,
        GovComplianceBatchItemRepository itemRepository,
        GovRuleRepository ruleRepository,
        GovRuleVersionRepository versionRepository,
        QualityRunService qualityRunService,
        GovQualityRunRepository qualityRunRepository,
        AuditService auditService,
        ObjectMapper objectMapper,
        OrganizationVisibilityService organizationVisibilityService
    ) {
        this.batchRepository = batchRepository;
        this.itemRepository = itemRepository;
        this.ruleRepository = ruleRepository;
        this.versionRepository = versionRepository;
        this.qualityRunService = qualityRunService;
        this.qualityRunRepository = qualityRunRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.organizationVisibilityService = organizationVisibilityService;
    }

    public ComplianceBatchDto createBatch(ComplianceBatchRequest request, String actor, String activeDeptHeader) {
        List<GovRule> rules = resolveRules(request.getRuleIds());
        if (rules.isEmpty()) {
            throw new IllegalArgumentException("请选择至少一个规则");
        }

        GovComplianceBatch batch = new GovComplianceBatch();
        batch.setName(resolveName(request, rules));
        batch.setTemplateCode(StringUtils.trimToNull(request.getTemplateCode()));
        batch.setEvidenceRequired(Boolean.TRUE.equals(request.getEvidenceRequired()));
        batch.setDataLevel(StringUtils.trimToNull(request.getDataLevel()));
        batch.setStatus("RUNNING");
        batch.setProgressPct(0);
        batch.setTriggeredBy(actor);
        batch.setTriggeredType("MANUAL");
        batch.setScheduledAt(Instant.now());
        batch.setOwnerDept(enforceOwnerDept(request.getOwnerDept(), activeDeptHeader));
        batch.setMetadataJson(writeMetadata(request.getMetadata()));
        batchRepository.save(batch);

        List<GovComplianceBatchItem> items = new ArrayList<>();
        for (GovRule rule : rules) {
            GovRuleVersion version = resolveVersion(rule);
            if (version.getBindings().isEmpty()) {
                log.warn("Rule {} has no bindings, skipping compliance entry", rule.getId());
                continue;
            }
            for (GovRuleBinding binding : version.getBindings()) {
                GovComplianceBatchItem item = new GovComplianceBatchItem();
                item.setBatch(batch);
                item.setRule(rule);
                item.setRuleVersion(version);
                item.setDatasetId(binding.getDatasetId());
                item.setStatus("QUEUED");
                item.setSeverity(rule.getSeverity());
                itemRepository.save(item);
                items.add(item);

                QualityRunTriggerRequest triggerRequest = new QualityRunTriggerRequest();
                triggerRequest.setRuleId(rule.getId());
                triggerRequest.setBindingId(binding.getId());
                triggerRequest.setTriggerType("COMPLIANCE");
                qualityRunService
                    .trigger(triggerRequest, actor)
                    .stream()
                    .findFirst()
                    .ifPresent(runDto -> qualityRunRepository
                        .findById(runDto.getId())
                        .ifPresent(run -> {
                            item.setQualityRun(run);
                            itemRepository.save(item);
                        })
                    );
            }
        }

        refreshBatchState(batch, items);
        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("targetId", batch.getId().toString());
        auditDetail.put("targetName", batch.getName());
        auditDetail.put("ruleCount", rules.size());
        auditDetail.put("summary", "启动合规批次：" + batch.getName());
        auditService.record(
            "EXECUTE",
            "governance.compliance.batch",
            "governance.compliance.batch",
            batch.getId().toString(),
            "SUCCESS",
            auditDetail
        );
        return GovernanceMapper.toDto(batch, items);
    }

    @Transactional(readOnly = true)
    public ComplianceBatchDto getBatch(UUID id, String activeDeptHeader) {
        GovComplianceBatch batch = batchRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        ensureBatchReadable(batch, activeDeptHeader);
        List<GovComplianceBatchItem> items = itemRepository.findByBatchId(id);
        return GovernanceMapper.toDto(batch, items);
    }

    @Transactional(readOnly = true)
    public List<ComplianceBatchDto> recentBatches(int limit, List<String> statusFilter, String activeDeptHeader) {
        int pageSize = limit > 0 ? limit : 10;
        List<GovComplianceBatch> batches;
        List<String> normalizedStatuses = normalizeStatuses(statusFilter);
        if (!normalizedStatuses.isEmpty()) {
            batches = batchRepository.findByStatusInOrderByCreatedDateDesc(normalizedStatuses);
        } else {
            int fetchSize = Math.max(pageSize, 20);
            Pageable pageable = PageRequest.of(0, fetchSize, Sort.Direction.DESC, "createdDate");
            batches = batchRepository.findAll(pageable).getContent();
        }
        String activeDept = resolveActiveDept(activeDeptHeader);
        boolean instituteScope = hasInstituteScope();
        return batches
            .stream()
            .filter(batch -> isBatchVisible(batch, activeDept, instituteScope))
            .limit(pageSize)
            .map(batch -> GovernanceMapper.toDto(batch, itemRepository.findByBatchId(batch.getId())))
            .collect(Collectors.toList());
    }

    public ComplianceBatchItemDto updateItem(UUID itemId, ComplianceItemUpdateRequest request, String actor, String activeDeptHeader) {
        if (request == null) {
            throw new IllegalArgumentException("请求参数不可为空");
        }
        GovComplianceBatchItem item = itemRepository.findById(itemId).orElseThrow(EntityNotFoundException::new);
        GovComplianceBatch batch = item.getBatch();
        ensureBatchWritable(batch, activeDeptHeader);
        Map<String, Object> before = toComplianceItemAuditView(item);
        if (StringUtils.isNotBlank(request.getStatus())) {
            item.setStatus(request.getStatus().trim().toUpperCase(Locale.ROOT));
        }
        if (request.getConclusion() != null) {
            item.setConclusion(StringUtils.trimToNull(request.getConclusion()));
        }
        if (request.getEvidenceRef() != null) {
            item.setEvidenceRef(StringUtils.trimToNull(request.getEvidenceRef()));
        }
        itemRepository.save(item);
        refreshBatchState(batch, null);
        Map<String, Object> after = toComplianceItemAuditView(item);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("before", before);
        detail.put("after", after);
        detail.put("batchId", batch.getId());
        detail.put("batchName", batch.getName());
        String ruleName = item.getRule() != null ? item.getRule().getName() : item.getId().toString();
        detail.put("targetId", item.getId().toString());
        detail.put("targetName", ruleName);
        detail.put("summary", "处理合规检查项：" + ruleName);
        detail.put("actor", actor);
        auditService.record(
            "UPDATE",
            "governance.compliance.item",
            "governance.compliance.item",
            item.getId() + "@" + batch.getId(),
            "SUCCESS",
            detail
        );
        return GovernanceMapper.toDto(item);
    }

    private void refreshBatchState(GovComplianceBatch batch, List<GovComplianceBatchItem> cachedItems) {
        List<GovComplianceBatchItem> items = cachedItems != null ? cachedItems : itemRepository.findByBatchId(batch.getId());
        int total = items.size();
        long completed = items.stream().filter(it -> isCompletedStatus(it.getStatus())).count();
        long failed = items.stream().filter(it -> isFailedStatus(it.getStatus())).count();

        if (total == 0) {
            batch.setProgressPct(0);
            batch.setStatus("RUNNING");
            batch.setFinishedAt(null);
            batch.setSummary("暂无检查项");
        } else {
            int progress = (int) Math.round((completed * 100.0) / total);
            batch.setProgressPct(Math.min(100, Math.max(0, progress)));
            if (completed == total) {
                batch.setStatus(failed > 0 ? "FAILED" : "COMPLETED");
                if (batch.getFinishedAt() == null) {
                    batch.setFinishedAt(Instant.now());
                }
            } else {
                batch.setStatus("RUNNING");
                batch.setFinishedAt(null);
            }
            if (completed > 0 && batch.getStartedAt() == null) {
                batch.setStartedAt(Instant.now());
            }
            batch.setSummary(String.format("总计 %d 项，已完成 %d，不合规 %d", total, completed, failed));
        }
        batchRepository.save(batch);
    }

    public void deleteBatch(UUID batchId, String actor, String activeDeptHeader) {
        GovComplianceBatch batch = batchRepository.findById(batchId).orElseThrow(EntityNotFoundException::new);
        ensureBatchWritable(batch, activeDeptHeader);
        Map<String, Object> before = toComplianceBatchAuditView(batch);
        batchRepository.delete(batch);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("before", before);
        detail.put("after", Map.of("deleted", true));
        detail.put("targetId", batchId.toString());
        detail.put("targetName", batch.getName());
        detail.put("summary", "删除合规批次：" + batch.getName());
        detail.put("actor", actor);
        auditService.record(
            "DELETE",
            "governance.compliance.batch",
            "governance.compliance.batch",
            batchId.toString(),
            "SUCCESS",
            detail
        );
    }

    private List<String> normalizeStatuses(List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return Collections.emptyList();
        }
        return statuses
            .stream()
            .filter(StringUtils::isNotBlank)
            .map(value -> value.trim().toUpperCase(Locale.ROOT))
            .distinct()
            .collect(Collectors.toList());
    }

    private static boolean isCompletedStatus(String status) {
        String normalized = normalizeStatus(status);
        if (normalized == null) {
            return false;
        }
        return PASS_STATUSES.contains(normalized) || FAIL_STATUSES.contains(normalized) || WAIVE_STATUSES.contains(normalized);
    }

    private static boolean isFailedStatus(String status) {
        String normalized = normalizeStatus(status);
        return normalized != null && FAIL_STATUSES.contains(normalized);
    }

    private static String normalizeStatus(String status) {
        if (status == null) {
            return null;
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    private List<GovRule> resolveRules(List<UUID> ruleIds) {
        if (ruleIds == null || ruleIds.isEmpty()) {
            return List.of();
        }
        return ruleIds.stream().map(id -> ruleRepository.findById(id).orElseThrow(EntityNotFoundException::new)).collect(Collectors.toList());
    }

    private Map<String, Object> toComplianceItemAuditView(GovComplianceBatchItem item) {
        if (item == null) {
            return Map.of();
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", item.getId());
        view.put("ruleId", item.getRule() != null ? item.getRule().getId() : null);
        view.put("status", item.getStatus());
        view.put("severity", item.getSeverity());
        view.put("conclusion", item.getConclusion());
        view.put("evidenceRef", item.getEvidenceRef());
        if (item.getQualityRun() != null) {
            view.put("qualityRunId", item.getQualityRun().getId());
        }
        return view;
    }

    private Map<String, Object> toComplianceBatchAuditView(GovComplianceBatch batch) {
        if (batch == null) {
            return Map.of();
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", batch.getId());
        view.put("name", batch.getName());
        view.put("status", batch.getStatus());
        view.put("summary", batch.getSummary());
        view.put("progressPct", batch.getProgressPct());
        view.put("startedAt", batch.getStartedAt());
        view.put("finishedAt", batch.getFinishedAt());
        view.put("ownerDept", batch.getOwnerDept());
        return view;
    }

    private String enforceOwnerDept(String requestedOwnerDept, String activeDeptHeader) {
        String requested = StringUtils.trimToNull(requestedOwnerDept);
        if (hasInstituteScope()) {
            if (requested != null) {
                return requested;
            }
            return StringUtils.trimToNull(resolveActiveDept(activeDeptHeader));
        }
        if (!hasDepartmentScope()) {
            throw new AccessDeniedException("当前账号无权创建合规批次");
        }
        String activeDept = resolveActiveDept(activeDeptHeader);
        if (StringUtils.isBlank(activeDept)) {
            throw new AccessDeniedException("当前账号未配置所属部门，无法执行该操作");
        }
        if (requested != null && !DepartmentUtils.matches(requested, activeDept)) {
            throw new AccessDeniedException("合规批次仅可归属当前登录部门");
        }
        return activeDept.trim();
    }

    private boolean isBatchVisible(GovComplianceBatch batch, String activeDept, boolean instituteScope) {
        if (batch == null) {
            return false;
        }
        String ownerDept = StringUtils.trimToNull(batch.getOwnerDept());
        if (ownerDept == null) {
            return true;
        }
        if (instituteScope) {
            return true;
        }
        if (organizationVisibilityService.isRoot(ownerDept)) {
            return true;
        }
        if (StringUtils.isBlank(activeDept)) {
            return false;
        }
        return DepartmentUtils.matches(ownerDept, activeDept);
    }

    private void ensureBatchReadable(GovComplianceBatch batch, String activeDeptHeader) {
        if (batch == null) {
            throw new EntityNotFoundException("合规批次不存在");
        }
        String activeDept = resolveActiveDept(activeDeptHeader);
        if (!isBatchVisible(batch, activeDept, hasInstituteScope())) {
            throw new AccessDeniedException("当前账号无权访问该合规批次");
        }
    }

    private void ensureBatchWritable(GovComplianceBatch batch, String activeDeptHeader) {
        ensureBatchReadable(batch, activeDeptHeader);
        if (hasInstituteScope()) {
            return;
        }
        if (!hasDepartmentScope()) {
            throw new AccessDeniedException("当前账号无权操作该合规批次");
        }
        String ownerDept = StringUtils.trimToNull(batch.getOwnerDept());
        String activeDept = resolveActiveDept(activeDeptHeader);
        if (ownerDept == null || StringUtils.isBlank(activeDept) || !DepartmentUtils.matches(ownerDept, activeDept)) {
            throw new AccessDeniedException("当前账号无权操作该合规批次");
        }
    }

    private String resolveActiveDept(String activeDeptHeader) {
        if (StringUtils.isNotBlank(activeDeptHeader)) {
            return activeDeptHeader.trim();
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        try {
            if (authentication instanceof JwtAuthenticationToken token) {
                String candidate = extractDeptClaim(token.getToken().getClaims().get("dept_code"));
                if (candidate != null) return candidate;
                candidate = extractDeptClaim(token.getToken().getClaims().get("deptCode"));
                if (candidate != null) return candidate;
                return extractDeptClaim(token.getToken().getClaims().get("department"));
            }
            if (authentication != null && authentication.getPrincipal() instanceof OAuth2AuthenticatedPrincipal principal) {
                String candidate = extractDeptClaim(principal.getAttribute("dept_code"));
                if (candidate != null) return candidate;
                candidate = extractDeptClaim(principal.getAttribute("deptCode"));
                if (candidate != null) return candidate;
                return extractDeptClaim(principal.getAttribute("department"));
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String extractDeptClaim(Object raw) {
        Object flattened = flattenValue(raw);
        if (flattened == null) {
            return null;
        }
        String text = flattened.toString();
        if (StringUtils.isBlank(text)) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Object flattenValue(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                if (element != null) {
                    return element;
                }
            }
            return null;
        }
        if (raw.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(raw);
            for (int i = 0; i < length; i++) {
                Object element = java.lang.reflect.Array.get(raw, i);
                if (element != null) {
                    return element;
                }
            }
            return null;
        }
        return raw;
    }

    private boolean hasInstituteScope() {
        return SecurityUtils.hasCurrentUserAnyOfAuthorities(
            AuthoritiesConstants.ADMIN,
            AuthoritiesConstants.OP_ADMIN,
            AuthoritiesConstants.INST_DATA_DEV,
            AuthoritiesConstants.INST_DATA_OWNER
        );
    }

    private boolean hasDepartmentScope() {
        return SecurityUtils.hasCurrentUserAnyOfAuthorities(
            AuthoritiesConstants.DEPT_DATA_DEV,
            AuthoritiesConstants.DEPT_DATA_OWNER
        );
    }

    private GovRuleVersion resolveVersion(GovRule rule) {
        GovRuleVersion version = rule.getLatestVersion();
        if (version == null) {
            version = versionRepository.findFirstByRuleIdOrderByVersionDesc(rule.getId()).orElse(null);
        }
        if (version == null) {
            throw new IllegalStateException("规则" + rule.getName() + "尚未发布版本");
        }
        return version;
    }

    private String resolveName(ComplianceBatchRequest request, List<GovRule> rules) {
        if (StringUtils.isNotBlank(request.getName())) {
            return request.getName().trim();
        }
        if (rules.size() == 1) {
            return rules.get(0).getName() + "合规检查";
        }
        return "合规批次-" + Instant.now().toString();
    }

    private String writeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize compliance metadata: {}", e.getMessage());
            return null;
        }
    }
}
