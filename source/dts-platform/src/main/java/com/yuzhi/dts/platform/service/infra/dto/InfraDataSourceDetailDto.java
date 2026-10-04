package com.yuzhi.dts.platform.service.infra.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record InfraDataSourceDetailDto(
    UUID id,
    String name,
    String type,
    String connectorKey,
    String jdbcUrl,
    String username,
    String description,
    String ownerDept,
    Map<String, Object> props,
    Map<String, Object> secrets,
    List<ApiSecretSummary> secretSummaries,
    String status,
    Instant lastVerifiedAt
) {
    public InfraDataSourceDetailDto {
        secretSummaries = secretSummaries == null ? List.of() : List.copyOf(secretSummaries);
    }

    public InfraDataSourceDetailDto(
        UUID id,
        String name,
        String type,
        String jdbcUrl,
        String username,
        String description,
        String ownerDept,
        Map<String, Object> props,
        Map<String, Object> secrets,
        List<ApiSecretSummary> secretSummaries,
        String status,
        Instant lastVerifiedAt
    ) {
        this(id, name, type, null, jdbcUrl, username, description, ownerDept, props, secrets, secretSummaries, status, lastVerifiedAt);
    }
}
