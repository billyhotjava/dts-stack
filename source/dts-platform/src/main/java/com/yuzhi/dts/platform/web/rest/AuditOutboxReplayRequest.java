package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.audit.AuditOutboxReplayReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record AuditOutboxReplayRequest(
    @NotBlank @Pattern(regexp = "^[0-9a-f]{64}$") String expectedPayloadHash,
    @NotNull AuditOutboxReplayReason reasonCode
) {}
