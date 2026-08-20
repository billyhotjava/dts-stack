package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import com.yuzhi.dts.platform.service.modeling.ModelSpecException;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelSemanticSyncCommandService;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelSemanticSyncCommandService.RetryResult;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelSemanticSyncCommandService.ServingSyncView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;

@ExtendWith(MockitoExtension.class)
class CatalogModelSemanticSyncResourceTest {

    private static final UUID MODEL_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final String ETAG = "\"model-serving-sync:" + MODEL_ID + ":7\"";

    @Mock
    private CatalogModelSemanticSyncCommandService service;

    @Mock
    private WarehousePlanActorProvider actorProvider;

    private CatalogModelSemanticSyncResource resource;

    @BeforeEach
    void setUp() {
        resource = new CatalogModelSemanticSyncResource(service, actorProvider, "tenant-a");
        lenient().when(actorProvider.currentActor())
            .thenReturn(new WarehousePlanActorProvider.WarehousePlanActor("xiezm", null));
    }

    @Test
    void retriesWithProjectionEtagAndReturnsTheNewVersion() {
        ServingSyncView pending = status("SYNC_PENDING", 8);
        when(service.retry("tenant-a", "xiezm", MODEL_ID, 7))
            .thenReturn(new RetryResult(pending, false, UUID.randomUUID()));

        var response = resource.retry(MODEL_ID, ETAG);

        assertThat(response.getHeaders().getETag()).isEqualTo("\"model-serving-sync:" + MODEL_ID + ":8\"");
        assertThat(response.getBody()).isNotNull();
        verify(service).retry("tenant-a", "xiezm", MODEL_ID, 7);
    }

    @Test
    void listsAClientBoundedServingStatusBatch() {
        ServingSyncView synced = status("SYNCED", 8);
        when(service.getMany("tenant-a", List.of(MODEL_ID))).thenReturn(List.of(synced));

        var response = resource.list(List.of(MODEL_ID));

        assertThat(response.getData()).containsExactly(synced);
        verify(service).getMany("tenant-a", List.of(MODEL_ID));
    }

    @Test
    void retryUsesTheExistingCatalogMaintainerAuthoritySet() throws Exception {
        PreAuthorize authorization = CatalogModelSemanticSyncResource.class
            .getDeclaredMethod("retry", UUID.class, String.class)
            .getAnnotation(PreAuthorize.class);

        assertThat(authorization.value())
            .isEqualTo(
                "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).CATALOG_MAINTAINERS)"
            );
    }

    @Test
    void rejectsMissingOrMismatchedProjectionEtagBeforeCallingService() {
        for (String value : new String[] { null, "\"model-serving-sync:" + UUID.randomUUID() + ":7\"" }) {
            assertThatThrownBy(() -> resource.retry(MODEL_ID, value))
                .isInstanceOf(ModelSpecException.class);
        }
        verify(service, never()).retry("tenant-a", "xiezm", MODEL_ID, 7);
    }

    private static ServingSyncView status(String syncStatus, long version) {
        return new ServingSyncView(
            MODEL_ID,
            "semantic-model:" + MODEL_ID,
            syncStatus,
            0,
            null,
            null,
            Instant.parse("2026-08-17T06:00:00Z"),
            version,
            true,
            null,
            null
        );
    }
}
