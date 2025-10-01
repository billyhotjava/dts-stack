package com.yuzhi.dts.platform.service.sql.dto;

public record SqlSubmitRequest(
    String sqlText,
    String datasource,
    String catalog,
    String schema,
    String clientRequestId,
    Integer fetchSize,
    Boolean dryRun
) {}
