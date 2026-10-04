package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.security.modeling.ModelingIdentity;
import com.yuzhi.dts.platform.service.catalog.CatalogSourceReferenceReadPort;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceLocator;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceType;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Resolves governed outputs through the existing producer and implementation records. */
@Service
public class ModelingSourceScopeGuard {
    private final JdbcTemplate jdbc;
    private final CatalogSourceReferenceReadPort catalog;
    private final ModelSpecAccessService access;
    public ModelingSourceScopeGuard(JdbcTemplate jdbc, CatalogSourceReferenceReadPort catalog, ModelSpecAccessService access) {
        this.jdbc = jdbc; this.catalog = catalog; this.access = access;
    }
    public void requireModels(String tenant, UUID targetPlan, Collection<UUID> modelIds) {
        if (modelIds == null || modelIds.isEmpty()) return;
        String dept = department(tenant, targetPlan);
        List<UUID> ids = modelIds.stream().distinct().toList();
        if (ModelingIdentity.optional().isPresent()) {
            if (access.capabilities(tenant, ids).size() != ids.size()) throw missing();
        } else if (!ModelingSystemExecution.permits(tenant, targetPlan)) throw missing();
        List<Object> args = new ArrayList<>(); args.add(tenant); args.addAll(ids);
        List<String> departments = jdbc.query("select p.owner_department_id from modeling_model_spec s join modeling_warehouse_plan p on p.id=s.plan_id and p.tenant_id=s.tenant_id where s.tenant_id=? and s.id in (" + String.join(",", Collections.nCopies(ids.size(), "?")) + ")", (rs,n) -> rs.getString(1), args.toArray());
        if (departments.size() != ids.size()) throw missing();
        if (departments.stream().anyMatch(value -> !dept.equals(value))) throw crossDepartment();
    }
    public void requireSource(String tenant, UUID targetPlan, SourceType type, SourceLocator locator) {
        if (type == null || locator == null) throw unverifiable();
        Optional<String> key = switch (type) {
            case CATALOG_TABLE -> locator.assetId() == null ? Optional.empty() : catalog.findDatasetAssetKeyByTableId(locator.assetId());
            case CONNECTION_TABLE -> catalog.findDatasetAssetKey(locator.connectionId(), locator.namespace(), locator.objectName());
            default -> Optional.empty();
        };
        List<UUID> models = new ArrayList<>();
        if (key.isPresent()) {
            models.addAll(jdbc.query("""
                select s.id from catalog_asset_producer_ref r
                left join modeling_model_spec s on s.id::text = r.producer_id and s.tenant_id = ?
                where r.asset_key = ? and r.producer_kind = 'MODELING' and r.valid_to is null
                """, (rs,n) -> rs.getObject(1, UUID.class), tenant, key.orElseThrow()));
        }
        if (type == SourceType.DBT_NODE) {
            models.addAll(jdbc.query("select model_spec_id from modeling_model_implementation where tenant_id=? and project_key=? and dbt_unique_id=?", (rs,n) -> rs.getObject(1, UUID.class), tenant, locator.projectKey(), locator.uniqueId()));
        }
        if (type == SourceType.CONNECTION_TABLE && locator.namespace() != null && locator.objectName() != null) {
            // Physical aliases cannot conceal an output that has already been observed as governed.
            models.addAll(jdbc.query("select distinct model_spec_id from modeling_physical_relation_observation where tenant_id=? and schema_name=? and identifier=? and verified=true and relation_exists=true", (rs,n) -> rs.getObject(1, UUID.class), tenant, locator.namespace(), locator.objectName()));
        }
        if (models.stream().anyMatch(Objects::isNull)) throw unverifiable();
        requireModels(tenant, targetPlan, models);
    }
    private String department(String tenant, UUID plan) {
        List<String> values = jdbc.query("select owner_department_id from modeling_warehouse_plan where tenant_id=? and id=?", (rs,n) -> rs.getString(1), tenant, plan);
        if (values.size()!=1 || values.getFirst()==null || values.getFirst().isBlank()) throw missing();
        if (ModelingIdentity.optional().isPresent() && !access.canReadPlan(tenant,plan)) throw missing();
        if (ModelingIdentity.optional().isEmpty() && !ModelingSystemExecution.permits(tenant, plan)) throw missing();
        return values.getFirst();
    }
    private static ModelSpecException missing() { return new ModelSpecException("MODEL_SPEC_NOT_FOUND", "模型不存在或不可见", ModelSpecException.Kind.NOT_FOUND); }
    private static ModelSpecException unverifiable() { return new ModelSpecException("MODELING_SOURCE_PROVENANCE_UNVERIFIABLE", "来源归属暂时无法核验", ModelSpecException.Kind.UNPROCESSABLE); }
    private static ModelSpecException crossDepartment() { return new ModelSpecException("MODELING_CROSS_DEPARTMENT_REFERENCE_PENDING_APPROVAL", "跨部门模型引用暂未开放", ModelSpecException.Kind.UNPROCESSABLE); }
}
