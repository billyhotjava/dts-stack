package com.yuzhi.dts.platform.service.infra.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record DataSourceRequest(
    @NotBlank String name,
    @NotBlank String type,
    @NotBlank String jdbcUrl,
    String username,
    String description,
    Map<String, Object> props,
    Map<String, Object> secrets
) {}
