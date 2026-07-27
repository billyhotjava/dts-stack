package com.yuzhi.dts.platform.web.rest.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.modeling.ModelMaterializationRunArtifactService;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationRunArtifactService.FinalizeCommand;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationRunArtifactService.RunArtifactView;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationRunArtifactService.SyncProbeCommand;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationRuntimeException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ModelMaterializationRunInternalResourceTest {

    private static final UUID GROUP_ID =
        UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Test
    void delegatesOnlyOpaqueRunIdentityForSyncAndFinalize() {
        var service = mock(
            ModelMaterializationRunArtifactService.class
        );
        var sync = new SyncProbeCommand(
            "RELEASE_BUILD",
            "a".repeat(64)
        );
        var finalize = new FinalizeCommand("SUCCEEDED");
        var synced = new RunArtifactView(
            GROUP_ID,
            "DBT_SUCCEEDED",
            UUID.fromString(
                "20000000-0000-0000-0000-000000000002"
            ),
            2
        );
        var finalized = new RunArtifactView(
            GROUP_ID,
            "DBT_SUCCEEDED",
            null,
            2
        );
        when(service.syncAndProbe(GROUP_ID, sync))
            .thenReturn(synced);
        when(service.finalizeRun(GROUP_ID, finalize))
            .thenReturn(finalized);
        var resource = new ModelMaterializationRunInternalResource(
            service
        );

        assertThat(resource.syncAndProbe(GROUP_ID, sync))
            .isEqualTo(synced);
        assertThat(resource.finalizeRun(GROUP_ID, finalize))
            .isEqualTo(finalized);
        verify(service).syncAndProbe(GROUP_ID, sync);
        verify(service).finalizeRun(GROUP_ID, finalize);
    }

    @Test
    void mapsArtifactFailureToNonSuccessWithoutLeakingPaths() {
        var resource = new ModelMaterializationRunInternalResource(
            mock(ModelMaterializationRunArtifactService.class)
        );

        var response = resource.handle(
            new ModelMaterializationRuntimeException(
                "MODEL_DBT_BUILD_RESULT_FAILED",
                "dbt result did not pass"
            )
        );

        assertThat(response.getStatusCode())
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody())
            .containsEntry(
                "code",
                "MODEL_DBT_BUILD_RESULT_FAILED"
            );
        assertThat(response.getBody().toString())
            .doesNotContain("/opt/")
            .doesNotContain("target/");
    }
}
