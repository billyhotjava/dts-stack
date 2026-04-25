package com.yuzhi.dts.platform.service.workbench;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClassificationMapperTest {

    @Test
    void toApiCode_maps_TOP_SECRET_to_S1() {
        assertThat(ClassificationMapper.toApiCode("TOP_SECRET")).isEqualTo("S1");
    }

    @Test
    void toApiCode_maps_SECRET_to_S2() {
        assertThat(ClassificationMapper.toApiCode("SECRET")).isEqualTo("S2");
    }

    @Test
    void toApiCode_maps_INTERNAL_to_S3() {
        assertThat(ClassificationMapper.toApiCode("INTERNAL")).isEqualTo("S3");
    }

    @Test
    void toApiCode_maps_PUBLIC_to_S4() {
        assertThat(ClassificationMapper.toApiCode("PUBLIC")).isEqualTo("S4");
    }

    @Test
    void toApiCode_is_case_insensitive_and_trims() {
        assertThat(ClassificationMapper.toApiCode("  top_secret  ")).isEqualTo("S1");
    }

    @Test
    void toApiCode_returns_null_for_null() {
        assertThat(ClassificationMapper.toApiCode(null)).isNull();
    }

    @Test
    void toApiCode_passes_through_unknown() {
        assertThat(ClassificationMapper.toApiCode("CONFIDENTIAL")).isEqualTo("CONFIDENTIAL");
    }

    @Test
    void toDbValues_maps_S1_to_TOP_SECRET() {
        assertThat(ClassificationMapper.toDbValues("S1")).containsExactly("TOP_SECRET");
    }

    @Test
    void toDbValues_maps_S4_to_PUBLIC() {
        assertThat(ClassificationMapper.toDbValues("S4")).containsExactly("PUBLIC");
    }

    @Test
    void toDbValues_returns_empty_list_for_null() {
        assertThat(ClassificationMapper.toDbValues(null)).isEmpty();
    }

    @Test
    void toDbValues_passes_through_unknown() {
        assertThat(ClassificationMapper.toDbValues("CUSTOM")).containsExactly("CUSTOM");
    }
}
