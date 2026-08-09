package com.yuzhi.dts.platform.service.modeling.imports.apply;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.FieldMapping;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.GeneratedInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.InputMode;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.PhysicalAssetInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.SaveImplementationCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.UpstreamModelInput;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.CreateModelSpecCommand;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.UpdateModelSpecCommand;
import com.yuzhi.dts.platform.service.modeling.ModelingDbtArtifactImportService.ArtifactType;
import com.yuzhi.dts.platform.service.modeling.ModelingDbtArtifactImportService.ImportedArtifact;
import com.yuzhi.dts.platform.service.modeling.ModelingDbtArtifactImportService.NodeKind;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyPlanContract.Candidate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Decodes only the server-persisted, checksum-protected preview payload used by apply. */
@Component
public class ModelSpecImportApplyCommandCodec {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public ModelSpecImportApplyCommandCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public DecodedCandidate decode(Candidate candidate, String applyPayloadJson) {
        return decode(candidate, applyPayloadJson, Set.copyOf(technicalPathNodeIds(candidate)));
    }

    public DecodedCandidate decode(
        Candidate candidate,
        String applyPayloadJson,
        Set<String> ownedTechnicalNodeIds
    ) {
        JsonNode payload = tree(applyPayloadJson, "Stored model import apply payload is invalid");
        CreateModelSpecCommand model = value(candidate.modelSpecJson(), CreateModelSpecCommand.class);
        SaveImplementationCommand implementation = implementation(candidate.implementationJson());
        String projectKey = requiredText(payload.path("dbt").path("projectName").asText(null), "projectKey");
        List<ImportedArtifact> artifacts = implementation.ownership() == ImplementationMode.DBT_MANAGED
            ? artifacts(candidate, payload, ownedTechnicalNodeIds)
            : List.of();
        return new DecodedCandidate(model, update(model), implementation, projectKey, artifacts);
    }

    public List<String> technicalPathNodeIds(Candidate candidate) {
        List<String> result = new ArrayList<>();
        JsonNode dependency = tree(candidate.dependencyPinsJson(), "Stored model import dependency payload is invalid");
        for (JsonNode uniqueId : dependency.path("technicalPathNodes")) {
            String value = uniqueId.asText(null);
            if (value != null && !value.isBlank()) {
                result.add(value);
            }
        }
        return result.stream().distinct().sorted().toList();
    }

    private SaveImplementationCommand implementation(String json) {
        JsonNode wrapper = tree(json, "Stored model import implementation payload is invalid");
        JsonNode command = wrapper.has("command") ? wrapper.path("command") : wrapper;
        InputMode mode = InputMode.valueOf(command.path("inputMode").asText());
        List<ImplementationInput> inputs = new ArrayList<>();
        for (JsonNode input : command.path("inputs")) {
            inputs.add(
                switch (mode) {
                    case PHYSICAL_ASSET -> new PhysicalAssetInput(
                        UUID.fromString(input.path("sourceBindingId").asText()),
                        input.path("resolvedVersion").asText()
                    );
                    case UPSTREAM_MODEL -> new UpstreamModelInput(
                        UUID.fromString(input.path("modelSpecId").asText()),
                        input.path("revision").asInt(),
                        input.path("checksum").asText(),
                        input.path("implementationRevision").asInt(),
                        input.path("implementationChecksum").asText(),
                        input.path("dbtUniqueId").asText()
                    );
                    case GENERATED -> new GeneratedInput(
                        input.path("generatorType").asText(),
                        objectMapper.convertValue(input.path("config"), MAP_TYPE)
                    );
                }
            );
        }
        List<FieldMapping> mappings = new ArrayList<>();
        for (JsonNode mapping : command.path("fieldMappings")) {
            mappings.add(new FieldMapping(mapping.path("sourceField").asText(), mapping.path("targetField").asText()));
        }
        return new SaveImplementationCommand(
            mode,
            inputs,
            mappings,
            objectMapper.convertValue(command.path("settings"), MAP_TYPE),
            ImplementationMode.valueOf(command.path("ownership").asText()),
            command.path("materialization").asText(),
            command.path("idempotencyKey").asText()
        );
    }

    private List<ImportedArtifact> artifacts(
        Candidate candidate,
        JsonNode payload,
        Set<String> ownedTechnicalNodeIds
    ) {
        LinkedHashMap<String, ImportedArtifact> result = new LinkedHashMap<>();
        JsonNode model = find(payload.path("models"), candidate.dbtUniqueId());
        JsonNode projection = tree(candidate.artifactJson(), "Stored model import artifact payload is invalid");
        addSql(
            result,
            candidate.dbtUniqueId(),
            NodeKind.MODEL,
            text(projection, "resourcePath", candidate.dbtUniqueId()),
            text(projection, "effectiveSql", null),
            text(projection, "effectiveSqlChecksum", null),
            materialization(model, "table")
        );
        addStructured(result, candidate.dbtUniqueId(), NodeKind.MODEL, model);

        JsonNode dependency = tree(candidate.dependencyPinsJson(), "Stored model import dependency payload is invalid");
        for (JsonNode uniqueId : dependency.path("technicalPathNodes")) {
            if (!ownedTechnicalNodeIds.contains(uniqueId.asText())) {
                continue;
            }
            JsonNode technical = find(payload.path("technicalNodes"), uniqueId.asText());
            if (technical == null) {
                continue;
            }
            String materialization = materialization(technical, "view");
            NodeKind kind = "ephemeral".equalsIgnoreCase(materialization) ? NodeKind.EPHEMERAL : NodeKind.STG;
            JsonNode sql = technical.path("sql");
            addSql(
                result,
                uniqueId.asText(),
                kind,
                text(technical, "resourcePath", uniqueId.asText()),
                text(sql, "effectiveSql", null),
                text(sql, "effectiveSqlChecksum", null),
                materialization
            );
            addStructured(result, uniqueId.asText(), kind, technical);
        }
        return result
            .values()
            .stream()
            .sorted(Comparator.comparing(ImportedArtifact::dbtUniqueId).thenComparing(item -> item.artifactType().name()))
            .toList();
    }

    private void addStructured(
        Map<String, ImportedArtifact> target,
        String uniqueId,
        NodeKind kind,
        JsonNode node
    ) {
        if (node == null) {
            return;
        }
        String path = text(node, "resourcePath", uniqueId);
        String materialization = materialization(node, kind == NodeKind.EPHEMERAL ? "ephemeral" : "view");
        ObjectNode schema = objectMapper.createObjectNode();
        schema.set("columns", node.path("columns").isMissingNode() ? objectMapper.createArrayNode() : node.path("columns"));
        schema.set("tests", node.path("tests").isMissingNode() ? objectMapper.createArrayNode() : node.path("tests"));
        add(target, uniqueId, kind, ArtifactType.SCHEMA, path + "#schema", canonical(schema), text(node, "schemaChecksum", null), materialization);
        add(
            target,
            uniqueId,
            kind,
            ArtifactType.CONFIG,
            path + "#config",
            canonical(node.path("executionSettings")),
            text(node, "configChecksum", null),
            materialization
        );
        add(
            target,
            uniqueId,
            kind,
            ArtifactType.DEPENDENCY,
            path + "#dependency",
            canonical(node.path("dependencies")),
            text(node, "dependencyChecksum", null),
            materialization
        );
    }

    private void addSql(
        Map<String, ImportedArtifact> target,
        String uniqueId,
        NodeKind kind,
        String path,
        String content,
        String checksum,
        String materialization
    ) {
        add(target, uniqueId, kind, ArtifactType.SQL, path, content, checksum, materialization);
    }

    private void add(
        Map<String, ImportedArtifact> target,
        String uniqueId,
        NodeKind kind,
        ArtifactType type,
        String path,
        String content,
        String checksum,
        String materialization
    ) {
        if (content == null || content.isBlank() || checksum == null || checksum.isBlank()) {
            return;
        }
        ImportedArtifact artifact = new ImportedArtifact(uniqueId, kind, type, path, checksum, content, materialization);
        target.put(uniqueId + "\u0000" + type.name(), artifact);
    }

    private UpdateModelSpecCommand update(CreateModelSpecCommand command) {
        return new UpdateModelSpecCommand(
            command.planId(),
            command.domainId(),
            command.modelType(),
            command.layer(),
            command.name(),
            command.description(),
            command.implementationMode(),
            command.materialization(),
            command.businessActivityRef(),
            command.consumptionScenario(),
            command.grain(),
            command.factShape(),
            command.timeSemantics(),
            command.fields(),
            command.sourceRefs(),
            command.dependsOn(),
            command.dimensionRefs(),
            command.metricRefs(),
            command.standardBindings(),
            command.generationStrategy(),
            command.dimensionProfile(),
            command.dataMartId(),
            command.variantCode(),
            command.implementationPolicy(),
            command.warehouseLayerCode(),
            command.businessProcessId(),
            command.subjectDomainId()
        );
    }

    private JsonNode find(JsonNode nodes, String uniqueId) {
        if (nodes instanceof ArrayNode array) {
            for (JsonNode node : array) {
                if (uniqueId.equals(node.path("dbtUniqueId").asText())) {
                    return node;
                }
            }
        }
        return null;
    }

    private String materialization(JsonNode node, String fallback) {
        if (node == null) {
            return fallback;
        }
        String direct = text(node, "materialization", null);
        String configured = text(node.path("executionSettings"), "materialized", null);
        String value = direct == null ? configured : direct;
        return value == null || value.isBlank() ? fallback : value.toLowerCase(Locale.ROOT);
    }

    private String canonical(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(canonicalize(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Stored model import artifact cannot be serialized", exception);
        }
    }

    private JsonNode canonicalize(JsonNode source) {
        if (source == null || source.isMissingNode() || source.isNull()) {
            return objectMapper.nullNode();
        }
        if (source.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            TreeMap<String, JsonNode> fields = new TreeMap<>();
            source.fields().forEachRemaining(entry -> fields.put(entry.getKey(), entry.getValue()));
            fields.forEach((key, value) -> result.set(key, canonicalize(value)));
            return result;
        }
        if (source.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            source.forEach(value -> result.add(canonicalize(value)));
            return result;
        }
        return source.deepCopy();
    }

    private JsonNode tree(String json, String message) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(message, exception);
        }
    }

    private <T> T value(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Stored model import command is invalid", exception);
        }
    }

    private static String text(JsonNode node, String field, String fallback) {
        if (node == null || !node.hasNonNull(field)) {
            return fallback;
        }
        String value = node.path(field).asText();
        return value.isBlank() ? fallback : value;
    }

    private static String requiredText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    public record DecodedCandidate(
        CreateModelSpecCommand createCommand,
        UpdateModelSpecCommand updateCommand,
        SaveImplementationCommand implementationCommand,
        String projectKey,
        List<ImportedArtifact> artifacts
    ) {
        public DecodedCandidate {
            artifacts = List.copyOf(artifacts);
        }
    }
}
