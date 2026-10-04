package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.modeling.serving.ModelPhysicalPreviewService;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.PhysicalPreviewView;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.PhysicalPreviewRequest;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.PreviewMode;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;

class ModelPhysicalPreviewResourceTest {

    @Test
    void defaultsToStructureAndAlwaysReturnsPrivateNoStore() {
        ModelPhysicalPreviewService service = org.mockito.Mockito.mock(ModelPhysicalPreviewService.class);
        WarehousePlanActorProvider actorProvider = org.mockito.Mockito.mock(WarehousePlanActorProvider.class);
        WarehousePlanActorProvider.WarehousePlanActor actor = org.mockito.Mockito.mock(WarehousePlanActorProvider.WarehousePlanActor.class);
        when(actorProvider.currentActor()).thenReturn(actor);
        when(actor.ownerId()).thenReturn("alice");
        when(service.preview(any(), any(), any())).thenReturn(org.mockito.Mockito.mock(PhysicalPreviewView.class));
        ModelPhysicalPreviewResource resource = new ModelPhysicalPreviewResource(service, actorProvider, "tenant-a");

        ResponseEntity<ApiResponse<PhysicalPreviewView>> response = resource.preview(
            "30000000-0000-0000-0000-000000000001",
            "7", "4", "a".repeat(64), "b".repeat(64), "SERVING",
            "20000000-0000-0000-0000-000000000001", "11", "2",
            "50000000-0000-0000-0000-000000000001", "1",
            "60000000-0000-0000-0000-000000000001", "c".repeat(64), "STRUCTURE", "100"
        );

        assertThat(response.getHeaders().getCacheControl()).isEqualTo(CacheControl.noStore().cachePrivate().getHeaderValue());
        assertThat(response.getHeaders().getVary()).containsExactly("Authorization");
        org.mockito.ArgumentCaptor<PhysicalPreviewRequest> request = org.mockito.ArgumentCaptor.forClass(PhysicalPreviewRequest.class);
        org.mockito.Mockito.verify(service).preview(org.mockito.ArgumentMatchers.eq("tenant-a"), org.mockito.ArgumentMatchers.eq("alice"), request.capture());
        assertThat(request.getValue().mode()).isEqualTo(PreviewMode.STRUCTURE);
    }

    @Test
    void invalidEnumIsDelegatedToTheStrictlyAuditedServiceBoundary() {
        ModelPhysicalPreviewService service = org.mockito.Mockito.mock(ModelPhysicalPreviewService.class);
        WarehousePlanActorProvider actorProvider = org.mockito.Mockito.mock(WarehousePlanActorProvider.class);
        WarehousePlanActorProvider.WarehousePlanActor actor = org.mockito.Mockito.mock(WarehousePlanActorProvider.WarehousePlanActor.class);
        when(actorProvider.currentActor()).thenReturn(actor);
        when(actor.ownerId()).thenReturn("alice");
        when(service.preview(any(), any(), any())).thenReturn(org.mockito.Mockito.mock(PhysicalPreviewView.class));
        ModelPhysicalPreviewResource resource = new ModelPhysicalPreviewResource(service, actorProvider, "tenant-a");

        resource.preview(
            "30000000-0000-0000-0000-000000000001",
            "7", "4", "a".repeat(64), "b".repeat(64), "SERVING",
            "20000000-0000-0000-0000-000000000001", "11", "2",
            "50000000-0000-0000-0000-000000000001", "1",
            "60000000-0000-0000-0000-000000000001", "c".repeat(64), "INVALID", "100"
        );

        org.mockito.ArgumentCaptor<PhysicalPreviewRequest> request = org.mockito.ArgumentCaptor.forClass(PhysicalPreviewRequest.class);
        org.mockito.Mockito.verify(service).preview(org.mockito.ArgumentMatchers.eq("tenant-a"), org.mockito.ArgumentMatchers.eq("alice"), request.capture());
        assertThat(request.getValue().mode()).isNull();
    }

    @Test
    void invalidOrMissingScalarParametersReachTheStrictlyAuditedServiceBoundary() {
        ModelPhysicalPreviewService service = org.mockito.Mockito.mock(ModelPhysicalPreviewService.class);
        WarehousePlanActorProvider actorProvider = org.mockito.Mockito.mock(WarehousePlanActorProvider.class);
        WarehousePlanActorProvider.WarehousePlanActor actor = org.mockito.Mockito.mock(WarehousePlanActorProvider.WarehousePlanActor.class);
        when(actorProvider.currentActor()).thenReturn(actor);
        when(actor.ownerId()).thenReturn("alice");
        when(service.preview(any(), any(), any())).thenReturn(org.mockito.Mockito.mock(PhysicalPreviewView.class));
        ModelPhysicalPreviewResource resource = new ModelPhysicalPreviewResource(service, actorProvider, "tenant-a");

        resource.preview(
            "not-a-uuid", "not-an-int", null, null, null, null,
            null, null, null, null, null, null, null, "STRUCTURE", "not-an-int"
        );

        org.mockito.ArgumentCaptor<PhysicalPreviewRequest> request = org.mockito.ArgumentCaptor.forClass(PhysicalPreviewRequest.class);
        org.mockito.Mockito.verify(service).preview(org.mockito.ArgumentMatchers.eq("tenant-a"), org.mockito.ArgumentMatchers.eq("alice"), request.capture());
        assertThat(request.getValue().modelSpecId()).isNull();
        assertThat(request.getValue().modelRevision()).isZero();
        assertThat(request.getValue().implementationRevision()).isZero();
        assertThat(request.getValue().candidateId()).isNull();
        assertThat(request.getValue().limit()).isZero();
    }
}
