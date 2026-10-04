package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider.WarehousePlanActor;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ModelSpecImportPreviewAdmissionGateTest {

    private final WarehousePlanActorProvider actorProvider = org.mockito.Mockito.mock(WarehousePlanActorProvider.class);
    private final ModelSpecImportPreviewAdmissionGate gate = new ModelSpecImportPreviewAdmissionGate(actorProvider);

    @Test
    void limitsPreviewFrequencyPerAuthenticatedActor() {
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("alice", "dept-a"));
        for (int index = 0; index < ModelSpecImportPreviewAdmissionGate.MAX_REQUESTS_PER_ACTOR_WINDOW; index++) {
            try (ModelSpecImportPreviewAdmissionGate.Admission ignored = gate.enter()) {
                // A completed request still consumes its fixed-window admission.
            }
        }

        assertThatThrownBy(gate::enter)
            .isInstanceOf(ModelSpecImportPreviewRequestParser.RequestLimitException.class)
            .satisfies(error -> {
                var rejection = (ModelSpecImportPreviewRequestParser.RequestLimitException) error;
                assertThat(rejection.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                assertThat(rejection.code()).isEqualTo("MODEL_IMPORT_PREVIEW_RATE_LIMITED");
            });
    }

    @Test
    void limitsAndReleasesActorConcurrency() {
        ModelSpecImportPreviewAdmissionGate.Admission first = gate.enter("alice");
        ModelSpecImportPreviewAdmissionGate.Admission second = gate.enter("alice");
        try {
            assertThatThrownBy(() -> gate.enter("alice"))
                .isInstanceOf(ModelSpecImportPreviewRequestParser.RequestLimitException.class)
                .satisfies(error ->
                    assertThat(((ModelSpecImportPreviewRequestParser.RequestLimitException) error).code()).isEqualTo(
                            "MODEL_IMPORT_PREVIEW_BUSY"
                        )
                );
            first.close();
            try (ModelSpecImportPreviewAdmissionGate.Admission replacement = gate.enter("alice")) {
                assertThat(replacement).isNotNull();
            }
        } finally {
            first.close();
            second.close();
        }
    }

    @Test
    void limitsAndReleasesPlanConcurrencyAcrossActors() {
        UUID planId = UUID.randomUUID();
        List<ModelSpecImportPreviewAdmissionGate.Admission> active = new ArrayList<>();
        for (int index = 0; index < ModelSpecImportPreviewAdmissionGate.MAX_CONCURRENT_PER_PLAN; index++) {
            ModelSpecImportPreviewAdmissionGate.Admission admission = gate.enter("actor-" + index);
            admission.admitPlan(planId);
            active.add(admission);
        }
        ModelSpecImportPreviewAdmissionGate.Admission rejected = gate.enter("actor-rejected");
        try {
            assertThatThrownBy(() -> rejected.admitPlan(planId))
                .isInstanceOf(ModelSpecImportPreviewRequestParser.RequestLimitException.class)
                .satisfies(error ->
                    assertThat(((ModelSpecImportPreviewRequestParser.RequestLimitException) error).code()).isEqualTo(
                            "MODEL_IMPORT_PREVIEW_BUSY"
                        )
                );
            active.removeFirst().close();
            rejected.admitPlan(planId);
        } finally {
            rejected.close();
            active.forEach(ModelSpecImportPreviewAdmissionGate.Admission::close);
        }
    }

    @Test
    void limitsPreviewFrequencyPerPlanAcrossActors() {
        UUID planId = UUID.randomUUID();
        for (int index = 0; index < ModelSpecImportPreviewAdmissionGate.MAX_REQUESTS_PER_PLAN_WINDOW; index++) {
            try (ModelSpecImportPreviewAdmissionGate.Admission admission = gate.enter("actor-" + index)) {
                admission.admitPlan(planId);
            }
        }
        try (ModelSpecImportPreviewAdmissionGate.Admission rejected = gate.enter("actor-rejected")) {
            assertThatThrownBy(() -> rejected.admitPlan(planId))
                .isInstanceOf(ModelSpecImportPreviewRequestParser.RequestLimitException.class)
                .satisfies(error ->
                    assertThat(((ModelSpecImportPreviewRequestParser.RequestLimitException) error).code()).isEqualTo(
                            "MODEL_IMPORT_PREVIEW_RATE_LIMITED"
                        )
                );
        }
    }
}
