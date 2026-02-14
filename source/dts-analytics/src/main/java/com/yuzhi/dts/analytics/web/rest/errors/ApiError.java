package com.yuzhi.dts.analytics.web.rest.errors;

import java.time.OffsetDateTime;

public record ApiError(
        OffsetDateTime timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        String requestId) {}
