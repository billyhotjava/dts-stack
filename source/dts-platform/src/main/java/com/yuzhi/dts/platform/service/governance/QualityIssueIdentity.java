package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.domain.governance.GovQualityRun;
import java.util.Locale;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;

/** Stable identity for aggregating repeated failures without replacing the representative quality-run source. */
final class QualityIssueIdentity {

    private static final String PREFIX = "quality:";

    private QualityIssueIdentity() {}

    static String problemKey(GovQualityRun run) {
        return problemPrefix(run) + normalizeCategory(run != null ? run.getErrorCategory() : null);
    }

    static String problemPrefix(GovQualityRun run) {
        if (run == null || run.getDatasetId() == null) {
            throw new IllegalArgumentException("质量问题缺少数据资产身份");
        }
        return PREFIX + run.getDatasetId() + ":" + bindingIdentity(run) + ":";
    }

    private static String bindingIdentity(GovQualityRun run) {
        if (run.getBinding() != null && run.getBinding().getId() != null) {
            return run.getBinding().getId().toString();
        }
        UUID ruleId = run.getRule() != null ? run.getRule().getId() : null;
        if (ruleId == null && run.getRuleVersion() != null && run.getRuleVersion().getRule() != null) {
            ruleId = run.getRuleVersion().getRule().getId();
        }
        if (ruleId == null) {
            throw new IllegalArgumentException("质量问题缺少规则绑定身份");
        }
        return "rule-" + ruleId;
    }

    private static String normalizeCategory(String value) {
        String category = StringUtils.defaultIfBlank(value, "EXECUTION_ERROR")
            .trim()
            .toUpperCase(Locale.ROOT)
            .replaceAll("[^A-Z0-9_:-]", "_");
        return category.length() <= 64 ? category : category.substring(0, 64);
    }
}
