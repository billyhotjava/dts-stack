package com.yuzhi.dts.platform.web.rest.internal;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.rollback.RollbackInvalidationException;
import com.yuzhi.dts.platform.service.rollback.RollbackInvalidationService;
import com.yuzhi.dts.platform.service.rollback.RollbackInvalidationService.CompletionView;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/rollback-invalidation/completions")
@PreAuthorize(
    "hasAuthority('" + AuthoritiesConstants.SERVICE_INTERNAL + "') and authentication.name == 'service:dts-ingestion'"
)
public class RollbackInvalidationCompletionResource {

    private final RollbackInvalidationService invalidations;

    public RollbackInvalidationCompletionResource(RollbackInvalidationService invalidations) {
        this.invalidations = invalidations;
    }

    @PostMapping
    public CompletionView complete(@Valid @RequestBody RollbackInvalidationCompletionRequest request) {
        return invalidations.complete(request.toCommand());
    }

    @ExceptionHandler(RollbackInvalidationException.class)
    public ResponseEntity<Map<String, String>> handleRollbackInvalidation(RollbackInvalidationException failure) {
        return ResponseEntity
            .status(failure.status())
            .body(Map.of("code", failure.code(), "message", failure.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidRequest(IllegalArgumentException failure) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(Map.of("code", "ROLLBACK_INVALIDATION_REQUEST_INVALID", "message", failure.getMessage()));
    }
}
