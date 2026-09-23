package com.yuzhi.dts.platform.service.modeling.authoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.UpdateModelSpecCommand;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Creates the one versioned logical plus optional structured-implementation authoring snapshot. */
@Component
public class ModelAuthoringSnapshotFactory {

    private final ObjectMapper objectMapper;

    public ModelAuthoringSnapshotFactory(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is required");
    }

    public JsonNode create(ModelSpecView model, ImplementationView implementation, String idempotencyKey) {
        UpdateModelSpecCommand command = new UpdateModelSpecCommand(
            model.planId(), model.domainId(), model.modelType(), model.layer(), model.name(), model.description(),
            model.implementationMode(), model.materialization(), model.businessActivityRef(), model.consumptionScenario(),
            model.grain(), model.factShape(), model.timeSemantics(), model.fields(), model.sourceRefs(), model.dependsOn(),
            model.dimensionRefs(), model.metricRefs(), model.standardBindings(), model.generationStrategy(),
            model.dimensionProfile(), model.dataMartId(), model.variantCode(), null, model.warehouseLayerCode(),
            model.businessProcessId(), model.subjectDomainId()
        );
        var snapshot = objectMapper.createObjectNode();
        snapshot.put("schemaVersion", 1);
        snapshot.set("modelSpec", objectMapper.valueToTree(command));
        JsonNode visualImplementation = visualImplementation(implementation, idempotencyKey);
        if (visualImplementation != null) snapshot.set("visualImplementation", visualImplementation);
        if (implementation != null && implementation.ownership() == ImplementationMode.DBT_MANAGED && implementation.inputs() != null) {
            for (JsonNode input : objectMapper.<JsonNode>valueToTree(implementation.inputs())) {
                JsonNode config = input.path("config");
                if (config.path("codeAuthoritative").asBoolean(false)) {
                    snapshot.remove("visualImplementation");
                    snapshot.put("codeAuthoritative", true);
                    if (config.path("visualReference").isObject()) snapshot.set("visualReference", config.path("visualReference").deepCopy());
                    break;
                }
            }
        }
        return snapshot;
    }

    private JsonNode visualImplementation(ImplementationView implementation, String idempotencyKey) {
        if (implementation == null) return null;
        if (implementation.ownership() == ImplementationMode.DBT_MANAGED && implementation.inputs() != null) {
            JsonNode inputs = objectMapper.valueToTree(implementation.inputs());
            for (JsonNode input : inputs) {
                JsonNode retained = input.path("config").path("visualImplementation");
                if (retained.isObject()) return retained.deepCopy();
            }
            return null;
        }
        var visual = objectMapper.createObjectNode();
        visual.put("projectKey", implementation.projectKey());
        visual.put("dbtUniqueId", implementation.dbtUniqueId());
        visual.put("inputMode", implementation.inputMode().name());
        visual.set("inputs", objectMapper.valueToTree(implementation.inputs()));
        visual.set("fieldMappings", objectMapper.valueToTree(implementation.fieldMappings()));
        visual.set("settings", objectMapper.valueToTree(implementation.settings()));
        visual.put("ownership", implementation.ownership().name());
        visual.put("materialization", implementation.materialization());
        visual.put("idempotencyKey", idempotencyKey);
        return visual;
    }
}
