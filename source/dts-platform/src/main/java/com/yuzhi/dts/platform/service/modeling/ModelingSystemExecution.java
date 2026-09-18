package com.yuzhi.dts.platform.service.modeling;

import java.util.Objects;
import java.util.UUID;

/** Package-private entry: only the authenticated deployed-binding execution path opens this scope. */
final class ModelingSystemExecution {
    private static final ThreadLocal<Binding> CURRENT = new ThreadLocal<>();
    private ModelingSystemExecution() {}
    static boolean permits(String tenant, UUID plan) {
        Binding binding = CURRENT.get();
        return binding != null && Objects.equals(binding.tenant(), tenant) && Objects.equals(binding.plan(), plan);
    }
    static com.yuzhi.dts.platform.security.modeling.ModelingIdentityService.Scope open(String tenant, UUID plan) {
        Binding previous = CURRENT.get(); CURRENT.set(new Binding(tenant, plan));
        return () -> { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); };
    }
    private record Binding(String tenant, UUID plan) {}
}
