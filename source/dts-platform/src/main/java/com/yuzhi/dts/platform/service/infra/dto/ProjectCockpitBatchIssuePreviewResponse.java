package com.yuzhi.dts.platform.service.infra.dto;

import java.util.List;
import java.util.UUID;

public record ProjectCockpitBatchIssuePreviewResponse(
    UUID batchId,
    Integer issueRowCount,
    Integer limit,
    List<ProjectCockpitBatchIssueRow> rows
) {}
