package com.yuzhi.dts.platform.service.governance;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Versioned analysis semantics; all field references are public keys, never SQL fragments. */
public final class IndicatorAnalysisContract {
    private IndicatorAnalysisContract() {}
    public record VersionRef(UUID id, String version) {
        public VersionRef {
            if (id == null || version == null || !version.matches("v[1-9][0-9]{0,8}")) {
                throw new IllegalArgumentException("指标固定版本无效");
            }
        }
    }
    public record Predicate(String fieldRef, String op, Object value) {}
    public record TimeBinding(String fieldRef, String fieldName, String timezone, String grain) {}
    public record TimeRange(String fieldRef, String start, String endExclusive, String timezone) {}
    public record Config(
        Map<String, String> dimensionBindings, TimeBinding timeBinding,
        List<String> resultGrain, List<String> allowedAggregations,
        List<VersionRef> modifierRefs, List<Predicate> predicates,
        VersionRef periodRef, String periodMode, boolean missingGroupsAsZero
    ) {
        public Config {
            dimensionBindings = dimensionBindings == null ? Map.of() : Map.copyOf(dimensionBindings);
            resultGrain = resultGrain == null ? List.of() : List.copyOf(resultGrain);
            allowedAggregations = allowedAggregations == null ? List.of() : List.copyOf(allowedAggregations);
            modifierRefs = modifierRefs == null ? List.of() : List.copyOf(modifierRefs);
            predicates = predicates == null ? List.of() : List.copyOf(predicates);
            if (dimensionBindings.size() > 16 || resultGrain.size() > 16 || modifierRefs.size() > 16 || predicates.size() > 32) {
                throw new IllegalArgumentException("指标分析配置超出预算");
            }
        }
    }
    public record Query(List<VersionRef> indicatorRefs, TimeRange timeRange, List<String> dimensions,
                        List<Predicate> filters, int limit, String scope) {
        public Query(List<VersionRef> indicatorRefs, TimeRange timeRange, List<String> dimensions, List<Predicate> filters, int limit) {
            this(indicatorRefs, timeRange, dimensions, filters, limit, "RANGE");
        }
        public Query {
            scope = scope == null ? "RANGE" : scope;
            if (!List.of("RANGE", "LATEST_PERIOD", "ALL_DATA").contains(scope)) throw new IllegalArgumentException("查询范围无效");
            indicatorRefs = indicatorRefs == null ? List.of() : List.copyOf(indicatorRefs);
            dimensions = dimensions == null ? List.of() : List.copyOf(dimensions);
            filters = filters == null ? List.of() : List.copyOf(filters);
            if (indicatorRefs.isEmpty() || indicatorRefs.size() > 10 || dimensions.size() > 5 || filters.size() > 32 || limit < 1 || limit > 1000) {
                throw new IllegalArgumentException("指标查询预算无效：指标最多10个、维度5个、筛选32项、结果1000行");
            }
            if (dimensions.stream().distinct().count() != dimensions.size()) throw new IllegalArgumentException("分组维度不能重复");
        }
    }
    public record Result(List<String> columns, List<Map<String, Object>> rows, List<VersionRef> resolvedVersions,
                         String queryId, Instant dataAsOf, boolean cacheHit, List<String> warnings) {}
}
