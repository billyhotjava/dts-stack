package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModelCandidateScopePolicyTest {
    @Test
    void keepsOrdinaryPlanExclusivityButAllowsDisjointStructureModels() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        for (CandidateOrigin existingOrigin : CandidateOrigin.values()) {
            var candidate = mock(CandidateView.class);
            var entry = mock(EntryView.class);
            when(candidate.status()).thenReturn(DeliveryStatus.BUILT);
            when(candidate.origin()).thenReturn(existingOrigin);
            when(candidate.environment()).thenReturn("DEV");
            when(candidate.entries()).thenReturn(List.of(entry));
            when(entry.modelSpecId()).thenReturn(first);
            for (CandidateOrigin requested : CandidateOrigin.values()) {
                assertThat(ModelCandidateScopePolicy.conflicts(candidate, requested, "DEV", List.of(first))).isTrue();
                assertThat(ModelCandidateScopePolicy.conflicts(candidate, requested, "DEV", List.of(second))).isFalse();
                assertThat(ModelCandidateScopePolicy.conflicts(candidate, requested, "TEST", List.of(first))).isFalse();
                assertThat(ModelCandidateScopePolicy.conflicts(candidate, requested, "DEV", List.of(second, first))).isTrue();
            }
            when(candidate.status()).thenReturn(DeliveryStatus.PUBLISHED);
            assertThat(ModelCandidateScopePolicy.conflicts(candidate, CandidateOrigin.SCHEMA_ONLY_INTENT, "DEV", List.of(first))).isFalse();
        }
    }
}
