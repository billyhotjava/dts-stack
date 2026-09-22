package com.yuzhi.dts.admin.service.personnel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F11 UT-002/UT-003/UT-004 的可执行覆盖：字段未传/显式空、版本判定、未知状态不猜测。
 * 真实持久化、并发与 Keycloak 联调由集成补证据。
 */
class PersonnelSourceFieldGuardTest {

    @Test
    @DisplayName("F11-UT-002：未传不变；允许清空才清空；禁止清空返回明确错误且不写变更")
    void absentKeepsAndExplicitNullRequiresPermission() {
        assertThat(PersonnelSourceFieldGuard.resolveField("B",
            PersonnelSourceFieldGuard.FieldUpdate.absent(), false))
            .isEqualTo(new PersonnelSourceFieldGuard.FieldDecision("B", false, null));

        var cleared = PersonnelSourceFieldGuard.resolveField("B",
            PersonnelSourceFieldGuard.FieldUpdate.value(null), true);
        assertThat(cleared.appliedValue()).isNull();
        assertThat(cleared.changed()).isTrue();

        var rejected = PersonnelSourceFieldGuard.resolveField("B",
            PersonnelSourceFieldGuard.FieldUpdate.value(null), false);
        assertThat(rejected.appliedValue()).isEqualTo("B");
        assertThat(rejected.changed()).isFalse();
        assertThat(rejected.rejectionReason()).isEqualTo("FIELD_CLEAR_NOT_ALLOWED");
    }

    @Test
    @DisplayName("F11-UT-003：旧值拒绝、同值无新增操作、无版本无法判先后")
    void versionComparison() {
        assertThat(PersonnelSourceFieldGuard.compareVersions("v12", "v11"))
            .isEqualTo(PersonnelSourceFieldGuard.VersionRelation.OLDER);
        assertThat(PersonnelSourceFieldGuard.compareVersions("v12", "v12"))
            .isEqualTo(PersonnelSourceFieldGuard.VersionRelation.EQUAL);
        assertThat(PersonnelSourceFieldGuard.compareVersions("v11", "v12"))
            .isEqualTo(PersonnelSourceFieldGuard.VersionRelation.NEWER);
        assertThat(PersonnelSourceFieldGuard.compareVersions(null, "v12"))
            .isEqualTo(PersonnelSourceFieldGuard.VersionRelation.UNKNOWN);
        assertThat(PersonnelSourceFieldGuard.compareVersions("v12", null))
            .isEqualTo(PersonnelSourceFieldGuard.VersionRelation.UNKNOWN);
    }

    @Test
    @DisplayName("F11-UT-004：未知状态不推定离职/在职；SYNCED不产生准入权由调用方保证")
    void unknownStatusIsNotGuessed() {
        assertThat(PersonnelSourceFieldGuard.mapLifecycle("ACTIVE")).get()
            .isEqualTo(PersonnelSourceFieldGuard.LifecycleHint.ACTIVE);
        assertThat(PersonnelSourceFieldGuard.mapLifecycle("TERMINATED")).get()
            .isEqualTo(PersonnelSourceFieldGuard.LifecycleHint.INACTIVE);
        assertThat(PersonnelSourceFieldGuard.mapLifecycle("SOME_FUTURE_STATUS")).isEmpty();
        assertThat(PersonnelSourceFieldGuard.mapLifecycle(null)).isEmpty();
        assertThat(PersonnelSourceFieldGuard.mapLifecycle("  ")).isEmpty();
    }
}
