package com.yuzhi.dts.platform.service.sql.dto;

import java.util.UUID;

public record SqlSubmitResponse(UUID executionId, String trinoQueryId, boolean queued) {}
