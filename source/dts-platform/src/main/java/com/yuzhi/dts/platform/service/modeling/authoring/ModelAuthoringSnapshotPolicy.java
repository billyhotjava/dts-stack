package com.yuzhi.dts.platform.service.modeling.authoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.FileInput;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.SourceBundleView;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Keeps historical visual settings separate from the executable SQL draft. */
public final class ModelAuthoringSnapshotPolicy {
    private ModelAuthoringSnapshotPolicy() {}

    public static JsonNode forSave(JsonNode previous, JsonNode submitted, boolean visualView, boolean codeChanged) {
        boolean alreadyCode = previous != null && (previous.path("codeAuthoritative").asBoolean(false)
            || previous.path("visualReference").isObject());
        boolean code = alreadyCode || submitted.path("codeAuthoritative").asBoolean(false)
            || submitted.path("visualReference").isObject() || (!visualView && codeChanged);
        if (visualView && code) {
            throw new ModelAuthoringException("MODEL_AUTHORING_VISUAL_REFERENCE_READ_ONLY",
                "代码已修改，可视化配置仅供只读参考，请在代码模式继续编辑", ModelAuthoringException.Kind.CONFLICT);
        }
        ObjectNode result = submitted.deepCopy();
        if (!code) return result;
        JsonNode reference = previous == null ? null : previous.get("visualReference");
        if (reference == null || !reference.isObject()) reference = previous == null ? null : previous.get("visualImplementation");
        if (reference == null || !reference.isObject()) reference = submitted.get("visualReference");
        if (reference == null || !reference.isObject()) reference = submitted.get("visualImplementation");
        result.remove("visualImplementation");
        result.put("codeAuthoritative", true);
        if (reference != null && reference.isObject()) result.set("visualReference", reference.deepCopy());
        return result;
    }

    public static boolean filesChanged(SourceBundleView source, List<FileInput> submitted) {
        Map<String, String> before = new LinkedHashMap<>();
        if (source != null) source.files().forEach(file -> before.put(file.path(), file.content()));
        Map<String, String> after = new LinkedHashMap<>();
        if (submitted != null) submitted.forEach(file -> after.put(file.path(), file.content()));
        return !before.equals(after);
    }
}
