package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecException;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import org.junit.jupiter.api.Test;

class DimensionDefinitionReservedIdempotencyBoundaryTest {

    @Test
    void rejectsTheInternalCompositeIdempotencyNamespaceBeforeCallingThePublicService() throws Exception {
        DimensionDefinitionApplicationService service = mock(DimensionDefinitionApplicationService.class);
        DimensionDefinitionResource resource = new DimensionDefinitionResource(
            service,
            new ObjectMapper().findAndRegisterModules(),
            mock(WarehousePlanActorProvider.class),
            "server-tenant"
        );

        assertThatThrownBy(() ->
                resource.create(
                    new ObjectMapper()
                        .readTree(
                            """
                            {
                              "domainId":"20000000-0000-0000-0000-000000000001",
                              "name":"Customer",
                              "definition":"Reusable customer dimension",
                              "ownerId":"business-owner",
                              "reuseScope":"DOMAIN",
                              "hierarchies":[],
                              "idempotencyKey":"dm:v2:dimension:reserved"
                            }
                            """
                        )
                )
            )
            .isInstanceOf(ModelSpecException.class)
            .satisfies(error -> {
                ModelSpecException exception = (ModelSpecException) error;
                org.assertj.core.api.Assertions.assertThat(exception.code())
                    .isEqualTo("DIMENSION_DEFINITION_REQUEST_INVALID");
                org.assertj.core.api.Assertions.assertThat(exception.details().toString())
                    .contains("DIMENSION_DEFINITION_IDEMPOTENCY_KEY_RESERVED");
            });
        verifyNoInteractions(service);
    }
}
