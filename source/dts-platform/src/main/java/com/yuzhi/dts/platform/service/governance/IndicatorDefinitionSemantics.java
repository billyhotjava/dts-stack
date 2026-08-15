package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import com.yuzhi.dts.platform.service.governance.IndicatorBusinessContextContract.MetricSourceRef;
import com.yuzhi.dts.platform.service.governance.IndicatorBusinessContextContract.MetricType;
import com.yuzhi.dts.platform.service.governance.IndicatorBusinessContextContract.SourceType;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/** Canonical indicator type and version-pinned source semantics with legacy projection fallback. */
final class IndicatorDefinitionSemantics {

    private static final Pattern MODEL_REVISION = Pattern.compile("r([1-9][0-9]*)", Pattern.CASE_INSENSITIVE);

    private IndicatorDefinitionSemantics() {}

    static MetricType metricType(GovIndicatorDefinition indicator) {
        if (indicator != null && StringUtils.hasText(indicator.getMetricType())) {
            try {
                return MetricType.valueOf(indicator.getMetricType().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {}
        }
        return indicator != null && Boolean.TRUE.equals(indicator.getIsDerived()) ? MetricType.DERIVED : MetricType.ATOMIC;
    }

    static boolean isDerivedLike(GovIndicatorDefinition indicator) {
        return metricType(indicator) != MetricType.ATOMIC;
    }

    static boolean isModelBoundAtomic(GovIndicatorDefinition indicator) {
        if (metricType(indicator) != MetricType.ATOMIC) return false;
        List<MetricSourceRef> refs = IndicatorMapper.readSourceRefs(indicator == null ? null : indicator.getSourceRefs());
        return refs.size() == 1 && refs.get(0) != null && refs.get(0).sourceType() == SourceType.SEMANTIC_MODEL_REVISION;
    }

    static Optional<String> expectedModelFieldReferenceTarget(GovIndicatorDefinition indicator) {
        if (!isModelBoundAtomic(indicator) || !StringUtils.hasText(indicator.getMeasureField())) return Optional.empty();
        MetricSourceRef ref = IndicatorMapper.readSourceRefs(indicator.getSourceRefs()).get(0);
        if (!StringUtils.hasText(ref.sourceId()) || !StringUtils.hasText(ref.sourceVersion())) return Optional.empty();
        Matcher revision = MODEL_REVISION.matcher(ref.sourceVersion().trim());
        if (!revision.matches()) return Optional.empty();
        return Optional.of(ref.sourceId().trim() + "@" + revision.group(1) + "#" + indicator.getMeasureField().trim());
    }
}
