package com.yuzhi.dts.platform.service.sprint64;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Versioned, backend-owned contracts for Sprint 64's process, layer, grain and bus-matrix loop.
 *
 * <p>The webapp may keep a session fallback, but these values are intentionally defined here so
 * API responses and governance gates cannot drift from the UI labels.</p>
 */
public final class Sprint64GovernanceContract {

    public static final int VERSION = 1;

    private static final List<WarehouseLayerDto> WAREHOUSE_LAYERS = List.of(
        new WarehouseLayerDto("ODS_RAW", "原始接入层", "保留来源字段与原始语义，不承担业务加工。", List.of(), List.of("ods_raw_", "raw_")),
        new WarehouseLayerDto("ODS_STANDARDIZED", "标准化接入层", "统一类型、编码、时间与审计字段。", List.of("ODS_RAW"), List.of("ods_", "stg_")),
        new WarehouseLayerDto("DWD", "明细事实 / 维度层", "按业务过程和声明粒度组织可复用明细。", List.of("ODS_STANDARDIZED", "DWD"), List.of("biz_dwd_", "dwd_", "dim_", "fact_")),
        new WarehouseLayerDto("DWS", "汇总服务层", "围绕一致性维度形成可复用主题汇总。", List.of("DWD", "DWS"), List.of("biz_dws_", "dws_")),
        new WarehouseLayerDto("ADS", "应用服务层", "面向报表、服务和数据产品发布。", List.of("DWD", "DWS", "ADS"), List.of("biz_ads_", "ads_"))
    );

    private static final List<ConformedDimensionDto> CONFORMED_DIMENSIONS = List.of(
        new ConformedDimensionDto("completion-status", "完成状态", "dim_completion_status_v2", List.of("*")),
        new ConformedDimensionDto("node-type", "节点类型", "dim_node_type_v1", List.of("*")),
        new ConformedDimensionDto("risk-level", "风险等级", "dim_risk_level_v1", List.of("*")),
        new ConformedDimensionDto("quality-zero-status", "质量归零状态", "dim_quality_zero_status_v1", List.of("*")),
        new ConformedDimensionDto("quality-reason", "质量问题原因", "dim_quality_reason_v1", List.of("*")),
        new ConformedDimensionDto("technical-change-type", "技术变更类型", "dim_technical_change_type_v1", List.of("*")),
        new ConformedDimensionDto("signing-status", "签署状态", "dim_signing_status_v1", List.of("*")),
        new ConformedDimensionDto("risk-category", "风险分类", "dim_risk_category_v1", List.of("*"))
    );

    private Sprint64GovernanceContract() {}

    public static List<WarehouseLayerDto> warehouseLayers() {
        return WAREHOUSE_LAYERS;
    }

    public static Optional<WarehouseLayerDto> resolveLayer(String code) {
        if (code == null) return Optional.empty();
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        return WAREHOUSE_LAYERS.stream().filter(layer -> layer.code().equals(normalized)).findFirst();
    }

    public static boolean isLayerFlowAllowed(String upstream, String target) {
        Optional<WarehouseLayerDto> targetLayer = resolveLayer(target);
        return targetLayer
            .flatMap(targetValue -> resolveLayer(upstream).map(layer -> targetValue.allowedUpstream().contains(layer.code())))
            .orElse(false);
    }

    public static List<ConformedDimensionDto> conformedDimensions() {
        return CONFORMED_DIMENSIONS;
    }

    public static GrainValidation validateGrain(String warehouseLayer, String statement, List<String> grainKeys) {
        String layer = warehouseLayer == null ? "" : warehouseLayer.trim().toUpperCase(Locale.ROOT);
        if (resolveLayer(layer).isEmpty()) {
            return new GrainValidation("blocked", "未识别的数仓层，不能执行粒度校验。", statement, grainKeys == null ? List.of() : List.copyOf(grainKeys));
        }
        boolean required = layer.equals("DWD") || layer.equals("DWS") || layer.equals("ADS");
        if (!required) return new GrainValidation("not_required", "ODS 层只要求保留来源字段，不强制业务粒度。", statement, grainKeys == null ? List.of() : List.copyOf(grainKeys));

        String normalizedStatement = statement == null ? "" : statement.trim();
        List<String> normalizedKeys = grainKeys == null
            ? List.of()
            : grainKeys.stream().map(key -> key == null ? "" : key.trim()).filter(key -> !key.isEmpty()).distinct().toList();
        if (normalizedStatement.isEmpty() || normalizedKeys.isEmpty()) {
            return new GrainValidation("blocked", "DWD 及以上层必须同时声明可读粒度语句和至少一个粒度键。", normalizedStatement, normalizedKeys);
        }
        return new GrainValidation("ready", "粒度声明完整。", normalizedStatement, normalizedKeys);
    }

    public record WarehouseLayerDto(String code, String title, String responsibility, List<String> allowedUpstream, List<String> namingPrefixes) {}

    public record ConformedDimensionDto(String dimensionId, String name, String sourceModel, List<String> domainIds) {}

    public record GrainValidation(String status, String message, String statement, List<String> grainKeys) {}
}
