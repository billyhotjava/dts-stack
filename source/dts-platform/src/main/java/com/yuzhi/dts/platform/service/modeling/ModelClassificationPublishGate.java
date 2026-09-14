package com.yuzhi.dts.platform.service.modeling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.security.SecurityLevelCatalog;
import com.yuzhi.dts.platform.repository.modeling.ModelLifecycleRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.SourceBindingState;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetKey;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationBoundary;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationBoundary.ClassificationFact;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationBoundary.SealRequest;
import com.yuzhi.dts.platform.service.catalog.CatalogSourceReferenceReadPort;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.Snapshot;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.GeneratedInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.PhysicalAssetInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.UpstreamModelInput;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelField;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.StandardBinding;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fail-closed classification gate for canonical ModelSpec and dbt publication.
 *
 * <p>The gate resolves only immutable, revision-pinned inputs. The output level is the maximum of
 * every resolved upstream level and every explicit field level. Missing or stale evidence blocks
 * publication; successful admission seals the output subject and can only raise it later.
 */
@Service
@Transactional(readOnly = true)
public class ModelClassificationPublishGate {

    private final ModelSpecApplicationService modelSpecs;
    private final ModelSpecRepository modelSpecRepository;
    private final ModelLifecycleRepository lifecycleRepository;
    private final CatalogSourceReferenceReadPort catalogSources;
    private final CatalogClassificationBoundary classifications;
    private final ObjectMapper objectMapper;
    private final ModelImplementationDependencyService dependencies;

    public ModelClassificationPublishGate(
        ModelSpecApplicationService modelSpecs,
        ModelSpecRepository modelSpecRepository,
        ModelLifecycleRepository lifecycleRepository,
        CatalogSourceReferenceReadPort catalogSources,
        CatalogClassificationBoundary classifications,
        ObjectMapper objectMapper
    ) {
        this(modelSpecs, modelSpecRepository, lifecycleRepository, catalogSources, classifications, objectMapper, null);
    }

    @Autowired
    public ModelClassificationPublishGate(
        ModelSpecApplicationService modelSpecs,
        ModelSpecRepository modelSpecRepository,
        ModelLifecycleRepository lifecycleRepository,
        CatalogSourceReferenceReadPort catalogSources,
        CatalogClassificationBoundary classifications,
        ObjectMapper objectMapper,
        ModelImplementationDependencyService dependencies
    ) {
        this.modelSpecs = modelSpecs;
        this.modelSpecRepository = modelSpecRepository;
        this.lifecycleRepository = lifecycleRepository;
        this.catalogSources = catalogSources;
        this.classifications = classifications;
        this.objectMapper = objectMapper;
        this.dependencies = dependencies;
    }

    public Decision evaluate(String tenantId, UUID modelSpecId, int revision, String checksum) {
        return evaluate(tenantId, modelSpecId, revision, checksum, Map.of());
    }

    private Decision evaluate(String tenantId, UUID modelSpecId, int revision, String checksum, Map<String, String> verifiedLevels) {
        ModelSpecView model = modelSpecs.get(tenantId, modelSpecId);
        List<Blocker> blockers = new ArrayList<>();
        if (model.revision() != revision || !Objects.equals(model.checksum(), checksum)) {
            blockers.add(
                new Blocker(
                    "CLASSIFICATION_MODEL_REVISION_STALE",
                    "ModelSpec revision or checksum changed after the release scope was pinned",
                    modelSpecId.toString()
                )
            );
            return Decision.blocked(modelSpecId, revision, outputKey(model, null), blockers);
        }

        ImplementationView implementation = lifecycleRepository.findImplementation(tenantId, modelSpecId).orElse(null);
        if (implementation == null || !current(model, implementation)) {
            blockers.add(
                new Blocker(
                    "CLASSIFICATION_IMPLEMENTATION_EVIDENCE_MISSING",
                    "Current implementation evidence is required before classification can be propagated",
                    modelSpecId.toString()
                )
            );
            return Decision.blocked(modelSpecId, revision, outputKey(model, implementation), blockers);
        }

        LinkedHashMap<String, String> upstreamLevels = new LinkedHashMap<>();
        for (ImplementationInput input : implementation.inputs()) {
            for (InputEvidence evidence : resolveInputEvidence(tenantId, model, implementation, input, blockers, verifiedLevels)) {
                upstreamLevels.put(evidence.subjectKey(), evidence.level());
            }
        }

        LinkedHashMap<String, String> fieldLevels = new LinkedHashMap<>();
        for (ModelField field : model.fields()) {
            if (field.securityLevel() == null || field.securityLevel().isBlank()) {
                continue;
            }
            try {
                fieldLevels.put(field.name(), SecurityLevelCatalog.requireDataLevel(field.securityLevel()).code());
            } catch (IllegalArgumentException invalid) {
                blockers.add(
                    new Blocker(
                        "CLASSIFICATION_FIELD_LEVEL_INVALID",
                        "Model field has an invalid classification",
                        field.name()
                    )
                );
            }
        }
        for (StandardBinding binding : model.standardBindings()) {
            if (binding.securityLevel() == null || binding.securityLevel().isBlank()) {
                continue;
            }
            try {
                String level = SecurityLevelCatalog.requireDataLevel(binding.securityLevel()).code();
                fieldLevels.merge(
                    binding.fieldName(),
                    level,
                    (currentLevel, candidateLevel) -> SecurityLevelCatalog.maxDataCode(currentLevel, candidateLevel)
                );
            } catch (IllegalArgumentException invalid) {
                blockers.add(
                    new Blocker(
                        "CLASSIFICATION_FIELD_LEVEL_INVALID",
                        "Model field standard binding has an invalid classification",
                        binding.fieldName()
                    )
                );
            }
        }

        List<String> candidates = new ArrayList<>(upstreamLevels.values());
        candidates.addAll(fieldLevels.values());
        String effectiveLevel = SecurityLevelCatalog.maxDataCode(candidates);
        if (effectiveLevel == null) {
            blockers.add(
                new Blocker(
                    "CLASSIFICATION_INPUT_REQUIRED",
                    "At least one sealed upstream or explicit field classification is required",
                    modelSpecId.toString()
                )
            );
        }
        String outputSubjectKey = outputKey(model, implementation);
        if (!blockers.isEmpty()) {
            return Decision.blocked(modelSpecId, revision, outputSubjectKey, blockers);
        }
        return new Decision(
            true,
            modelSpecId,
            revision,
            outputSubjectKey,
            effectiveLevel,
            Map.copyOf(upstreamLevels),
            Map.copyOf(fieldLevels),
            List.of(),
            null
        );
    }

    @Transactional
    public Decision admitAndSeal(String tenantId, UUID modelSpecId, int revision, String checksum, String triggerRef) {
        return admitAndSeal(tenantId, modelSpecId, revision, checksum, triggerRef, Map.of());
    }

    @Transactional
    public Decision admitAndSeal(String tenantId, UUID modelSpecId, int revision, String checksum, String triggerRef,
        Map<String, String> verifiedLevels) {
        Decision decision = evaluate(tenantId, modelSpecId, revision, checksum, verifiedLevels);
        if (!decision.ready()) {
            return decision;
        }
        List<String> candidates = new ArrayList<>(decision.upstreamLevels().values());
        candidates.addAll(decision.fieldLevels().values());
        String evidenceJson = evidenceJson(decision);
        ClassificationFact seal = classifications.sealOrRaise(
            new SealRequest(
                "ASSET",
                decision.outputSubjectKey(),
                "DBT_MODEL",
                null,
                null,
                null,
                candidates,
                "UPSTREAM_INHERITANCE",
                triggerRef,
                sha256(modelSpecId + ":" + revision + ":" + checksum + ":" + evidenceJson),
                evidenceJson
            )
        );
        for (Map.Entry<String, String> field : decision.fieldLevels().entrySet()) {
            classifications.sealOrRaise(
                new SealRequest(
                    "COLUMN",
                    decision.outputSubjectKey() + "/column:" + normalize(field.getKey()),
                    "DBT_MODEL",
                    field.getValue(),
                    null,
                    null,
                    List.of(),
                    "SOURCE_DECLARATION",
                    triggerRef,
                    sha256(modelSpecId + ":" + revision + ":" + field.getKey() + ":" + field.getValue()),
                    "{\"modelSpecId\":\"" + modelSpecId + "\",\"field\":\"" + escape(field.getKey()) + "\"}"
                )
            );
        }
        return decision.withSeal(seal);
    }

    private List<InputEvidence> resolveInputEvidence(
        String tenantId,
        ModelSpecView model,
        ImplementationView implementation,
        ImplementationInput input,
        List<Blocker> blockers,
        Map<String, String> verifiedLevels
    ) {
        if (!(input instanceof GeneratedInput generated)) {
            return resolveInput(tenantId, model, input, blockers, verifiedLevels).stream().toList();
        }
        Object snapshotValue = generated.config().get("dependencySnapshot");
        if (snapshotValue == null && "DBT".equals(generated.generatorType()) && dependencies != null) {
            try {
                // Canonical ZIP/manual dbt paths store pinned dependencies on ModelSpec,
                // not inside generator config. Reuse the same resolver as materialization.
                snapshotValue = dependencies.resolveCurrent(tenantId, model, implementation,
                    implementation.projectKey(), null).snapshot();
            } catch (ModelSpecException invalid) {
                blockers.add(new Blocker(invalid.code(), invalid.getMessage(), model.id().toString()));
                return List.of();
            }
        }
        if (snapshotValue == null) {
            if (hasDeclaredDependencies(model)) {
                blockers.add(
                    new Blocker(
                        "CLASSIFICATION_DEPENDENCY_SNAPSHOT_MISSING",
                        "Generated implementation is missing revision-pinned dependency evidence",
                        model.id().toString()
                    )
                );
            }
            return List.of();
        }
        try {
            Snapshot snapshot = objectMapper.convertValue(snapshotValue, Snapshot.class);
            if (
                snapshot == null ||
                !Objects.equals(model.id(), snapshot.modelSpecId()) ||
                model.revision() != snapshot.modelRevision() ||
                !Objects.equals(model.checksum(), snapshot.modelChecksum())
            ) {
                blockers.add(
                    new Blocker(
                        "CLASSIFICATION_DEPENDENCY_SNAPSHOT_STALE",
                        "Generated implementation dependency evidence does not match the current ModelSpec revision",
                        model.id().toString()
                    )
                );
                return List.of();
            }
            List<InputEvidence> resolved = new ArrayList<>();
            for (var source : snapshot.physicalSources()) {
                resolveInput(
                    tenantId,
                    model,
                    new PhysicalAssetInput(source.sourceBindingId(), source.resolvedVersion()),
                    blockers, verifiedLevels
                ).ifPresent(resolved::add);
            }
            for (var upstream : snapshot.modelInputs()) {
                resolveInput(
                    tenantId,
                    model,
                    new UpstreamModelInput(
                        upstream.modelSpecId(),
                        upstream.revision(),
                        upstream.checksum(),
                        upstream.implementationRevision(),
                        upstream.implementationChecksum(),
                        upstream.dbtUniqueId()
                    ),
                    blockers, verifiedLevels
                ).ifPresent(resolved::add);
            }
            return List.copyOf(resolved);
        } catch (IllegalArgumentException invalidSnapshot) {
            blockers.add(
                new Blocker(
                    "CLASSIFICATION_DEPENDENCY_SNAPSHOT_INVALID",
                    "Generated implementation dependency evidence cannot be read",
                    model.id().toString()
                )
            );
            return List.of();
        }
    }

    /** Only call with models whose pinned candidate outputs have all been physically verified. */
    public List<Decision> evaluateVerifiedScope(String tenantId, List<ModelSpecView> verifiedModels) {
        Map<UUID, Decision> results = new LinkedHashMap<>();
        Map<String, String> levels = new LinkedHashMap<>();
        List<ModelSpecView> remaining = new ArrayList<>(verifiedModels);
        while (!remaining.isEmpty()) {
            int before = remaining.size();
            var iterator = remaining.iterator();
            while (iterator.hasNext()) {
                ModelSpecView model = iterator.next();
                Decision decision = evaluate(tenantId, model.id(), model.revision(), model.checksum(), levels);
                results.put(model.id(), decision);
                if (decision.ready()) {
                    levels.put(decision.outputSubjectKey(), decision.effectiveLevel());
                    iterator.remove();
                }
            }
            if (remaining.size() == before) break;
        }
        return verifiedModels.stream().map(model -> results.get(model.id())).toList();
    }

    private static boolean hasDeclaredDependencies(ModelSpecView model) {
        return (
            model.sourceRefs() != null && !model.sourceRefs().isEmpty() ||
            model.dependsOn() != null && !model.dependsOn().isEmpty() ||
            model.dimensionRefs() != null && !model.dimensionRefs().isEmpty()
        );
    }

    private Optional<InputEvidence> resolveInput(
        String tenantId,
        ModelSpecView model,
        ImplementationInput input,
        List<Blocker> blockers,
        Map<String, String> verifiedLevels
    ) {
        if (input instanceof PhysicalAssetInput physical) {
            SourceBindingState binding = modelSpecRepository
                .findSourceBinding(tenantId, model.planId(), physical.sourceBindingId())
                .orElse(null);
            if (
                binding == null ||
                !"CONFIRMED".equals(binding.confirmationStatus()) ||
                !Objects.equals(binding.sourceVersion(), physical.resolvedVersion())
            ) {
                blockers.add(
                    new Blocker(
                        "CLASSIFICATION_SOURCE_BINDING_STALE",
                        "Physical source binding is missing, unconfirmed or stale",
                        physical.sourceBindingId().toString()
                    )
                );
                return Optional.empty();
            }
            String subjectKey = sourceSubjectKey(binding, blockers);
            if (subjectKey == null) {
                return Optional.empty();
            }
            return sealedEvidence(subjectKey, blockers, Map.of());
        }
        if (input instanceof UpstreamModelInput upstream) {
            String subjectKey = upstream.implementationPinned()
                ? CatalogAssetKey.dbtModel(upstream.dbtUniqueId(), null)
                : modelRevisionKey(upstream.modelSpecId(), upstream.revision());
            return sealedEvidence(subjectKey, blockers, verifiedLevels);
        }
        if (input instanceof GeneratedInput) {
            return Optional.empty();
        }
        blockers.add(
            new Blocker(
                "CLASSIFICATION_INPUT_UNSUPPORTED",
                "Implementation input type cannot provide classification evidence",
                input.getClass().getSimpleName()
            )
        );
        return Optional.empty();
    }

    private Optional<InputEvidence> sealedEvidence(String subjectKey, List<Blocker> blockers, Map<String, String> verifiedLevels) {
        ClassificationFact snapshot = classifications.resolve("ASSET", subjectKey).orElse(null);
        String verifiedLevel = verifiedLevels.get(subjectKey);
        if (verifiedLevel != null) {
            return Optional.of(new InputEvidence(subjectKey, SecurityLevelCatalog.maxDataCode(
                verifiedLevel, snapshot == null ? null : snapshot.effectiveLevel())));
        }
        if (snapshot == null) {
            blockers.add(
                new Blocker(
                    "PENDING_CLASSIFICATION",
                    "Sealed upstream classification is missing",
                    subjectKey
                )
            );
            return Optional.empty();
        }
        if (!snapshot.propagated()) {
            blockers.add(
                new Blocker(
                    "CLASSIFICATION_PROPAGATION_PENDING",
                    "Upstream classification propagation has not converged",
                    subjectKey
                )
            );
            return Optional.empty();
        }
        return Optional.of(new InputEvidence(subjectKey, snapshot.effectiveLevel()));
    }

    private String sourceSubjectKey(SourceBindingState binding, List<Blocker> blockers) {
        try {
            return switch (binding.sourceType()) {
                case "CATALOG_TABLE" -> catalogSources
                    .findDatasetAssetKeyByTableId(UUID.fromString(binding.sourceId()))
                    .orElseGet(() -> missingBindingAsset(binding, blockers));
                case "CONNECTION_TABLE" -> connectionDatasetAssetKey(binding)
                    .orElseGet(() -> missingBindingAsset(binding, blockers));
                case "EXCEL_FILE" -> "external-exchange-file:" + UUID.fromString(binding.sourceId());
                case "DBT_NODE" -> dbtSourceKey(binding);
                default -> {
                    blockers.add(
                        new Blocker(
                            "CLASSIFICATION_SOURCE_TYPE_UNSUPPORTED",
                            "Source type cannot provide classification evidence",
                            binding.sourceType()
                        )
                    );
                    yield null;
                }
            };
        } catch (Exception invalidLocator) {
            blockers.add(
                new Blocker(
                    "CLASSIFICATION_SOURCE_LOCATOR_INVALID",
                    "Source locator cannot be resolved for classification",
                    binding.id().toString()
                )
            );
            return null;
        }
    }

    private Optional<String> connectionDatasetAssetKey(SourceBindingState binding) throws Exception {
        JsonNode locator = objectMapper.readTree(binding.locatorJson());
        UUID connectionId = UUID.fromString(locator.path("connectionId").asText());
        String namespace = locator.path("namespace").asText();
        String objectName = locator.path("objectName").asText();
        return catalogSources.findDatasetAssetKey(
            connectionId,
            namespace,
            objectName
        );
    }

    private String dbtSourceKey(SourceBindingState binding) throws Exception {
        JsonNode locator = objectMapper.readTree(binding.locatorJson());
        return CatalogAssetKey.dbtModel(locator.path("uniqueId").asText(), binding.sourceId());
    }

    private String missingBindingAsset(SourceBindingState binding, List<Blocker> blockers) {
        blockers.add(
            new Blocker(
                "PENDING_LINEAGE",
                "Confirmed source binding is not linked to a catalog asset",
                binding.id().toString()
            )
        );
        return null;
    }

    private String outputKey(ModelSpecView model, ImplementationView implementation) {
        if (implementation != null && implementation.dbtUniqueId() != null && !implementation.dbtUniqueId().isBlank()) {
            return CatalogAssetKey.dbtModel(implementation.dbtUniqueId(), model.name());
        }
        return modelRevisionKey(model.id(), model.revision());
    }

    private static String modelRevisionKey(UUID modelSpecId, int revision) {
        return "model-spec:" + modelSpecId + ":revision:" + revision;
    }

    private static boolean current(ModelSpecView model, ImplementationView implementation) {
        return (
            implementation.revision() == model.revision() &&
            Objects.equals(implementation.modelChecksum(), model.checksum())
        );
    }

    private String evidenceJson(Decision decision) {
        try {
            return objectMapper.writeValueAsString(
                Map.of(
                    "modelSpecId",
                    decision.modelSpecId(),
                    "revision",
                    decision.revision(),
                    "upstreamLevels",
                    decision.upstreamLevels(),
                    "fieldLevels",
                    decision.fieldLevels()
                )
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize classification publication evidence", exception);
        }
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to calculate classification evidence checksum", exception);
        }
    }

    private static String normalize(String value) {
        return String.valueOf(value)
            .trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9_.:-]+", "_")
            .replaceAll("_+", "_");
    }

    private static String escape(String value) {
        return String.valueOf(value).replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record InputEvidence(String subjectKey, String level) {}

    public record Blocker(String code, String message, String subjectRef) {}

    public record Decision(
        boolean ready,
        UUID modelSpecId,
        int revision,
        String outputSubjectKey,
        String effectiveLevel,
        Map<String, String> upstreamLevels,
        Map<String, String> fieldLevels,
        List<Blocker> blockers,
        ClassificationFact seal
    ) {
        private static Decision blocked(
            UUID modelSpecId,
            int revision,
            String outputSubjectKey,
            List<Blocker> blockers
        ) {
            return new Decision(
                false,
                modelSpecId,
                revision,
                outputSubjectKey,
                null,
                Map.of(),
                Map.of(),
                List.copyOf(blockers),
                null
            );
        }

        private Decision withSeal(ClassificationFact snapshot) {
            return new Decision(
                ready,
                modelSpecId,
                revision,
                outputSubjectKey,
                effectiveLevel,
                upstreamLevels,
                fieldLevels,
                blockers,
                snapshot
            );
        }
    }
}
