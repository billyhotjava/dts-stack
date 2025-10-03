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
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.governance.dto.ComplianceBatchDto;
import com.yuzhi.dts.platform.service.governance.dto.ComplianceBatchItemDto;
import com.yuzhi.dts.platform.service.governance.request.ComplianceBatchRequest;
import com.yuzhi.dts.platform.service.governance.request.ComplianceItemUpdateRequest;
import com.yuzhi.dts.platform.service.governance.request.QualityRunTriggerRequest;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Locale;
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

    public ComplianceService(
        GovComplianceBatchRepository batchRepository,
        GovComplianceBatchItemRepository itemRepository,
        GovRuleRepository ruleRepository,
        GovRuleVersionRepository versionRepository,
        QualityRunService qualityRunService,
        GovQualityRunRepository qualityRunRepository,
        AuditService auditService,
        ObjectMapper objectMapper
    ) {
        this.batchRepository = batchRepository;
        this.itemRepository = itemRepository;
        this.ruleRepository = ruleRepository;
        this.versionRepository = versionRepository;
        this.qualityRunService = qualityRunService;
        this.qualityRunRepository = qualityRunRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public ComplianceBatchDto createBatch(ComplianceBatchRequest request, String actor) {
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
        auditService.audit("EXECUTE", "governance.compliance.batch", batch.getId().toString());
        return GovernanceMapper.toDto(batch, items);
    }

    @Transactional(readOnly = true)
    public ComplianceBatchDto getBatch(UUID id) {
        GovComplianceBatch batch = batchRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        List<GovComplianceBatchItem> items = itemRepository.findByBatchId(id);
        return GovernanceMapper.toDto(batch, items);
    }

    @Transactional(readOnly = true)
    public List<ComplianceBatchDto> recentBatches(int limit, List<String> statusFilter) {
        int pageSize = limit > 0 ? limit : 10;
        List<GovComplianceBatch> batches;
        List<String> normalizedStatuses = normalizeStatuses(statusFilter);
        if (!normalizedStatuses.isEmpty()) {
            batches = batchRepository.findByStatusInOrderByCreatedDateDesc(normalizedStatuses);
            if (batches.size() > pageSize) {
                batches = batches.subList(0, pageSize);
            }
        } else {
            Pageable pageable = PageRequest.of(0, pageSize, Sort.Direction.DESC, "createdDate");
            batches = batchRepository.findAll(pageable).getContent();
        }
        return batches
            .stream()
            .map(batch -> GovernanceMapper.toDto(batch, itemRepository.findByBatchId(batch.getId())))
            .collect(Collectors.toList());
    }

    public ComplianceBatchItemDto updateItem(UUID itemId, ComplianceItemUpdateRequest request, String actor) {
        if (request == null) {
            throw new IllegalArgumentException("请求参数不可为空");
        }
        GovComplianceBatchItem item = itemRepository.findById(itemId).orElseThrow(EntityNotFoundException::new);
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
        GovComplianceBatch batch = item.getBatch();
        refreshBatchState(batch, null);
        auditService.audit("UPDATE", "governance.compliance.item", actor + ":" + item.getId() + "@" + batch.getId());
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
