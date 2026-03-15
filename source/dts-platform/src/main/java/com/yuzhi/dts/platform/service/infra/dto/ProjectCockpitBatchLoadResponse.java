package com.yuzhi.dts.platform.service.infra.dto;

import java.util.UUID;

public record ProjectCockpitBatchLoadResponse(
        UUID batchId,
        String batchCode,
        Integer loadedRowCount,
        Integer issueCount,
        String status) {}
