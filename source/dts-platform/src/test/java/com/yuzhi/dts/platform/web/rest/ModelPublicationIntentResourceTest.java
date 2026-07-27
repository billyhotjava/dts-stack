package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelPublicationIntentService;
import com.yuzhi.dts.platform.service.modeling.ModelPublicationIntentService.NextHumanAction;
import com.yuzhi.dts.platform.service.modeling.ModelPublicationIntentService.OnlineReadiness;
import com.yuzhi.dts.platform.service.modeling.ModelPublicationIntentService.PublicationIntentResult;
import com.yuzhi.dts.platform.service.modeling.ModelPublicationIntentService.PublicationOutcome;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ModelPublicationIntentResourceTest {

    private static final UUID MODEL_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID CANDIDATE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final String ETAG = "\"release-candidate:" + CANDIDATE_ID + ":7\"";

    @Mock
    private ModelPublicationIntentService service;

    @Mock
    private WarehousePlanActorProvider actorProvider;

    private ModelPublicationIntentResource resource;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        resource = new ModelPublicationIntentResource(service, actorProvider, "tenant-a");
        objectMapper = new ObjectMapper();
        lenient().when(actorProvider.currentActor()).thenReturn(
            new WarehousePlanActorProvider.WarehousePlanActor("maintainer-a", null)
        );
    }

    @Test
    void acceptsOnlyCandidateAndReasonAndReturnsTheCurrentCandidateProjection() {
        when(service.start(
            "tenant-a",
            "maintainer-a",
            MODEL_ID,
            CANDIDATE_ID,
            7,
            "publish-1",
            "submit current model"
        ))
            .thenReturn(
                new PublicationIntentResult(
                    CANDIDATE_ID,
                    8,
                    DeliveryStatus.QUALITY_RUNNING,
                    PublicationOutcome.QUALITY_RUNNING,
                    NextHumanAction.NONE,
                    null,
                    OnlineReadiness.PROCESSING,
                    "/modeling/plans/30000000-0000-0000-0000-000000000001",
                    false
                )
            );

        var response = resource.start(
            MODEL_ID,
            ETAG,
            "publish-1",
            objectMapper
                .createObjectNode()
                .put("candidateId", CANDIDATE_ID.toString())
                .put("reason", "submit current model")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getHeaders().getETag())
            .isEqualTo("\"release-candidate:" + CANDIDATE_ID + ":8\"");
        verify(service).start(
            "tenant-a",
            "maintainer-a",
            MODEL_ID,
            CANDIDATE_ID,
            7,
            "publish-1",
            "submit current model"
        );
    }

    @Test
    void rejectsClientActionRoleTargetAndScheduleInjection() {
        for (String field : new String[] { "action", "targetStatus", "role", "reviewer", "operator", "selector", "schedule" }) {
            assertThatThrownBy(() ->
                resource.start(
                    MODEL_ID,
                    ETAG,
                    "publish-2",
                    objectMapper
                        .createObjectNode()
                        .put("candidateId", CANDIDATE_ID.toString())
                        .put("reason", "submit")
                        .put(field, "attacker-controlled")
                )
            )
                .isInstanceOf(ModelReleaseCandidateException.class)
                .satisfies(error ->
                    assertThat(((ModelReleaseCandidateException) error).code())
                        .isEqualTo("MODEL_PUBLICATION_INTENT_REQUEST_INVALID")
                );
        }
    }
}
