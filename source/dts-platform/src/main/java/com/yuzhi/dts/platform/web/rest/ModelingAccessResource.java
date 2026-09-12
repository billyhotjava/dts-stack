package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.security.modeling.ModelingIdentity;
import com.yuzhi.dts.platform.service.modeling.*;
import com.yuzhi.dts.platform.service.modeling.ModelSpecAccessService.*;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/modeling")
public class ModelingAccessResource {
    private final ModelSpecAccessService access;
    private final ModelingDepartmentScope departments;
    private final ModelSpecApplicationService models;
    private final String tenant;
    public ModelingAccessResource(ModelSpecAccessService access, ModelingDepartmentScope departments, ModelSpecApplicationService models,
        @Value("${dts.platform.modeling.default-tenant-id:default}") String tenant) {
        this.access = access; this.departments = departments; this.models = models; this.tenant = tenant;
    }
    @GetMapping("/authorization")
    public ApiResponse<Map<String, Object>> authorization() {
        var user = ModelingIdentity.current();
        return ApiResponses.ok(Map.of("canModel", true, "canSelectDepartment", ModelingIdentity.institute(user),
            "departmentCode", Objects.toString(user.deptCode(), ""), "departments", departments.departments()));
    }
    @GetMapping("/model-specs/access-capabilities")
    public ApiResponse<Map<UUID, Capabilities>> capabilities(@RequestParam List<UUID> ids) { return ApiResponses.ok(access.capabilities(tenant, ids)); }
    @GetMapping("/model-specs/{id}/access-grants")
    public ApiResponse<GrantList> grants(@PathVariable UUID id) { return ApiResponses.ok(access.grants(tenant, id)); }
    @GetMapping("/model-specs/{id}/access-candidates")
    public ApiResponse<?> candidates(@PathVariable UUID id, @RequestParam(defaultValue = "") String keyword) { return ApiResponses.ok(access.candidates(tenant, id, keyword)); }
    @PostMapping("/model-specs/{id}/access-grants")
    public ResponseEntity<ApiResponse<Grant>> grant(@PathVariable UUID id, @RequestBody GrantCommand command) {
        GrantResult result = access.grant(tenant, id, command);
        return ResponseEntity.status(result.created() ? 201 : 200).body(ApiResponses.ok(result.grant()));
    }
    @DeleteMapping("/model-specs/{id}/access-grants/{grantId}")
    public ResponseEntity<Void> revoke(@PathVariable UUID id, @PathVariable UUID grantId) {
        access.revoke(tenant, id, grantId); return ResponseEntity.noContent().build();
    }
    @GetMapping("/model-specs/similar")
    public ApiResponse<List<SimilarModel>> similar(@RequestParam UUID planId, @RequestParam ModelType modelType,
        @RequestParam(required = false) UUID businessProcessId, @RequestParam(required = false) List<String> sourceKeys,
        @RequestParam(required = false) List<String> grainKeys, @RequestParam(required = false) UUID excludeModelSpecId) {
        if (!access.canReadPlan(tenant, planId)) throw new ModelSpecException("MODEL_SPEC_NOT_FOUND", "模型不存在或不可见", ModelSpecException.Kind.NOT_FOUND);
        if (excludeModelSpecId != null && !models.get(tenant, excludeModelSpecId).planId().equals(planId)) {
            throw new ModelSpecException("MODEL_SPEC_NOT_FOUND", "模型不存在或不可见", ModelSpecException.Kind.NOT_FOUND);
        }
        if (modelType == ModelType.APPLICATION) return ApiResponses.ok(List.of());
        Set<String> sources = nonempty(sourceKeys), grain = nonempty(grainKeys);
        if (sources.size() > 200 || grain.size() > 200) throw new ModelSpecException("MODEL_SIMILAR_QUERY_INVALID", "查询条件过多", ModelSpecException.Kind.BAD_REQUEST);
        List<SimilarModel> result = new ArrayList<>();
        for (var model : models.list(tenant, planId, null, modelType, null)) {
            if (model.id().equals(excludeModelSpecId) || model.status() == ModelStatus.ARCHIVED) continue;
            List<String> reasons = new ArrayList<>();
            if (businessProcessId != null && businessProcessId.equals(model.businessProcessId())) reasons.add("业务过程相同");
            if (!sources.isEmpty() && sources.equals(nonempty(model.sourceRefs().stream().map(SourceRef::ref).toList()))) reasons.add("来源相同");
            if (!grain.isEmpty() && model.grain() != null && grain.equals(nonempty(model.grain().keys()))) reasons.add("粒度相同");
            if (!reasons.isEmpty()) result.add(new SimilarModel(model.id(), model.name(), model.layer(), model.status(), reasons));
        }
        result.sort(Comparator.comparingInt((SimilarModel item) -> item.matchReasons().size()).reversed().thenComparing(SimilarModel::modelSpecId));
        return ApiResponses.ok(result.stream().limit(10).toList());
    }
    private static Set<String> nonempty(List<String> values) {
        if (values == null) return Set.of();
        return values.stream().filter(Objects::nonNull).map(String::trim).filter(value -> !value.isEmpty()).collect(java.util.stream.Collectors.toSet());
    }
    public record SimilarModel(UUID modelSpecId, String name, Layer layer, ModelStatus status, List<String> matchReasons) {}
}
