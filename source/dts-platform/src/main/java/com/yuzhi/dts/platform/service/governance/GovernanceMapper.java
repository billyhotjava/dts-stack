package com.yuzhi.dts.platform.service.governance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.yuzhi.dts.platform.domain.governance.GovComplianceBatch;
import com.yuzhi.dts.platform.domain.governance.GovComplianceBatchItem;
import com.yuzhi.dts.platform.domain.governance.GovIssueAction;
import com.yuzhi.dts.platform.domain.governance.GovIssueTicket;
import com.yuzhi.dts.platform.domain.governance.GovQualityMetric;
import com.yuzhi.dts.platform.domain.governance.GovQualityRun;
import com.yuzhi.dts.platform.domain.governance.GovRule;
import com.yuzhi.dts.platform.domain.governance.GovRuleBinding;
import com.yuzhi.dts.platform.domain.governance.GovRuleVersion;
import com.yuzhi.dts.platform.service.governance.dto.ComplianceBatchDto;
import com.yuzhi.dts.platform.service.governance.dto.ComplianceBatchItemDto;
import com.yuzhi.dts.platform.service.governance.dto.IssueActionDto;
import com.yuzhi.dts.platform.service.governance.dto.IssueTicketDto;
import com.yuzhi.dts.platform.service.governance.dto.QualityMetricDto;
import com.yuzhi.dts.platform.service.governance.dto.QualityRuleBindingDto;
import com.yuzhi.dts.platform.service.governance.dto.QualityRuleDto;
import com.yuzhi.dts.platform.service.governance.dto.QualityRuleVersionDto;
import com.yuzhi.dts.platform.service.governance.dto.QualityRunDto;
import java.time.Instant;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

final class GovernanceMapper {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();
    private static final Set<String> PASS_STATUSES = Set.of("PASSED", "SUCCESS", "COMPLIANT");
    private static final Set<String> FAIL_STATUSES = Set.of("FAILED", "NON_COMPLIANT", "BREACHED");
    private static final Set<String> WAIVE_STATUSES = Set.of("WAIVED", "ACCEPTED_RISK");

    private GovernanceMapper() {}

    static QualityRuleDto toDto(GovRule entity) {
        if (entity == null) {
            return null;
        }
        QualityRuleDto dto = new QualityRuleDto();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setType(entity.getType());
        dto.setCategory(entity.getCategory());
        dto.setDescription(entity.getDescription());
        dto.setOwner(entity.getOwner());
        dto.setSeverity(entity.getSeverity());
        dto.setDataLevel(entity.getDataLevel());
        dto.setExecutor(entity.getExecutor());
        dto.setFrequencyCron(entity.getFrequencyCron());
        dto.setTemplate(Boolean.TRUE.equals(entity.getTemplate()));
        dto.setEnabled(Boolean.TRUE.equals(entity.getEnabled()));
        dto.setDatasetId(entity.getDatasetId());
        dto.setLatestVersionId(Optional.ofNullable(entity.getLatestVersion()).map(GovRuleVersion::getId).orElse(null));
        dto.setCreatedDate(entity.getCreatedDate());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setLastModifiedDate(entity.getLastModifiedDate());
        dto.setLastModifiedBy(entity.getLastModifiedBy());
        dto.setLatestVersion(toDto(entity.getLatestVersion()));
        if (entity.getLatestVersion() != null) {
            dto.setBindings(toBindingDtoList(entity.getLatestVersion().getBindings()));
        } else {
            dto.setBindings(Collections.emptyList());
        }
        return dto;
    }

    static QualityRuleVersionDto toDto(GovRuleVersion entity) {
        if (entity == null) {
            return null;
        }
        QualityRuleVersionDto dto = new QualityRuleVersionDto();
        dto.setId(entity.getId());
        dto.setRuleId(Optional.ofNullable(entity.getRule()).map(GovRule::getId).orElse(null));
        dto.setVersion(entity.getVersion());
        dto.setStatus(entity.getStatus());
        dto.setDefinition(entity.getDefinition());
        dto.setChecksum(entity.getChecksum());
        dto.setNotes(entity.getNotes());
        dto.setApprovedBy(entity.getApprovedBy());
        dto.setApprovedAt(entity.getApprovedAt());
        dto.setCreatedDate(entity.getCreatedDate());
        dto.setCreatedBy(entity.getCreatedBy());
        return dto;
    }

    static List<QualityRuleBindingDto> toBindingDtoList(Set<GovRuleBinding> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return Collections.emptyList();
        }
        return bindings.stream().map(GovernanceMapper::toDto).collect(Collectors.toList());
    }

    static QualityRuleBindingDto toDto(GovRuleBinding entity) {
        if (entity == null) {
            return null;
        }
        QualityRuleBindingDto dto = new QualityRuleBindingDto();
        dto.setId(entity.getId());
        dto.setRuleVersionId(Optional.ofNullable(entity.getRuleVersion()).map(GovRuleVersion::getId).orElse(null));
        dto.setDatasetId(entity.getDatasetId());
        dto.setDatasetAlias(entity.getDatasetAlias());
        dto.setScopeType(entity.getScopeType());
        dto.setFieldRefs(splitCsv(entity.getFieldRefs()));
        dto.setFilterExpression(entity.getFilterExpression());
        dto.setScheduleOverride(entity.getScheduleOverride());
        dto.setCreatedDate(entity.getCreatedDate());
        dto.setCreatedBy(entity.getCreatedBy());
        return dto;
    }

    static QualityRunDto toDto(GovQualityRun entity, List<GovQualityMetric> metrics) {
        if (entity == null) {
            return null;
        }
        QualityRunDto dto = new QualityRunDto();
        dto.setId(entity.getId());
        dto.setRuleId(Optional.ofNullable(entity.getRule()).map(GovRule::getId).orElse(null));
        dto.setRuleVersionId(Optional.ofNullable(entity.getRuleVersion()).map(GovRuleVersion::getId).orElse(null));
        dto.setBindingId(Optional.ofNullable(entity.getBinding()).map(GovRuleBinding::getId).orElse(null));
        dto.setDatasetId(entity.getDatasetId());
        dto.setJobId(entity.getJobId());
        dto.setTriggerType(entity.getTriggerType());
        dto.setTriggerRef(entity.getTriggerRef());
        dto.setStatus(entity.getStatus());
        dto.setSeverity(entity.getSeverity());
        dto.setDataLevel(entity.getDataLevel());
        dto.setScheduledAt(entity.getScheduledAt());
        dto.setStartedAt(entity.getStartedAt());
        dto.setFinishedAt(entity.getFinishedAt());
        dto.setDurationMs(entity.getDurationMs());
        dto.setMessage(entity.getMessage());
        dto.setMetricsJson(entity.getMetricsJson());
        dto.setCreatedDate(entity.getCreatedDate());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setMetrics(metrics == null ? Collections.emptyList() : metrics.stream().map(GovernanceMapper::toDto).collect(Collectors.toList()));
        return dto;
    }

    static QualityMetricDto toDto(GovQualityMetric entity) {
        if (entity == null) {
            return null;
        }
        QualityMetricDto dto = new QualityMetricDto();
        dto.setId(entity.getId());
        dto.setRunId(Optional.ofNullable(entity.getRun()).map(GovQualityRun::getId).orElse(null));
        dto.setMetricKey(entity.getMetricKey());
        dto.setMetricValue(entity.getMetricValue());
        dto.setThresholdValue(entity.getThresholdValue());
        dto.setStatus(entity.getStatus());
        dto.setDetail(entity.getDetail());
        return dto;
    }

    static ComplianceBatchDto toDto(GovComplianceBatch entity, List<GovComplianceBatchItem> items) {
        if (entity == null) {
            return null;
        }
        ComplianceBatchDto dto = new ComplianceBatchDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setTemplateCode(entity.getTemplateCode());
        dto.setStatus(entity.getStatus());
        dto.setProgressPct(entity.getProgressPct());
        dto.setEvidenceRequired(Boolean.TRUE.equals(entity.getEvidenceRequired()));
        dto.setDataLevel(entity.getDataLevel());
        dto.setScheduledAt(entity.getScheduledAt());
        dto.setStartedAt(entity.getStartedAt());
        dto.setFinishedAt(entity.getFinishedAt());
        dto.setTriggeredBy(entity.getTriggeredBy());
        dto.setTriggeredType(entity.getTriggeredType());
        dto.setSummary(entity.getSummary());
        dto.setMetadataJson(entity.getMetadataJson());
        dto.setCreatedDate(entity.getCreatedDate());
        dto.setCreatedBy(entity.getCreatedBy());
        List<GovComplianceBatchItem> sourceItems = items != null ? items : entity.getItems().stream().collect(Collectors.toList());
        dto.setItems(sourceItems.stream().map(GovernanceMapper::toDto).collect(Collectors.toList()));
        dto.setTotalItems(sourceItems.size());
        long passed = sourceItems.stream().filter(item -> isPassedStatus(item.getStatus())).count();
        long failed = sourceItems.stream().filter(item -> isFailedStatus(item.getStatus())).count();
        long completed = sourceItems.stream().filter(item -> isCompletedStatus(item.getStatus())).count();
        dto.setPassedItems((int) passed);
        dto.setFailedItems((int) failed);
        dto.setCompletedItems((int) completed);
        dto.setPendingItems(dto.getTotalItems() != null ? Math.max(0, dto.getTotalItems() - (int) completed) : null);
        dto.setHasFailure(failed > 0);
        dto.setLastUpdated(resolveLastUpdated(entity, sourceItems));
        return dto;
    }

    static ComplianceBatchItemDto toDto(GovComplianceBatchItem entity) {
        if (entity == null) {
            return null;
        }
        ComplianceBatchItemDto dto = new ComplianceBatchItemDto();
        dto.setId(entity.getId());
        dto.setBatchId(Optional.ofNullable(entity.getBatch()).map(GovComplianceBatch::getId).orElse(null));
        dto.setRuleId(Optional.ofNullable(entity.getRule()).map(GovRule::getId).orElse(null));
        dto.setRuleVersionId(Optional.ofNullable(entity.getRuleVersion()).map(GovRuleVersion::getId).orElse(null));
        dto.setDatasetId(entity.getDatasetId());
        dto.setQualityRunId(Optional.ofNullable(entity.getQualityRun()).map(GovQualityRun::getId).orElse(null));
        dto.setStatus(entity.getStatus());
        dto.setSeverity(entity.getSeverity());
        dto.setConclusion(entity.getConclusion());
        dto.setEvidenceRef(entity.getEvidenceRef());
        dto.setCreatedDate(entity.getCreatedDate());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setRuleName(Optional.ofNullable(entity.getRule()).map(GovRule::getName).orElse(null));
        dto.setRuleCode(Optional.ofNullable(entity.getRule()).map(GovRule::getCode).orElse(null));
        dto.setRuleVersion(Optional.ofNullable(entity.getRuleVersion()).map(GovRuleVersion::getVersion).orElse(null));
        dto.setRuleSeverity(Optional.ofNullable(entity.getRule()).map(GovRule::getSeverity).orElse(null));
        dto.setDatasetAlias(resolveDatasetAlias(entity));
        dto.setQualityRunStatus(Optional.ofNullable(entity.getQualityRun()).map(GovQualityRun::getStatus).orElse(null));
        dto.setQualityRunStartedAt(Optional.ofNullable(entity.getQualityRun()).map(GovQualityRun::getStartedAt).orElse(null));
        dto.setQualityRunFinishedAt(Optional.ofNullable(entity.getQualityRun()).map(GovQualityRun::getFinishedAt).orElse(null));
        dto.setQualityRunDurationMs(Optional
            .ofNullable(entity.getQualityRun())
            .map(GovQualityRun::getDurationMs)
            .orElse(null));
        dto.setQualityRunMessage(Optional.ofNullable(entity.getQualityRun()).map(GovQualityRun::getMessage).orElse(null));
        dto.setLastUpdated(Optional.ofNullable(entity.getLastModifiedDate()).orElse(entity.getCreatedDate()));
        return dto;
    }

    static IssueTicketDto toDto(GovIssueTicket entity) {
        if (entity == null) {
            return null;
        }
        IssueTicketDto dto = new IssueTicketDto();
        dto.setId(entity.getId());
        dto.setSourceType(entity.getSourceType());
        dto.setSourceId(Optional.ofNullable(entity.getComplianceBatch()).map(GovComplianceBatch::getId).orElse(null));
        dto.setTitle(entity.getTitle());
        dto.setSummary(entity.getSummary());
        dto.setStatus(entity.getStatus());
        dto.setSeverity(entity.getSeverity());
        dto.setPriority(entity.getPriority());
        dto.setDataLevel(entity.getDataLevel());
        dto.setAssignedTo(entity.getAssignedTo());
        dto.setAssignedAt(entity.getAssignedAt());
        dto.setDueAt(entity.getDueAt());
        dto.setResolvedAt(entity.getResolvedAt());
        dto.setResolution(entity.getResolution());
        dto.setOwner(entity.getOwner());
        dto.setTags(splitCsv(entity.getTags()));
        dto.setCreatedDate(entity.getCreatedDate());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setLastModifiedDate(entity.getLastModifiedDate());
        dto.setLastModifiedBy(entity.getLastModifiedBy());
        dto.setActions(entity.getActions().stream().map(GovernanceMapper::toDto).collect(Collectors.toList()));
        return dto;
    }

    static IssueActionDto toDto(GovIssueAction entity) {
        if (entity == null) {
            return null;
        }
        IssueActionDto dto = new IssueActionDto();
        dto.setId(entity.getId());
        dto.setTicketId(Optional.ofNullable(entity.getTicket()).map(GovIssueTicket::getId).orElse(null));
        dto.setActionType(entity.getActionType());
        dto.setActor(entity.getActor());
        dto.setNotes(entity.getNotes());
        dto.setAttachments(readJsonList(entity.getAttachmentsJson()));
        dto.setCreatedDate(entity.getCreatedDate());
        dto.setCreatedBy(entity.getCreatedBy());
        return dto;
    }

    private static boolean isPassedStatus(String status) {
        if (status == null) {
            return false;
        }
        return PASS_STATUSES.contains(status.toUpperCase(Locale.ROOT));
    }

    private static boolean isFailedStatus(String status) {
        if (status == null) {
            return false;
        }
        return FAIL_STATUSES.contains(status.toUpperCase(Locale.ROOT));
    }

    private static boolean isCompletedStatus(String status) {
        if (status == null) {
            return false;
        }
        String normalized = status.toUpperCase(Locale.ROOT);
        return PASS_STATUSES.contains(normalized) || FAIL_STATUSES.contains(normalized) || WAIVE_STATUSES.contains(normalized);
    }

    private static Instant resolveLastUpdated(GovComplianceBatch batch, List<GovComplianceBatchItem> items) {
        Instant batchUpdated = batch.getLastModifiedDate();
        Instant itemUpdated = items
            .stream()
            .map(item -> Optional.ofNullable(item.getLastModifiedDate()).orElse(item.getCreatedDate()))
            .filter(Objects::nonNull)
            .max(Comparator.naturalOrder())
            .orElse(null);
        return latestInstant(batchUpdated, itemUpdated);
    }

    private static Instant latestInstant(Instant first, Instant second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.isAfter(second) ? first : second;
    }

    private static String resolveDatasetAlias(GovComplianceBatchItem item) {
        if (item.getDatasetId() == null) {
            return null;
        }
        String alias = Optional
            .ofNullable(item.getRuleVersion())
            .map(GovRuleVersion::getBindings)
            .map(bindings -> bindings
                .stream()
                .filter(binding -> item.getDatasetId().equals(binding.getDatasetId()))
                .map(GovRuleBinding::getDatasetAlias)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null))
            .orElse(null);
        if (alias != null && !alias.isBlank()) {
            return alias.trim();
        }
        return item.getDatasetId().toString();
    }

    static List<String> splitCsv(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }
        return List.of(raw.split(","))
            .stream()
            .map(String::trim)
            .filter(token -> !token.isBlank())
            .map(token -> token.toLowerCase(Locale.ROOT))
            .distinct()
            .collect(Collectors.toList());
    }

    static String joinCsv(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values
            .stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(token -> !token.isBlank())
            .map(token -> token.toLowerCase(Locale.ROOT))
            .distinct()
            .collect(Collectors.joining(","));
    }

    static List<String> readJsonList(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return OBJECT_MAPPER.readValue(raw, OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException ex) {
            return Collections.emptyList();
        }
    }

    static String writeJsonList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(values);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to serialize attachments list", ex);
        }
    }
}
