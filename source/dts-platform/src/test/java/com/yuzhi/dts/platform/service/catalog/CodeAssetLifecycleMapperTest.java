package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CodeAssetLifecycleMapperTest {

    @Test
    void mapsIndicatorLifecycleWithoutFalsePendingGovernance() {
        assertThat(CodeAssetLifecycleMapper.fromIndicatorStatus("DRAFT")).isEqualTo("DRAFT_GOVERNANCE");
        assertThat(CodeAssetLifecycleMapper.fromIndicatorStatus("PENDING_APPROVAL")).isEqualTo("DRAFT_GOVERNANCE");
        assertThat(CodeAssetLifecycleMapper.fromIndicatorStatus("APPROVED")).isEqualTo("DRAFT_GOVERNANCE");
        assertThat(CodeAssetLifecycleMapper.fromIndicatorStatus("PUBLISHED")).isEqualTo("ACTIVE");
        assertThat(CodeAssetLifecycleMapper.fromIndicatorStatus("ARCHIVED")).isEqualTo("ARCHIVED");
        assertThat(CodeAssetLifecycleMapper.fromIndicatorStatus("DEPRECATED")).isEqualTo("DEPRECATED");
        assertThat(CodeAssetLifecycleMapper.fromIndicatorStatus("")).isEqualTo("PENDING_GOVERNANCE");
    }

    @Test
    void mapsModelingSqlModelLifecycleWithoutFalsePendingGovernance() {
        assertThat(CodeAssetLifecycleMapper.fromModelingSqlModelStatus("ACTIVE", true)).isEqualTo("ACTIVE");
        assertThat(CodeAssetLifecycleMapper.fromModelingSqlModelStatus("PROMOTED", true)).isEqualTo("ACTIVE");
        assertThat(CodeAssetLifecycleMapper.fromModelingSqlModelStatus("TESTING", true)).isEqualTo("TESTING");
        assertThat(CodeAssetLifecycleMapper.fromModelingSqlModelStatus("DRAFT", true)).isEqualTo("DRAFT_GOVERNANCE");
        assertThat(CodeAssetLifecycleMapper.fromModelingSqlModelStatus("ACTIVE", false)).isEqualTo("ARCHIVED");
        assertThat(CodeAssetLifecycleMapper.fromModelingSqlModelStatus(null, true)).isEqualTo("PENDING_GOVERNANCE");
    }

    @Test
    void mapsApiAndDataStandardLifecycle() {
        assertThat(CodeAssetLifecycleMapper.fromApiServiceStatus("PUBLISHED")).isEqualTo("ACTIVE");
        assertThat(CodeAssetLifecycleMapper.fromApiServiceStatus("DISABLED")).isEqualTo("ARCHIVED");
        assertThat(CodeAssetLifecycleMapper.fromApiServiceStatus("DRAFT")).isEqualTo("DRAFT_GOVERNANCE");
        assertThat(CodeAssetLifecycleMapper.fromDataStandardStatus("ACTIVE")).isEqualTo("ACTIVE");
        assertThat(CodeAssetLifecycleMapper.fromDataStandardStatus("DEPRECATED")).isEqualTo("DEPRECATED");
        assertThat(CodeAssetLifecycleMapper.fromDataStandardStatus("RETIRED")).isEqualTo("ARCHIVED");
        assertThat(CodeAssetLifecycleMapper.fromGlossaryStatus("APPROVED")).isEqualTo("DRAFT_GOVERNANCE");
        assertThat(CodeAssetLifecycleMapper.fromGlossaryStatus("PUBLISHED")).isEqualTo("ACTIVE");
        assertThat(CodeAssetLifecycleMapper.fromGlossaryStatus("RETIRED")).isEqualTo("ARCHIVED");
    }
}
