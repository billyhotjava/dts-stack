package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModelSpecPlanWriteAccessAdapterTest {
    @Test void delegatesObjectAndPlanPoliciesWithoutOwnerFallback() {
        var policy = mock(ModelSpecAccessService.class);
        var adapter = new ModelSpecPlanWriteAccessAdapter(policy);
        UUID plan = UUID.randomUUID(), model = UUID.randomUUID();
        when(policy.canMaintain("tenant", plan, "stable-user")).thenReturn(true);
        assertThat(adapter.canMaintain("tenant", plan, "stable-user")).isTrue();
        assertThat(adapter.canEdit("tenant", model, "stable-user")).isFalse();
        doThrow(new ModelSpecException("MODEL_OPERATION_SCOPE_DENIED", "denied", ModelSpecException.Kind.FORBIDDEN))
            .when(policy).requireOperation("tenant", List.of(model), "stable-user");
        assertThatThrownBy(() -> adapter.requireOperation("tenant", List.of(model), "stable-user"))
            .isInstanceOf(ModelSpecException.class);
        verify(policy).requireOperation("tenant", List.of(model), "stable-user");
    }
}
