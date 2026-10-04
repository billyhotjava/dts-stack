package com.yuzhi.dts.copilot.analytics.web.rest.errors;

import java.time.OffsetDateTime;

public record ApiError(
        OffsetDateTime timestamp,
        int status,
        String error,
        String code,
        boolean retryable,
        String message,
        String path,
        String requestId) {}
