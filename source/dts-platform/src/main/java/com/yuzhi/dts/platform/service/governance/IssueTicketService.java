package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.config.GovernanceProperties;
import com.yuzhi.dts.platform.domain.governance.GovComplianceBatch;
import com.yuzhi.dts.platform.domain.governance.GovIssueAction;
import com.yuzhi.dts.platform.domain.governance.GovIssueTicket;
import com.yuzhi.dts.platform.repository.governance.GovComplianceBatchRepository;
import com.yuzhi.dts.platform.repository.governance.GovIssueActionRepository;
import com.yuzhi.dts.platform.repository.governance.GovIssueTicketRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.governance.dto.IssueActionDto;
import com.yuzhi.dts.platform.service.governance.dto.IssueTicketDto;
import com.yuzhi.dts.platform.service.governance.request.IssueActionRequest;
import com.yuzhi.dts.platform.service.governance.request.IssueTicketUpsertRequest;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class IssueTicketService {

    private final GovIssueTicketRepository ticketRepository;
    private final GovIssueActionRepository actionRepository;
    private final GovComplianceBatchRepository batchRepository;
    private final GovernanceProperties properties;
    private final AuditService auditService;

    public IssueTicketService(
        GovIssueTicketRepository ticketRepository,
        GovIssueActionRepository actionRepository,
        GovComplianceBatchRepository batchRepository,
        GovernanceProperties properties,
        AuditService auditService
    ) {
        this.ticketRepository = ticketRepository;
        this.actionRepository = actionRepository;
        this.batchRepository = batchRepository;
        this.properties = properties;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<IssueTicketDto> listActiveTickets() {
        return ticketRepository
            .findByStatusInOrderByCreatedDateDesc(List.of("NEW", "IN_PROGRESS", "PENDING"))
            .stream()
            .map(GovernanceMapper::toDto)
            .collect(Collectors.toList());
    }

    public IssueTicketDto create(IssueTicketUpsertRequest request, String actor) {
        GovIssueTicket ticket = new GovIssueTicket();
        applyUpsert(ticket, request);
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
        auditService.audit("CREATE", "governance.issue", ticket.getId().toString());
        return GovernanceMapper.toDto(ticket);
    }

    public IssueTicketDto update(UUID id, IssueTicketUpsertRequest request) {
        GovIssueTicket ticket = ticketRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        applyUpsert(ticket, request);
        if (StringUtils.isNotBlank(request.getSeverity())) {
            ticket.setSeverity(request.getSeverity());
        }
        if (StringUtils.isNotBlank(request.getPriority())) {
            ticket.setPriority(request.getPriority());
        }
        ticketRepository.save(ticket);
        auditService.audit("UPDATE", "governance.issue", id.toString());
        return GovernanceMapper.toDto(ticket);
    }

    public IssueTicketDto close(UUID id, String resolution, String actor) {
        GovIssueTicket ticket = ticketRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        ticket.setStatus("CLOSED");
        ticket.setResolution(resolution);
        ticket.setResolvedAt(Instant.now());
        ticketRepository.save(ticket);
        auditService.audit("UPDATE", "governance.issue.close", id.toString());
        return GovernanceMapper.toDto(ticket);
    }

    public IssueActionDto appendAction(UUID ticketId, IssueActionRequest request, String actor) {
        GovIssueTicket ticket = ticketRepository.findById(ticketId).orElseThrow(EntityNotFoundException::new);
        GovIssueAction action = new GovIssueAction();
        action.setTicket(ticket);
        action.setActionType(StringUtils.defaultIfBlank(request.getActionType(), "NOTE"));
        action.setActor(actor);
        action.setNotes(StringUtils.trimToNull(request.getNotes()));
        action.setAttachmentsJson(GovernanceMapper.writeJsonList(request.getAttachments()));
        actionRepository.save(action);
        auditService.audit("UPDATE", "governance.issue.action", ticketId.toString());
        return GovernanceMapper.toDto(action);
    }

    private void applyUpsert(GovIssueTicket ticket, IssueTicketUpsertRequest request) {
        ticket.setSourceType(StringUtils.trimToNull(request.getSourceType()));
        ticket.setTitle(StringUtils.trimToNull(request.getTitle()));
        ticket.setSummary(StringUtils.trimToNull(request.getSummary()));
        ticket.setSeverity(StringUtils.trimToNull(request.getSeverity()));
        ticket.setPriority(StringUtils.trimToNull(request.getPriority()));
        ticket.setStatus(StringUtils.trimToNull(request.getStatus()));
        ticket.setDataLevel(StringUtils.trimToNull(request.getDataLevel()));
        ticket.setOwner(StringUtils.trimToNull(request.getOwner()));
        ticket.setAssignedTo(StringUtils.trimToNull(request.getAssignedTo()));
        ticket.setTags(GovernanceMapper.joinCsv(request.getTags()));
    }

    private void setSource(GovIssueTicket ticket, String sourceType, UUID sourceId) {
        if (StringUtils.isBlank(sourceType) || sourceId == null) {
            ticket.setComplianceBatch(null);
            return;
        }
        if ("COMPLIANCE".equalsIgnoreCase(sourceType)) {
            GovComplianceBatch batch = batchRepository.findById(sourceId).orElseThrow(EntityNotFoundException::new);
            ticket.setComplianceBatch(batch);
        }
    }
}
