package com.yuzhi.dts.platform.security.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import org.junit.jupiter.api.Test;

class AssetActionTest {

    @Test
    void exposesExactlyTheEightProtocolActions() {
        assertThat(AssetAction.values())
            .extracting(AssetAction::code, AssetAction::displayName)
            .containsExactly(
                tuple("CREATE", "新增"),
                tuple("DELETE", "删除"),
                tuple("UPDATE", "修改"),
                tuple("COPY", "复制"),
                tuple("IMPORT", "导入"),
                tuple("EXPORT", "导出"),
                tuple("ARCHIVE", "归档"),
                tuple("DESTROY", "销毁")
            );
    }

    @Test
    void parsesEnglishAndChineseAliasesWithoutSilentFallback() {
        assertThat(AssetAction.from("export")).isEqualTo(AssetAction.EXPORT);
        assertThat(AssetAction.from("  COPY ")).isEqualTo(AssetAction.COPY);
        assertThat(AssetAction.from("销毁")).isEqualTo(AssetAction.DESTROY);
        assertThat(AssetAction.from("修改")).isEqualTo(AssetAction.UPDATE);

        assertThatThrownBy(() -> AssetAction.from("read"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("read");
        assertThatThrownBy(() -> AssetAction.from(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
