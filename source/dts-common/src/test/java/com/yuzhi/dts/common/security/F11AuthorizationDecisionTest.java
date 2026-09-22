package com.yuzhi.dts.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * F11 UT-022/UT-023（决策组合）与 UT-014/UT-015/UT-024（范围匹配）的可执行覆盖。
 * 纯函数断言；数据库约束、事务与真实目录由集成补证据。
 */
class F11AuthorizationDecisionTest {

    @Test
    @DisplayName("F11-UT-023：适用明确拒绝优先，不被另一个ALLOW覆盖")
    void applicableDenyWinsOverAllow() {
        var combined = AuthorizationCombiner.combine(
            List.of(PolicyOutcome.allow("DEPT_SCOPE_OK", "v1"), PolicyOutcome.deny("OBJECT_DENY", "v1")),
            "v1"
        );
        assertThat(combined.decision()).isEqualTo(PermissionDecision.DENY);
        assertThat(combined.reasonCode()).contains("OBJECT_DENY");
    }

    @Test
    @DisplayName("F11-UT-022：必需策略INDETERMINATE失败关闭；全NOT_APPLICABLE也拒绝")
    void indeterminateAndNotApplicableFailClosed() {
        var indeterminate = AuthorizationCombiner.combine(
            List.of(PolicyOutcome.allow("DEPT_SCOPE_OK", "v1"), PolicyOutcome.indeterminate("ROW_FILTER_UNAVAILABLE", "v1")),
            "v1"
        );
        assertThat(indeterminate.decision()).isEqualTo(PermissionDecision.DENY);
        assertThat(indeterminate.reasonCode()).contains("DEPENDENCY_INDETERMINATE");

        var noneApplicable = AuthorizationCombiner.combine(
            List.of(PolicyOutcome.notApplicable("NO_DATASET_POLICY", "v1")),
            "v1"
        );
        assertThat(noneApplicable.decision()).isEqualTo(PermissionDecision.DENY);

        var empty = AuthorizationCombiner.combine(List.of(), "v1");
        assertThat(empty.decision()).isEqualTo(PermissionDecision.DENY);
    }

    @Test
    @DisplayName("F11-UT-022：无关策略不污染结果——调用方只传入适用策略时ALLOW成立")
    void allowWhenOnlyApplicableAllow() {
        var combined = AuthorizationCombiner.combine(List.of(PolicyOutcome.allow("ALL_CHECKS_PASS", "v2")), "v2");
        assertThat(combined.decision()).isEqualTo(PermissionDecision.ALLOW);
        assertThat(combined.reasonCode()).contains("v2");
    }

    @Test
    @DisplayName("F11-UT-014：A只读+B导出不做笛卡尔组合，A的export拒绝")
    void scopedGrantsDoNotCrossProduct() {
        var readA = new ScopedGrant("t1", PermissionCodes.CATALOG_DATASET_READ, "1153", Set.of("ASSET-A"), Set.of("read"));
        var exportB = new ScopedGrant("t1", PermissionCodes.CATALOG_DATASET_EXPORT, "1153", Set.of("ASSET-B"), Set.of("export"));

        // 用 B 的 export 码读 A：权限码不同 → 拒绝
        assertThat(ScopedGrant.matches(readA,
            new ScopedGrant.AccessRequest("t1", PermissionCodes.CATALOG_DATASET_EXPORT, "1153", "ASSET-A", "export"))).isFalse();
        // 用 A 的 read 码导出：权限码不同 → 拒绝
        assertThat(ScopedGrant.matches(readA,
            new ScopedGrant.AccessRequest("t1", PermissionCodes.CATALOG_DATASET_READ, "1153", "ASSET-A", "export"))).isFalse();
        // B 的 export 正例成立
        assertThat(ScopedGrant.matches(exportB,
            new ScopedGrant.AccessRequest("t1", PermissionCodes.CATALOG_DATASET_EXPORT, "1153", "ASSET-B", "export"))).isTrue();
    }

    @Test
    @DisplayName("F11-UT-015：null范围不解释成全所；跨租户拒绝")
    void nullScopeNeverMeansGlobal() {
        var nullScope = new ScopedGrant("t1", PermissionCodes.CATALOG_DATASET_READ, null, Set.of("ASSET-A"), Set.of("read"));
        assertThat(ScopedGrant.matches(nullScope,
            new ScopedGrant.AccessRequest("t1", PermissionCodes.CATALOG_DATASET_READ, "1153", "ASSET-A", "read"))).isFalse();

        var scoped = new ScopedGrant("t1", PermissionCodes.CATALOG_DATASET_READ, "1153", Set.of("ASSET-A"), Set.of("read"));
        assertThat(ScopedGrant.matches(scoped,
            new ScopedGrant.AccessRequest("t2", PermissionCodes.CATALOG_DATASET_READ, "1153", "ASSET-A", "read"))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "1153-child", "root:1153", "153", "115", "" })
    @DisplayName("F11-UT-024：部门精确匹配，后缀/父子不产生授权")
    void departmentIsMatchedExactly(String other) {
        var grant = new ScopedGrant("t1", PermissionCodes.MODELING_MODEL_UPDATE, "1153", Set.of(), Set.of());
        assertThat(ScopedGrant.matches(grant,
            new ScopedGrant.AccessRequest("t1", PermissionCodes.MODELING_MODEL_UPDATE, other, null, null))).isFalse();
        assertThat(ScopedGrant.matches(grant,
            new ScopedGrant.AccessRequest("t1", PermissionCodes.MODELING_MODEL_UPDATE, "1153", null, null))).isTrue();
    }

    @Test
    @DisplayName("F11-UT-017：未知权限码非法，字典外码不授予")
    void unknownPermissionCodesAreRejected() {
        assertThat(PermissionCodes.isKnown("modeling:model:delete")).isFalse();
        assertThat(PermissionCodes.isValid("MODELING:model:read")).isFalse();
        assertThatThrownBy(() -> new ScopedGrant("t1", "unknown:code", "1153", Set.of(), Set.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
