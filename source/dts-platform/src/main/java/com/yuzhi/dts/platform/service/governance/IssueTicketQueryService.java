package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.domain.governance.GovIssueTicket;
import com.yuzhi.dts.platform.repository.governance.GovIssueTicketRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.DepartmentUtils;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.security.policy.DataLevel;
import com.yuzhi.dts.platform.service.governance.dto.IssueTicketDto;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import com.yuzhi.dts.platform.service.security.OrganizationVisibilityService;
import jakarta.persistence.EntityNotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.util.CollectionUtils;

/** Read-side issue filtering, visibility and SLA aggregation. */
final class IssueTicketQueryService {

    private static final String STATUS_OPEN = "OPEN";
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_RESOLVED = "RESOLVED";
    private static final String STATUS_CLOSED = "CLOSED";
    private static final Set<String> CLOSED_STATUSES = Set.of(STATUS_CLOSED);

    private final GovIssueTicketRepository ticketRepository;
    private final AccessChecker accessChecker;
    private final OrganizationVisibilityService organizationVisibilityService;
    private final QualityEffectiveDepartmentResolver departmentResolver;

    IssueTicketQueryService(
        GovIssueTicketRepository ticketRepository,
        AccessChecker accessChecker,
        OrganizationVisibilityService organizationVisibilityService,
        QualityEffectiveDepartmentResolver departmentResolver
    ) {
        this.ticketRepository = ticketRepository;
        this.accessChecker = accessChecker;
        this.organizationVisibilityService = organizationVisibilityService;
        this.departmentResolver = departmentResolver;
    }

    List<IssueTicketDto> listTickets(
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
        if (StringUtils.isNotBlank(priority)) {
            String normalizedPriority = priority.trim().toUpperCase(Locale.ROOT);
            spec = spec.and((root, query, cb) -> cb.equal(cb.upper(root.get("priority")), normalizedPriority));
        }
        if (overdue != null) {
            Instant now = Instant.now();
            if (Boolean.TRUE.equals(overdue)) {
                spec = spec.and((root, query, cb) ->
                    cb.and(cb.lessThan(root.get("dueAt"), now), cb.not(root.get("status").in(CLOSED_STATUSES)))
                );
            } else {
                spec = spec.and((root, query, cb) ->
                    cb.or(
                        cb.isNull(root.get("dueAt")),
                        cb.greaterThanOrEqualTo(root.get("dueAt"), now),
                        root.get("status").in(CLOSED_STATUSES)
                    )
                );
            }
        }
        if (StringUtils.isNotBlank(keyword)) {
            String term = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) ->
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
            .toList();
    }

    IssueTicketDto get(UUID id, String actor, String activeDeptHeader) {
        GovIssueTicket ticket = ticketRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        String effectiveActor = StringUtils.defaultIfBlank(actor, SecurityUtils.getCurrentUserLogin().orElse("anonymous"));
        if (!canReadTicket(ticket, effectiveActor, activeDeptHeader)) {
            throw new AccessDeniedException("无权访问该问题单");
        }
        return GovernanceMapper.toDto(ticket);
    }

    Optional<IssueTicketDto> findBySource(String sourceType, UUID sourceRefId, String actor, String activeDeptHeader) {
        String normalizedType = StringUtils.trimToNull(sourceType);
        if (normalizedType == null || sourceRefId == null) {
            throw new IllegalArgumentException("sourceType 和 sourceId 不能为空");
        }
        GovIssueTicket ticket = ticketRepository
            .findFirstBySourceTypeIgnoreCaseAndSourceRefIdOrderByCreatedDateDesc(normalizedType, sourceRefId)
            .orElse(null);
        if (ticket == null) {
            return Optional.empty();
        }
        String effectiveActor = StringUtils.defaultIfBlank(actor, SecurityUtils.getCurrentUserLogin().orElse("anonymous"));
        if (!canReadTicket(ticket, effectiveActor, activeDeptHeader)) {
            throw new AccessDeniedException("无权访问该问题单");
        }
        return Optional.of(GovernanceMapper.toDto(ticket));
    }

    Map<String, Object> issueSlaMetrics(int days, String actor, String activeDeptHeader) {
        int windowDays = Math.max(1, Math.min(days, 365));
        Instant since = Instant.now().minus(Duration.ofDays(windowDays));
        String effectiveActor = StringUtils.defaultIfBlank(actor, SecurityUtils.getCurrentUserLogin().orElse("anonymous"));
        List<GovIssueTicket> visible = ticketRepository
            .findByCreatedDateAfterOrderByCreatedDateAsc(since)
            .stream()
            .filter(ticket -> canReadTicket(ticket, effectiveActor, activeDeptHeader))
            .toList();
        Instant now = Instant.now();
        long total = visible.size();
        long open = visible.stream().filter(ticket -> !STATUS_CLOSED.equals(normalizeIssueStatusLenient(ticket.getStatus()))).count();
        long overdue = visible
            .stream()
            .filter(ticket -> !STATUS_CLOSED.equals(normalizeIssueStatusLenient(ticket.getStatus())))
            .filter(ticket -> ticket.getDueAt() != null && ticket.getDueAt().isBefore(now))
            .count();
        double avgHandlingHours = visible
            .stream()
            .filter(ticket -> ticket.getCreatedDate() != null)
            .mapToLong(ticket -> {
                Instant end = ticket.getResolvedAt() != null ? ticket.getResolvedAt() : now;
                return end.isBefore(ticket.getCreatedDate()) ? 0L : Duration.between(ticket.getCreatedDate(), end).toMinutes();
            })
            .average()
            .orElse(0.0D) / 60.0D;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("windowDays", windowDays);
        payload.put("total", total);
        payload.put("open", open);
        payload.put("overdue", overdue);
        payload.put("overdueRate", open <= 0 ? 0.0D : (double) overdue * 100.0D / (double) open);
        payload.put("avgHandlingHours", avgHandlingHours);
        return payload;
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
        if (isOwnerOrAssignee(ticket, actor) || hasInstituteScope()) {
            return true;
        }
        String ownerDept = StringUtils.trimToNull(ticket.getOwnerDept());
        if (StringUtils.isBlank(ownerDept)) {
            return false;
        }
        if (organizationVisibilityService.isRoot(ownerDept)) {
            return true;
        }
        String activeDept = departmentResolver.resolve(activeDeptHeader);
        return StringUtils.isNotBlank(activeDept) && DepartmentUtils.matches(ownerDept, activeDept);
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
        DataLevel required = DataLevel.normalize(ticket.getDataLevel());
        if (required == null) {
            required = DataLevel.DATA_INTERNAL;
        }
        return required.rank() <= accessChecker.resolveHighestDataLevel().rank();
    }

    private boolean isSuperAdmin() {
        return SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.OP_ADMIN, AuthoritiesConstants.ADMIN) ||
            SecurityUtils.isOpAdminAccount();
    }

    private boolean hasInstituteScope() {
        return SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES);
    }

    private int normalizeLimit(int limit) {
        return limit <= 0 ? 50 : Math.min(limit, 200);
    }

    private List<String> normalizeStatuses(String raw) {
        if (StringUtils.isBlank(raw)) {
            return List.of();
        }
        List<String> statuses = new ArrayList<>();
        for (String token : raw.split(",")) {
            String trimmed = token != null ? token.trim() : "";
            if (!trimmed.isEmpty()) {
                statuses.add(normalizeIssueStatus(trimmed));
            }
        }
        return statuses;
    }

    private String normalizePrincipalFilter(String raw, String actor) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.equalsIgnoreCase("me") ? StringUtils.trimToNull(actor) : StringUtils.trimToNull(trimmed);
    }

    private String normalizeIssueStatus(String status) {
        String normalized = StringUtils.trimToNull(status);
        if (normalized == null) {
            return null;
        }
        return switch (normalized.toUpperCase(Locale.ROOT)) {
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
        } catch (RuntimeException ignored) {
            return STATUS_OPEN;
        }
    }
}
