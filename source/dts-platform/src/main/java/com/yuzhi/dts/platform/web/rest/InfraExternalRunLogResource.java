package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.infra.InfraExternalRunLog;
import com.yuzhi.dts.platform.repository.infra.InfraExternalRunLogRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.ClassificationUtils;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.infra.ExternalRunReconciliationService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/infra/external-runs")
@Transactional
public class InfraExternalRunLogResource {

    private static final String INFRA_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).INFRA_MAINTAINERS)";

    private final InfraExternalRunLogRepository repo;
    private final ClassificationUtils classificationUtils;
    private final AuditService audit;
    private final ExternalRunReconciliationService reconciliationService;

    public InfraExternalRunLogResource(
        InfraExternalRunLogRepository repo,
        ClassificationUtils classificationUtils,
        AuditService audit,
        ExternalRunReconciliationService reconciliationService
    ) {
        this.repo = repo;
        this.classificationUtils = classificationUtils;
        this.audit = audit;
        this.reconciliationService = reconciliationService;
    }

    @GetMapping
    public ApiResponse<List<InfraExternalRunLog>> list(
        @RequestParam(required = false) String entryKey,
        @RequestParam(required = false) UUID artifactId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false, defaultValue = "true") boolean enabledOnly,
        @RequestParam(required = false, defaultValue = "200") int limit
    ) {
        String key = trimToNull(entryKey);
        String st = trimToNull(status);
        String kw = trimToNull(keyword);
        int safeLimit = Math.max(1, Math.min(limit, 500));

        List<InfraExternalRunLog> list = repo
            .search(key, artifactId, st, kw, enabledOnly)
            .stream()
            .filter(this::canRead)
            .limit(safeLimit)
            .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "查看外部平台作业运行台账");
        payload.put("entryKey", key);
        payload.put("artifactId", artifactId != null ? artifactId.toString() : null);
        payload.put("status", st);
        payload.put("keyword", kw);
        payload.put("enabledOnly", enabledOnly);
        payload.put("limit", safeLimit);
        payload.put("size", list.size());
        audit.auditAction("INFRA_EXTERNAL_RUN_VIEW", AuditStage.SUCCESS, "list", payload);
        return ApiResponses.ok(list);
    }

    @GetMapping("/{id}")
    public ApiResponse<InfraExternalRunLog> get(@PathVariable UUID id) {
        InfraExternalRunLog entity = repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not found"));
        if (!canRead(entity)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "forbidden");
        }
        audit.auditAction("INFRA_EXTERNAL_RUN_VIEW", AuditStage.SUCCESS, id.toString(), Map.of("summary", "查看外部平台作业运行详情", "id", id.toString()));
        return ApiResponses.ok(entity);
    }

    public record ExternalRunReconcileRequest(Integer rowCountTolerance, BigDecimal amountTolerance) {}

    @PostMapping("/{id}/reconcile")
    public ApiResponse<Map<String, Object>> reconcile(@PathVariable UUID id, @RequestBody(required = false) ExternalRunReconcileRequest request) {
        InfraExternalRunLog entity = repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not found"));
        if (!canRead(entity)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "forbidden");
        }
        int rowTol = request != null && request.rowCountTolerance() != null ? request.rowCountTolerance() : 0;
        BigDecimal amountTol = request != null ? request.amountTolerance() : null;
        Map<String, Object> result = reconciliationService.evaluate(entity, rowTol, amountTol);
        audit.auditAction(
            "INFRA_EXTERNAL_RUN_RECONCILE",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of("summary", "执行装载对账校验", "id", id.toString(), "overallStatus", result.get("overallStatus"))
        );
        return ApiResponses.ok(result);
    }

    @GetMapping("/reconcile/daily")
    public ApiResponse<Map<String, Object>> reconcileDaily(
        @RequestParam(required = false) String date,
        @RequestParam(required = false) String entryKey,
        @RequestParam(required = false, defaultValue = "500") int limit
    ) {
        LocalDate day = null;
        try {
            if (StringUtils.hasText(date)) {
                day = LocalDate.parse(date.trim());
            }
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid date");
        }
        if (day == null) {
            day = LocalDate.now(ZoneOffset.UTC);
        }
        Instant from = ExternalRunReconciliationService.dayStartUtc(day);
        Instant to = ExternalRunReconciliationService.dayEndUtc(day);
        int safeLimit = Math.max(1, Math.min(limit, 2000));
        String key = trimToNull(entryKey);

        List<InfraExternalRunLog> runs = repo
            .findByFinishedAtGreaterThanEqualAndFinishedAtLessThan(from, to)
            .stream()
            .filter(this::canRead)
            .filter(r -> key == null || (r.getEntryKey() != null && r.getEntryKey().equalsIgnoreCase(key)))
            .limit(safeLimit)
            .toList();
        ExternalRunReconciliationService.DailyReport report = reconciliationService.dailyReport(runs, day);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("date", report.date().toString());
        payload.put("total", report.total());
        payload.put("okCount", report.okCount());
        payload.put("failedCount", report.failedCount());
        payload.put("unknownCount", report.unknownCount());
        payload.put("failedSamples", report.failedSamples());
        audit.auditAction(
            "INFRA_EXTERNAL_RUN_DAILY_REPORT",
            AuditStage.SUCCESS,
            day.toString(),
            Map.of("summary", "查看装载对账日报", "date", day.toString(), "entryKey", key, "total", report.total(), "failedCount", report.failedCount())
        );
        return ApiResponses.ok(payload);
    }

    @PostMapping
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<InfraExternalRunLog> create(@Valid @RequestBody InfraExternalRunLog body) {
        InfraExternalRunLog entity = new InfraExternalRunLog();
        apply(entity, body, true);
        if (entity.getStartedAt() == null) {
            entity.setStartedAt(Instant.now());
        }
        entity.setDurationMs(computeDuration(entity.getStartedAt(), entity.getFinishedAt(), entity.getDurationMs()));
        InfraExternalRunLog saved = repo.save(entity);
        audit.auditAction(
            "INFRA_EXTERNAL_RUN_EDIT",
            AuditStage.SUCCESS,
            saved.getId() != null ? saved.getId().toString() : "new",
            Map.of("summary", "新增外部平台作业运行台账", "entryKey", saved.getEntryKey(), "status", saved.getStatus())
        );
        return ApiResponses.ok(saved);
    }

    @PutMapping("/{id}")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<InfraExternalRunLog> update(@PathVariable UUID id, @Valid @RequestBody InfraExternalRunLog body) {
        InfraExternalRunLog entity = repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not found"));
        apply(entity, body, false);
        entity.setDurationMs(computeDuration(entity.getStartedAt(), entity.getFinishedAt(), entity.getDurationMs()));
        InfraExternalRunLog saved = repo.save(entity);
        audit.auditAction(
            "INFRA_EXTERNAL_RUN_EDIT",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of("summary", "更新外部平台作业运行台账", "id", id.toString(), "entryKey", saved.getEntryKey(), "status", saved.getStatus())
        );
        return ApiResponses.ok(saved);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> delete(@PathVariable UUID id) {
        InfraExternalRunLog entity = repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not found"));
        entity.setEnabled(Boolean.FALSE);
        repo.save(entity);
        audit.auditAction("INFRA_EXTERNAL_RUN_DELETE", AuditStage.SUCCESS, id.toString(), Map.of("summary", "禁用外部平台作业运行台账", "id", id.toString()));
        return ApiResponses.ok(Map.of("ok", true));
    }

    private void apply(InfraExternalRunLog target, InfraExternalRunLog patch, boolean creating) {
        String entryKey = trimToNull(patch.getEntryKey());
        if (!StringUtils.hasText(entryKey)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "entryKey required");
        }

        target.setEntryKey(entryKey);
        target.setArtifactId(patch.getArtifactId());
        target.setArtifactType(trimToNull(patch.getArtifactType()));
        target.setArtifactName(trimToNull(patch.getArtifactName()));
        target.setExternalRunId(trimToNull(patch.getExternalRunId()));
        target.setExternalUrl(trimToNull(patch.getExternalUrl()));
        target.setMessage(trimToNull(patch.getMessage()));
        target.setMetricsJson(trimToNull(patch.getMetricsJson()));
        target.setOwnerDept(trimToNull(patch.getOwnerDept()));

        String status = trimToNull(patch.getStatus());
        if (StringUtils.hasText(status)) {
            target.setStatus(status.toUpperCase(Locale.ROOT));
        } else if (creating && !StringUtils.hasText(target.getStatus())) {
            target.setStatus("SUBMITTED");
        }

        String classification = trimToNull(patch.getClassification());
        target.setClassification(classification != null ? classification.toUpperCase(Locale.ROOT) : "INTERNAL");

        if (patch.getStartedAt() != null) {
            target.setStartedAt(patch.getStartedAt());
        } else if (creating && target.getStartedAt() == null) {
            target.setStartedAt(Instant.now());
        }
        if (patch.getFinishedAt() != null) {
            target.setFinishedAt(patch.getFinishedAt());
        }
        if (patch.getDurationMs() != null) {
            target.setDurationMs(patch.getDurationMs());
        }

        if (patch.getEnabled() != null) {
            target.setEnabled(Boolean.TRUE.equals(patch.getEnabled()));
        } else if (creating) {
            target.setEnabled(Boolean.TRUE);
        }
    }

    private Long computeDuration(Instant startedAt, Instant finishedAt, Long existing) {
        if (existing != null && existing.longValue() >= 0) return existing;
        if (startedAt == null || finishedAt == null) return existing;
        long ms = java.time.Duration.between(startedAt, finishedAt).toMillis();
        return Math.max(0, ms);
    }

    private boolean canRead(InfraExternalRunLog entity) {
        if (entity == null) return false;
        if (!classificationUtils.canAccess(entity.getClassification())) return false;
        if (SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES)) return true;
        String userDept = currentClaim("dept_code");
        String ownerDept = trimToNull(entity.getOwnerDept());
        if (!StringUtils.hasText(ownerDept)) return true;
        if (!StringUtils.hasText(userDept)) return false;
        return ownerDept.equalsIgnoreCase(userDept.trim());
    }

    private String currentClaim(String name) {
        try {
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
                Object v = token.getToken().getClaims().get(name);
                return firstTextValue(v);
            }
            if (auth != null && auth.getPrincipal() instanceof org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal principal) {
                Object v = principal.getAttribute(name);
                return firstTextValue(v);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String firstTextValue(Object raw) {
        Object flattened = flatten(raw);
        if (flattened == null) return null;
        String text = flattened.toString();
        return text == null || text.isBlank() ? null : text.trim();
    }

    private Object flatten(Object raw) {
        if (raw == null) return null;
        if (raw instanceof java.util.Collection<?> c) {
            return c.stream().filter(Objects::nonNull).findFirst().orElse(null);
        }
        if (raw.getClass().isArray()) {
            int len = java.lang.reflect.Array.getLength(raw);
            for (int i = 0; i < len; i++) {
                Object e = java.lang.reflect.Array.get(raw, i);
                if (e != null) return e;
            }
            return null;
        }
        return raw;
    }

    private String trimToNull(String s) {
        if (!StringUtils.hasText(s)) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
