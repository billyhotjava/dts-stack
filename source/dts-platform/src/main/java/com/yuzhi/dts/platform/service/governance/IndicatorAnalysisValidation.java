package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import java.util.*;

final class IndicatorAnalysisValidation {
    private IndicatorAnalysisValidation() {}
    static boolean qualifier(GovIndicatorDefinition definition) {
        return List.of("MODIFIER", "TIME_PERIOD").contains(String.valueOf(definition.getCategory()).toUpperCase(Locale.ROOT));
    }
    static void validate(GovIndicatorDefinition definition, boolean required) {
        var config = IndicatorMapper.readAnalysisConfig(definition.getAnalysisConfig());
        if (config == null) { if (required) throw invalid("请补充指标分析配置"); return; }
        config.dimensionBindings().forEach((key, field) -> { identifier(key); identifier(field); });
        if (config.resultGrain().stream().distinct().count() != config.resultGrain().size()) throw invalid("结果粒度不能重复");
        if (!config.dimensionBindings().keySet().containsAll(config.resultGrain())) throw invalid("结果粒度必须包含在公共维度中");
        if (config.periodRef() != null && config.timeBinding() == null) throw invalid("固定时间周期需要业务时间映射");
        for (String aggregation : config.allowedAggregations()) {
            if (!List.of("SUM", "COUNT", "COUNT_DISTINCT", "AVG", "MIN", "MAX").contains(aggregation)) throw invalid("允许聚合方式无效");
        }
        if (config.timeBinding() != null) {
            identifier(config.timeBinding().fieldRef()); identifier(config.timeBinding().fieldName());
            try { java.time.ZoneId.of(config.timeBinding().timezone()); } catch (Exception error) { throw invalid("业务时区无效"); }
            if (!"native".equals(config.timeBinding().grain())) throw invalid("首批使用模型原生时间粒度，请使用已建模的日、月或年字段");
        }
        for (var predicate : config.predicates()) {
            if (predicate == null || !config.dimensionBindings().containsKey(predicate.fieldRef())) throw invalid("限定规则必须绑定公共维度");
            Object raw = predicate.value(); List<?> values = raw instanceof List<?> list ? list : Collections.singletonList(raw);
            if (values.isEmpty() || values.size() > 100) throw invalid("限定值数量无效");
            for (Object value : values) if (!(value instanceof String || value instanceof Number || value instanceof Boolean)) throw invalid("限定值必须是标量");
            if (!List.of("EQ", "IN", "BETWEEN").contains(String.valueOf(predicate.op())) || ("EQ".equals(predicate.op()) && values.size() != 1) || ("BETWEEN".equals(predicate.op()) && values.size() != 2)) throw invalid("限定规则操作符或值数量无效");
        }
        if (required && "MODIFIER".equals(definition.getCategory()) && config.predicates().isEmpty()) throw invalid("修饰词必须填写限定规则");
        if ("TIME_PERIOD".equals(definition.getCategory()) && !"RANGE".equals(config.periodMode())) throw invalid("时间周期必须声明明确区间方式");
        if (!qualifier(definition) && !IndicatorDefinitionSemantics.isDerivedLike(definition) && !config.allowedAggregations().contains(definition.getAggregationType())) throw invalid("度量聚合方式必须在允许聚合中声明");
        if (qualifier(definition) && (!config.modifierRefs().isEmpty() || config.periodRef() != null)) throw invalid("限定规则不能递归引用其他修饰词或周期");
    }
    private static void identifier(String value) { if (value == null || !value.matches("[A-Za-z_][A-Za-z0-9_]{0,62}")) throw invalid("公共维度或模型字段编码无效"); }
    private static IndicatorConflictException invalid(String message) { return new IndicatorConflictException(message); }
}
