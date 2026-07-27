package com.yuzhi.dts.platform.web.rest.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.modeling.ModelMaterializationRuntimeException;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationRuntimeSpecService;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationRuntimeSpecService.RuntimeSpecView;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ModelMaterializationRuntimeSpecInternalResourceTest {

    @Test
    void consumesTokenFromDedicatedHeaderAndReturnsOpaqueReferences() {
        var service = mock(ModelMaterializationRuntimeSpecService.class);
        var view = new RuntimeSpecView(
            UUID.fromString(
                "10000000-0000-0000-0000-000000000001"
            ),
            "RELEASE_BUILD",
            "a".repeat(64),
            "dim_customer",
            "dev",
            UUID.fromString(
                "20000000-0000-0000-0000-000000000002"
            ),
            Instant.parse("2026-07-27T14:10:00Z"),
            "sha256:" + "b".repeat(64)
        );
        when(service.consume("opaque-token")).thenReturn(view);
        var resource =
            new ModelMaterializationRuntimeSpecInternalResource(service);

        assertThat(resource.consume("opaque-token")).isEqualTo(view);
        verify(service).consume("opaque-token");
    }

    @Test
    void mapsInvalidTokenWithoutEchoingIt() {
        var resource =
            new ModelMaterializationRuntimeSpecInternalResource(
                mock(ModelMaterializationRuntimeSpecService.class)
            );

        var response = resource.handle(
            new ModelMaterializationRuntimeException(
                "MODEL_RUNTIME_SPEC_TOKEN_INVALID",
                "Runtime spec token is invalid"
            )
        );

        assertThat(response.getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody())
            .containsEntry(
                "code",
                "MODEL_RUNTIME_SPEC_TOKEN_INVALID"
            );
        assertThat(response.getBody().toString())
            .doesNotContain("opaque-token");
    }
}
