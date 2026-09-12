package com.yuzhi.dts.platform.service.modeling;

import java.util.Collection;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Existing plan access seam delegates to the F9 authoritative scope and object policy. */
@Component
public class ModelSpecPlanWriteAccessAdapter implements ModelSpecPlanWriteAccessPort {
    private final ModelSpecAccessService access;
    public ModelSpecPlanWriteAccessAdapter(ModelSpecAccessService access) { this.access = access; }
    @Override public void requireBindingOperation(String tenant, UUID plan, UUID binding, String actor) { access.requireBindingOperation(tenant, plan, binding, actor); }
    @Override public boolean canMaintain(String tenant, UUID plan, String actor) { return access.canMaintain(tenant, plan, actor); }
    @Override public boolean canReadPlan(String tenant, UUID plan) { return access.canReadPlan(tenant, plan); }
    @Override public boolean canReadPlan(UUID plan) { return access.canReadPlan(plan); }
    @Override public boolean canEdit(String tenant, UUID model, String actor) { return access.canEdit(tenant, model, actor); }
    @Override public void requireEdit(String tenant, UUID model, String actor) { access.requireEdit(tenant, model, actor); }
    @Override public void requireOperation(String tenant, Collection<UUID> ids, String actor) { access.requireOperation(tenant, ids, actor); }
    @Override public void initializeOwner(String tenant, UUID model, String actor) { access.initializeOwner(tenant, model, actor); }
    @Override public void reclassifyAccess(String tenant, UUID model, String actor, ModelSpecContract.ModelType from, ModelSpecContract.ModelType to) {
        access.reclassifyAccess(tenant, model, actor, from, to);
    }
}
