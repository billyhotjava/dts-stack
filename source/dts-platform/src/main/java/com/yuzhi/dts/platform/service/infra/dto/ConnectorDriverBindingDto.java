package com.yuzhi.dts.platform.service.infra.dto;

public record ConnectorDriverBindingDto(
    String policy,
    String status,
    String driverClass,
    String fileName,
    String version,
    String jdkSpec,
    String message
) {}
