package com.yuzhi.dts.platform.service.sql.dto;

public record SqlValidateRequest(
    String sqlText,
    String datasource,
    String catalog,
    String schema,
    String clientRequestId
) {}
