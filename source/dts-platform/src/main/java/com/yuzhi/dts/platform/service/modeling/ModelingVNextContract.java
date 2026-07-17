package com.yuzhi.dts.platform.service.modeling;

import java.util.ArrayList;
import java.util.List;

/** Cross-layer contract for the new ModelSpec-based modeling version. */
public final class ModelingVNextContract {

    public static final int CONTRACT_VERSION = 1;

    private ModelingVNextContract() {}

    public enum Layer {
        ODS,
        STG,
        DWD,
        DWS,
        ADS,
    }

    public enum ObjectKind {
        ENTITY,
        FACT,
        EVENT,
        SNAPSHOT,
        DIMENSION,
    }

    public enum ModelType {
        FACT,
        DIMENSION,
        SUMMARY,
        APPLICATION,
    }

    public enum ImplementationMode {
        DESIGNER_GENERATED,
        DBT_MANAGED,
    }

    public record Grain(String statement, List<String> keys) {}

    public record SourceRef(String kind, String ref, Layer layer) {}

    public record StandardBinding(String fieldName, String standardElementId, String referenceCode, String securityLevel) {}

    public record BusinessObject(
        String id,
        String code,
        String name,
        String description,
        ObjectKind objectKind,
        String processId,
        List<String> businessKey,
        Grain grain,
        List<SourceRef> sourceRefs,
        String status,
        ImplementationMode implementationMode
    ) {}

    public record ModelSpec(
        String id,
        String objectId,
        String processId,
        Layer layer,
        ModelType modelType,
        ImplementationMode implementationMode,
        String name,
        Grain grain,
        List<StandardBinding> standardBindings,
        List<SourceRef> sourceRefs,
        List<String> dimensions,
        List<String> metrics,
        String materialization,
        int revision,
        List<String> dependsOn,
        String legacyRef
    ) {
        public ModelSpec(
            String id,
            String objectId,
            String processId,
            Layer layer,
            ModelType modelType,
            ImplementationMode implementationMode,
            String name,
            Grain grain,
            List<StandardBinding> standardBindings,
            List<SourceRef> sourceRefs,
            List<String> dimensions,
            List<String> metrics,
            String materialization,
            int revision
        ) {
            this(id, objectId, processId, layer, modelType, implementationMode, name, grain, standardBindings, sourceRefs, dimensions, metrics, materialization, revision, List.of(), null);
        }

        public ModelSpec(
            String id,
            String objectId,
            String processId,
            Layer layer,
            ModelType modelType,
            ImplementationMode implementationMode,
            String name,
            Grain grain,
            List<StandardBinding> standardBindings,
            List<SourceRef> sourceRefs,
            int revision
        ) {
            this(id, objectId, processId, layer, modelType, implementationMode, name, grain, standardBindings, sourceRefs, List.of(), List.of(), "table", revision, List.of(), null);
        }
    }

    public record LegacyModelRef(String modelId, String dbtUniqueId, String path, String status) {}

    public static List<String> validateModelSpec(ModelSpec model) {
        List<String> issues = new ArrayList<>();
        if (model.layer() == Layer.DWD && (model.grain() == null || model.grain().keys() == null || model.grain().keys().isEmpty())) {
            issues.add("DWD 模型必须声明粒度键");
        }
        if (
            model.layer() == Layer.DWD &&
            model.implementationMode() == ImplementationMode.DESIGNER_GENERATED &&
            (model.standardBindings() == null || model.standardBindings().isEmpty())
        ) {
            issues.add("DWD 设计器模型至少绑定一个数据标准");
        }
        return List.copyOf(issues);
    }

}
