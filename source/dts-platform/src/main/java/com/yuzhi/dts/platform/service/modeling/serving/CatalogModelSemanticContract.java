package com.yuzhi.dts.platform.service.modeling.serving;

import java.util.List;

/** Stable wire contract from one serving ModelSpec revision to dts-analytics. */
public final class CatalogModelSemanticContract {

    private CatalogModelSemanticContract() {}

    public record PublishPayload(
        String platformDataSourceId,
        String modelName,
        String tableName,
        String schemaName,
        String label,
        String description,
        String securityLevel,
        String grain,
        String specVersion,
        boolean exposedToModeler,
        List<MetricPayload> metrics,
        List<DimensionPayload> dimensions,
        List<JoinPayload> joins
    ) {
        public PublishPayload {
            metrics = metrics == null ? List.of() : List.copyOf(metrics);
            dimensions = dimensions == null ? List.of() : List.copyOf(dimensions);
            joins = joins == null ? List.of() : List.copyOf(joins);
        }
    }

    public record MetricPayload(
        String name,
        String displayName,
        String aggregation,
        String field,
        String unit,
        String timeDimension,
        String timeGrain,
        String tags,
        String securityLevel,
        String description
    ) {}

    public record DimensionPayload(
        String name,
        String displayName,
        String fieldRole,
        String timeGrain
    ) {}

    public record JoinPayload(
        String to,
        String on,
        String type,
        String relationship,
        boolean fanoutWarning,
        boolean approvalRequired,
        String description
    ) {}
}
