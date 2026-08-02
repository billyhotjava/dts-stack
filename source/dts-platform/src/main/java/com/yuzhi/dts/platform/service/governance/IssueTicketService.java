package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.config.GovernanceProperties;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.governance.GovComplianceBatch;
import com.yuzhi.dts.platform.domain.governance.GovIssueAction;
import com.yuzhi.dts.platform.domain.governance.GovIssueTicket;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.governance.GovComplianceBatchRepository;
import com.yuzhi.dts.platform.repository.governance.GovIssueActionRepository;
import com.yuzhi.dts.platform.repository.governance.GovIssueTicketRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.DepartmentUtils;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.security.policy.DataLevel;
import com.yuzhi.dts.platform.service.governance.dto.IssueActionDto;
import com.yuzhi.dts.platform.service.governance.dto.IssueTicketDto;
import com.yuzhi.dts.platform.service.governance.request.IssueActionRequest;
import com.yuzhi.dts.platform.service.governance.request.IssueTicketUpsertRequest;
import jakarta.persistence.EntityNotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@Service
@Transactional
public class IssueTicketService {

    private static final Logger log = LoggerFactory.getLogger(IssueTicketService.class);
    private static final List<String> OPEN_STATUSES = List.of("OPEN", "IN_PROGRESS", "RESOLVED");
    private static final String STATUS_OPEN = "OPEN";
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_RESOLVED = "RESOLVED";
    private static final String STATUS_CLOSED = "CLOSED";

    public enum CreateOrTouchDisposition {
        CREATED,
        TOUCHED
    }

    public record CreateOrTouchResult(IssueTicketDto ticket, CreateOrTouchDisposition disposition) {}

    private final GovIssueTicketRepository ticketRepository;
    private final GovIssueActionRepository actionRepository;
    private final GovComplianceBatchRepository batchRepository;
    private final CatalogDatasetRepository datasetRepository;
    private final com.yuzhi.dts.platform.service.security.AccessChecker accessChecker;
    private final com.yuzhi.dts.platform.service.security.OrganizationVisibilityService organizationVisibilityService;
    private final GovernanceProperties properties;
    private final QualityAuditRecorder qualityAuditRecorder;
    private final QualityEffectiveDepartmentResolver departmentResolver;
    private final QualityDatasetReadGuard datasetReadGuard;
    private final IssueSourcePolicy issueSourcePolicy;
    private final IssueTicketQueryService queryService;
    private final ThreadLocal<Boolean> suppressAutomaticAudit = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public IssueTicketService(
        GovIssueTicketRepository ticketRepository,
        GovIssueActionRepository actionRepository,
        GovComplianceBatchRepository batchRepository,
        CatalogDatasetRepository datasetRepository,
        com.yuzhi.dts.platform.service.security.AccessChecker accessChecker,
        com.yuzhi.dts.platform.service.security.OrganizationVisibilityService organizationVisibilityService,
        GovernanceProperties properties,
        QualityAuditRecorder qualityAuditRecorder,
        QualityEffectiveDepartmentResolver departmentResolver,
        QualityDatasetReadGuard datasetReadGuard,
        IssueSourcePolicy issueSourcePolicy
    ) {
        this.ticketRepository = ticketRepository;
        this.actionRepository = actionRepository;
        this.batchRepository = batchRepository;
        this.datasetRepository = datasetRepository;
        this.accessChecker = accessChecker;
        this.organizationVisibilityService = organizationVisibilityService;
        this.properties = properties;
        this.qualityAuditRecorder = qualityAuditRecorder;
        this.departmentResolver = departmentResolver;
        this.datasetReadGuard = datasetReadGuard;
        this.issueSourcePolicy = issueSourcePolicy;
        this.queryService = new IssueTicketQueryService(
            ticketRepository,
            accessChecker,
            organizationVisibilityService,
            departmentResolver
        );
    }

    @Transactional(readOnly = true)
    public List<IssueTicketDto> listTickets(
        String status,
        String sourceType,
        UUID datasetId,
        String assignedTo,
        String owner,
        String priority,
        Boolean overdue,
        String keyword,
        int limit,
        String actor,
        String activeDeptHeader
    ) {
        return queryService.listTickets(
            status,
            sourceType,
            datasetId,
            assignedTo,
            owner,
            priority,
            overdue,
            keyword,
            limit,
            actor,
            activeDeptHeader
        );
    }

    @Transactional(readOnly = true)
    public IssueTicketDto get(UUID id, String actor, String activeDeptHeader) {
        return queryService.get(id, actor, activeDeptHeader);
    }

    @Transactional(readOnly = true)
    public Optional<IssueTicketDto> findBySource(
        String sourceType,
        UUID sourceRefId,
        String actor,
        String activeDeptHeader
    ) {
        return queryService.findBySource(sourceType, sourceRefId, actor, activeDeptHeader);
    }

    public IssueTicketDto create(IssueTicketUpsertRequest request, String actor, String activeDeptHeader) {
        try {
            issueSourcePolicy.validateClientCreate(request, activeDeptHeader);
            if (request != null && StringUtils.isNotBlank(request.getSourceType()) && request.getSourceId() != null) {
                Optional<IssueTicketDto> existing = findBySource(
                    request.getSourceType(),
                    request.getSourceId(),
                    actor,
                    activeDeptHeader
                );
                if (existing.isPresent()) {
                    return existing.orElseThrow();
                }
            }
            return createInternal(request, actor, activeDeptHeader, true);
        } catch (RuntimeException ex) {
            recordIssueFailure("GOV_ISSUE_CREATE", "UNASSIGNED", "新建问题单失败", ex);
            throw ex;
        }
    }

    private IssueTicketDto createInternal(
        IssueTicketUpsertRequest request,
        String actor,
        String activeDeptHeader,
        boolean enforceDatasetRead
    ) {
        String effectiveActor = StringUtils.defaultIfBlank(actor, SecurityUtils.getCurrentUserLogin().orElse("anonymous"));
        GovIssueTicket ticket = new GovIssueTicket();
        applyUpsert(ticket, request, activeDeptHeader, enforceDatasetRead);
        if (StringUtils.isBlank(ticket.getStatus())) {
            ticket.setStatus(STATUS_OPEN);
        }
        if (StringUtils.isBlank(ticket.getPriority())) {
            ticket.setPriority(properties.getIssue().getDefaultPriority());
        }
        if (StringUtils.isBlank(ticket.getAssignedTo())) {
            ticket.setAssignedTo(properties.getIssue().getDefaultAssignee());
        }
        if (StringUtils.isNotBlank(ticket.getAssignedTo()) && ticket.getAssignedAt() == null) {
            ticket.setAssignedAt(Instant.now());
        }
        if (ticket.getDueAt() == null) {
            ticket.setDueAt(Instant.now().plus(resolveSlaDuration(ticket.getSeverity(), ticket.getPriority())));
        }
        setSource(ticket, request.getSourceType(), request.getSourceId());
        ticketRepository.save(ticket);
        ticketRepository.flush();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "新建问题单：" + StringUtils.defaultString(ticket.getTitle(), ticket.getId().toString()));
        payload.put("title", ticket.getTitle());
        payload.put("status", ticket.getStatus());
        payload.put("severity", ticket.getSeverity());
        payload.put("priority", ticket.getPriority());
        payload.put("dataLevel", ticket.getDataLevel());
        payload.put("datasetId", ticket.getDatasetId() != null ? ticket.getDatasetId().toString() : null);
        payload.put("ownerDept", ticket.getOwnerDept());
        payload.put("assignedTo", ticket.getAssignedTo());
        payload.put("actor", effectiveActor);
        if (!Boolean.TRUE.equals(suppressAutomaticAudit.get())) {
            qualityAuditRecorder.recordAction("GOV_ISSUE_CREATE", AuditStage.SUCCESS, ticket.getId().toString(), payload);
        }

        return GovernanceMapper.toDto(ticket);
    }

    public IssueTicketDto createOrTouch(
        String sourceType,
        UUID sourceRefId,
        IssueTicketUpsertRequest request,
        String actor,
        String note
    ) {
        return createOrTouch(sourceType, sourceRefId, request, actor, note, null);
    }

    public IssueTicketDto createOrTouch(
        String sourceType,
        UUID sourceRefId,
        IssueTicketUpsertRequest request,
        String actor,
        String note,
        String activeDeptHeader
    ) {
        return createOrTouchResult(sourceType, sourceRefId, request, actor, note, activeDeptHeader, false).ticket();
    }

    public CreateOrTouchResult createOrTouchWithDisposition(
        String sourceType,
        UUID sourceRefId,
        IssueTicketUpsertRequest request,
        String actor,
        String note
    ) {
        return createOrTouchResult(sourceType, sourceRefId, request, actor, note, null, true);
    }

    private CreateOrTouchResult createOrTouchResult(
        String sourceType,
        UUID sourceRefId,
        IssueTicketUpsertRequest request,
        String actor,
        String note,
        String activeDeptHeader,
        boolean suppressBuiltInAudit
    ) {
        Boolean previousSuppression = suppressAutomaticAudit.get();
        suppressAutomaticAudit.set(suppressBuiltInAudit || Boolean.TRUE.equals(previousSuppression));
        try {
            String normalizedType = StringUtils.trimToNull(sourceType);
            if (normalizedType != null) {
                normalizedType = normalizedType.trim().toUpperCase(Locale.ROOT);
            }

            if (normalizedType != null && sourceRefId != null) {
                GovIssueTicket existing = ticketRepository
                    .findFirstBySourceTypeIgnoreCaseAndSourceRefIdAndStatusInOrderByCreatedDateDesc(normalizedType, sourceRefId, OPEN_STATUSES)
                    .orElse(null);
                if (existing != null) {
                    boolean enriched = enrichTicket(existing, request, activeDeptHeader);
                    if (enriched) {
                        ticketRepository.save(existing);
                        ticketRepository.flush();
                    }
                    if (StringUtils.isNotBlank(note)) {
                        IssueActionRequest action = new IssueActionRequest();
                        action.setActionType("AUTO_NOTE");
                        action.setNotes(note);
                        appendAction(existing.getId(), action, actor, activeDeptHeader);
                    }
                    return new CreateOrTouchResult(GovernanceMapper.toDto(existing), CreateOrTouchDisposition.TOUCHED);
                }
            }

            IssueTicketUpsertRequest effective = request != null ? request : new IssueTicketUpsertRequest();
            effective.setSourceType(normalizedType);
            effective.setSourceId(sourceRefId);
            IssueTicketDto created;
            try {
                created = createInternal(effective, actor, activeDeptHeader, false);
            } catch (RuntimeException ex) {
                recordIssueFailure("GOV_ISSUE_CREATE", "UNASSIGNED", "新建问题单失败", ex);
                throw ex;
            }
            if (created != null && created.getId() != null && StringUtils.isNotBlank(note)) {
                IssueActionRequest action = new IssueActionRequest();
                action.setActionType("AUTO_NOTE");
                action.setNotes(note);
                appendAction(created.getId(), action, actor, activeDeptHeader);
            }
            return new CreateOrTouchResult(created, CreateOrTouchDisposition.CREATED);
        } finally {
            if (Boolean.TRUE.equals(previousSuppression)) {
                suppressAutomaticAudit.set(Boolean.TRUE);
            } else {
                suppressAutomaticAudit.remove();
            }
        }
    }

    public IssueTicketDto update(UUID id, IssueTicketUpsertRequest request, String actor, String activeDeptHeader) {
        try {
            return updateInternal(id, request, actor, activeDeptHeader);
        } catch (RuntimeException ex) {
            recordIssueFailure("GOV_ISSUE_UPDATE", resourceId(id), "更新问题单失败", ex);
            throw ex;
        }
    }

    private IssueTicketDto updateInternal(UUID id, IssueTicketUpsertRequest request, String actor, String activeDeptHeader) {
        String effectiveActor = StringUtils.defaultIfBlank(actor, SecurityUtils.getCurrentUserLogin().orElse("anonymous"));
        GovIssueTicket ticket = ticketRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        ensureWritable(ticket, effectiveActor, activeDeptHeader);
        issueSourcePolicy.assertImmutable(ticket, request, activeDeptHeader);
        String previousStatus = normalizeIssueStatus(ticket.getStatus());
        applyUpsert(ticket, request, activeDeptHeader, true);
        if (StringUtils.isNotBlank(request.getSeverity())) {
            ticket.setSeverity(request.getSeverity());
        }
        if (StringUtils.isNotBlank(request.getPriority())) {
            ticket.setPriority(request.getPriority());
        }
        if (request.getDueAt() != null) {
            ticket.setDueAt(request.getDueAt());
        }
        if (StringUtils.isNotBlank(request.getResolution())) {
            ticket.setResolution(request.getResolution().trim());
        }
        String requestedStatus = normalizeIssueStatus(request.getStatus());
        if (requestedStatus != null) {
            ensureStatusTransition(previousStatus, requestedStatus);
            ticket.setStatus(requestedStatus);
            if (STATUS_RESOLVED.equals(requestedStatus) && ticket.getResolvedAt() == null) {
                ticket.setResolvedAt(Instant.now());
            }
            if (!STATUS_RESOLVED.equals(requestedStatus) && !STATUS_CLOSED.equals(requestedStatus)) {
                ticket.setResolvedAt(null);
                ticket.setResolution(null);
            }
        }
        if ((STATUS_RESOLVED.equals(ticket.getStatus()) || STATUS_CLOSED.equals(ticket.getStatus())) && StringUtils.isBlank(ticket.getResolution())) {
            throw new IllegalArgumentException("问题单进入已解决/已关闭状态时必须填写处理结论");
        }
        if (StringUtils.isNotBlank(ticket.getAssignedTo()) && ticket.getAssignedAt() == null) {
            ticket.setAssignedAt(Instant.now());
        }
        if (ticket.getDueAt() == null) {
            ticket.setDueAt(Instant.now().plus(resolveSlaDuration(ticket.getSeverity(), ticket.getPriority())));
        }
        ticketRepository.save(ticket);
        ticketRepository.flush();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "更新问题单：" + StringUtils.defaultString(ticket.getTitle(), ticket.getId().toString()));
        payload.put("title", ticket.getTitle());
        payload.put("status", ticket.getStatus());
        payload.put("severity", ticket.getSeverity());
        payload.put("priority", ticket.getPriority());
        payload.put("dataLevel", ticket.getDataLevel());
        payload.put("datasetId", ticket.getDatasetId() != null ? ticket.getDatasetId().toString() : null);
        payload.put("ownerDept", ticket.getOwnerDept());
        payload.put("assignedTo", ticket.getAssignedTo());
        payload.put("actor", effectiveActor);
        qualityAuditRecorder.recordAction("GOV_ISSUE_UPDATE", AuditStage.SUCCESS, id.toString(), payload);

        return GovernanceMapper.toDto(ticket);
    }

    public IssueTicketDto close(UUID id, String resolution, String actor, String activeDeptHeader) {
        try {
            return closeInternal(id, resolution, actor, activeDeptHeader);
        } catch (RuntimeException ex) {
            recordIssueFailure("GOV_ISSUE_CLOSE", resourceId(id), "关闭问题单失败", ex);
            throw ex;
        }
    }

    private IssueTicketDto closeInternal(UUID id, String resolution, String actor, String activeDeptHeader) {
        String effectiveActor = StringUtils.defaultIfBlank(actor, SecurityUtils.getCurrentUserLogin().orElse("anonymous"));
        GovIssueTicket ticket = ticketRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        ensureWritable(ticket, effectiveActor, activeDeptHeader);
        ensureStatusTransition(normalizeIssueStatus(ticket.getStatus()), STATUS_CLOSED);
        if (StringUtils.isBlank(resolution)) {
            throw new IllegalArgumentException("关闭问题单必须填写处理结论");
        }
        ticket.setStatus(STATUS_CLOSED);
        ticket.setResolution(resolution.trim());
        ticket.setResolvedAt(Instant.now());
        ticketRepository.save(ticket);
        ticketRepository.flush();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "关闭问题单：" + StringUtils.defaultString(ticket.getTitle(), ticket.getId().toString()));
        payload.put("title", ticket.getTitle());
        payload.put("resolution", resolution);
        payload.put("actor", effectiveActor);
        qualityAuditRecorder.recordAction("GOV_ISSUE_CLOSE", AuditStage.SUCCESS, id.toString(), payload);

        return GovernanceMapper.toDto(ticket);
    }

    public IssueActionDto appendAction(UUID ticketId, IssueActionRequest request, String actor, String activeDeptHeader) {
        try {
            return appendActionInternal(ticketId, request, actor, activeDeptHeader);
        } catch (RuntimeException ex) {
            recordIssueFailure("GOV_ISSUE_ACTION_APPEND", resourceId(ticketId), "追加问题单处理记录失败", ex);
            throw ex;
        }
    }

    private IssueActionDto appendActionInternal(UUID ticketId, IssueActionRequest request, String actor, String activeDeptHeader) {
        String effectiveActor = StringUtils.defaultIfBlank(actor, SecurityUtils.getCurrentUserLogin().orElse("anonymous"));
        GovIssueTicket ticket = ticketRepository.findById(ticketId).orElseThrow(EntityNotFoundException::new);
        ensureWritable(ticket, effectiveActor, activeDeptHeader);
        GovIssueAction action = new GovIssueAction();
        action.setTicket(ticket);
        action.setActionType(StringUtils.defaultIfBlank(request.getActionType(), "NOTE"));
        action.setActor(effectiveActor);
        action.setNotes(StringUtils.trimToNull(request.getNotes()));
        action.setAttachmentsJson(GovernanceMapper.writeJsonList(request.getAttachments()));
        actionRepository.save(action);
        actionRepository.flush();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "追加问题单处理记录：" + StringUtils.defaultString(ticket.getTitle(), ticket.getId().toString()));
        payload.put("title", ticket.getTitle());
        payload.put("actionType", action.getActionType());
        payload.put("actor", effectiveActor);
        if (!Boolean.TRUE.equals(suppressAutomaticAudit.get())) {
            qualityAuditRecorder.recordAction("GOV_ISSUE_ACTION_APPEND", AuditStage.SUCCESS, ticketId.toString(), payload);
        }

        return GovernanceMapper.toDto(action);
    }

    private void recordIssueFailure(String actionCode, String resourceId, String summary, RuntimeException original) {
        if (Boolean.TRUE.equals(suppressAutomaticAudit.get())) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", summary);
        payload.put("errorType", original.getClass().getSimpleName());
        try {
            qualityAuditRecorder.recordFailureAction(actionCode, resourceId, payload);
        } catch (RuntimeException auditFailure) {
            log.warn(
                "event=quality_issue_failure_audit_write_failed actionCode={} resourceId={} errorType={}",
                actionCode,
                resourceId,
                auditFailure.getClass().getSimpleName()
            );
        }
    }

    private String resourceId(UUID id) {
        return id != null ? id.toString() : "UNASSIGNED";
    }

    @Transactional(readOnly = true)
    public Map<String, Object> issueSlaMetrics(int days, String actor, String activeDeptHeader) {
        return queryService.issueSlaMetrics(days, actor, activeDeptHeader);
    }

    private void applyUpsert(
        GovIssueTicket ticket,
        IssueTicketUpsertRequest request,
        String activeDeptHeader,
        boolean enforceDatasetRead
    ) {
        if (ticket == null || request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        ticket.setTitle(StringUtils.trimToNull(request.getTitle()));
        ticket.setSummary(StringUtils.trimToNull(request.getSummary()));
        ticket.setSeverity(StringUtils.trimToNull(request.getSeverity()));
        ticket.setPriority(StringUtils.trimToNull(request.getPriority()));
        String normalizedStatus = normalizeIssueStatus(request.getStatus());
        if (normalizedStatus != null) {
            ticket.setStatus(normalizedStatus);
        }
        ticket.setDataLevel(StringUtils.trimToNull(request.getDataLevel()));
        ticket.setDatasetId(request.getDatasetId());
        ticket.setOwner(StringUtils.trimToNull(request.getOwner()));
        ticket.setAssignedTo(StringUtils.trimToNull(request.getAssignedTo()));
        if (request.getDueAt() != null) {
            ticket.setDueAt(request.getDueAt());
        }
        if (StringUtils.isNotBlank(request.getResolution())) {
            ticket.setResolution(request.getResolution().trim());
        }
        ticket.setTags(GovernanceMapper.joinCsv(request.getTags()));
        normalizeTicketFields(ticket, request, activeDeptHeader, enforceDatasetRead);
    }

    private void normalizeTicketFields(
        GovIssueTicket ticket,
        IssueTicketUpsertRequest request,
        String activeDeptHeader,
        boolean enforceDatasetRead
    ) {
        if (ticket == null) {
            return;
        }
        UUID datasetId = ticket.getDatasetId();
        if (datasetId == null) {
            datasetId = parseDatasetIdFromTags(request != null ? request.getTags() : null);
        }
        if (datasetId == null) {
            datasetId = parseDatasetIdFromCsvTags(ticket.getTags());
        }
        if (datasetId != null) {
            ticket.setDatasetId(datasetId);
        }

        CatalogDataset dataset = null;
        if (datasetId != null) {
            dataset = enforceDatasetRead
                ? datasetReadGuard.requireReadable(datasetId, activeDeptHeader)
                : datasetRepository.findById(datasetId).orElse(null);
        }
        if (datasetId != null && dataset == null) {
            throw new IllegalArgumentException("数据集不存在");
        }

        ticket.setDataLevel(resolveEffectiveDataLevel(ticket.getDataLevel(), dataset));
        if (StringUtils.isBlank(ticket.getOwnerDept())) {
            String inferredDept = dataset != null ? StringUtils.trimToNull(dataset.getOwnerDept()) : null;
            if (StringUtils.isBlank(inferredDept)) {
                inferredDept = departmentResolver.resolve(activeDeptHeader);
            }
            ticket.setOwnerDept(StringUtils.trimToNull(inferredDept));
        }
    }

    private String resolveEffectiveDataLevel(String requestedDataLevel, CatalogDataset dataset) {
        DataLevel requested = DataLevel.normalize(requestedDataLevel);
        DataLevel datasetLevel = dataset != null ? DataLevel.normalize(dataset.getClassification()) : null;
        DataLevel effective = requested;
        if (datasetLevel != null && (effective == null || datasetLevel.rank() > effective.rank())) {
            effective = datasetLevel;
        }
        if (effective == null) {
            effective = DataLevel.DATA_INTERNAL;
        }
        return effective.classification();
    }

    private boolean enrichTicket(GovIssueTicket ticket, IssueTicketUpsertRequest request, String activeDeptHeader) {
        if (ticket == null || request == null) {
            return false;
        }
        UUID beforeDatasetId = ticket.getDatasetId();
        String beforeDataLevel = ticket.getDataLevel();
        String beforeOwnerDept = ticket.getOwnerDept();
        if (ticket.getDatasetId() == null && request.getDatasetId() != null) {
            ticket.setDatasetId(request.getDatasetId());
        }
        if (StringUtils.isBlank(ticket.getDataLevel()) && StringUtils.isNotBlank(request.getDataLevel())) {
            ticket.setDataLevel(StringUtils.trimToNull(request.getDataLevel()));
        }
        if (StringUtils.isBlank(ticket.getOwnerDept())) {
            String inferredDept = departmentResolver.resolve(activeDeptHeader);
            ticket.setOwnerDept(StringUtils.trimToNull(inferredDept));
        }
        normalizeTicketFields(ticket, request, activeDeptHeader, false);
        return !Objects.equals(beforeDatasetId, ticket.getDatasetId()) ||
            !Objects.equals(beforeDataLevel, ticket.getDataLevel()) ||
            !Objects.equals(beforeOwnerDept, ticket.getOwnerDept());
    }

    private UUID parseDatasetIdFromTags(List<String> tags) {
        if (CollectionUtils.isEmpty(tags)) {
            return null;
        }
        for (String tag : tags) {
            UUID parsed = parseDatasetIdTag(tag);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private UUID parseDatasetIdFromCsvTags(String tagsCsv) {
        if (StringUtils.isBlank(tagsCsv)) {
            return null;
        }
        for (String token : tagsCsv.split(",")) {
            UUID parsed = parseDatasetIdTag(token);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private UUID parseDatasetIdTag(String token) {
        if (StringUtils.isBlank(token)) {
            return null;
        }
        String trimmed = token.trim();
        if (!trimmed.startsWith("datasetId=")) {
            return null;
        }
        String value = trimmed.substring("datasetId=".length()).trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void setSource(GovIssueTicket ticket, String sourceType, UUID sourceId) {
        if (StringUtils.isBlank(sourceType)) {
            ticket.setSourceType(null);
            ticket.setSourceRefId(null);
            ticket.setComplianceBatch(null);
            return;
        }
        ticket.setSourceType(sourceType.trim());
        ticket.setSourceRefId(sourceId);
        if ("COMPLIANCE".equalsIgnoreCase(sourceType) && sourceId != null) {
            GovComplianceBatch batch = batchRepository.findById(sourceId).orElseThrow(EntityNotFoundException::new);
            ticket.setComplianceBatch(batch);
        } else {
            ticket.setComplianceBatch(null);
        }
    }

    private void ensureWritable(GovIssueTicket ticket, String actor, String activeDeptHeader) {
        if (ticket == null) {
            throw new EntityNotFoundException("问题单不存在");
        }
        if (isSuperAdmin()) {
            return;
        }
        if (SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.GOVERNANCE_MAINTAINERS)) {
            if (hasInstituteScope()) {
                return;
            }
            if (isOwnerOrAssignee(ticket, actor)) {
                return;
            }
            String ownerDept = StringUtils.trimToNull(ticket.getOwnerDept());
            String activeDept = departmentResolver.resolve(activeDeptHeader);
            if (StringUtils.isBlank(ownerDept) || StringUtils.isBlank(activeDept)) {
                throw new AccessDeniedException("无权限维护该问题单");
            }
            if (organizationVisibilityService.isRoot(ownerDept) || DepartmentUtils.matches(ownerDept, activeDept)) {
                return;
            }
            throw new AccessDeniedException("无权限维护该问题单");
        }
        if (isOwnerOrAssignee(ticket, actor)) {
            return;
        }
        throw new AccessDeniedException("无权限维护该问题单");
    }

    private boolean isOwnerOrAssignee(GovIssueTicket ticket, String actor) {
        if (ticket == null || StringUtils.isBlank(actor)) {
            return false;
        }
        String normalizedActor = actor.trim();
        return StringUtils.equalsIgnoreCase(normalizedActor, ticket.getAssignedTo()) ||
            StringUtils.equalsIgnoreCase(normalizedActor, ticket.getOwner()) ||
            StringUtils.equalsIgnoreCase(normalizedActor, ticket.getCreatedBy());
    }

    private boolean dataLevelAllowed(GovIssueTicket ticket) {
        if (ticket == null) {
            return false;
        }
        DataLevel required = DataLevel.normalize(ticket.getDataLevel());
        if (required == null) {
            required = DataLevel.DATA_INTERNAL;
        }
        int maxRank = accessChecker.resolveHighestDataLevel().rank();
        return required.rank() <= maxRank;
    }

    private boolean isSuperAdmin() {
        if (SecurityUtils.hasCurrentUserAnyOfAuthorities(
            AuthoritiesConstants.OP_ADMIN,
            AuthoritiesConstants.ADMIN
        )) {
            return true;
        }
        return SecurityUtils.isOpAdminAccount();
    }

    private boolean hasInstituteScope() {
        return SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES);
    }

    private String normalizeIssueStatus(String status) {
        String normalized = StringUtils.trimToNull(status);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "NEW", "OPEN" -> STATUS_OPEN;
            case "PROCESSING", "IN_PROGRESS", "PENDING" -> STATUS_IN_PROGRESS;
            case "RESOLVED" -> STATUS_RESOLVED;
            case "CLOSED" -> STATUS_CLOSED;
            default -> throw new IllegalArgumentException("不支持的问题单状态: " + status);
        };
    }

    private String normalizeIssueStatusLenient(String status) {
        try {
            String normalized = normalizeIssueStatus(status);
            return normalized != null ? normalized : STATUS_OPEN;
        } catch (Exception ex) {
            return STATUS_OPEN;
        }
    }

    private void ensureStatusTransition(String fromStatus, String toStatus) {
        String current = StringUtils.defaultIfBlank(fromStatus, STATUS_OPEN);
        if (Objects.equals(current, toStatus)) {
            return;
        }
        List<String> allowed = switch (current) {
            case STATUS_OPEN -> List.of(STATUS_IN_PROGRESS);
            case STATUS_IN_PROGRESS -> List.of(STATUS_RESOLVED);
            case STATUS_RESOLVED -> List.of(STATUS_CLOSED);
            case STATUS_CLOSED -> List.of();
            default -> List.of();
        };
        if (!allowed.contains(toStatus)) {
            throw new IllegalStateException("问题单状态不允许从 " + current + " 变更为 " + toStatus);
        }
    }

    private Duration resolveSlaDuration(String severity, String priority) {
        String priorityKey = StringUtils.defaultString(priority).trim().toUpperCase(Locale.ROOT);
        if (priorityKey.isEmpty()) {
            priorityKey = StringUtils.defaultString(severity).trim().toUpperCase(Locale.ROOT);
        }
        return switch (priorityKey) {
            case "URGENT", "CRITICAL" -> Duration.ofHours(24);
            case "HIGH" -> Duration.ofHours(48);
            case "MEDIUM" -> Duration.ofHours(72);
            case "LOW" -> Duration.ofHours(120);
            default -> Duration.ofHours(72);
        };
    }
}
