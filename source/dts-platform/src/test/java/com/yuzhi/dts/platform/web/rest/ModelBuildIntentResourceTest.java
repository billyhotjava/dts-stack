package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationBuildRepository.QueuedBuildGroup;
import com.yuzhi.dts.platform.service.modeling.ModelBuildIntentService;
import com.yuzhi.dts.platform.service.modeling.ModelBuildIntentService.BuildIntentResult;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryAuditView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateOrigin;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EntryView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.ExpectedVersion;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ModelBuildIntentResourceTest {

    private static final UUID PLAN_ID =
        UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID CANDIDATE_ID =
        UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID MODEL_ID =
        UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final String CHECKSUM = "a".repeat(64);
    private static final String ETAG =
        "\"model-spec:" + MODEL_ID + ":3:" + CHECKSUM + "\"";

    @Mock
    private ModelBuildIntentService service;

    @Mock
    private WarehousePlanActorProvider actorProvider;

    private ModelBuildIntentResource resource;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        resource = new ModelBuildIntentResource(
            service,
            actorProvider,
            "tenant-a"
        );
        objectMapper = new ObjectMapper();
        lenient().when(actorProvider.currentActor())
            .thenReturn(
                new WarehousePlanActorProvider.WarehousePlanActor(
                    "builder-a",
                    null
                )
            );
    }

    @Test
    void acceptsOnlyBusinessContextAndReturnsTheCanonicalCandidateLocation() {
        CandidateView candidate = candidate();
        QueuedBuildGroup group = new QueuedBuildGroup(
            CANDIDATE_ID,
            2,
            UUID.randomUUID(),
            UUID.randomUUID(),
            "postgres-primary",
            "dts_release_build_postgres_primary",
            "dts_rc_test",
            "c".repeat(64),
            List.of()
        );
        when(
            service.start(
                eq("tenant-a"),
                eq("builder-a"),
                eq(MODEL_ID),
                any(),
                any()
            )
        )
            .thenReturn(new BuildIntentResult(candidate, group, false));

        var response = resource.start(
            MODEL_ID,
            ETAG,
            "intent-key",
            objectMapper
                .createObjectNode()
                .put("planId", PLAN_ID.toString())
                .put("environment", "DEV")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getHeaders().getETag())
            .isEqualTo("\"release-candidate:" + CANDIDATE_ID + ":2\"");
        assertThat(response.getHeaders().getLocation().toString())
            .isEqualTo(
                "/api/modeling/plans/" +
                PLAN_ID +
                "/release-candidates/" +
                CANDIDATE_ID
            );
        ArgumentCaptor<ExpectedVersion> expected =
            ArgumentCaptor.forClass(ExpectedVersion.class);
        ArgumentCaptor<ModelBuildIntentService.BuildIntentCommand> command =
            ArgumentCaptor.forClass(
                ModelBuildIntentService.BuildIntentCommand.class
            );
        verify(service).start(
            eq("tenant-a"),
            eq("builder-a"),
            eq(MODEL_ID),
            expected.capture(),
            command.capture()
        );
        assertThat(expected.getValue())
            .extracting(
                ExpectedVersion::modelSpecId,
                ExpectedVersion::revision,
                ExpectedVersion::checksum
            )
            .containsExactly(MODEL_ID, 3, CHECKSUM);
        assertThat(command.getValue().planId()).isEqualTo(PLAN_ID);
        assertThat(command.getValue().environment()).isEqualTo("DEV");
        assertThat(command.getValue().idempotencyKey()).isEqualTo("intent-key");
    }

    @Test
    void rejectsSelectorTargetProfileAndOtherTechnicalInjectionFields() {
        for (String field : List.of(
            "selector",
            "dagId",
            "checksum",
            "target",
            "profile",
            "projectDir"
        )) {
            assertThatThrownBy(() ->
                resource.start(
                    MODEL_ID,
                    ETAG,
                    "intent-key",
                    objectMapper
                        .createObjectNode()
                        .put("planId", PLAN_ID.toString())
                        .put("environment", "DEV")
                        .put(field, "attacker-controlled")
                )
            )
                .isInstanceOf(ModelReleaseCandidateException.class)
                .extracting(error ->
                    ((ModelReleaseCandidateException) error).code()
                )
                .isEqualTo("MODEL_BUILD_INTENT_REQUEST_INVALID");
        }
        verify(service, never()).start(any(), any(), any(), any(), any());
    }

    @Test
    void requiresTheCurrentStrongModelSpecEtag() {
        assertThatThrownBy(() ->
            resource.start(
                MODEL_ID,
                null,
                "intent-key",
                objectMapper
                    .createObjectNode()
                    .put("planId", PLAN_ID.toString())
                    .put("environment", "DEV")
            )
        )
            .isInstanceOf(ModelReleaseCandidateException.class)
            .extracting(error ->
                ((ModelReleaseCandidateException) error).code()
            )
            .isEqualTo("MODEL_BUILD_INTENT_IF_MATCH_REQUIRED");
        verify(service, never()).start(any(), any(), any(), any(), any());
    }

    private static CandidateView candidate() {
        Instant now = Instant.parse("2026-07-27T12:00:00Z");
        return new CandidateView(
            CANDIDATE_ID,
            "tenant-a",
            PLAN_ID,
            "DEV",
            DeliveryStatus.BUILDING,
            2,
            "candidate-key",
            "b".repeat(64),
            new DeliveryAuditView(
                "builder-a",
                now.minusSeconds(60),
                null,
                null,
                null,
                null,
                null,
                null
            ),
            "builder-a",
            now,
            List.of(
                new EntryView(
                    UUID.randomUUID(),
                    "tenant-a",
                    CANDIDATE_ID,
                    PLAN_ID,
                    MODEL_ID,
                    3,
                    CHECKSUM,
                    null,
                    ImplementationMode.DESIGNER_GENERATED,
                    DeliveryStatus.BUILDING,
                    0,
                    "single model"
                )
            ),
            CandidateOrigin.SINGLE_MODEL_INTENT,
            "postgres-primary",
            "postgres",
            "dts",
            "dev"
        );
    }
}
