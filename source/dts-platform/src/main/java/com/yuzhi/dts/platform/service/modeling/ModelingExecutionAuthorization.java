package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.security.modeling.ModelingIdentityService;
import com.yuzhi.dts.platform.security.modeling.ModelingIdentityException;
import com.yuzhi.dts.platform.repository.modeling.PlanOperationalRunRepository.OpenedRun;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ModelingExecutionAuthorization {
    private final JdbcTemplate jdbc;
    private final ModelingIdentityService identities;
    private final ModelSpecWriteAccessPort access;
    private final ModelingPermissionAudit audit;
    public ModelingExecutionAuthorization(JdbcTemplate jdbc, ModelingIdentityService identities, ModelSpecPlanWriteAccessPort access, ModelingPermissionAudit audit) {
        this.jdbc=jdbc; this.identities=identities; this.access=access; this.audit=audit;
    }
    public ModelingIdentityService.Scope candidate(String tenant, UUID candidate, int version, String eventStatus) {
        List<String> actors = jdbc.query("select actor_id from modeling_model_release_candidate_command where tenant_id=? and candidate_id=? and candidate_version=? and to_status=?", (rs,n) -> rs.getString(1), tenant, candidate, version, eventStatus);
        if (actors.size()!=1) throw denied("MODELING_EXECUTION_INITIATOR_MISSING");
        return actor(tenant, actors.getFirst(), jdbc.query("select model_spec_id from modeling_model_release_candidate_entry where tenant_id=? and candidate_id=? order by model_spec_id", (rs,n) -> rs.getObject(1,UUID.class), tenant,candidate));
    }
    public ModelingIdentityService.Scope actor(String tenant, String actor, Collection<UUID> models) {
        ModelingIdentityService.Scope identity = openActor(actor);
        try { access.requireOperation(tenant, models, actor); return identity; }
        catch (ModelSpecException failure) { identity.close(); audit.denied(actor,"MODELING_OPERATION_SCOPE_DENIED",null,"MODEL_EXECUTION_AUTHORIZATION_REVOKED"); throw denied("MODEL_EXECUTION_AUTHORIZATION_REVOKED"); }
        catch (RuntimeException failure) { identity.close(); throw failure; }
    }
    public ModelingIdentityService.Scope operational(OpenedRun run) {
        List<String> actors = jdbc.query("select initiator_id from modeling_operational_run_dispatch where tenant_id=? and id=? and binding_id=? and binding_version=? and scope_checksum=? and trigger_type='MANUAL'", (rs,n) -> rs.getString(1),run.tenantId(),run.pipelineRunGroupId(),run.bindingId(),run.bindingVersion(),run.scopeChecksum());
        if (actors.size()!=1 || actors.getFirst()==null) throw denied("MODELING_EXECUTION_INITIATOR_MISSING");
        ModelingIdentityService.Scope identity=openActor(actors.getFirst());
        try {
            requireBindingSnapshot(run);
            access.requireBindingOperation(run.tenantId(),run.planId(),run.bindingId(),actors.getFirst());
            return identity;
        } catch (ModelSpecException failure) { identity.close(); audit.denied(actors.getFirst(),"MODELING_OPERATION_SCOPE_DENIED",null,"MODEL_EXECUTION_AUTHORIZATION_REVOKED"); throw denied("MODEL_EXECUTION_AUTHORIZATION_REVOKED"); }
        catch (RuntimeException failure) { identity.close(); throw failure; }
    }
    ModelingIdentityService.Scope system(OpenedRun run) {
        if (!"CRON".equals(run.triggerType())) throw denied("MODELING_SYSTEM_SCOPE_INVALID");
        requireBindingSnapshot(run);
        return ModelingSystemExecution.open(run.tenantId(),run.planId());
    }
    private void requireBindingSnapshot(OpenedRun run) {
        Integer count=jdbc.queryForObject("select count(*) from modeling_plan_execution_binding where tenant_id=? and id=? and plan_id=? and version=? and desired_scope_checksum=? and deployment_status='ACTIVE' and deployed_checksum=desired_deployment_checksum and coalesce(airflow_paused,false)=false", Integer.class,run.tenantId(),run.bindingId(),run.planId(),run.bindingVersion(),run.scopeChecksum());
        if (count==null || count!=1) throw denied("MODELING_EXECUTION_SCOPE_CHANGED");
    }
    private ModelingIdentityService.Scope openActor(String actor) {
        try { return identities.openCurrentUser(actor); }
        catch (ModelingIdentityException failure) {
            if (failure.status() == 503) throw failure;
            audit.denied(actor,"MODELING_OPERATION_SCOPE_DENIED",null,"MODEL_EXECUTION_AUTHORIZATION_REVOKED");
            throw denied("MODEL_EXECUTION_AUTHORIZATION_REVOKED");
        }
    }
    private static ModelingIdentityException denied(String code) { return new ModelingIdentityException(403,code,"后台任务授权或执行范围已失效"); }
}
