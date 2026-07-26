package com.yuzhi.dts.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class SecurityLevelCatalogMonotonicTest {

    @Test
    void maxMustBeIndependentOfCandidateOrder() {
        List<String> levels = List.of("INTERNAL", "CONFIDENTIAL", "PUBLIC", "SECRET");

        assertThat(SecurityLevelCatalog.maxDataCode(levels)).isEqualTo("CONFIDENTIAL");
        assertThat(SecurityLevelCatalog.maxDataCode(List.of("SECRET", "PUBLIC", "CONFIDENTIAL", "INTERNAL")))
            .isEqualTo("CONFIDENTIAL");
    }

    @Test
    void effectiveLevelMustNeverMoveBelowPreviousLevel() {
        for (String previous : List.of("PUBLIC", "INTERNAL", "SECRET", "CONFIDENTIAL")) {
            for (String candidate : List.of("PUBLIC", "INTERNAL", "SECRET", "CONFIDENTIAL")) {
                String effective = SecurityLevelCatalog.maxDataCode(previous, candidate);
                assertThat(SecurityLevelCatalog.isDataAtLeast(effective, previous)).isTrue();
            }
        }
    }

    @Test
    void unknownLevelMustNotBeTreatedAsPublic() {
        assertThat(SecurityLevelCatalog.DataSecurityLevel.parse("UNKNOWN")).isNull();
        assertThatThrownBy(() -> SecurityLevelCatalog.maxDataCode(List.of("UNKNOWN")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported data security level");
    }
}
