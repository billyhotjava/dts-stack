package com.yuzhi.dts.platform.service.modeling;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.yuzhi.dts.platform.repository.modeling.ModelReleaseCandidateRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
class ModelDataRegistrationServiceTest {
    @Test
    void rejectsAStaleModelBeforeRegisteringOrPublishingAnything() {
        var models=mock(ModelSpecApplicationService.class); var candidates=mock(ModelReleaseCandidateRepository.class);
        var access=mock(ModelSpecPlanWriteAccessPort.class); var registration=mock(CandidateQualityAssetRegistrationService.class);
        UUID id=UUID.randomUUID(), plan=UUID.randomUUID(); var model=mock(ModelSpecContract.ModelSpecView.class);
        when(model.planId()).thenReturn(plan); when(model.revision()).thenReturn(4); when(models.get("tenant", id)).thenReturn(model);
        when(access.canMaintain("tenant", plan, "actor")).thenReturn(true);
        var service=new ModelDataRegistrationService(models, candidates, access, registration);
        assertThatThrownBy(() -> service.register("tenant", "actor", id, new ModelDataRegistrationService.Command(UUID.randomUUID(), 1, 3, "a".repeat(64))))
            .isInstanceOfSatisfying(ModelReleaseCandidateException.class, error -> assertThat(error.code()).isEqualTo("MODEL_DATA_REGISTRATION_STALE"));
        verifyNoInteractions(registration, candidates);
    }
}
