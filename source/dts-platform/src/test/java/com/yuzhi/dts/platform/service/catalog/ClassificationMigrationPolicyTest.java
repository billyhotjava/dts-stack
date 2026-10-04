package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClassificationMigrationPolicyTest {

    @Test
    void shouldCreateFromLegacyWithoutLowering() {
        var decision = ClassificationMigrationPolicy.evaluate("SECRET", null, null);

        assertThat(decision.code()).isEqualTo("CREATE");
        assertThat(decision.computedLevel()).isEqualTo("SECRET");
    }

    @Test
    void shouldRaiseWhenDetectionIsHigher() {
        var decision = ClassificationMigrationPolicy.evaluate("INTERNAL", "CONFIDENTIAL", "SECRET");

        assertThat(decision.code()).isEqualTo("RAISE");
        assertThat(decision.computedLevel()).isEqualTo("CONFIDENTIAL");
    }

    @Test
    void shouldBlockWhenExistingFactIsLowerThanLegacy() {
        var decision = ClassificationMigrationPolicy.evaluate("CONFIDENTIAL", null, "SECRET");

        assertThat(decision.code()).isEqualTo("BLOCKED_DOWNGRADE");
        assertThat(decision.computedLevel()).isEqualTo("CONFIDENTIAL");
    }

    @Test
    void shouldBlockUnknownAndMissingClassification() {
        assertThat(ClassificationMigrationPolicy.evaluate("UNKNOWN", null, null).code())
            .isEqualTo("BLOCKED_INVALID");
        assertThat(ClassificationMigrationPolicy.evaluate(null, null, null).code())
            .isEqualTo("BLOCKED_MISSING");
    }

    @Test
    void shouldBeIdempotentWhenExistingFactAlreadyWins() {
        var decision = ClassificationMigrationPolicy.evaluate("INTERNAL", "SECRET", "CONFIDENTIAL");

        assertThat(decision.code()).isEqualTo("UNCHANGED");
        assertThat(decision.computedLevel()).isEqualTo("CONFIDENTIAL");
    }
}
