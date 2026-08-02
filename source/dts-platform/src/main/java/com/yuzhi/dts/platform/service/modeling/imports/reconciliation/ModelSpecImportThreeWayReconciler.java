package com.yuzhi.dts.platform.service.modeling.imports.reconciliation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.DriftAction;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.DriftDecision;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.DriftInput;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.RenameMapping;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Pure three-way decision engine; business metadata is retained independently of technical drift. */
@Component
public class ModelSpecImportThreeWayReconciler {

    private static final Set<String> PRESERVED_BUSINESS_FIELDS = Set.of(
        "planId",
        "domainId",
        "modelType",
        "layer",
        "name",
        "businessName",
        "description",
        "businessDefinition",
        "implementationMode",
        "businessActivityRef",
        "consumptionScenario",
        "grain",
        "factShape",
        "timeSemantics",
        "dimensionRefs",
        "metricRefs",
        "standardBindings",
        "generationStrategy",
        "dimensionProfile",
        "dimensionDefinitionRef",
        "dataMartId",
        "variantCode",
        "implementationPolicy",
        "governanceBindings"
    );
    private static final Set<String> PRESERVED_FIELD_BUSINESS_FIELDS = Set.of(
        "displayName",
        "role",
        "securityLevel",
        "dimensionAttributeCode",
        "redundant",
        "redundancySourceRef"
    );

    private final ObjectMapper objectMapper;

    public ModelSpecImportThreeWayReconciler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public DriftDecision evaluate(DriftInput input) {
        Objects.requireNonNull(input, "input is required");
        JsonNode proposed = preserveBusinessSemantics(input.currentModelSpec(), input.incomingModelSpec());
        if (input.base() == null) {
            return new DriftDecision(DriftAction.CONFLICT, "BASE_CHECKPOINT_MISSING", false, proposed);
        }
        boolean mappingChanged =
            input.currentMapping().modelSpecRevision() != input.base().mappedModelSpecRevision() ||
            !Objects.equals(input.currentMapping().modelSpecEtag(), input.base().mappedModelSpecEtag());
        if (mappingChanged && !input.mappingRevalidated()) {
            return new DriftDecision(DriftAction.BLOCKED_REMAP, "MAPPING_STALE", true, proposed);
        }

        boolean externalChanged = !Objects.equals(
            input.incomingExternalChecksum(),
            input.base().acceptedExternalChecksum()
        );
        boolean internalChanged = !Objects.equals(
            input.currentTechnical().implementationChecksum(),
            input.base().acceptedImplementationChecksum()
        );
        if (!externalChanged && !internalChanged) {
            return new DriftDecision(DriftAction.SKIP, "NO_CHANGE", mappingChanged, proposed);
        }
        if (!externalChanged) {
            return new DriftDecision(DriftAction.SKIP, "CURRENT_ONLY_CHANGED", mappingChanged, proposed);
        }
        if (!internalChanged) {
            return new DriftDecision(DriftAction.UPDATE, "INCOMING_ONLY_CHANGED", mappingChanged, proposed);
        }
        if (Objects.equals(input.currentTechnical().implementationChecksum(), input.incomingExternalChecksum())) {
            return new DriftDecision(DriftAction.SKIP, "CONVERGED_CHANGE", mappingChanged, proposed);
        }
        return new DriftDecision(DriftAction.CONFLICT, "BOTH_CHANGED_DIVERGED", mappingChanged, proposed);
    }

    /** Renames are accepted only as an explicit one-to-one identity decision. */
    public Map<String, String> validateRenameMappings(List<RenameMapping> mappings) {
        if (mappings == null || mappings.isEmpty()) return Map.of();
        if (mappings.size() > ModelSpecImportReconciliationContract.MAX_ITEMS) {
            throw new IllegalArgumentException("Rename mapping exceeds 200 items");
        }
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        java.util.HashSet<String> targets = new java.util.HashSet<>();
        for (RenameMapping mapping : mappings) {
            Objects.requireNonNull(mapping, "Rename mapping cannot be null");
            if (result.putIfAbsent(mapping.oldUniqueId(), mapping.newUniqueId()) != null || !targets.add(mapping.newUniqueId())) {
                throw new IllegalArgumentException("Rename mappings must be bijective");
            }
        }
        return Map.copyOf(result);
    }

    private JsonNode preserveBusinessSemantics(JsonNode current, JsonNode incoming) {
        ObjectNode merged = incoming != null && incoming.isObject()
            ? ((ObjectNode) incoming).deepCopy()
            : objectMapper.createObjectNode();
        if (current == null || !current.isObject()) return merged;
        ObjectNode business = (ObjectNode) current;
        for (String field : PRESERVED_BUSINESS_FIELDS) {
            JsonNode value = business.get(field);
            if (value != null) merged.set(field, value.deepCopy());
        }
        preserveFieldSemantics(business, merged);
        return merged;
    }

    private void preserveFieldSemantics(ObjectNode current, ObjectNode incoming) {
        JsonNode currentFields = current.get("fields");
        JsonNode incomingFields = incoming.get("fields");
        if (currentFields == null || !currentFields.isArray() || incomingFields == null || !incomingFields.isArray()) {
            return;
        }
        Map<String, JsonNode> currentByName = new LinkedHashMap<>();
        currentFields.forEach(field -> {
            String name = field.path("name").asText(null);
            if (name != null && !name.isBlank()) currentByName.putIfAbsent(name.trim(), field);
        });
        ArrayNode fields = objectMapper.createArrayNode();
        incomingFields.forEach(field -> {
            if (!field.isObject()) {
                fields.add(field.deepCopy());
                return;
            }
            ObjectNode retained = ((ObjectNode) field).deepCopy();
            JsonNode currentField = currentByName.get(field.path("name").asText(""));
            if (currentField != null && currentField.isObject()) {
                for (String businessField : PRESERVED_FIELD_BUSINESS_FIELDS) {
                    JsonNode value = currentField.get(businessField);
                    if (value != null) retained.set(businessField, value.deepCopy());
                }
            }
            fields.add(retained);
        });
        incoming.set("fields", fields);
    }
}
