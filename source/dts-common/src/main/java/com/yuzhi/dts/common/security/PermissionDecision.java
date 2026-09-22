package com.yuzhi.dts.common.security;

/**
 * F11-T05 统一授权决策结果。
 *
 * <p>适用明确拒绝优先；必需策略缺失或无法确定时失败关闭；未适用不等于允许。
 * 判定必须保留原因码与政策版本供审计，外部错误不得枚举隐藏资源。
 */
public enum PermissionDecision {
    ALLOW,
    DENY,
    NOT_APPLICABLE,
    INDETERMINATE
}
