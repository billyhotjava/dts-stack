package com.yuzhi.dts.platform.service.sql.dto;

import java.time.Instant;
import java.util.UUID;

public record TempViewDto(String viewName, UUID executionId, long rowCount, Instant expiresAt) {}
