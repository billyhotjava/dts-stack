package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.modeling.ModelingIdentity;
import com.yuzhi.dts.platform.security.modeling.ModelingIdentityException;
import com.yuzhi.dts.platform.service.admin.gateway.directory.AdminDirectoryGateway;
import com.yuzhi.dts.platform.service.admin.gateway.directory.AdminDirectoryGateway.ModelingUser;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ModelSpecAccessService implements ModelSpecWriteAccessPort {
    private final JdbcTemplate jdbc;
    private final AdminDirectoryGateway directory;
    private final ModelSpecDomainReadAccessPort domains;
    private final ModelingPermissionAudit audit;
    private final String serverTenant;

    public ModelSpecAccessService(JdbcTemplate jdbc, AdminDirectoryGateway directory, ModelSpecDomainReadAccessPort domains,
        ModelingPermissionAudit audit, @Value("${dts.platform.modeling.default-tenant-id:default}") String serverTenant) {
        this.jdbc = jdbc; this.directory = directory; this.domains = domains; this.audit = audit; this.serverTenant = serverTenant;
    }

    @Transactional
    public void requireBindingOperation(String tenant, UUID planId, UUID bindingId, String actor) {
        var ids = jdbc.query("""
            select e.model_spec_id from modeling_plan_execution_binding b
            join modeling_plan_execution_binding_entry e on e.binding_id = b.id and e.tenant_id = b.tenant_id
            where b.tenant_id = ? and b.plan_id = ? and b.id = ? order by e.model_spec_id
            """, (rs, n) -> rs.getObject(1, UUID.class), tenant, planId, bindingId);
        if (ids.isEmpty()) throw missing();
        requireOperation(tenant, ids, actor);
    }

    public void requirePathRead(String path) {
        var matcher = java.util.regex.Pattern.compile("^/api/modeling/(model-specs|plans|warehouse-plans)/([0-9a-fA-F-]{36})(?:/|$)").matcher(path);
        if (!matcher.find()) return;
        UUID id;
        try { id = UUID.fromString(matcher.group(2)); } catch (IllegalArgumentException ex) { throw missing(); }
        if ("model-specs".equals(matcher.group(1))) requireVisible(serverTenant, id, false);
        else if (!canReadPlan(serverTenant, id)) throw missing();
    }

    public boolean canMaintain(String tenant, UUID planId, String actor) {
        return serverTenant.equals(tenant) && ModelingIdentity.matchesActor(actor) && canReadPlan(tenant, planId);
    }

    @Override
    public boolean canReadPlan(UUID planId) { return canReadPlan(serverTenant, planId); }
    @Override
    public boolean canReadPlan(String tenant, UUID planId) {
        if (!serverTenant.equals(tenant) || planId == null) return false;
        var request = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        String cacheKey = getClass().getName() + ":plans:" + tenant + ":" + ModelingIdentity.current().id();
        @SuppressWarnings("unchecked")
        Set<UUID> plans = request == null ? null : (Set<UUID>) request.getAttribute(cacheKey, 0);
        if (plans == null) {
            var user = ModelingIdentity.current();
            plans = new HashSet<>(jdbc.query("select id from modeling_warehouse_plan where tenant_id = ? and (? or owner_department_id = ?)",
                (rs, n) -> rs.getObject(1, UUID.class), tenant, ModelingIdentity.institute(user), user.deptCode()));
            if (request != null) request.setAttribute(cacheKey, plans, 0);
        }
        return plans.contains(planId);
    }

    @Override
    public boolean canEdit(String tenant, UUID id, String actor) {
        if (!serverTenant.equals(tenant) || !ModelingIdentity.matchesActor(actor)) return false;
        return visibleRows(tenant, List.of(id), false).stream().anyMatch(this::editable);
    }

    @Override
    @Transactional
    public void requireEdit(String tenant, UUID id, String actor) {
        Row row = requireVisible(tenant, id, true);
        if (!ModelingIdentity.matchesActor(actor) || !editable(row)) {
            deny(actor, id, "MODEL_EDIT_GRANT_REQUIRED");
        }
    }

    @Override
    @Transactional
    public void requireOperation(String tenant, Collection<UUID> ids, String actor) {
        if (ids == null || ids.isEmpty() || ids.size() > 200 || ids.stream().anyMatch(Objects::isNull)) {
            throw new ModelSpecException("MODEL_OPERATION_SCOPE_INVALID", "模型操作范围无效", ModelSpecException.Kind.BAD_REQUEST);
        }
        List<Row> targets = visibleRows(tenant, ids, true);
        if (targets.size() != new HashSet<>(ids).size()) throw missing();
        if (!ModelingIdentity.matchesActor(actor) || targets.stream().anyMatch(row -> !editable(row))) {
            audit.denied(actor, "MODELING_OPERATION_SCOPE_DENIED", null, "MODEL_OPERATION_SCOPE_DENIED");
            throw new ModelSpecException("MODEL_OPERATION_SCOPE_DENIED", "没有操作全部目标模型的权限", ModelSpecException.Kind.FORBIDDEN);
        }
    }

    @Override
    @Transactional
    public void initializeOwner(String tenant, UUID id, String actor) {
        if (!ModelingIdentity.matchesActor(actor)) throw missing();
        jdbc.update("""
            update modeling_model_spec set owner_id = ?, owner_display_name = ?
             where tenant_id = ? and id = ? and model_type = 'APPLICATION' and owner_id is null
            """, actor, ModelingIdentity.current().displayName(), tenant, id);
    }

    @Override
    @Transactional
    public void reclassifyAccess(String tenant, UUID id, String actor, ModelSpecContract.ModelType from, ModelSpecContract.ModelType to) {
        Row row = requireVisible(tenant, id, true);
        if (!ModelingIdentity.matchesActor(actor) || !editable(row)) deny(actor, id, "MODEL_EDIT_GRANT_REQUIRED");
        if (from == to || (from != ModelSpecContract.ModelType.APPLICATION && to != ModelSpecContract.ModelType.APPLICATION)) return;
        if (!manager(row)) deny(actor, id, "MODEL_ACCESS_MANAGE_DENIED");
        if (to == ModelSpecContract.ModelType.APPLICATION && row.ownerId() == null) {
            jdbc.update("update modeling_model_spec set owner_id = ?, owner_display_name = ? where tenant_id = ? and id = ? and owner_id is null",
                actor, ModelingIdentity.current().displayName(), tenant, id);
        }
        if (from == ModelSpecContract.ModelType.APPLICATION) {
            jdbc.update("update modeling_model_access set revoked_by = ?, revoked_at = current_timestamp where tenant_id = ? and model_spec_id = ? and revoked_at is null", actor, tenant, id);
            audit.success(actor, "MODELING_MODEL_ACCESS_REVOKE", id.toString(), Map.of("reason", "LAYER_CHANGED"));
        }
    }

    public Map<UUID, Capabilities> capabilities(String tenant, Collection<UUID> ids) {
        Map<UUID, Capabilities> result = new LinkedHashMap<>();
        for (Row row : visibleRows(tenant, ids, false)) {
            result.put(row.id(), new Capabilities(row.ownerId(), row.ownerName(), editable(row), manager(row), row.department()));
        }
        return result;
    }

    public GrantList grants(String tenant, UUID id) {
        Row row = requireVisible(tenant, id, false);
        requireAds(row);
        if (!manager(row)) return new GrantList(false, List.of());
        return new GrantList(true, jdbc.query("""
            select id, grantee_type, grantee_id, grantee_name, permission, granted_by, granted_at
              from modeling_model_access where tenant_id = ? and model_spec_id = ? and revoked_at is null order by granted_at, id
            """, (rs, n) -> new Grant(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getTimestamp(7).toInstant()), tenant, id));
    }

    public List<ModelingUser> candidates(String tenant, UUID id, String keyword) {
        Row row = requireVisible(tenant, id, false);
        requireAds(row);
        if (!manager(row)) deny(ModelingIdentity.current().id(), id, "MODEL_ACCESS_MANAGE_DENIED");
        try { return directory.modelingCandidates(keyword, row.department()); }
        catch (RuntimeException ex) { throw directoryUnavailable(); }
    }

    @Transactional
    public GrantResult grant(String tenant, UUID id, GrantCommand command) {
        Row row = requireVisible(tenant, id, true);
        requireAds(row);
        String actor = ModelingIdentity.current().id();
        if (!manager(row)) deny(actor, id, "MODEL_ACCESS_MANAGE_DENIED");
        if ("ARCHIVED".equals(row.status())) throw new ModelSpecException("MODEL_SPEC_ARCHIVED", "已归档模型不能新增授权", ModelSpecException.Kind.CONFLICT);
        if (command == null || !"EDITOR".equals(command.permission()) || command.granteeId() == null || command.granteeId().isBlank() || command.granteeId().length() > 128) {
            throw new ModelSpecException("MODEL_ACCESS_INVALID", "共享参数无效", ModelSpecException.Kind.BAD_REQUEST);
        }
        String name;
        if ("USER".equals(command.granteeType())) {
            ModelingUser target;
            try { target = directory.currentModelingUser(command.granteeId()); }
            catch (com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayException ex) {
                if (Integer.valueOf(404).equals(ex.getUpstreamStatus())) throw invalidGrantee();
                throw directoryUnavailable();
            } catch (RuntimeException ex) { throw directoryUnavailable(); }
            if (target == null || !command.granteeId().equals(target.id()) || !ModelingIdentity.isAuthor(target)) throw invalidGrantee();
            if (!row.department().equals(target.deptCode())) throw new ModelSpecException("MODEL_ACCESS_CROSS_DEPARTMENT_PENDING_APPROVAL", "跨部门共享暂未开放", ModelSpecException.Kind.UNPROCESSABLE);
            name = target.displayName();
        } else if ("ROLE".equals(command.granteeType()) && Set.of(AuthoritiesConstants.DEPT_DATA_OWNER, AuthoritiesConstants.DEPT_LEADER).contains(command.granteeId())) {
            name = AuthoritiesConstants.DEPT_LEADER.equals(command.granteeId()) ? "部门领导" : "部门数据管理员";
        } else throw invalidGrantee();
        int inserted = jdbc.update("""
            insert into modeling_model_access(id, tenant_id, model_spec_id, grantee_type, grantee_id, grantee_name, permission, granted_by, granted_at)
            values (?, ?, ?, ?, ?, ?, 'EDITOR', ?, current_timestamp)
            on conflict (tenant_id, model_spec_id, grantee_type, grantee_id) where revoked_at is null do nothing
            """, UUID.randomUUID(), tenant, id, command.granteeType(), command.granteeId(), name, actor);
        Grant grant = grants(tenant, id).items().stream().filter(item -> item.granteeType().equals(command.granteeType()) && item.granteeId().equals(command.granteeId())).findFirst().orElseThrow();
        if (inserted == 1) audit.success(actor, "MODELING_MODEL_ACCESS_GRANT", id.toString(), Map.of("grantId", grant.id(), "granteeType", grant.granteeType(), "granteeId", grant.granteeId()));
        return new GrantResult(grant, inserted == 1);
    }

    @Transactional
    public void revoke(String tenant, UUID id, UUID grantId) {
        Row row = requireVisible(tenant, id, true);
        requireAds(row);
        String actor = ModelingIdentity.current().id();
        if (!manager(row)) deny(actor, id, "MODEL_ACCESS_MANAGE_DENIED");
        Integer count = jdbc.queryForObject("select count(*) from modeling_model_access where tenant_id = ? and model_spec_id = ? and id = ?", Integer.class, tenant, id, grantId);
        if (count == null || count == 0) throw missing();
        int changed = jdbc.update("update modeling_model_access set revoked_by = ?, revoked_at = current_timestamp where tenant_id = ? and model_spec_id = ? and id = ? and revoked_at is null", actor, tenant, id, grantId);
        if (changed == 1) audit.success(actor, "MODELING_MODEL_ACCESS_REVOKE", id.toString(), Map.of("grantId", grantId));
    }

    private List<Row> rows(String tenant, Collection<UUID> ids, boolean lock) {
        if (!serverTenant.equals(tenant) || ids == null || ids.isEmpty()) return List.of();
        if (ids.size() > 200) throw new ModelSpecException("MODEL_OPERATION_SCOPE_INVALID", "单次最多操作 200 个模型", ModelSpecException.Kind.BAD_REQUEST);
        if (lock) {
            List<Object> lockArgs = new ArrayList<>(); lockArgs.add(tenant); lockArgs.addAll(ids);
            jdbc.query("select id from modeling_warehouse_plan where tenant_id = ? and id in (select plan_id from modeling_model_spec where tenant_id = ? and id in (" + placeholders(ids.size()) + ")) order by id for update",
                rs -> { }, java.util.stream.Stream.concat(java.util.stream.Stream.of(tenant), lockArgs.stream()).toArray());
            jdbc.query("select id from modeling_model_spec where tenant_id = ? and id in (" + placeholders(ids.size()) + ") order by id for update",
                rs -> { }, lockArgs.toArray());
        }
        var user = ModelingIdentity.current();
        List<Object> parameters = new ArrayList<>();
        parameters.add(user.id());
        parameters.addAll(user.roles());
        parameters.add(tenant);
        parameters.addAll(ids);
        String sql = """
            select s.id, s.plan_id, s.domain_id, s.model_type, s.status, s.owner_id, s.owner_display_name,
                   p.owner_department_id, p.lifecycle_status,
                   exists(select 1 from modeling_model_access a where a.tenant_id = s.tenant_id and a.model_spec_id = s.id
                       and a.revoked_at is null and a.permission = 'EDITOR' and
                       ((a.grantee_type = 'USER' and a.grantee_id = ?) or (a.grantee_type = 'ROLE' and a.grantee_id in (%s)))) as granted
              from modeling_model_spec s join modeling_warehouse_plan p on p.id = s.plan_id and p.tenant_id = s.tenant_id
             where s.tenant_id = ? and s.id in (%s) order by s.id
            """.formatted(placeholders(user.roles().size()), placeholders(ids.size()));
        return jdbc.query(sql, (rs, n) -> new Row(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
            rs.getObject(3, UUID.class), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getBoolean(10)), parameters.toArray());
    }
    private Row requireVisible(String tenant, UUID id, boolean lock) {
        return visibleRows(tenant, List.of(id), lock).stream().findFirst().orElseThrow(ModelSpecAccessService::missing);
    }
    private List<Row> visibleRows(String tenant, Collection<UUID> ids, boolean lock) {
        List<Row> rows = rows(tenant, ids, lock);
        if (rows.isEmpty()) return rows;
        Set<UUID> visible = visibleDomains();
        return rows.stream().filter(row -> ModelingIdentity.department(row.department()) && row.domainId()!=null && visible.contains(row.domainId())).toList();
    }
    private Set<UUID> visibleDomains() {
        var request = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        String key = getClass().getName() + ":domains:" + ModelingIdentity.current().id();
        @SuppressWarnings("unchecked") Set<UUID> result = request == null ? null : (Set<UUID>) request.getAttribute(key, 0);
        if (result == null) { result = Set.copyOf(domains.visibleDomainIds()); if (request != null) request.setAttribute(key, result, 0); }
        return result;
    }
    private boolean manager(Row row) {
        var user = ModelingIdentity.current();
        return ModelingIdentity.institute(user) || (row.department().equals(user.deptCode()) &&
            (user.roles().contains(AuthoritiesConstants.DEPT_LEADER) || ("APPLICATION".equals(row.type()) && user.id().equals(row.ownerId()))));
    }
    private boolean editable(Row row) {
        if ("ARCHIVED".equals(row.status()) || Set.of("ARCHIVED", "PUBLISHED").contains(row.planStatus())) return false;
        return !"APPLICATION".equals(row.type()) || manager(row) || (row.department().equals(ModelingIdentity.current().deptCode()) && row.granted());
    }
    private void requireAds(Row row) { if (!"APPLICATION".equals(row.type())) throw new ModelSpecException("MODEL_ACCESS_LAYER_NOT_SHAREABLE", "仅应用层模型支持共享编辑权", ModelSpecException.Kind.UNPROCESSABLE); }
    private void deny(String actor, UUID id, String code) {
        audit.denied(actor, "MODELING_MODEL_EDIT_DENIED", id.toString(), code);
        throw new ModelSpecException(code, "没有操作此模型的权限", ModelSpecException.Kind.FORBIDDEN);
    }
    private static String placeholders(int size) { return String.join(",", Collections.nCopies(size, "?")); }
    private static ModelSpecException missing() { return new ModelSpecException("MODEL_SPEC_NOT_FOUND", "模型不存在或不可见", ModelSpecException.Kind.NOT_FOUND); }
    private static ModelSpecException invalidGrantee() { return new ModelSpecException("MODEL_ACCESS_GRANTEE_NOT_AUTHOR", "请选择有效建模用户或部门建模角色", ModelSpecException.Kind.UNPROCESSABLE); }
    private static ModelingIdentityException directoryUnavailable() { return new ModelingIdentityException(503, "MODEL_ACCESS_DIRECTORY_UNAVAILABLE", "当前用户目录不可用，请稍后重试"); }
    private record Row(UUID id, UUID planId, UUID domainId, String type, String status, String ownerId, String ownerName, String department, String planStatus, boolean granted) {}
    public record Capabilities(String ownerId, String ownerName, boolean canEdit, boolean canManage, String departmentCode) {}
    public record Grant(UUID id, String granteeType, String granteeId, String granteeName, String permission, String grantedBy, java.time.Instant grantedAt) {}
    public record GrantList(boolean canManage, List<Grant> items) {}
    public record GrantCommand(String granteeType, String granteeId, String permission) {}
    public record GrantResult(Grant grant, boolean created) {}
}
