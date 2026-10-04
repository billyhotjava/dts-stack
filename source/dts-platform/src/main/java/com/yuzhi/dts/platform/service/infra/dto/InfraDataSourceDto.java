package com.yuzhi.dts.platform.service.infra.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record InfraDataSourceDto(
    UUID id,
    String name,
    String type,
    String connectorKey,
    String connectorName,
    String connectorCategory,
    String defaultEngine,
    String jdbcUrl,
    String username,
    String description,
    String ownerDept,
    Map<String, Object> props,
    Instant createdAt,
    Instant lastUpdatedAt,
    Instant lastVerifiedAt,
    String status,
    boolean hasSecrets,
    String engineVersion,
    String driverVersion,
    Long lastTestElapsedMillis,
    Instant lastHeartbeatAt,
    String heartbeatStatus,
    Integer heartbeatFailureCount,
    String lastError
) {
    public InfraDataSourceDto(
        UUID id,
        String name,
        String type,
        String jdbcUrl,
        String username
    ) {
        this(id, name, type, null, null, null, null, jdbcUrl, username, null, null, Map.of(), null, null, null, null, false, null, null, null, null, null, null, null);
    }

    public InfraDataSourceDto(
        UUID id,
        String name,
        String type,
        String jdbcUrl,
        String username,
        String description,
        String ownerDept,
        Map<String, Object> props,
        Instant createdAt,
        Instant lastUpdatedAt,
        Instant lastVerifiedAt,
        String status,
        boolean hasSecrets,
        String engineVersion,
        String driverVersion,
        Long lastTestElapsedMillis,
        Instant lastHeartbeatAt,
        String heartbeatStatus,
        Integer heartbeatFailureCount,
        String lastError
    ) {
        this(
            id,
            name,
            type,
            null,
            null,
            null,
            null,
            jdbcUrl,
            username,
            description,
            ownerDept,
            props,
            createdAt,
            lastUpdatedAt,
            lastVerifiedAt,
            status,
            hasSecrets,
            engineVersion,
            driverVersion,
            lastTestElapsedMillis,
            lastHeartbeatAt,
            heartbeatStatus,
            heartbeatFailureCount,
            lastError
        );
    }
}
