package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecException;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.BusinessModelRepresentationView;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.DriftStatus;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.PreviewCapabilityProjection;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.PhysicalPreviewProjection;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.RepresentationScope;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.RuntimeObservationProjection;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.VisualizationCapability;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationException;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationService;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider.WarehousePlanActor;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ModelRepresentationResourceTest {

    private static final UUID MODEL_ID = UUID.fromString("20000000-0000-0000-0000-000000000083");

    @Mock
    private ModelRepresentationService service;

    @Mock
    private WarehousePlanActorProvider actorProvider;

    @Test
    void forwardsCanonicalActorAndExactPinsAndReturnsTheProjectionEtag() {
        BusinessModelRepresentationView view = new BusinessModelRepresentationView(
            RepresentationScope.BUSINESS,
            MODEL_ID,
            2,
            "a".repeat(64),
            3,
            "b".repeat(64),
            ImplementationMode.DBT_MANAGED,
            VisualizationCapability.BUSINESS_VISUAL_READ,
            List.of(),
            List.of(),
            List.of(),
            null,
            List.of(),
            null,
            null,
            new RuntimeObservationProjection(null, DriftStatus.UNAVAILABLE, false, false, null, null),
            new PreviewCapabilityProjection(true, List.of()),
            PhysicalPreviewProjection.unavailable(),
            DriftStatus.UNAVAILABLE,
            "\"representation-etag\""
        );
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("user-83", "dept-1"));
        when(service.get("tenant-1", "user-83", MODEL_ID, 2, 3, RepresentationScope.BUSINESS, false)).thenReturn(view);
        ModelRepresentationResource resource = resource();

        var response = resource.get(MODEL_ID, 2, 3, RepresentationScope.BUSINESS);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getETag()).isEqualTo("\"representation-etag\"");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isSameAs(view);
        verify(service).get("tenant-1", "user-83", MODEL_ID, 2, 3, RepresentationScope.BUSINESS, false);
    }

    @Test
    void mapsPinnedRepresentationConflictsToHttp409() {
        var response = resource().handleRepresentationError(
            new ModelRepresentationException(
                "MODEL_REPRESENTATION_IMPLEMENTATION_PIN_REQUIRED",
                "pin required",
                ModelRepresentationException.Kind.CONFLICT
            )
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("MODEL_REPRESENTATION_IMPLEMENTATION_PIN_REQUIRED");
    }

    @ParameterizedTest
    @MethodSource("modelSpecStatuses")
    void mapsOwnerReadGateErrorsWithoutExposingInvisibleModels(ModelSpecException.Kind kind, HttpStatus expected) {
        var response = resource().handleModelSpecError(
            new ModelSpecException("MODEL_SPEC_NOT_FOUND", "ModelSpec was not found", kind)
        );

        assertThat(response.getStatusCode()).isEqualTo(expected);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("ModelSpec was not found");
    }

    private ModelRepresentationResource resource() {
        return new ModelRepresentationResource(service, actorProvider, "tenant-1");
    }

    private static Stream<Arguments> modelSpecStatuses() {
        return Stream.of(
            Arguments.of(ModelSpecException.Kind.BAD_REQUEST, HttpStatus.BAD_REQUEST),
            Arguments.of(ModelSpecException.Kind.UNPROCESSABLE, HttpStatus.BAD_REQUEST),
            Arguments.of(ModelSpecException.Kind.PRECONDITION_REQUIRED, HttpStatus.BAD_REQUEST),
            Arguments.of(ModelSpecException.Kind.FORBIDDEN, HttpStatus.FORBIDDEN),
            Arguments.of(ModelSpecException.Kind.NOT_FOUND, HttpStatus.NOT_FOUND),
            Arguments.of(ModelSpecException.Kind.CONFLICT, HttpStatus.CONFLICT)
        );
    }
}
