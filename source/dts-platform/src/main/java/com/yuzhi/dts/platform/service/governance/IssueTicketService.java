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
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.governance.dto.IssueActionDto;
import com.yuzhi.dts.platform.service.governance.dto.IssueTicketDto;
import com.yuzhi.dts.platform.service.governance.request.IssueActionRequest;
import com.yuzhi.dts.platform.service.governance.request.IssueTicketUpsertRequest;
import jakarta.persistence.EntityNotFoundException;
import java.lang.reflect.Array;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@Service
@Transactional
public class IssueTicketService {

    private static final List<String> OPEN_STATUSES = List.of("NEW", "IN_PROGRESS", "PENDING");

    private final GovIssueTicketRepository ticketRepository;
    private final GovIssueActionRepository actionRepository;
    private final GovComplianceBatchRepository batchRepository;
    private final CatalogDatasetRepository datasetRepository;
    private final com.yuzhi.dts.platform.service.security.AccessChecker accessChecker;
    private final com.yuzhi.dts.platform.service.security.OrganizationVisibilityService organizationVisibilityService;
    private final GovernanceProperties properties;
    private final AuditService auditService;

    public IssueTicketService(
        GovIssueTicketRepository ticketRepository,
        GovIssueActionRepository actionRepository,
        GovComplianceBatchRepository batchRepository,
        CatalogDatasetRepository datasetRepository,
        com.yuzhi.dts.platform.service.security.AccessChecker accessChecker,
        com.yuzhi.dts.platform.service.security.OrganizationVisibilityService organizationVisibilityService,
        GovernanceProperties properties,
        AuditService auditService
    ) {
        this.ticketRepository = ticketRepository;
        this.actionRepository = actionRepository;
        this.batchRepository = batchRepository;
        this.datasetRepository = datasetRepository;
        this.accessChecker = accessChecker;
        this.organizationVisibilityService = organizationVisibilityService;
        this.properties = properties;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<IssueTicketDto> listTickets(
        String status,
        String sourceType,
        UUID datasetId,
        String assignedTo,
        String owner,
        String keyword,
        int limit,
        String actor,
        String activeDeptHeader
    ) {
        List<String> statuses = normalizeStatuses(status);
        Pageable pageable = PageRequest.of(0, normalizeLimit(limit), Sort.Direction.DESC, "createdDate");

        Specification<GovIssueTicket> spec = Specification.where(null);
        if (!CollectionUtils.isEmpty(statuses)) {
            spec = spec.and((root, query, cb) -> root.get("status").in(statuses));
        }
        if (StringUtils.isNotBlank(sourceType)) {
            String normalized = sourceType.trim().toUpperCase(Locale.ROOT);
            spec = spec.and((root, query, cb) -> cb.equal(cb.upper(root.get("sourceType")), normalized));
        }
        if (datasetId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("datasetId"), datasetId));
        }
        String normalizedAssigned = normalizePrincipalFilter(assignedTo, actor);
        if (StringUtils.isNotBlank(normalizedAssigned)) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("assignedTo"), normalizedAssigned));
        }
        String normalizedOwner = normalizePrincipalFilter(owner, actor);
        if (StringUtils.isNotBlank(normalizedOwner)) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("owner"), normalizedOwner));
        }
        if (StringUtils.isNotBlank(keyword)) {
            String term = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            spec =
                spec.and((root, query, cb) ->
                    cb.or(
                        cb.like(cb.lower(root.get("title")), term),
                        cb.like(cb.lower(root.get("summary")), term),
                        cb.like(cb.lower(root.get("tags")), term)
                    )
                );
        }

        String effectiveActor = StringUtils.defaultIfBlank(actor, SecurityUtils.getCurrentUserLogin().orElse("anonymous"));
        return ticketRepository
            .findAll(spec, pageable)
            .getContent()
            .stream()
            .filter(ticket -> canReadTicket(ticket, effectiveActor, activeDeptHeader))
            .map(GovernanceMapper::toDto)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public IssueTicketDto get(UUID id, String actor, String activeDeptHeader) {
        GovIssueTicket ticket = ticketRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        String effectiveActor = StringUtils.defaultIfBlank(actor, SecurityUtils.getCurrentUserLogin().orElse("anonymous"));
        if (!canReadTicket(ticket, effectiveActor, activeDeptHeader)) {
            throw new AccessDeniedException("无权访问该问题单");
        }
        return GovernanceMapper.toDto(ticket);
    }

    public IssueTicketDto create(IssueTicketUpsertRequest request, String actor, String activeDeptHeader) {
        String effectiveActor = StringUtils.defaultIfBlank(actor, SecurityUtils.getCurrentUserLogin().orElse("anonymous"));
        GovIssueTicket ticket = new GovIssueTicket();
        applyUpsert(ticket, request, activeDeptHeader);
        if (StringUtils.isBlank(ticket.getStatus())) {
            ticket.setStatus("NEW");
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
        setSource(ticket, request.getSourceType(), request.getSourceId());
        ticketRepository.save(ticket);

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
        auditService.auditAction("GOV_ISSUE_CREATE", AuditStage.SUCCESS, ticket.getId().toString(), payload);

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
                }
                if (StringUtils.isNotBlank(note)) {
                    IssueActionRequest action = new IssueActionRequest();
                    action.setActionType("AUTO_NOTE");
                    action.setNotes(note);
                    appendAction(existing.getId(), action, actor, activeDeptHeader);
                }
                return GovernanceMapper.toDto(existing);
            }
        }

        IssueTicketUpsertRequest effective = request != null ? request : new IssueTicketUpsertRequest();
        effective.setSourceType(normalizedType);
        effective.setSourceId(sourceRefId);
        IssueTicketDto created = create(effective, actor, activeDeptHeader);
        if (created != null && created.getId() != null && StringUtils.isNotBlank(note)) {
            IssueActionRequest action = new IssueActionRequest();
            action.setActionType("AUTO_NOTE");
            action.setNotes(note);
            appendAction(created.getId(), action, actor, activeDeptHeader);
        }
        return created;
    }

    public IssueTicketDto update(UUID id, IssueTicketUpsertRequest request, String actor, String activeDeptHeader) {
        String effectiveActor = StringUtils.defaultIfBlank(actor, SecurityUtils.getCurrentUserLogin().orElse("anonymous"));
        GovIssueTicket ticket = ticketRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        ensureWritable(ticket, effectiveActor, activeDeptHeader);
        applyUpsert(ticket, request, activeDeptHeader);
        if (StringUtils.isNotBlank(request.getSeverity())) {
            ticket.setSeverity(request.getSeverity());
        }
        if (StringUtils.isNotBlank(request.getPriority())) {
            ticket.setPriority(request.getPriority());
        }
        if (StringUtils.isNotBlank(ticket.getAssignedTo()) && ticket.getAssignedAt() == null) {
            ticket.setAssignedAt(Instant.now());
        }
        ticketRepository.save(ticket);

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
        auditService.auditAction("GOV_ISSUE_UPDATE", AuditStage.SUCCESS, id.toString(), payload);

        return GovernanceMapper.toDto(ticket);
    }

    public IssueTicketDto close(UUID id, String resolution, String actor, String activeDeptHeader) {
        String effectiveActor = StringUtils.defaultIfBlank(actor, SecurityUtils.getCurrentUserLogin().orElse("anonymous"));
        GovIssueTicket ticket = ticketRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        ensureWritable(ticket, effectiveActor, activeDeptHeader);
        ticket.setStatus("CLOSED");
        ticket.setResolution(resolution);
        ticket.setResolvedAt(Instant.now());
        ticketRepository.save(ticket);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "关闭问题单：" + StringUtils.defaultString(ticket.getTitle(), ticket.getId().toString()));
        payload.put("title", ticket.getTitle());
        payload.put("resolution", resolution);
        payload.put("actor", effectiveActor);
        auditService.auditAction("GOV_ISSUE_CLOSE", AuditStage.SUCCESS, id.toString(), payload);

        return GovernanceMapper.toDto(ticket);
    }

    public IssueActionDto appendAction(UUID ticketId, IssueActionRequest request, String actor, String activeDeptHeader) {
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

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "追加问题单处理记录：" + StringUtils.defaultString(ticket.getTitle(), ticket.getId().toString()));
        payload.put("title", ticket.getTitle());
        payload.put("actionType", action.getActionType());
        payload.put("actor", effectiveActor);
        auditService.auditAction("GOV_ISSUE_ACTION_APPEND", AuditStage.SUCCESS, ticketId.toString(), payload);

        return GovernanceMapper.toDto(action);
    }

    private void applyUpsert(GovIssueTicket ticket, IssueTicketUpsertRequest request, String activeDeptHeader) {
        if (ticket == null || request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        ticket.setSourceType(StringUtils.trimToNull(request.getSourceType()));
        ticket.setTitle(StringUtils.trimToNull(request.getTitle()));
        ticket.setSummary(StringUtils.trimToNull(request.getSummary()));
        ticket.setSeverity(StringUtils.trimToNull(request.getSeverity()));
        ticket.setPriority(StringUtils.trimToNull(request.getPriority()));
        ticket.setStatus(StringUtils.trimToNull(request.getStatus()));
        ticket.setDataLevel(StringUtils.trimToNull(request.getDataLevel()));
        ticket.setDatasetId(request.getDatasetId());
        ticket.setOwner(StringUtils.trimToNull(request.getOwner()));
        ticket.setAssignedTo(StringUtils.trimToNull(request.getAssignedTo()));
        ticket.setTags(GovernanceMapper.joinCsv(request.getTags()));
        normalizeTicketFields(ticket, request, activeDeptHeader);
    }

    private void normalizeTicketFields(GovIssueTicket ticket, IssueTicketUpsertRequest request, String activeDeptHeader) {
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

        CatalogDataset dataset = datasetId != null ? datasetRepository.findById(datasetId).orElse(null) : null;
        if (datasetId != null && dataset == null) {
            throw new IllegalArgumentException("数据集不存在");
        }

        if (StringUtils.isBlank(ticket.getDataLevel())) {
            String inferred = dataset != null ? StringUtils.trimToNull(dataset.getClassification()) : null;
            ticket.setDataLevel(StringUtils.defaultIfBlank(inferred, DataLevel.DATA_INTERNAL.classification()));
        }
        if (StringUtils.isBlank(ticket.getOwnerDept())) {
            String inferredDept = dataset != null ? StringUtils.trimToNull(dataset.getOwnerDept()) : null;
            if (StringUtils.isBlank(inferredDept)) {
                inferredDept = resolveActiveDept(activeDeptHeader);
            }
            ticket.setOwnerDept(StringUtils.trimToNull(inferredDept));
        }
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
            String inferredDept = resolveActiveDept(activeDeptHeader);
            ticket.setOwnerDept(StringUtils.trimToNull(inferredDept));
        }
        normalizeTicketFields(ticket, request, activeDeptHeader);
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

    private boolean canReadTicket(GovIssueTicket ticket, String actor, String activeDeptHeader) {
        if (ticket == null) {
            return false;
        }
        if (isSuperAdmin()) {
            return true;
        }
        if (!dataLevelAllowed(ticket)) {
            return false;
        }
        if (isOwnerOrAssignee(ticket, actor)) {
            return true;
        }
        if (hasInstituteScope()) {
            return true;
        }
        String ownerDept = StringUtils.trimToNull(ticket.getOwnerDept());
        if (StringUtils.isBlank(ownerDept)) {
            return false;
        }
        if (organizationVisibilityService.isRoot(ownerDept)) {
            return true;
        }
        String activeDept = resolveActiveDept(activeDeptHeader);
        if (StringUtils.isBlank(activeDept)) {
            return false;
        }
        return DepartmentUtils.matches(ownerDept, activeDept);
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
            String activeDept = resolveActiveDept(activeDeptHeader);
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

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return 50;
        }
        return Math.min(limit, 200);
    }

    private List<String> normalizeStatuses(String raw) {
        if (StringUtils.isBlank(raw)) {
            return OPEN_STATUSES;
        }
        List<String> statuses = new ArrayList<>();
        for (String token : raw.split(",")) {
            if (token == null) {
                continue;
            }
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            statuses.add(trimmed.toUpperCase(Locale.ROOT));
        }
        return statuses.isEmpty() ? OPEN_STATUSES : statuses;
    }

    private String normalizePrincipalFilter(String raw, String actor) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.equalsIgnoreCase("me")) {
            return StringUtils.trimToNull(actor);
        }
        return StringUtils.trimToNull(trimmed);
    }

    private String resolveActiveDept(String activeDeptHeader) {
        if (org.springframework.util.StringUtils.hasText(activeDeptHeader)) {
            return activeDeptHeader.trim();
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        try {
            if (authentication instanceof JwtAuthenticationToken token) {
                String candidate = extractDeptClaim(token.getToken().getClaims().get("dept_code"));
                if (candidate != null) {
                    return candidate;
                }
                candidate = extractDeptClaim(token.getToken().getClaims().get("deptCode"));
                if (candidate != null) {
                    return candidate;
                }
                return extractDeptClaim(token.getToken().getClaims().get("department"));
            }
            if (authentication != null && authentication.getPrincipal() instanceof OAuth2AuthenticatedPrincipal principal) {
                String candidate = extractDeptClaim(principal.getAttribute("dept_code"));
                if (candidate != null) {
                    return candidate;
                }
                candidate = extractDeptClaim(principal.getAttribute("deptCode"));
                if (candidate != null) {
                    return candidate;
                }
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
        if (!org.springframework.util.StringUtils.hasText(text)) {
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
            int length = Array.getLength(raw);
            for (int i = 0; i < length; i++) {
                Object element = Array.get(raw, i);
                if (element != null) {
                    return element;
                }
            }
            return null;
        }
        return raw;
    }
}
