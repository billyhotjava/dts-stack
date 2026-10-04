package com.yuzhi.dts.admin.service.personnel;

import java.util.Set;

/**
 * F11-T07 人员/组织字段写入守卫（纯函数）。
 *
 * <p>规则：
 *
 * <ul>
 *   <li>未传（absent）→ 保持现值，不写变更；</li>
 *   <li>显式空且字段允许清空 → 清空；不允许清空 → 拒绝且不写该变更（不统一按 null 覆盖）；</li>
 *   <li>MDM 未知原始状态不推定离职/在职，返回空由调用方隔离；活跃值不解除人工停用由调用方按
 *       {@code access_state} 独立判断，本类只做状态映射；</li>
 *   <li>版本比较：双方版本齐全才判新旧，否则 UNKNOWN 由调用方进冲突/核验队列（摘要只做去重）。</li>
 * </ul>
 */
public final class PersonnelSourceFieldGuard {

    private PersonnelSourceFieldGuard() {}

    public record FieldUpdate(String value, boolean present, boolean explicitNull) {
        public static FieldUpdate absent() {
            return new FieldUpdate(null, false, false);
        }

        public static FieldUpdate value(String value) {
            return new FieldUpdate(value, true, value == null);
        }
    }

    public record FieldDecision(String appliedValue, boolean changed, String rejectionReason) {
        public static FieldDecision keep(String current) {
            return new FieldDecision(current, false, null);
        }
    }

    /**
     * 单字段写入判定。
     *
     * @param current 当前目录值
     * @param update 本次输入（区分未传/显式空）
     * @param clearAllowed 该字段是否允许显式清空
     */
    public static FieldDecision resolveField(String current, FieldUpdate update, boolean clearAllowed) {
        if (update == null || !update.present()) {
            return FieldDecision.keep(current);
        }
        if (update.explicitNull() || update.value() == null) {
            if (clearAllowed) {
                boolean changed = current != null;
                return new FieldDecision(null, changed, null);
            }
            return new FieldDecision(current, false, "FIELD_CLEAR_NOT_ALLOWED");
        }
        String next = update.value().trim();
        if (next.isEmpty()) {
            if (clearAllowed) {
                return new FieldDecision(null, current != null, null);
            }
            return new FieldDecision(current, false, "FIELD_CLEAR_NOT_ALLOWED");
        }
        if (next.equals(current)) {
            return FieldDecision.keep(current);
        }
        return new FieldDecision(next, true, null);
    }

    /** 已确认的 MDM 原始状态映射；未知值返回空（阻断猜测），调用方不得默认在职/离职。 */
    private static final Set<String> ACTIVE_STATUSES = Set.of("ACTIVE", "IN_SERVICE", "ON_DUTY");
    private static final Set<String> INACTIVE_STATUSES = Set.of("TERMINATED", "RETIRED", "DISMISSED");

    public enum LifecycleHint {
        ACTIVE,
        INACTIVE
    }

    public static java.util.Optional<LifecycleHint> mapLifecycle(String mdmStatus) {
        if (mdmStatus == null || mdmStatus.isBlank()) {
            return java.util.Optional.empty();
        }
        String normalized = mdmStatus.trim().toUpperCase(java.util.Locale.ROOT);
        if (ACTIVE_STATUSES.contains(normalized)) {
            return java.util.Optional.of(LifecycleHint.ACTIVE);
        }
        if (INACTIVE_STATUSES.contains(normalized)) {
            return java.util.Optional.of(LifecycleHint.INACTIVE);
        }
        return java.util.Optional.empty();
    }

    public enum VersionRelation {
        NEWER,
        OLDER,
        EQUAL,
        UNKNOWN
    }

    /** 双方版本齐全才判新旧；任一缺失 → UNKNOWN（调用方冲突隔离，不按摘要判先后）。 */
    public static VersionRelation compareVersions(String current, String incoming) {
        if (current == null || incoming == null || !current.matches("v?[0-9]+") || !incoming.matches("v?[0-9]+")
            || current.startsWith("v") != incoming.startsWith("v")) {
            return VersionRelation.UNKNOWN;
        }
        int offset = current.startsWith("v") ? 1 : 0;
        int cmp = new java.math.BigInteger(incoming.substring(offset)).compareTo(new java.math.BigInteger(current.substring(offset)));
        if (cmp == 0) {
            return VersionRelation.EQUAL;
        }
        return cmp > 0 ? VersionRelation.NEWER : VersionRelation.OLDER;
    }
}
