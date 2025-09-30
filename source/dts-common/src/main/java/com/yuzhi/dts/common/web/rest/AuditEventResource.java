package com.yuzhi.dts.common.web.rest;

import com.yuzhi.dts.common.security.AuthoritiesConstants;
import com.yuzhi.dts.common.service.AuditEventCriteria;
import com.yuzhi.dts.common.service.AuditEventQueryService;
import com.yuzhi.dts.common.service.AuditEventService;
import com.yuzhi.dts.common.service.dto.AuditEventDTO;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/audit-events")
public class AuditEventResource {

    private final AuditEventService service;
    private final AuditEventQueryService queryService;

    public AuditEventResource(AuditEventService service, AuditEventQueryService queryService) {
        this.service = service;
        this.queryService = queryService;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.AUDIT_WRITE + "','SCOPE_audit:write')")
    public ResponseEntity<AuditEventDTO> create(
        @Valid @RequestBody AuditEventDTO dto,
        @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey
    ) {
        AuditEventDTO saved = service.create(dto, idempotencyKey);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(saved.getId()).toUri();
        return ResponseEntity.status(HttpStatus.CREATED).location(location).body(saved);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.AUDIT_READ + "','SCOPE_audit:read')")
    public ResponseEntity<Page<AuditEventDTO>> find(
        @RequestParam(required = false) String actor,
        @RequestParam(required = false) String action,
        @RequestParam(required = false) String targetKind,
        @RequestParam(required = false) String targetRefPrefix,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @PageableDefault(sort = "createdAt", size = 20) Pageable pageable
    ) {
        AuditEventCriteria c = new AuditEventCriteria();
        c.setActor(actor);
        c.setAction(action);
        c.setTargetKind(targetKind);
        c.setTargetRefPrefix(targetRefPrefix);
        c.setFrom(from);
        c.setTo(to);
        Page<AuditEventDTO> page = queryService.findByCriteria(c, pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.AUDIT_READ + "','SCOPE_audit:read')")
    public ResponseEntity<AuditEventDTO> getById(@PathVariable Long id) {
        return queryService.findOne(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/_bulk")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.AUDIT_WRITE + "','SCOPE_audit:write')")
    public ResponseEntity<Map<String, Object>> bulkCreate(
        @Valid @RequestBody List<AuditEventDTO> items,
        @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey
    ) {
        if (items == null) items = List.of();
        if (items.size() > 100) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Bulk size must be <= 100"));
        }
        int success = 0;
        List<Map<String, Object>> errors = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            AuditEventDTO dto = items.get(i);
            try {
                service.create(dto, idempotencyKey);
                success++;
            } catch (Exception e) {
                Map<String, Object> err = new HashMap<>();
                err.put("index", i);
                err.put("message", e.getMessage());
                errors.add(err);
            }
        }
        Map<String, Object> body = new HashMap<>();
        body.put("success", success);
        body.put("failed", items.size() - success);
        body.put("errors", errors);
        return ResponseEntity.status(HttpStatus.MULTI_STATUS).body(body);
    }
}
