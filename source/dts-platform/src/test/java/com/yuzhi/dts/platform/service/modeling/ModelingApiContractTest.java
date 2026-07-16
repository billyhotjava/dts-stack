package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ModelingApiContractTest {

    @Test
    void writeEnvelopeRequiresRevisionAndIdempotencyKey() {
        ModelingApiContract.WriteEnvelope<String> request = new ModelingApiContract.WriteEnvelope<>("payload", 2, "idem-2");

        assertThat(ModelingApiContract.validateWriteRequest(request)).isEmpty();
    }

    @Test
    void invalidWriteEnvelopeHasStableErrorCode() {
        ModelingApiContract.WriteEnvelope<String> request = new ModelingApiContract.WriteEnvelope<>("payload", 0, "");

        assertThatThrownBy(() -> ModelingApiContract.requireValidWriteRequest(request))
            .isInstanceOf(ModelingApiContract.ModelingApiException.class)
            .satisfies(error -> assertThat(((ModelingApiContract.ModelingApiException) error).code()).isEqualTo(ModelingApiContract.ErrorCode.MODEL_REVISION_CONFLICT));
    }
}
