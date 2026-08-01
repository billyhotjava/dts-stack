package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.audit.AuditOutboxReplayException;
import com.yuzhi.dts.platform.service.audit.AuditOutboxReplayService;
import com.yuzhi.dts.platform.service.audit.AuditOutboxReplayService.ReplayView;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-outbox")
public class AuditOutboxReplayResource {

    private static final String REPLAY_AUTHORIZATION =
        "hasAnyAuthority('ROLE_SYS_ADMIN', 'ROLE_ADMIN', 'ROLE_OP_ADMIN')";

    private final AuditOutboxReplayService service;

    public AuditOutboxReplayResource(AuditOutboxReplayService service) {
        this.service = service;
    }

    @PostMapping("/{id}/replay")
    @PreAuthorize(REPLAY_AUTHORIZATION)
    public ResponseEntity<ApiResponse<ReplayView>> replay(
        @PathVariable("id") UUID id,
        @Valid @RequestBody AuditOutboxReplayRequest request
    ) {
        ReplayView replay = service.replay(id, request.expectedPayloadHash(), request.reasonCode());
        return ResponseEntity.accepted().body(ApiResponses.ok("Replay accepted", replay));
    }

    @ExceptionHandler(AuditOutboxReplayException.class)
    public ResponseEntity<ApiResponse<Object>> handleReplayError(AuditOutboxReplayException exception) {
        HttpStatus status = switch (exception.kind()) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status).body(
            new ApiResponse<>(ResultStatus.ERROR.getCode(), exception.getMessage(), exception.code(), null)
        );
    }
}
