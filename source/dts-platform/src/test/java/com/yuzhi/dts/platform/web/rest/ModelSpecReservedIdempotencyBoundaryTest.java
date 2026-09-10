package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.CreateModelSpecCommand;
import com.yuzhi.dts.platform.service.modeling.ModelSpecCreateRequestDecoder;
import com.yuzhi.dts.platform.service.modeling.ModelSpecException;
import com.yuzhi.dts.platform.service.modeling.ModelSpecStageGateService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecUpdateRequestDecoder;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelSpecReservedIdempotencyBoundaryTest {

    @Test
    void rejectsTheInternalCompositeIdempotencyNamespaceBeforeCallingThePublicService() {
        ModelSpecApplicationService service = mock(ModelSpecApplicationService.class);
        ModelSpecCreateRequestDecoder createDecoder = mock(ModelSpecCreateRequestDecoder.class);
        CreateModelSpecCommand command = mock(CreateModelSpecCommand.class);
        when(command.idempotencyKey()).thenReturn("dm:v2:model:reserved");
        when(createDecoder.decode(org.mockito.ArgumentMatchers.any()))
            .thenReturn(new ModelSpecCreateRequestDecoder.DecodeResult(command, List.of()));
        ModelSpecResource resource = new ModelSpecResource(
            service,
            createDecoder,
            mock(ModelSpecUpdateRequestDecoder.class),
            mock(ModelSpecStageGateService.class),
            mock(WarehousePlanActorProvider.class),
            new com.yuzhi.dts.platform.service.modeling.ModelingContextInitializationService(null, null),
            "server-tenant"
        );

        assertThatThrownBy(() -> resource.create(new ObjectMapper().createObjectNode().put("planId", java.util.UUID.randomUUID().toString())))
            .isInstanceOf(ModelSpecException.class)
            .satisfies(error -> {
                ModelSpecException exception = (ModelSpecException) error;
                assertThat(exception.code()).isEqualTo("MODEL_SPEC_REQUEST_INVALID");
                assertThat(exception.details().toString()).contains("MODEL_SPEC_IDEMPOTENCY_KEY_RESERVED");
            });
        verifyNoInteractions(service);
    }
}
