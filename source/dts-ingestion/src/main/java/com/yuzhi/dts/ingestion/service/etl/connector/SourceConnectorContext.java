package com.yuzhi.dts.ingestion.service.etl.connector;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SourceConnectorContext(
    Long taskId,
    String taskName,
    UUID sourceDataSourceId,
    String sourceType,
    Map<String, Object> sourceConfig,
    String syncMode,
    Map<String, Object> syncConfig,
    List<String> streams
) {}

