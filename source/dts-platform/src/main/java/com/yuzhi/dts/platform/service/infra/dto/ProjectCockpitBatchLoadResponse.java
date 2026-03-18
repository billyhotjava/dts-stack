package com.yuzhi.dts.platform.service.infra.dto;

import java.util.UUID;

public record ProjectCockpitBatchLoadResponse(
        UUID batchId,
        String batchCode,
        Integer loadedRowCount,
        Integer acceptedRowCount,
        Integer rejectedRowCount,
        Integer warningRowCount,
        Integer issueCount,
        String status) {}
