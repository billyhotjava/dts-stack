package com.yuzhi.dts.common.security;

import java.util.Objects;

/**
 * F11-T05 单个领域策略的判定输出。
 *
 * @param decision 判定结果
 * @param reasonCode 稳定原因码（如 POLICY_DENY、DEPENDENCY_INDETERMINATE、NOT_APPLICABLE），用于审计与中文文案映射
 * @param policyVersion 政策版本；缺失时调用方按“必需策略缺失”失败关闭，不得默认放行
 */
public record PolicyOutcome(PermissionDecision decision, String reasonCode, String policyVersion) {
    public PolicyOutcome {
        Objects.requireNonNull(decision, "decision");
        if (reasonCode == null || reasonCode.isBlank()) {
            throw new IllegalArgumentException("reasonCode 不能为空");
        }
    }

    public static PolicyOutcome allow(String reasonCode, String policyVersion) {
        return new PolicyOutcome(PermissionDecision.ALLOW, reasonCode, policyVersion);
    }

    public static PolicyOutcome deny(String reasonCode, String policyVersion) {
        return new PolicyOutcome(PermissionDecision.DENY, reasonCode, policyVersion);
    }

    public static PolicyOutcome notApplicable(String reasonCode, String policyVersion) {
        return new PolicyOutcome(PermissionDecision.NOT_APPLICABLE, reasonCode, policyVersion);
    }

    public static PolicyOutcome indeterminate(String reasonCode, String policyVersion) {
        return new PolicyOutcome(PermissionDecision.INDETERMINATE, reasonCode, policyVersion);
    }
}
