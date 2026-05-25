package com.yuzhi.dts.admin.service.audit;

import com.yuzhi.dts.admin.security.SecurityUtils;
import com.yuzhi.dts.common.net.IpAddressUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * Records audit-log actions performed via the admin UI itself (auditor querying or
 * exporting the audit_entry table). Auditing the auditor is mandatory for compliance
 * — see {@code SECURITY_AUDIT_VIEW} and {@code AUDIT_LOG_EXPORT} button codes.
 */
@Component
public class AuditEntryActionRecorder {

    private static final Logger log = LoggerFactory.getLogger(AuditEntryActionRecorder.class);

    private final AuditV2Service auditV2Service;

    public AuditEntryActionRecorder(AuditV2Service auditV2Service) {
        this.auditV2Service = auditV2Service;
    }

    public void record(
        String buttonCode,
        AuditSearchCriteria criteria,
        Pageable pageable,
        long totalElements,
        int returnedCount,
        HttpServletRequest request
    ) {
        if (auditV2Service == null) {
            return;
        }
        boolean export = ButtonCodes.AUDIT_LOG_EXPORT.equals(buttonCode);
        String actor = SecurityUtils.getCurrentAuditableLogin();
        AuditActionRequest.Builder builder = AuditActionRequest
            .builder(actor, buttonCode)
            .actorName(actor)
            .actorRoles(SecurityUtils.getCurrentUserAuthorities())
            .summary(export ? "导出审计日志" : "查询审计日志")
            .result(AuditResultStatus.SUCCESS)
            .allowEmptyTargets();
        if ("system".equalsIgnoreCase(actor)) {
            builder.allowSystemActor();
        }
        if (request != null) {
            builder.client(clientIp(request), request.getHeader("User-Agent"));
            builder.request(request.getRequestURI(), request.getMethod());
        } else {
            builder.request(export ? "/api/audit-entries/export" : "/api/audit-entries", "GET");
        }
        Map<String, Object> detail = buildDetail(export, criteria, pageable, totalElements, returnedCount);
        if (!detail.isEmpty()) {
            builder.detail("detail", detail);
        }
        builder.metadata("totalElements", totalElements);
        builder.metadata("returnedCount", returnedCount);
        builder.metadata("export", export);
        try {
            auditV2Service.record(builder.build());
        } catch (Exception ex) {
            log.warn("Failed to record audit log action [{}]: {}", buttonCode, ex.getMessage());
        }
    }

    private Map<String, Object> buildDetail(
        boolean export,
        AuditSearchCriteria criteria,
        Pageable pageable,
        long totalElements,
        int returnedCount
    ) {
        LinkedHashMap<String, Object> detail = new LinkedHashMap<>();
        Map<String, Object> filters = buildFilterDetail(criteria);
        if (!filters.isEmpty()) {
            detail.put("filters", filters);
        }
        Map<String, Object> pagination = buildPaginationDetail(pageable, returnedCount, totalElements);
        if (!pagination.isEmpty()) {
            detail.put("pagination", pagination);
        }
        detail.put("returnedCount", returnedCount);
        detail.put("totalElements", totalElements);
        detail.put("actionType", export ? "export" : "query");
        return detail;
    }

    private Map<String, Object> buildFilterDetail(AuditSearchCriteria criteria) {
        LinkedHashMap<String, Object> filters = new LinkedHashMap<>();
        if (criteria == null) {
            return filters;
        }
        putIfHasText(filters, "actor", criteria.actor());
        putIfHasText(filters, "module", criteria.module());
        putIfHasText(filters, "operationType", criteria.operationKind());
        putIfHasText(filters, "actionCode", criteria.action());
        putIfHasText(filters, "operationGroup", criteria.operationGroup());
        putIfHasText(filters, "sourceSystem", criteria.sourceSystem());
        putIfHasText(filters, "result", criteria.result());
        putIfHasText(filters, "targetTable", criteria.targetTable());
        putIfHasText(filters, "targetId", criteria.targetId());
        putIfHasText(filters, "clientIp", criteria.clientIp());
        putIfHasText(filters, "keyword", criteria.keyword());
        putIfHasText(filters, "changeRequestRef", criteria.changeRequestRef());
        if (criteria.hasChangeRequest() != null) {
            filters.put("hasChangeRequest", criteria.hasChangeRequest());
        }
        if (criteria.from() != null) {
            filters.put("from", formatInstant(criteria.from()));
        }
        if (criteria.to() != null) {
            filters.put("to", formatInstant(criteria.to()));
        }
        return filters;
    }

    private Map<String, Object> buildPaginationDetail(Pageable pageable, int returnedCount, long totalElements) {
        LinkedHashMap<String, Object> info = new LinkedHashMap<>();
        info.put("returnedCount", returnedCount);
        info.put("totalElements", totalElements);
        if (pageable == null) {
            info.put("paged", false);
            return info;
        }
        boolean paged = pageable.isPaged();
        info.put("paged", paged);
        if (paged) {
            info.put("page", pageable.getPageNumber());
            info.put("size", pageable.getPageSize());
        }
        if (pageable.getSort() != null && pageable.getSort().isSorted()) {
            info.put("sort", pageable.getSort().toString());
        }
        return info;
    }

    private void putIfHasText(Map<String, Object> target, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            target.put(key, value.trim());
        }
    }

    private String formatInstant(Instant instant) {
        return instant != null ? instant.toString() : null;
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return IpAddressUtils.resolveClientIp(request::getHeader, request.getRemoteAddr());
    }
}
