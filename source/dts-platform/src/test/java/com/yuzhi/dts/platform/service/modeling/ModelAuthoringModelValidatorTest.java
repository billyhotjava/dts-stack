package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ModelAuthoringModelValidatorTest {

    @Test
    void delegatesToTheCanonicalModelSpecValidationRules() {
        assertThat(new ModelAuthoringModelValidator().validate(null))
            .extracting("code", "field")
            .containsExactly(org.assertj.core.groups.Tuple.tuple("MODEL_SPEC_REQUEST_INVALID", "$"));
    }
}
