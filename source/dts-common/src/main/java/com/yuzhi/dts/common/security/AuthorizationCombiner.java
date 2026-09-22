package com.yuzhi.dts.common.security;

import java.util.List;
import java.util.Objects;

/**
 * F11-T05 统一决策组合器（纯函数，无 IO）。
 *
 * <p>组合规则（顺序即优先级）：
 *
 * <ol>
 *   <li>任一适用 {@code DENY} → {@code DENY}（明确拒绝优先，不可被另一个 ALLOW 覆盖）；</li>
 *   <li>任一 {@code INDETERMINATE}（必需策略异常/无法确定）→ {@code DENY}（失败关闭）；</li>
 *   <li>至少一个 {@code ALLOW} → {@code ALLOW}；</li>
 *   <li>空输入或全部 {@code NOT_APPLICABLE} → {@code DENY}（未适用不等于允许）。</li>
 * </ol>
 *
 * <p>无关策略的 DENY 不得参与组合——调用方只传入适用于本次资源类型+动作的策略结果。
 */
public final class AuthorizationCombiner {

    private AuthorizationCombiner() {}

    public record CombinedDecision(PermissionDecision decision, String reasonCode) {}

    public static CombinedDecision combine(List<PolicyOutcome> outcomes, String policyVersion) {
        if (outcomes == null || outcomes.isEmpty()) {
            return new CombinedDecision(PermissionDecision.DENY, "NO_APPLICABLE_POLICY");
        }
        String version = policyVersion == null ? "unknown" : policyVersion;
        for (PolicyOutcome outcome : outcomes) {
            Objects.requireNonNull(outcome, "outcome");
            if (outcome.decision() == PermissionDecision.DENY) {
                return new CombinedDecision(PermissionDecision.DENY, outcome.reasonCode() + "@" + version);
            }
        }
        for (PolicyOutcome outcome : outcomes) {
            if (outcome.decision() == PermissionDecision.INDETERMINATE) {
                return new CombinedDecision(PermissionDecision.DENY, "DEPENDENCY_INDETERMINATE:" + outcome.reasonCode());
            }
        }
        for (PolicyOutcome outcome : outcomes) {
            if (outcome.decision() == PermissionDecision.ALLOW) {
                return new CombinedDecision(PermissionDecision.ALLOW, outcome.reasonCode() + "@" + version);
            }
        }
        return new CombinedDecision(PermissionDecision.DENY, "NO_APPLICABLE_POLICY");
    }
}
