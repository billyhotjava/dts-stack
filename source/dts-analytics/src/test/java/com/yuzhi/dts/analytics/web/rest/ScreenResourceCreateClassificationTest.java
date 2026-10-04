package com.yuzhi.dts.analytics.web.rest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint-24 F3/T04：创建大屏的密级校验回归测试。
 *
 * 校验逻辑被抽成 ScreenResource.normalizeRequiredClassification 静态 helper，
 * 单测直接驱动 helper，避免为这几条分支启动 Spring 上下文 + mock 15 个 dependency。
 */
class ScreenResourceCreateClassificationTest {

    @Test
    @DisplayName("null classification → 抛 IAE，错误消息含 required + 4 个候选")
    void nullClassification_throws() {
        assertThatThrownBy(() -> ScreenResource.normalizeRequiredClassification(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("required")
            .hasMessageContaining("PUBLIC")
            .hasMessageContaining("CONFIDENTIAL");
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "  ", "\t" })
    @DisplayName("空白 classification → 与 null 同等待遇，required 错误消息")
    void blankClassification_throws(String raw) {
        assertThatThrownBy(() -> ScreenResource.normalizeRequiredClassification(raw))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("required");
    }

    @ParameterizedTest
    @ValueSource(strings = { "TOP_SECRET", "private", "Open", "999", "junk" })
    @DisplayName("不在白名单的值 → 抛 IAE，错误消息含 must be one of")
    void unknownClassification_throws(String raw) {
        assertThatThrownBy(() -> ScreenResource.normalizeRequiredClassification(raw))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must be one of");
    }

    @ParameterizedTest
    @ValueSource(strings = { "PUBLIC", "INTERNAL", "SECRET", "CONFIDENTIAL" })
    @DisplayName("合法大写值直接返回")
    void validUppercase_returnsAsIs(String raw) {
        assertThat(ScreenResource.normalizeRequiredClassification(raw)).isEqualTo(raw);
    }

    @ParameterizedTest
    @ValueSource(strings = { "public", "internal", "Secret", "confidential" })
    @DisplayName("小写 / 混合大小写 → 自动 normalize 为大写")
    void mixedCase_normalizedToUpper(String raw) {
        String result = ScreenResource.normalizeRequiredClassification(raw);
        assertThat(result).isEqualTo(raw.toUpperCase());
    }

    @Test
    @DisplayName("前后有空白 → 自动 trim")
    void leadingTrailingSpace_trimmed() {
        assertThat(ScreenResource.normalizeRequiredClassification("  INTERNAL  ")).isEqualTo("INTERNAL");
        assertThat(ScreenResource.normalizeRequiredClassification("\tSECRET\n")).isEqualTo("SECRET");
    }

    @Test
    @DisplayName("错误消息文案包含运维可读的引导信息（非内部 stack trace）")
    void errorMessage_isUserFacing() {
        try {
            ScreenResource.normalizeRequiredClassification(null);
        } catch (IllegalArgumentException ex) {
            // 不应包含 java.* / org.springframework.* 等 stack 关键词
            assertThat(ex.getMessage()).doesNotContain("java.");
            assertThat(ex.getMessage()).doesNotContain("Exception");
        }
    }
}
