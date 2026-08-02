package com.yuzhi.dts.platform.service.modeling.representation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelField;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelRevisionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.CapabilityReason;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.DependencyProjection;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.DriftStatus;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.FieldProjection;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.LogicalModelProjection;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.Provenance;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.ProvenanceRef;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.RuntimeObservationProjection;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationEvidencePort.ArtifactEvidence;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationEvidencePort.ObservedField;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationEvidencePort.RepresentationEvidence;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationEvidencePort.RuntimeEvidence;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Applies DECLARED -> COMPILED -> OBSERVED provenance without allowing stale evidence to win. */
@Component
public class ModelRepresentationProvenanceProjector {

    private final ObjectMapper objectMapper;

    public ModelRepresentationProvenanceProjector(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ProjectionResult declared(ModelSpecView model) {
        ProvenanceRef declared = new ProvenanceRef(Provenance.DECLARED, model.checksum(), null);
        return new ProjectionResult(
            logical(model, declared, Map.of()),
            dependencies(model.dependsOn(), declared),
            unavailableRuntime(),
            DriftStatus.UNAVAILABLE,
            false,
            true,
            false,
            List.of()
        );
    }

    public ProjectionResult project(ModelSpecView model, RepresentationEvidence evidence) {
        RuntimeEvidence runtime = evidence.runtime();
        boolean runtimeExact = runtimeMatches(runtime, model, evidence);
        DriftStatus drift = runtime == null ? DriftStatus.UNAVAILABLE : runtimeExact ? DriftStatus.CURRENT : DriftStatus.STALE;

        Optional<ArtifactEvidence> schemaArtifact = evidence
            .artifacts()
            .stream()
            .filter(this::isSchemaEvidence)
            .filter(artifact -> "COMPILED".equalsIgnoreCase(artifact.status()) || "READY".equalsIgnoreCase(artifact.status()))
            .findFirst();
        SchemaProjection schema = schemaArtifact
            .map(artifact -> readCompiledFields(artifact, evidence.implementation().dbtUniqueId()))
            .orElseGet(() -> new SchemaProjection(Map.of(), true));
        Map<String, String> compiledFields = schema.fields();
        Map<String, ObservedField> observedFields = runtimeExact ? observedFields(runtime) : Map.of();

        boolean compiledCoversDeclared = !compiledFields.isEmpty() && model.fields().stream().allMatch(field -> compiledFields.containsKey(field.name()));
        boolean observedCoversDeclared = runtimeExact && model.fields().stream().allMatch(field -> observedFields.containsKey(field.name()));
        boolean fieldsTrusted = compiledCoversDeclared || observedCoversDeclared;

        Map<String, ProvenanceRef> fieldProvenance = new HashMap<>();
        for (ModelField field : model.fields()) {
            if (observedFields.containsKey(field.name())) {
                fieldProvenance.put(
                    field.name(),
                    new ProvenanceRef(Provenance.OBSERVED, runtime.metadataChecksum(), runtime.observedAt())
                );
            } else if (compiledFields.containsKey(field.name()) && schemaArtifact.isPresent()) {
                fieldProvenance.put(
                    field.name(),
                    new ProvenanceRef(Provenance.COMPILED, schemaArtifact.get().artifactChecksum(), null)
                );
            }
        }

        ProvenanceRef modelProvenance;
        if (observedCoversDeclared) {
            modelProvenance = new ProvenanceRef(Provenance.OBSERVED, runtime.metadataChecksum(), runtime.observedAt());
        } else if (compiledCoversDeclared && schemaArtifact.isPresent()) {
            modelProvenance = new ProvenanceRef(Provenance.COMPILED, schemaArtifact.get().artifactChecksum(), null);
        } else {
            modelProvenance = new ProvenanceRef(Provenance.DECLARED, model.checksum(), null);
        }

        ProvenanceRef dependencyProvenance = evidence.implementation() == null
            ? new ProvenanceRef(Provenance.DECLARED, model.checksum(), null)
            : new ProvenanceRef(Provenance.COMPILED, evidence.implementation().implementationChecksum(), null);
        boolean dynamicDependencies = evidence
            .projectionIssues()
            .contains(CapabilityReason.MODEL_REPRESENTATION_DYNAMIC_DEPENDENCY);
        boolean dependenciesTrusted =
            !dynamicDependencies &&
            !evidence.projectionIssues().contains(CapabilityReason.MODEL_REPRESENTATION_DEPENDENCIES_UNTRUSTED);
        List<CapabilityReason> reasons = schema.valid()
            ? evidence.projectionIssues()
            : mergeReasons(evidence.projectionIssues(), CapabilityReason.MODEL_REPRESENTATION_SCHEMA_INVALID);

        return new ProjectionResult(
            logical(model, modelProvenance, fieldProvenance),
            dependencies(model.dependsOn(), dependencyProvenance),
            runtimeProjection(runtime, drift),
            drift,
            fieldsTrusted,
            dependenciesTrusted,
            dynamicDependencies,
            reasons
        );
    }

    private LogicalModelProjection logical(
        ModelSpecView model,
        ProvenanceRef modelProvenance,
        Map<String, ProvenanceRef> fieldProvenance
    ) {
        List<FieldProjection> fields = model.fields().stream()
            .map(field ->
                new FieldProjection(
                    field.name(),
                    field.displayName(),
                    field.dataType(),
                    field.nullable(),
                    field.role(),
                    field.securityLevel(),
                    field.dimensionAttributeCode(),
                    fieldProvenance.getOrDefault(
                        field.name(),
                        new ProvenanceRef(Provenance.DECLARED, model.checksum(), null)
                    )
                )
            )
            .toList();
        return new LogicalModelProjection(
            model.id(),
            model.planId(),
            model.domainId(),
            model.modelType(),
            model.layer(),
            model.name(),
            model.description(),
            model.materialization(),
            model.status(),
            fields,
            modelProvenance
        );
    }

    private static List<DependencyProjection> dependencies(List<ModelRevisionRef> refs, ProvenanceRef provenance) {
        if (refs == null) return List.of();
        return refs.stream().map(ref -> new DependencyProjection(ref.modelSpecId(), ref.revision(), provenance)).toList();
    }

    private RuntimeObservationProjection runtimeProjection(RuntimeEvidence runtime, DriftStatus drift) {
        if (runtime == null) return unavailableRuntime();
        return new RuntimeObservationProjection(
            runtime.evidenceId(),
            drift,
            runtime.verified(),
            runtime.relationExists(),
            runtime.metadataChecksum(),
            runtime.observedAt()
        );
    }

    private static RuntimeObservationProjection unavailableRuntime() {
        return new RuntimeObservationProjection(null, DriftStatus.UNAVAILABLE, false, false, null, null);
    }

    private boolean isSchemaEvidence(ArtifactEvidence artifact) {
        if (artifact == null || artifact.artifactType() == null || artifact.artifactContent() == null) return false;
        return (
            "SCHEMA".equalsIgnoreCase(artifact.artifactType()) ||
            "CATALOG".equalsIgnoreCase(artifact.artifactType()) ||
            "MANIFEST".equalsIgnoreCase(artifact.artifactType())
        );
    }

    private SchemaProjection readCompiledFields(ArtifactEvidence artifact, String dbtUniqueId) {
        try {
            JsonNode root = objectMapper.readTree(artifact.artifactContent());
            JsonNode columns;
            if ("SCHEMA".equalsIgnoreCase(artifact.artifactType())) {
                columns = root == null ? null : root.get("columns");
            } else {
                JsonNode nodes = root == null ? null : root.get("nodes");
                JsonNode modelNode = nodes == null || dbtUniqueId == null ? null : nodes.get(dbtUniqueId);
                columns = modelNode == null ? null : modelNode.get("columns");
            }
            Map<String, String> fields = readColumns(columns);
            return new SchemaProjection(fields, columns != null && (columns.isArray() || columns.isObject()));
        } catch (Exception ignored) {
            return new SchemaProjection(Map.of(), false);
        }
    }

    private static Map<String, String> readColumns(JsonNode columns) {
        if (columns == null || (!columns.isArray() && !columns.isObject())) return Map.of();
        Map<String, String> fields = new LinkedHashMap<>();
        if (columns.isArray()) {
            columns.forEach(column -> addColumn(column, null, fields));
        } else {
            columns.fields().forEachRemaining(entry -> addColumn(entry.getValue(), entry.getKey(), fields));
        }
        return Map.copyOf(fields);
    }

    private static void addColumn(JsonNode column, String fallbackName, Map<String, String> fields) {
        if (column == null || !column.isObject()) return;
        JsonNode nameNode = column.get("name");
        String name = nameNode != null && nameNode.isTextual() ? nameNode.asText() : fallbackName;
        if (name == null || name.isBlank()) return;
        JsonNode dataType = column.has("dataType") ? column.get("dataType") : column.get("type");
        fields.putIfAbsent(name, dataType != null && dataType.isTextual() ? dataType.asText() : "");
    }

    private static Map<String, ObservedField> observedFields(RuntimeEvidence runtime) {
        if (runtime == null || runtime.fields() == null) return Map.of();
        Map<String, ObservedField> result = new LinkedHashMap<>();
        runtime.fields().stream().filter(Objects::nonNull).filter(field -> field.name() != null).forEach(field -> result.put(field.name(), field));
        return Map.copyOf(result);
    }

    private static boolean runtimeMatches(RuntimeEvidence runtime, ModelSpecView model, RepresentationEvidence evidence) {
        if (runtime == null || evidence.implementation() == null) return false;
        return (
            runtime.verified() &&
            runtime.relationExists() &&
            Objects.equals(runtime.modelSpecId(), model.id()) &&
            runtime.modelRevision() == model.revision() &&
            Objects.equals(runtime.modelChecksum(), model.checksum()) &&
            runtime.implementationRevision() == evidence.implementation().implementationRevision() &&
            Objects.equals(runtime.implementationChecksum(), evidence.implementation().implementationChecksum())
        );
    }

    private static List<CapabilityReason> mergeReasons(List<CapabilityReason> reasons, CapabilityReason addition) {
        java.util.LinkedHashSet<CapabilityReason> merged = new java.util.LinkedHashSet<>(reasons);
        merged.add(addition);
        return List.copyOf(merged);
    }

    private record SchemaProjection(Map<String, String> fields, boolean valid) {}

    public record ProjectionResult(
        LogicalModelProjection logicalModel,
        List<DependencyProjection> dependencies,
        RuntimeObservationProjection runtimeObservation,
        DriftStatus driftStatus,
        boolean fieldsTrusted,
        boolean dependenciesTrusted,
        boolean dynamicDependencies,
        List<CapabilityReason> reasons
    ) {
        public ProjectionResult {
            dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
        }
    }
}
