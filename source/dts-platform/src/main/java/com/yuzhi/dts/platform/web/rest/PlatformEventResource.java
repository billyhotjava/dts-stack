package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.event.PlatformEventOutboxService;
import com.yuzhi.dts.platform.service.event.dto.PlatformEventDto;
import com.yuzhi.dts.platform.service.event.dto.PlatformEventRequest;
import com.yuzhi.dts.platform.service.event.dto.PlatformEventSummaryDto;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/events")
@Transactional
@PreAuthorize("hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).DATA_MAINTAINER_ROLES)")
public class PlatformEventResource {

    private final PlatformEventOutboxService eventService;
    private final AuditService auditService;

    public PlatformEventResource(PlatformEventOutboxService eventService, AuditService auditService) {
        this.eventService = eventService;
        this.auditService = auditService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> list(
        @RequestParam(required = false) String domain,
        @RequestParam(required = false) String eventType,
        @RequestParam(required = false) String aggregateType,
        @RequestParam(required = false) String aggregateId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String dispatchStatus,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<PlatformEventDto> result = eventService.list(
            domain,
            eventType,
            aggregateType,
            aggregateId,
            status,
            dispatchStatus,
            PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200), Sort.by("occurredAt").descending())
        );
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", result.getContent());
        payload.put("total", result.getTotalElements());
        payload.put("page", result.getNumber());
        payload.put("size", result.getSize());
        payload.put("totalPages", result.getTotalPages());
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "查看平台事件列表");
        if (domain != null) auditPayload.put("domain", domain);
        if (eventType != null) auditPayload.put("eventType", eventType);
        if (aggregateType != null) auditPayload.put("aggregateType", aggregateType);
        if (aggregateId != null) auditPayload.put("aggregateId", aggregateId);
        if (status != null) auditPayload.put("status", status);
        if (dispatchStatus != null) auditPayload.put("dispatchStatus", dispatchStatus);
        auditService.auditAction("PLATFORM_EVENT_LIST", AuditStage.SUCCESS, "platform-events", auditPayload);
        return ApiResponses.ok(payload);
    }

    @GetMapping("/summary")
    @Transactional(readOnly = true)
    public ApiResponse<PlatformEventSummaryDto> summary() {
        PlatformEventSummaryDto summary = eventService.summarize();
        auditService.auditAction("PLATFORM_EVENT_SUMMARY", AuditStage.SUCCESS, "platform-events", Map.of("summary", "查看平台事件概要"));
        return ApiResponses.ok(summary);
    }

    @PostMapping
    public ApiResponse<PlatformEventDto> publish(@Valid @RequestBody PlatformEventRequest request) {
        return ApiResponses.ok(eventService.publish(request));
    }
}
