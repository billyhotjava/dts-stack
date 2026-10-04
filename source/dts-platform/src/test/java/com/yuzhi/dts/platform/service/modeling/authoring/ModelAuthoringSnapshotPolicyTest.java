package com.yuzhi.dts.platform.service.modeling.authoring;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelAuthoringSnapshotPolicyTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void codeEditRetainsOriginalVisualSnapshotAndLogicalChanges() throws Exception {
        var before = json.readTree("""
            {"schemaVersion":1,"modelSpec":{"name":"旧名称"},
             "visualImplementation":{"settings":{"targetPhysicalName":"dwd_risk","loadStrategy":"FULL"},"fieldMappings":[{"sourceField":"id","targetField":"risk_id"}]}}
            """);
        var requested = json.readTree("""
            {"schemaVersion":1,"modelSpec":{"name":"新名称","description":"保留说明"}}
            """);
        var saved = ModelAuthoringSnapshotPolicy.forSave(before, requested, false, true);
        assertThat(saved.path("modelSpec").path("name").asText()).isEqualTo("新名称");
        assertThat(saved.path("visualReference")).isEqualTo(before.path("visualImplementation"));
        assertThat(saved.has("visualImplementation")).isFalse();
        assertThat(saved.path("codeAuthoritative").asBoolean()).isTrue();
        assertThat(before.has("visualImplementation")).isTrue();
        var repeated = ModelAuthoringSnapshotPolicy.forSave(saved, requested, false, false);
        assertThat(repeated).isEqualTo(saved);
        assertThatThrownBy(() -> ModelAuthoringSnapshotPolicy.forSave(saved, before, true, false))
            .isInstanceOf(ModelAuthoringException.class)
            .extracting(error -> ((ModelAuthoringException) error).code())
            .isEqualTo("MODEL_AUTHORING_VISUAL_REFERENCE_READ_ONLY");
    }

    @Test
    void unchangedCodeViewDoesNotInvalidateVisualEditing() throws Exception {
        var snapshot = json.readTree("{\"schemaVersion\":1,\"modelSpec\":{},\"visualImplementation\":{\"settings\":{}}}");
        assertThat(ModelAuthoringSnapshotPolicy.forSave(snapshot, snapshot, false, false)).isEqualTo(snapshot);
        assertThat(ModelAuthoringSnapshotPolicy.forSave(snapshot, snapshot, true, false)).isEqualTo(snapshot);
    }

    @Test
    void codeOnlyModelsStayCodeOnlyWithoutInventingAReference() throws Exception {
        var snapshot = json.readTree("{\"schemaVersion\":1,\"modelSpec\":{}}");
        var saved = ModelAuthoringSnapshotPolicy.forSave(snapshot, snapshot, false, true);
        assertThat(saved.has("visualReference")).isFalse();
        assertThat(saved.has("visualImplementation")).isFalse();
        assertThat(saved.path("codeAuthoritative").asBoolean()).isTrue();
        assertThat(ModelAuthoringSnapshotPolicy.filesChanged(null, List.of())).isFalse();
    }
}
