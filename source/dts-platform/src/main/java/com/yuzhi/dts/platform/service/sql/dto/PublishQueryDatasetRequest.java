package com.yuzhi.dts.platform.service.sql.dto;

public record PublishQueryDatasetRequest(
    Integer versionNo,
    String changeSummary
) {}
