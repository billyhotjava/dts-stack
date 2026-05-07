package com.yuzhi.dts.analytics.web.rest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint-24 F5/T03：密级降级方向判定测试。
 *
 * 阶梯：PUBLIC(0) < INTERNAL(1) < SECRET(2) < CONFIDENTIAL(3)。
 * 降级 = next_rank < before_rank；同级 / 升级 不算降级。
 * 边界规则：null / blank / 不在阶梯的值都视作 PUBLIC（最低），
 * 让历史未设密大屏的 owner 能不受 reason 阻塞地补登。
 */
class ScreenResourceClassificationDowngradeTest {

    @ParameterizedTest
    @CsvSource({
        "CONFIDENTIAL,SECRET",
        "CONFIDENTIAL,INTERNAL",
        "CONFIDENTIAL,PUBLIC",
        "SECRET,INTERNAL",
        "SECRET,PUBLIC",
        "INTERNAL,PUBLIC",
    })
    @DisplayName("从高到低 → 算降级")
    void downgradeReturnsTrue(String before, String after) {
        assertThat(ScreenResource.isDowngrade(before, after)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
        "PUBLIC,INTERNAL",
        "PUBLIC,SECRET",
        "PUBLIC,CONFIDENTIAL",
        "INTERNAL,SECRET",
        "INTERNAL,CONFIDENTIAL",
        "SECRET,CONFIDENTIAL",
    })
    @DisplayName("从低到高 → 不算降级")
    void upgradeReturnsFalse(String before, String after) {
        assertThat(ScreenResource.isDowngrade(before, after)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
        "PUBLIC,PUBLIC",
        "INTERNAL,INTERNAL",
        "SECRET,SECRET",
        "CONFIDENTIAL,CONFIDENTIAL",
    })
    @DisplayName("同级 → 不算降级（updateClassification 在 same-level 路径之前已 return changed=false，但 helper 仍要稳健）")
    void sameLevelReturnsFalse(String before, String after) {
        assertThat(ScreenResource.isDowngrade(before, after)).isFalse();
    }

    @Test
    @DisplayName("before=null（历史未设密大屏）→ 任何修改都不算降级，鼓励 owner 补登")
    void nullBefore_neverDowngrade() {
        assertThat(ScreenResource.isDowngrade(null, "PUBLIC")).isFalse();
        assertThat(ScreenResource.isDowngrade(null, "INTERNAL")).isFalse();
        assertThat(ScreenResource.isDowngrade(null, "SECRET")).isFalse();
        assertThat(ScreenResource.isDowngrade(null, "CONFIDENTIAL")).isFalse();
    }

    @Test
    @DisplayName("blank before（空字符串未设密）→ 与 null 同等待遇")
    void blankBefore_neverDowngrade() {
        assertThat(ScreenResource.isDowngrade("", "INTERNAL")).isFalse();
        assertThat(ScreenResource.isDowngrade("   ", "SECRET")).isFalse();
    }

    @Test
    @DisplayName("不在阶梯的值视作 PUBLIC（保守降级判定）")
    void unknownValue_treatedAsPublic() {
        // before=junk → rank=0；after=SECRET → rank=2；2<0 = false → 不算降级
        assertThat(ScreenResource.isDowngrade("junk", "SECRET")).isFalse();
        // before=SECRET → rank=2；after=junk → rank=0；0<2 = true → 算降级
        assertThat(ScreenResource.isDowngrade("SECRET", "junk")).isTrue();
    }

    @Test
    @DisplayName("混合大小写 / 前后空白 → 自动 normalize 后判定")
    void caseAndSpace_normalized() {
        assertThat(ScreenResource.isDowngrade("secret", "internal")).isTrue();
        assertThat(ScreenResource.isDowngrade(" SECRET ", "  INTERNAL  ")).isTrue();
        assertThat(ScreenResource.isDowngrade("Public", "Internal")).isFalse();
    }
}
