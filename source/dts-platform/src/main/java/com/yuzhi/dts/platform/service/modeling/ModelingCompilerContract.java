package com.yuzhi.dts.platform.service.modeling;

import java.util.List;

/** Minimal immutable input consumed by the deterministic modeling compiler. */
public final class ModelingCompilerContract {

    private ModelingCompilerContract() {}

    public enum Layer {
        ODS,
        STG,
        DWD,
        DWS,
        ADS,
    }

    public enum ModelType {
        SOURCE,
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

    public record StandardBinding(String fieldName, String standardElementId) {}

    public record CompilerModel(
        String id,
        Layer layer,
        ModelType modelType,
        ImplementationMode implementationMode,
        String name,
        Grain grain,
        List<StandardBinding> standardBindings,
        List<SourceRef> sourceRefs,
        List<String> dimensions,
        List<String> metrics,
        int revision
    ) {}

}
