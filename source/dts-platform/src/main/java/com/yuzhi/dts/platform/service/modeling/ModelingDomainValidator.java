package com.yuzhi.dts.platform.service.modeling;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/** Pure domain rules for the ModelSpec-based modeling workflow. */
public final class ModelingDomainValidator {

    private ModelingDomainValidator() {}

    public record Issue(String code, String message) {}

    public static List<Issue> validateBusinessObject(ModelingVNextContract.BusinessObject object, String tenantId) {
        List<Issue> issues = new ArrayList<>();
        if (object == null) return List.of(new Issue("OBJECT_REQUIRED", "业务对象不能为空"));
        if (isBlank(tenantId)) issues.add(new Issue("TENANT_REQUIRED", "租户不能为空"));
        if (isBlank(object.code())) issues.add(new Issue("CODE_REQUIRED", "业务对象编码不能为空"));
        if (isBlank(object.name())) issues.add(new Issue("NAME_REQUIRED", "业务对象名称不能为空"));
        if (isBlank(object.processId())) issues.add(new Issue("PROCESS_REQUIRED", "业务对象必须绑定业务过程"));
        if (!hasGrain(object.grain())) issues.add(new Issue("GRAIN_REQUIRED", "业务对象必须声明业务粒度"));
        if (object.businessKey() == null || object.businessKey().stream().noneMatch(ModelingDomainValidator::notBlank)) {
            issues.add(new Issue("BUSINESS_KEY_REQUIRED", "业务对象必须声明业务键或技术键"));
        }
        return List.copyOf(issues);
    }

    public static List<Issue> validateModelSpec(ModelingVNextContract.ModelSpec model, ModelingVNextContract.BusinessObject object) {
        List<Issue> issues = new ArrayList<>();
        if (model == null) return List.of(new Issue("MODEL_REQUIRED", "模型不能为空"));
        if (isBlank(model.name())) issues.add(new Issue("NAME_REQUIRED", "模型名称不能为空"));
        if (isBlank(model.processId())) issues.add(new Issue("PROCESS_REQUIRED", "模型必须绑定业务过程"));
        if (model.layer() == null) issues.add(new Issue("LAYER_REQUIRED", "模型层级不能为空"));
        if (!hasGrain(model.grain())) issues.add(new Issue("GRAIN_REQUIRED", "模型必须声明业务粒度"));
        if (object == null) {
            issues.add(new Issue("MODEL_OBJECT_NOT_FOUND", "模型关联的业务对象不存在"));
        } else {
            if (isBlank(object.processId()) || !object.processId().equals(model.processId())) {
                issues.add(new Issue("PROCESS_MISMATCH", "模型与业务对象必须属于同一业务过程"));
            }
            if (model.objectId() == null || !model.objectId().equals(object.id())) {
                issues.add(new Issue("OBJECT_MISMATCH", "模型只能关联当前业务对象"));
            }
        }
        if (model.layer() == ModelingVNextContract.Layer.DWD) {
            if (object == null || object.businessKey() == null || object.businessKey().stream().noneMatch(ModelingDomainValidator::notBlank)) {
                issues.add(new Issue("BUSINESS_KEY_REQUIRED", "DWD 模型必须声明业务键或技术键"));
            }
            if (
                model.implementationMode() == ModelingVNextContract.ImplementationMode.DESIGNER_GENERATED &&
                (model.standardBindings() == null || model.standardBindings().isEmpty())
            ) {
                issues.add(new Issue("STANDARD_BINDING_REQUIRED", "DWD 设计器模型至少绑定一个数据标准"));
            }
        }
        return List.copyOf(issues);
    }

    public static List<Issue> validateBusinessObjectCode(String code, List<String> existingCodes, String tenantId) {
        List<Issue> issues = new ArrayList<>();
        if (isBlank(tenantId)) issues.add(new Issue("TENANT_REQUIRED", "租户不能为空"));
        if (isBlank(code)) {
            issues.add(new Issue("CODE_REQUIRED", "业务对象编码不能为空"));
        } else if (existingCodes != null && new HashSet<>(existingCodes).stream().map(ModelingDomainValidator::normalize).anyMatch(normalize(code)::equals)) {
            issues.add(new Issue("DUPLICATE_OBJECT_CODE", "同一租户下业务对象编码不能重复"));
        }
        return List.copyOf(issues);
    }

    private static boolean hasGrain(ModelingVNextContract.Grain grain) {
        return grain != null && notBlank(grain.statement()) && grain.keys() != null && grain.keys().stream().anyMatch(ModelingDomainValidator::notBlank);
    }

    private static boolean notBlank(String value) {
        return !isBlank(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
