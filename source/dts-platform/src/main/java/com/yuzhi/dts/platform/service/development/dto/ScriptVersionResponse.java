package com.yuzhi.dts.platform.service.development.dto;

import java.time.Instant;
import java.util.UUID;

public record ScriptVersionResponse(
    UUID id,
    UUID scriptId,
    Integer versionNo,
    String status,
    String content,
    String changeSummary,
    String createdBy,
    Instant createdDate
) {}
