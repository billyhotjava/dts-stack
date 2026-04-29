package com.yuzhi.dts.platform.service.infra.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class OdsGenerationDtos {

    private OdsGenerationDtos() {}

    public record OdsGenerationRequest(
        String odsSchema,
        String systemCode,
        String bizCode,
        String entityCode,
        Boolean includeTechnicalColumns,
        Boolean includeRawJson,
        String syncMode,
        List<OdsSourceTableRequest> tables
    ) {}

    public record OdsSourceTableRequest(
        String schema,
        String name,
        String comment,
        List<String> primaryKeys,
        List<String> incrementalCandidates,
        List<OdsSourceColumnRequest> columns
    ) {}

    public record OdsSourceColumnRequest(
        String name,
        String targetName,
        String dataType,
        String nativeType,
        String targetDataType,
        Boolean nullable,
        String comment,
        Boolean primaryKey,
        Boolean indexed,
        Boolean incrementalCandidate
    ) {}

    public record OdsGenerationPreviewResponse(
        UUID dataSourceId,
        String dataSourceName,
        String odsSchema,
        List<OdsTablePlanDto> tables,
        String dbtSourceYaml,
        List<String> warnings
    ) {}

    public record OdsGenerationApplyResult(
        UUID dataSourceId,
        String dataSourceName,
        int mappingsUpserted,
        int columnsUpserted,
        int lineageCreated,
        int lineageUpdated,
        int lineageSkipped,
        String dbtMessage,
        List<OdsTablePlanDto> tables,
        List<String> warnings
    ) {}

    public record OdsSyncTaskDraftResponse(
        UUID dataSourceId,
        String dataSourceName,
        String taskName,
        Map<String, Object> payload,
        List<OdsTablePlanDto> tables,
        List<String> warnings
    ) {}

    public record OdsTablePlanDto(
        String sourceSchema,
        String sourceTable,
        String odsSchema,
        String odsTable,
        String systemCode,
        String bizCode,
        String entityCode,
        List<String> primaryKeys,
        List<String> incrementalCandidates,
        List<OdsColumnPlanDto> columns,
        List<OdsTechnicalColumnDto> technicalColumns,
        String createTableSql,
        String dbtSourceYaml,
        Map<String, Object> addaxJobDraft,
        Map<String, Object> airflowDagDraft,
        List<String> warnings
    ) {}

    public record OdsColumnPlanDto(
        String sourceName,
        String targetName,
        String sourceType,
        String odsType,
        String comment,
        Boolean nullable,
        boolean primaryKey,
        boolean indexed,
        boolean incrementalCandidate,
        boolean overrideRequired
    ) {}

    public record OdsTechnicalColumnDto(String name, String dataType, String comment) {}
}
