package com.yuzhi.dts.platform.service.infra.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SchemaDiscoverDtos {

    private SchemaDiscoverDtos() {}

    public record SchemaDiscoverRequest(
        String schema,
        String tablePattern,
        Integer maxTables,
        Integer sampleLimit,
        Boolean includeColumns,
        Boolean includeIndexes,
        Boolean includeSample,
        Boolean useCache,
        Boolean forceRefresh
    ) {}

    public record SchemaDiscoverResponse(
        UUID dataSourceId,
        String dataSourceName,
        String connectorKey,
        String databaseProduct,
        String databaseVersion,
        List<String> schemas,
        List<SchemaDiscoverTableDto> tables,
        long elapsedMs,
        String status,
        String error,
        Instant discoveredAt,
        boolean cached,
        String cacheKey,
        SchemaDiscoverDriftDto drift
    ) {}

    public record SchemaDiscoverDriftDto(
        int addedTables,
        int removedTables,
        int changedTables,
        String detailsJson
    ) {}

    public record SchemaDiscoverTableDto(
        String schema,
        String name,
        String type,
        String comment,
        boolean view,
        List<SchemaDiscoverColumnDto> columns,
        List<String> primaryKeys,
        List<SchemaDiscoverIndexDto> indexes,
        List<String> incrementalCandidates,
        List<Map<String, Object>> sampleRows
    ) {}

    public record SchemaDiscoverColumnDto(
        String name,
        String dataType,
        String nativeType,
        boolean nullable,
        String defaultValue,
        String comment,
        Integer ordinalPosition,
        boolean primaryKey,
        boolean indexed,
        boolean incrementalCandidate
    ) {}

    public record SchemaDiscoverIndexDto(String name, boolean unique, List<String> columns) {}
}
