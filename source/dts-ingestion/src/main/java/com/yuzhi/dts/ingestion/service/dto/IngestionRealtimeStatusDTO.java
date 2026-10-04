package com.yuzhi.dts.ingestion.service.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record IngestionRealtimeStatusDTO(
    Long taskId,
    String connectorType,
    String status,
    String topicName,
    String consumerGroup,
    String checkpointToken,
    Long lagMs,
    BigDecimal throughputRps,
    Long backlogCount,
    Instant lastHeartbeat,
    Instant updatedAt
) {}
