package com.yuzhi.dts.platform.service.governance;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Stable indicator ownership and source-reference rules approved by ADR-86-06/07. */
public final class IndicatorBusinessContextContract {

    private static final Pattern GROUP_CODE = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]{0,63}");

    private IndicatorBusinessContextContract() {}

    public enum MetricType {
        ATOMIC,
        DERIVED,
        COMPOSITE,
    }

    public enum SourceType {
        SEMANTIC_MODEL_REVISION,
        PHYSICAL_ASSET,
        INDICATOR_VERSION,
    }

    public record MetricSourceRef(SourceType sourceType, String sourceId, String sourceVersion) {}

    public record BusinessContext(
        UUID businessCategoryId,
        UUID dataDomainId,
        UUID businessProcessId,
        MetricType metricType,
        String metricGroupCode,
        List<MetricSourceRef> sourceRefs
    ) {
        public BusinessContext {
            sourceRefs = sourceRefs == null ? List.of() : List.copyOf(sourceRefs);
        }
    }

    public record ValidationIssue(String code, String message) {}

    public record ValidationResult(boolean valid, List<ValidationIssue> issues) {
        public ValidationResult {
            issues = issues == null ? List.of() : List.copyOf(issues);
        }
    }

    public static ValidationResult validateDraft(BusinessContext context) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (context == null) {
            return new ValidationResult(true, List.of());
        }
        if (context.metricGroupCode() != null && !GROUP_CODE.matcher(context.metricGroupCode()).matches()) {
            issue(issues, "INDICATOR_METRIC_GROUP_CODE_INVALID", "指标分组编码必须是稳定 ASCII 编码");
        }
        validateSourceRefs(context.sourceRefs(), issues);
        if (context.metricType() == MetricType.ATOMIC && context.businessProcessId() != null && context.dataDomainId() == null) {
            issue(issues, "INDICATOR_DATA_DOMAIN_REQUIRED", "原子指标配置业务过程时必须同时配置数据域");
        }
        return new ValidationResult(issues.isEmpty(), issues);
    }

    public static ValidationResult validateDeliverable(BusinessContext target, List<BusinessContext> upstream) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (target == null || target.metricType() == null) {
            issue(issues, "INDICATOR_METRIC_TYPE_REQUIRED", "指标类型不能为空");
            return new ValidationResult(false, issues);
        }
        if (target.businessCategoryId() == null) {
            issue(issues, "INDICATOR_BUSINESS_CATEGORY_REQUIRED", "业务分类不能为空");
        }
        ValidationResult draft = validateDraft(target);
        issues.addAll(draft.issues());
        List<BusinessContext> dependencies = upstream == null ? List.of() : upstream;

        switch (target.metricType()) {
            case ATOMIC -> validateAtomic(target, dependencies, issues);
            case DERIVED -> validateDerived(target, dependencies, issues);
            case COMPOSITE -> validateComposite(target, dependencies, issues);
        }
        return new ValidationResult(issues.isEmpty(), issues);
    }

    private static void validateAtomic(BusinessContext target, List<BusinessContext> upstream, List<ValidationIssue> issues) {
        if (target.dataDomainId() == null) {
            issue(issues, "INDICATOR_DATA_DOMAIN_REQUIRED", "原子指标必须配置数据域");
        }
        if (target.businessProcessId() == null) {
            issue(issues, "INDICATOR_BUSINESS_PROCESS_REQUIRED", "原子指标必须配置业务过程");
        }
        if (!upstream.isEmpty()) {
            issue(issues, "INDICATOR_ATOMIC_UPSTREAM_INVALID", "原子指标不能引用上游指标版本");
        }
        if (target.sourceRefs().isEmpty()) {
            issue(issues, "INDICATOR_SOURCE_REQUIRED", "原子指标至少需要一个固定模型版本或物理资产来源");
        } else if (
            target.sourceRefs().stream().anyMatch(ref -> ref.sourceType() == SourceType.INDICATOR_VERSION)
        ) {
            issue(issues, "INDICATOR_ATOMIC_SOURCE_TYPE_INVALID", "原子指标来源不能是指标版本");
        }
    }

    private static void validateDerived(BusinessContext target, List<BusinessContext> upstream, List<ValidationIssue> issues) {
        if (upstream.isEmpty()) {
            issue(issues, "INDICATOR_UPSTREAM_REQUIRED", "派生指标至少需要一个固定上游指标版本");
            return;
        }
        validateIndicatorVersionSources(target, issues);
        Set<UUID> categories = values(upstream, BusinessContext::businessCategoryId);
        Set<UUID> domains = values(upstream, BusinessContext::dataDomainId);
        if (categories.size() != 1 || categories.contains(null) || !categories.contains(target.businessCategoryId())) {
            issue(issues, "INDICATOR_CROSS_CATEGORY_NOT_SUPPORTED", "首版不支持跨业务分类指标");
        }
        if (domains.size() != 1 || domains.contains(null)) {
            issue(issues, "INDICATOR_CROSS_DOMAIN_NOT_SUPPORTED", "派生指标要求所有上游处于同一数据域");
        } else if (!domains.contains(target.dataDomainId())) {
            issue(issues, "INDICATOR_DATA_DOMAIN_MISMATCH", "派生指标必须固化上游共同数据域");
        }
        validateOptionalCommonProcess(target, upstream, issues);
    }

    private static void validateComposite(BusinessContext target, List<BusinessContext> upstream, List<ValidationIssue> issues) {
        if (upstream.size() < 2) {
            issue(issues, "INDICATOR_COMPOSITE_UPSTREAM_MINIMUM", "复合指标至少需要两个固定上游指标版本");
            return;
        }
        validateIndicatorVersionSources(target, issues);
        Set<UUID> categories = values(upstream, BusinessContext::businessCategoryId);
        if (categories.size() != 1 || categories.contains(null) || !categories.contains(target.businessCategoryId())) {
            issue(issues, "INDICATOR_CROSS_CATEGORY_NOT_SUPPORTED", "首版不支持跨业务分类指标");
        }
        Set<UUID> domains = values(upstream, BusinessContext::dataDomainId);
        if (domains.size() == 1 && !domains.contains(null)) {
            if (!domains.contains(target.dataDomainId())) {
                issue(issues, "INDICATOR_DATA_DOMAIN_MISMATCH", "同域复合指标必须固化共同数据域");
            }
        } else if (target.dataDomainId() != null) {
            issue(issues, "INDICATOR_CROSS_DOMAIN_CONTEXT_MUST_BE_EMPTY", "跨域复合指标不得虚构单一数据域");
        }
        validateOptionalCommonProcess(target, upstream, issues);
    }

    private static void validateIndicatorVersionSources(BusinessContext target, List<ValidationIssue> issues) {
        if (target.sourceRefs().isEmpty()) {
            issue(issues, "INDICATOR_SOURCE_REQUIRED", "派生或复合指标必须固定上游指标版本");
        } else if (target.sourceRefs().stream().anyMatch(ref -> ref.sourceType() != SourceType.INDICATOR_VERSION)) {
            issue(issues, "INDICATOR_SOURCE_TYPE_INVALID", "派生或复合指标来源只能是固定指标版本");
        }
    }

    private static void validateOptionalCommonProcess(
        BusinessContext target,
        List<BusinessContext> upstream,
        List<ValidationIssue> issues
    ) {
        Set<UUID> processes = values(upstream, BusinessContext::businessProcessId);
        if (processes.size() == 1 && !processes.contains(null)) {
            if (!processes.contains(target.businessProcessId())) {
                issue(issues, "INDICATOR_BUSINESS_PROCESS_MISMATCH", "同过程指标必须固化共同业务过程");
            }
        } else if (target.businessProcessId() != null) {
            issue(issues, "INDICATOR_MULTI_PROCESS_CONTEXT_MUST_BE_EMPTY", "多过程指标不得虚构单一业务过程");
        }
    }

    private static void validateSourceRefs(List<MetricSourceRef> sourceRefs, List<ValidationIssue> issues) {
        Set<String> unique = new LinkedHashSet<>();
        for (MetricSourceRef ref : sourceRefs) {
            if (
                ref == null ||
                ref.sourceType() == null ||
                ref.sourceId() == null ||
                ref.sourceId().isBlank() ||
                ref.sourceVersion() == null ||
                ref.sourceVersion().isBlank()
            ) {
                issue(issues, "INDICATOR_SOURCE_REF_INVALID", "指标来源必须包含类型、稳定 ID 和固定版本");
                continue;
            }
            String key = ref.sourceType() + "|" + ref.sourceId().trim() + "|" + ref.sourceVersion().trim();
            if (!unique.add(key)) {
                issue(issues, "INDICATOR_SOURCE_REF_DUPLICATE", "指标来源引用不能重复");
            }
        }
    }

    private static Set<UUID> values(
        List<BusinessContext> contexts,
        java.util.function.Function<BusinessContext, UUID> extractor
    ) {
        Set<UUID> result = new LinkedHashSet<>();
        contexts.stream().map(extractor).forEach(result::add);
        return result;
    }

    private static void issue(List<ValidationIssue> issues, String code, String message) {
        if (issues.stream().noneMatch(existing -> existing.code().equals(code))) {
            issues.add(new ValidationIssue(code, message));
        }
    }
}
