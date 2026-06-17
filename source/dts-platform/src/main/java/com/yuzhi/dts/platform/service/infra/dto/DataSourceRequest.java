package com.yuzhi.dts.platform.service.infra.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record DataSourceRequest(
    @NotBlank String name,
    @NotBlank String type,
    String connectorKey,
    String jdbcUrl,
    String username,
    String description,
    String ownerDept,
    Map<String, Object> props,
    Map<String, Object> secrets
) {
    public DataSourceRequest(
        String name,
        String type,
        String jdbcUrl,
        String username,
        String description,
        Map<String, Object> props,
        Map<String, Object> secrets
    ) {
        this(name, type, null, jdbcUrl, username, description, null, props, secrets);
    }

    public DataSourceRequest(
        String name,
        String type,
        String connectorKey,
        String jdbcUrl,
        String username,
        String description,
        Map<String, Object> props,
        Map<String, Object> secrets
    ) {
        this(name, type, connectorKey, jdbcUrl, username, description, null, props, secrets);
    }
}
