package com.yuzhi.dts.platform.service.infra.dto;

public record InfraJdbcDriverUpdateRequest(
    String driverClass,
    String version,
    String jdkSpec
) {}
