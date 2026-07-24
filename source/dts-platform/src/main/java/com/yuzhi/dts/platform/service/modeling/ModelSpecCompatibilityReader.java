package com.yuzhi.dts.platform.service.modeling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.repository.modeling.DimensionDefinitionRepository;
import com.yuzhi.dts.platform.repository.modeling.DimensionDefinitionRepository.LegacyDefinitionRef;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.ListFilter;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.StoredModelSpec;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Single read path for canonical v2 snapshots and immutable v1 compatibility views. */
@Service
@Transactional(readOnly = true)
public class ModelSpecCompatibilityReader {

    private final ModelSpecRepository repository;
    private final ModelSpecSnapshotCodec codec;
    private final ObjectMapper objectMapper;
    private final DimensionDefinitionRepository dimensionDefinitions;

    public ModelSpecCompatibilityReader(
        ModelSpecRepository repository,
        ModelSpecSnapshotCodec codec,
        ObjectMapper objectMapper
    ) {
        this(repository, codec, objectMapper, null);
    }

    @Autowired
    public ModelSpecCompatibilityReader(
        ModelSpecRepository repository,
        ModelSpecSnapshotCodec codec,
        ObjectMapper objectMapper,
        DimensionDefinitionRepository dimensionDefinitions
    ) {
        this.repository = repository;
        this.codec = codec;
        this.objectMapper = objectMapper;
        this.dimensionDefinitions = dimensionDefinitions;
    }

    public ModelSpecView get(String tenantId, UUID modelSpecId) {
        return repository
            .findCurrent(tenantId, modelSpecId)
            .map(this::read)
            .orElseThrow(() -> notFound(modelSpecId));
    }

    public List<ModelSpecView> list(String tenantId, UUID planId, UUID domainId, ModelType modelType, Layer layer) {
        return repository.listCurrent(tenantId, new ListFilter(planId, domainId, modelType, layer)).stream().map(this::read).toList();
    }

    public ModelSpecView revision(String tenantId, ModelRevisionRef reference) {
        if (reference == null || reference.modelSpecId() == null || reference.revision() < 1) {
            throw new ModelSpecException(
                "MODEL_SPEC_REVISION_REF_INVALID",
                "ModelSpec revision reference is invalid",
                ModelSpecException.Kind.BAD_REQUEST
            );
        }
        return repository
            .findRevision(tenantId, reference.modelSpecId(), reference.revision())
            .map(this::read)
            .orElseThrow(() -> notFound(reference.modelSpecId()));
    }

    public ModelSpecView read(StoredModelSpec stored) {
        if (stored.contractVersion() == ModelSpecContract.CONTRACT_VERSION) {
            return readCanonical(stored);
        }
        if (stored.contractVersion() == ModelingVNextContract.CONTRACT_VERSION) {
            return readLegacy(stored);
        }
        throw new ModelSpecException(
            "MODEL_SPEC_CONTRACT_VERSION_UNSUPPORTED",
            "Stored ModelSpec contract version is unsupported",
            ModelSpecException.Kind.CONFLICT
        );
    }

    private ModelSpecView readCanonical(StoredModelSpec stored) {
        if (stored.currentSnapshot() == null || stored.currentSnapshot().isBlank()) {
            throw snapshotConflict("Canonical ModelSpec revision snapshot is missing");
        }
        ModelSpecView view = codec.readView(stored.currentSnapshot());
        String contentChecksum = codec.contentChecksum(view);
        if (
            view.contractVersion() != ModelSpecContract.CONTRACT_VERSION ||
            view.compatibilityMode() != CompatibilityMode.CANONICAL ||
            !Objects.equals(view.id(), stored.id()) ||
            !Objects.equals(view.planId(), stored.planId()) ||
            !Objects.equals(view.domainId(), stored.domainId()) ||
            view.status() != stored.status() ||
            view.revision() != stored.revision() ||
            !Objects.equals(view.checksum(), contentChecksum) ||
            !Objects.equals(view.checksum(), stored.revisionChecksum()) ||
            (stored.currentHead() &&
                (stored.currentChecksum() == null ||
                    !Objects.equals(stored.currentChecksum(), stored.revisionChecksum())))
        ) {
            throw snapshotConflict("Canonical ModelSpec revision snapshot does not match the ledger head");
        }
        DimensionDefinitionRef projected = legacyDimensionDefinitionRef(
            stored,
            view.modelType(),
            view.dimensionDefinitionRef()
        );
        return projected == view.dimensionDefinitionRef()
            ? view
            : withDimensionDefinitionRef(view, projected);
    }

    private ModelSpecView readLegacy(StoredModelSpec stored) {
        if (stored.legacySpecJson() == null || stored.legacySpecJson().isBlank()) {
            throw snapshotConflict("Legacy ModelSpec revision payload is missing");
        }
        ModelingVNextContract.ModelSpec legacy;
        try {
            legacy = objectMapper.readValue(stored.legacySpecJson(), ModelingVNextContract.ModelSpec.class);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw snapshotConflict("Legacy ModelSpec revision payload cannot be read");
        }
        UUID id = parseUuid(legacy.id(), stored.id());
        ModelType modelType = ModelType.valueOf(legacy.modelType().name());
        Layer layer = Layer.valueOf(legacy.layer().name());
        List<ModelField> fields = legacyFields(legacy);
        List<SourceRef> sources = legacySources(legacy.sourceRefs());
        DimensionDefinitionRef dimensionDefinitionRef = legacyDimensionDefinitionRef(stored, modelType, null);
        return new ModelSpecView(
            ModelingVNextContract.CONTRACT_VERSION,
            id,
            stored.planId(),
            stored.domainId(),
            modelType,
            layer,
            legacy.name(),
            null,
            ImplementationMode.valueOf(legacy.implementationMode().name()),
            legacy.materialization(),
            modelType == ModelType.FACT ? legacy.processId() : null,
            null,
            legacy.grain() == null ? null : new Grain(legacy.grain().statement(), legacy.grain().keys()),
            null,
            null,
            fields,
            sources,
            List.of(),
            List.of(),
            List.of(),
            legacyBindings(legacy.standardBindings()),
            null,
            null,
            dimensionDefinitionRef,
            stored.status(),
            stored.revision(),
            normalizedChecksum(stored.checksum(), stored.legacySpecJson()),
            stored.createdAt(),
            stored.updatedAt(),
            CompatibilityMode.LEGACY_READONLY,
            legacyRefs(legacy)
        );
    }

    private DimensionDefinitionRef legacyDimensionDefinitionRef(
        StoredModelSpec stored,
        ModelType modelType,
        DimensionDefinitionRef existing
    ) {
        if (existing != null) return existing;
        if (
            modelType != ModelType.DIMENSION ||
            dimensionDefinitions == null ||
            stored.domainId() == null
        ) {
            return null;
        }
        LegacyDefinitionRef reference = dimensionDefinitions
            .findLegacyDefinitionRef(stored.tenantId(), stored.id(), stored.domainId())
            .orElse(null);
        return reference == null
            ? null
            : new DimensionDefinitionRef(reference.dimensionDefinitionId(), reference.revision());
    }

    private static ModelSpecView withDimensionDefinitionRef(
        ModelSpecView view,
        DimensionDefinitionRef dimensionDefinitionRef
    ) {
        return new ModelSpecView(
            view.contractVersion(),
            view.id(),
            view.planId(),
            view.domainId(),
            view.modelType(),
            view.layer(),
            view.name(),
            view.description(),
            view.implementationMode(),
            view.materialization(),
            view.businessActivityRef(),
            view.consumptionScenario(),
            view.grain(),
            view.factShape(),
            view.timeSemantics(),
            view.fields(),
            view.sourceRefs(),
            view.dependsOn(),
            view.dimensionRefs(),
            view.metricRefs(),
            view.standardBindings(),
            view.generationStrategy(),
            view.dimensionProfile(),
            dimensionDefinitionRef,
            view.status(),
            view.revision(),
            view.checksum(),
            view.createdAt(),
            view.updatedAt(),
            view.compatibilityMode(),
            view.legacyRefs()
        );
    }

    private static List<ModelField> legacyFields(ModelingVNextContract.ModelSpec legacy) {
        List<ModelField> fields = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        List<String> grainKeys = legacy.grain() == null || legacy.grain().keys() == null
            ? List.of()
            : legacy.grain().keys();
        for (String dimension : safe(legacy.dimensions())) {
            if (dimension == null || dimension.isBlank() || !seen.add(dimension)) continue;
            FieldRole role = grainKeys.contains(dimension) ? FieldRole.KEY : FieldRole.ATTRIBUTE;
            fields.add(new ModelField(dimension, "legacy_unknown", true, null, role, null));
        }
        for (String metric : safe(legacy.metrics())) {
            if (metric == null || metric.isBlank() || !seen.add(metric)) continue;
            fields.add(new ModelField(metric, "legacy_unknown", true, null, FieldRole.MEASURE, null));
        }
        return List.copyOf(fields);
    }

    private static List<SourceRef> legacySources(List<ModelingVNextContract.SourceRef> legacySources) {
        List<SourceRef> result = new ArrayList<>();
        int sortOrder = 0;
        for (ModelingVNextContract.SourceRef source : safe(legacySources)) {
            if (source == null || source.ref() == null || source.layer() == null) continue;
            SourceKind kind;
            try {
                kind = SourceKind.valueOf(source.kind());
            } catch (IllegalArgumentException | NullPointerException ignored) {
                continue;
            }
            result.add(
                new SourceRef(
                    kind,
                    source.ref(),
                    Layer.valueOf(source.layer().name()),
                    SourceRole.PRIMARY,
                    null,
                    null,
                    null,
                    sortOrder++,
                    null,
                    null
                )
            );
        }
        return List.copyOf(result);
    }

    private static List<StandardBinding> legacyBindings(List<ModelingVNextContract.StandardBinding> bindings) {
        return safe(bindings)
            .stream()
            .filter(Objects::nonNull)
            .map(binding ->
                new StandardBinding(
                    binding.fieldName(),
                    nullableUuid(binding.standardElementId()),
                    null,
                    binding.referenceCode(),
                    null,
                    null,
                    null,
                    binding.securityLevel()
                )
            )
            .toList();
    }

    private static LegacyRefs legacyRefs(ModelingVNextContract.ModelSpec legacy) {
        List<LegacySourceRef> unresolvedSources = safe(legacy.sourceRefs())
            .stream()
            .filter(Objects::nonNull)
            .filter(source -> {
                try {
                    SourceKind.valueOf(source.kind());
                    return false;
                } catch (IllegalArgumentException | NullPointerException ignored) {
                    return true;
                }
            })
            .map(source ->
                new LegacySourceRef(
                    source.kind(),
                    source.ref(),
                    source.layer() == null ? null : source.layer().name()
                )
            )
            .toList();
        List<LegacyStandardRef> unresolvedStandards = safe(legacy.standardBindings())
            .stream()
            .filter(Objects::nonNull)
            .filter(binding -> binding.standardElementId() != null && nullableUuid(binding.standardElementId()) == null)
            .map(binding ->
                new LegacyStandardRef(
                    binding.fieldName(),
                    binding.standardElementId(),
                    binding.referenceCode(),
                    binding.securityLevel()
                )
            )
            .toList();
        String legacyModelRef = legacy.legacyRef();
        if (legacyModelRef == null || legacyModelRef.isBlank()) {
            legacyModelRef = legacy.objectId();
        }
        return new LegacyRefs(legacyModelRef, safe(legacy.dependsOn()), unresolvedSources, unresolvedStandards);
    }

    private static UUID nullableUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static UUID parseUuid(String value, UUID fallback) {
        UUID parsed = nullableUuid(value);
        return parsed == null ? fallback : parsed;
    }

    private static String normalizedChecksum(String checksum, String payload) {
        if (checksum != null && checksum.matches("[0-9a-f]{64}")) return checksum;
        try {
            return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static ModelSpecException notFound(UUID id) {
        return new ModelSpecException(
            "MODEL_SPEC_NOT_FOUND",
            "ModelSpec was not found",
            ModelSpecException.Kind.NOT_FOUND,
            id == null ? java.util.Map.of() : java.util.Map.of("modelSpecId", id)
        );
    }

    private static ModelSpecException snapshotConflict(String message) {
        return new ModelSpecException("MODEL_SPEC_SNAPSHOT_INVALID", message, ModelSpecException.Kind.CONFLICT);
    }
}
