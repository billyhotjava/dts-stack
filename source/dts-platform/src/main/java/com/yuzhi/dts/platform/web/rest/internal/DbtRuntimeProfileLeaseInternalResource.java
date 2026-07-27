package com.yuzhi.dts.platform.web.rest.internal;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.etl.DbtRuntimeProfileException;
import com.yuzhi.dts.platform.service.etl.DbtRuntimeProfileLeaseService;
import com.yuzhi.dts.platform.service.etl.DbtRuntimeProfileLeaseService.LeaseView;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
    "/api/internal/modeling/materialization/profile-leases"
)
@PreAuthorize(
    "hasAuthority('" +
    AuthoritiesConstants.SERVICE_INTERNAL +
    "') and authentication.name == 'service:dts-airflow'"
)
public class DbtRuntimeProfileLeaseInternalResource {

    private final DbtRuntimeProfileLeaseService leases;

    public DbtRuntimeProfileLeaseInternalResource(
        DbtRuntimeProfileLeaseService leases
    ) {
        this.leases = leases;
    }

    @PostMapping("/{leaseId}/consume")
    public LeaseView consume(@PathVariable UUID leaseId) {
        return leases.consume(leaseId);
    }

    @DeleteMapping("/{leaseId}")
    public ResponseEntity<Void> release(@PathVariable UUID leaseId) {
        leases.release(leaseId);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(DbtRuntimeProfileException.class)
    public ResponseEntity<Map<String, Object>> handle(
        DbtRuntimeProfileException failure
    ) {
        HttpStatus status = switch (failure.code()) {
            case "DBT_PROFILE_LEASE_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case
                "DBT_PROFILE_LEASE_ALREADY_CONSUMED",
                "DBT_PROFILE_LEASE_EXPIRED" -> HttpStatus.CONFLICT;
            case
                "DBT_RUNTIME_PROFILE_ROOT_MISSING",
                "DBT_RUNTIME_PROFILE_ROOT_NOT_TMPFS",
                "DBT_RUNTIME_PROFILE_ROOT_OWNER_INVALID",
                "DBT_RUNTIME_PROFILE_ROOT_PERMISSIONS_INVALID",
                "DBT_RUNTIME_PROFILE_EXPECTED_UID_INVALID",
                "DBT_RUNTIME_PROFILE_ROOT_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        return ResponseEntity
            .status(status)
            .body(
                Map.of(
                    "code",
                    failure.code(),
                    "message",
                    failure.getMessage()
                )
            );
    }
}
