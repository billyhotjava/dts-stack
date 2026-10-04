package com.yuzhi.dts.platform.service.modeling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationAvailabilityPinRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationAvailabilityPinRepository.AvailabilityPin;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationAvailabilityPinRepository.PinnedSnapshot;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationSourceSnapshotRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationSourceSnapshotRepository.InputSnapshot;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationSourceSnapshotRepository.SourceBindingDescriptor;
import com.yuzhi.dts.platform.service.catalog.CatalogMaterializationSourceAvailabilityPort;
import com.yuzhi.dts.platform.service.catalog.CatalogMaterializationSourceAvailabilityPort.CurrentSource;
import com.yuzhi.dts.platform.service.catalog.CatalogMaterializationSourceAvailabilityPort.ExpectedSource;
import com.yuzhi.dts.platform.service.catalog.CatalogMaterializationSourceAvailabilityPort.SourceDescriptor;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException.Kind;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.SourceKind;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.SourceRef;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Fail-closed execution-time check for physical sources pinned by a materialization candidate. */
@Service
public class ModelMaterializationSourceAvailabilityGuard {

    public static final String SOURCE_UNAVAILABLE = "MODEL_SOURCE_AVAILABILITY_FENCE_ACTIVE";
    public static final String SOURCE_GENERATION_STALE = "MODEL_MATERIALIZATION_SOURCE_GENERATION_STALE";
    public static final String SOURCE_PIN_MISSING = "MODEL_MATERIALIZATION_SOURCE_PIN_MISSING";
    private static final Pattern DBT_IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private final ModelMaterializationSourceSnapshotRepository snapshots;
    private final ModelSpecSourceValidationPort sourceValidation;
    private final CatalogMaterializationSourceAvailabilityPort catalogAvailability;
    private final ModelMaterializationAvailabilityPinRepository pins;
    private final ObjectMapper objectMapper;

    public ModelMaterializationSourceAvailabilityGuard(
        ModelMaterializationSourceSnapshotRepository snapshots,
        ModelSpecSourceValidationPort sourceValidation,
        CatalogMaterializationSourceAvailabilityPort catalogAvailability,
        ModelMaterializationAvailabilityPinRepository pins,
        ObjectMapper objectMapper
    ) {
        this.snapshots = snapshots;
        this.sourceValidation = sourceValidation;
        this.catalogAvailability = catalogAvailability;
        this.pins = pins;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public void requireCandidateCurrent(String tenantId, UUID candidateId, int candidateVersion) {
        requireCurrent(snapshots.findCandidateInputs(tenantId, candidateId, candidateVersion), candidateId);
    }

    @Transactional(readOnly = true)
    public void requireDispatchCurrent(UUID dispatchId) {
        requireCurrent(snapshots.findDispatchInputs(dispatchId), dispatchId);
    }

    /** Resolves the exact dbt source tuples from the same version-bound inputs used by the fence. */
    @Transactional(readOnly = true)
    public List<PinnedSourceDefinition> pinnedDispatchSources(UUID dispatchId) {
        List<PhysicalSourceRequest> requests = requireCurrent(
            snapshots.findDispatchInputs(dispatchId),
            dispatchId
        );
        return requests
            .stream()
            .map(request -> pinnedSource(request, dispatchId))
            .filter(java.util.Objects::nonNull)
            .sorted(
                Comparator.comparing(PinnedSourceDefinition::sourceName)
                    .thenComparing(PinnedSourceDefinition::tableName)
                    .thenComparing(definition -> definition.sourceBindingId().toString())
            )
            .toList();
    }

    /** Operational runs use only the published identities captured by their fixed run scope. */
    @Transactional(readOnly = true)
    public List<PinnedSourceDefinition> pinnedOperationalSources(UUID groupId) {
        return requireCurrent(snapshots.findOperationalInputs(groupId), groupId, false)
            .stream()
            .map(request -> pinnedSource(request, groupId))
            .filter(java.util.Objects::nonNull)
            .sorted(Comparator.comparing(PinnedSourceDefinition::sourceName)
                .thenComparing(PinnedSourceDefinition::tableName)
                .thenComparing(definition -> definition.sourceBindingId().toString()))
            .toList();
    }

    private PinnedSourceDefinition pinnedSource(PhysicalSourceRequest request, UUID dispatchId) {
        SourceRef source = sourceValidation
            .resolveCurrentBindingForExecutionCompiler(
                request.tenantId(),
                request.planId(),
                request.sourceBindingId(),
                request.resolvedVersion()
            )
            .orElseThrow(() -> unavailable(
                dispatchId,
                request.modelSpecId(),
                "The pinned source relation is unavailable"
            ));
        if (source.kind() == SourceKind.DBT_MODEL) return null;
        if (source.kind() != SourceKind.TABLE || source.ref() == null) {
            throw unavailable(dispatchId, request.modelSpecId(), "The pinned source relation is invalid");
        }
        String ref = source.ref().trim();
        int separator = ref.indexOf('.');
        String sourceName = separator > 0 ? ref.substring(0, separator) : "ods";
        String tableName = separator > 0 ? ref.substring(separator + 1) : ref;
        if (!DBT_IDENTIFIER.matcher(sourceName).matches() || !DBT_IDENTIFIER.matcher(tableName).matches()) {
            throw unavailable(dispatchId, request.modelSpecId(), "The pinned dbt source identity is invalid");
        }
        return new PinnedSourceDefinition(
            request.sourceBindingId(),
            request.resolvedVersion(),
            sourceName,
            sourceName,
            tableName
        );
    }

    /**
     * Captures the complete physical-source generation after the runtime lease has been attached.
     * The catalog adapter holds a SHARE table lock until this transaction commits, so a rollback
     * cannot cross the pin linearization point.
     */
    public void pinDispatchCurrent(UUID dispatchId, Instant pinnedAt) {
        PinnedSnapshot existing = pins.findSnapshot(dispatchId);
        if (existing.complete()) {
            GenerationCheck replay = checkPinnedCurrentForUpdate(dispatchId);
            if (!replay.current()) {
                throw unavailable(dispatchId, null, "The runtime source generation is stale");
            }
            return;
        }
        List<InputSnapshot> roots = snapshots.findDispatchInputs(dispatchId);
        List<PhysicalSourceRequest> requested = requireCurrent(roots, dispatchId);
        List<SourceDescriptor> descriptors = requested
            .stream()
            .map(request -> descriptor(request, dispatchId))
            .toList();
        List<SourceDescriptor> availabilityManaged = descriptors
            .stream()
            .filter(descriptor -> availabilityManaged(descriptor, dispatchId))
            .toList();
        List<CurrentSource> current;
        try {
            current = catalogAvailability.lockAndRead(availabilityManaged);
        } catch (RuntimeException failure) {
            throw unavailable(dispatchId, null, "The catalog source generation cannot be pinned");
        }
        if (
            current.size() != availabilityManaged.size() ||
            current.stream().anyMatch(source -> !source.isAvailable())
        ) {
            throw unavailable(dispatchId, null, "A physical source is fenced or unavailable");
        }
        // Revalidate the version-bound source while the catalog generation lock is still held.
        requireCurrent(snapshots.findDispatchInputs(dispatchId), dispatchId);
        Map<UUID, PhysicalSourceRequest> requestsByBinding = new HashMap<>();
        requested.forEach(request -> requestsByBinding.put(request.sourceBindingId(), request));
        List<AvailabilityPin> captured = current
            .stream()
            .map(source -> {
                PhysicalSourceRequest request = requestsByBinding.get(source.sourceBindingId());
                if (request == null) {
                    throw unavailable(dispatchId, null, "The catalog source identity changed while pinning");
                }
                return new AvailabilityPin(
                    source.sourceBindingId(),
                    source.assetType(),
                    source.assetKey(),
                    source.status(),
                    source.epoch(),
                    source.sourceSequence(),
                    source.eventId(),
                    request.resolvedVersion()
                );
            })
            .toList();
        pins.persistSnapshot(dispatchId, captured, pinnedAt);
    }

    /** Locks the dispatch and catalog generation, then compares it with the runtime lease pin. */
    public GenerationCheck checkPinnedCurrentForUpdate(UUID dispatchId) {
        PinnedSnapshot snapshot;
        try {
            snapshot = pins.lockSnapshot(dispatchId);
        } catch (RuntimeException failure) {
            return GenerationCheck.stale(SOURCE_PIN_MISSING, List.of());
        }
        if (!snapshot.complete()) {
            return GenerationCheck.stale(SOURCE_PIN_MISSING, List.of());
        }
        List<ExpectedSource> expected = snapshot
            .pins()
            .stream()
            .map(pin ->
                new ExpectedSource(
                    pin.sourceBindingId(),
                    pin.assetType(),
                    pin.assetKey(),
                    pin.status(),
                    pin.epoch(),
                    pin.sourceSequence(),
                    pin.eventId()
                )
            )
            .toList();
        List<CatalogMaterializationSourceAvailabilityPort.GenerationDrift> catalogDrift;
        try {
            catalogDrift = catalogAvailability.lockAndCompare(expected);
        } catch (RuntimeException failure) {
            return GenerationCheck.stale(SOURCE_GENERATION_STALE, fromPins(snapshot.pins(), SOURCE_GENERATION_STALE));
        }
        if (!catalogDrift.isEmpty()) {
            return GenerationCheck.stale(
                SOURCE_GENERATION_STALE,
                catalogDrift.stream().map(GenerationDrift::fromCatalog).toList()
            );
        }
        try {
            requireCurrent(snapshots.findDispatchInputs(dispatchId), dispatchId);
        } catch (ModelReleaseCandidateException unavailable) {
            return GenerationCheck.stale(unavailable.code(), fromPins(snapshot.pins(), unavailable.code()));
        }
        return GenerationCheck.currentCheck();
    }

    private List<PhysicalSourceRequest> requireCurrent(List<InputSnapshot> roots, UUID boundaryId) {
        return requireCurrent(roots, boundaryId, true);
    }

    private List<PhysicalSourceRequest> requireCurrent(
        List<InputSnapshot> roots, UUID boundaryId, boolean allowPublishedHead
    ) {
        if (roots == null || roots.isEmpty()) {
            throw unavailable(boundaryId, null, "Materialization input snapshot is missing");
        }
        Map<ModelPin, InputSnapshot> candidateInputs = new HashMap<>();
        roots.forEach(snapshot -> candidateInputs.put(ModelPin.of(snapshot), snapshot));
        ArrayDeque<InputSnapshot> pending = new ArrayDeque<>(roots);
        Set<ModelPin> visited = new HashSet<>();
        Map<UUID, PhysicalSourceRequest> physicalSources = new java.util.LinkedHashMap<>();
        while (!pending.isEmpty()) {
            InputSnapshot snapshot = pending.removeFirst();
            ModelPin pin = ModelPin.of(snapshot);
            if (!visited.add(pin)) {
                continue;
            }
            JsonNode inputs = parseInputs(snapshot, boundaryId);
            switch (snapshot.inputMode()) {
                case "PHYSICAL_ASSET" -> validatePhysicalInputs(
                    snapshot,
                    inputs,
                    boundaryId,
                    physicalSources
                );
                case "UPSTREAM_MODEL" -> enqueueUpstreamInputs(
                    snapshot,
                    inputs,
                    boundaryId,
                    candidateInputs,
                    pending,
                    allowPublishedHead
                );
                case "GENERATED" -> validateGeneratedSources(
                    snapshot,
                    boundaryId,
                    physicalSources
                );
                default -> throw unavailable(boundaryId, snapshot.modelSpecId(), "Implementation input mode is invalid");
            }
        }
        return List.copyOf(physicalSources.values());
    }

    private void validatePhysicalInputs(
        InputSnapshot snapshot,
        JsonNode inputs,
        UUID boundaryId,
        Map<UUID, PhysicalSourceRequest> physicalSources
    ) {
        for (JsonNode input : inputs) {
            UUID sourceBindingId = uuid(input.path("sourceBindingId").asText(null));
            String resolvedVersion = input.path("resolvedVersion").asText(null);
            if (
                sourceBindingId == null ||
                resolvedVersion == null ||
                resolvedVersion.isBlank() ||
                !sourceValidation.isCurrentBindingForExecution(
                    snapshot.tenantId(),
                    snapshot.planId(),
                    sourceBindingId,
                    resolvedVersion
                )
            ) {
                throw unavailable(boundaryId, snapshot.modelSpecId(), "A physical source is fenced or stale");
            }
            PhysicalSourceRequest request = new PhysicalSourceRequest(
                snapshot.tenantId(),
                snapshot.planId(),
                snapshot.modelSpecId(),
                sourceBindingId,
                resolvedVersion.trim()
            );
            PhysicalSourceRequest previous = physicalSources.putIfAbsent(sourceBindingId, request);
            if (previous != null && !sameSourceGeneration(previous, request)) {
                throw unavailable(boundaryId, snapshot.modelSpecId(), "A source binding has conflicting generation pins");
            }
        }
    }

    private void validateGeneratedSources(
        InputSnapshot snapshot,
        UUID boundaryId,
        Map<UUID, PhysicalSourceRequest> physicalSources
    ) {
        JsonNode inputs = parseInputs(snapshot, boundaryId);
        if (inputs.size() == 1 && ModelSchemaOnlySupport.GENERATOR.equals(inputs.get(0).path("generatorType").asText())) return;
        JsonNode sourceRefs = parseSourceRefs(snapshot, boundaryId);
        if (!sourceRefs.isEmpty()) {
            validatePhysicalInputs(snapshot, sourceRefs, boundaryId, physicalSources);
        }
    }

    private JsonNode parseSourceRefs(InputSnapshot snapshot, UUID boundaryId) {
        if (snapshot.sourceRefsJson() == null || snapshot.sourceRefsJson().isBlank()) {
            return objectMapper.createArrayNode();
        }
        try {
            JsonNode sources = objectMapper.readTree(snapshot.sourceRefsJson());
            if (!sources.isArray()) {
                throw unavailable(boundaryId, snapshot.modelSpecId(), "Model source references are unreadable");
            }
            return sources;
        } catch (ModelReleaseCandidateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unavailable(boundaryId, snapshot.modelSpecId(), "Model source references are unreadable");
        }
    }

    private static boolean sameSourceGeneration(
        PhysicalSourceRequest previous,
        PhysicalSourceRequest current
    ) {
        return previous.tenantId().equals(current.tenantId()) &&
            previous.planId().equals(current.planId()) &&
            previous.sourceBindingId().equals(current.sourceBindingId()) &&
            previous.resolvedVersion().equals(current.resolvedVersion());
    }

    private SourceDescriptor descriptor(PhysicalSourceRequest request, UUID boundaryId) {
        SourceBindingDescriptor binding = snapshots
            .findSourceBindingDescriptor(
                request.tenantId(),
                request.planId(),
                request.sourceBindingId(),
                request.resolvedVersion()
            )
            .orElseThrow(() ->
                unavailable(boundaryId, request.modelSpecId(), "The source binding identity is unavailable")
            );
        return new SourceDescriptor(
            binding.sourceBindingId(),
            binding.sourceType(),
            binding.sourceId(),
            binding.locatorJson()
        );
    }

    private boolean availabilityManaged(SourceDescriptor descriptor, UUID boundaryId) {
        return switch (descriptor.sourceType()) {
            case "CATALOG_TABLE", "CONNECTION_TABLE", "DBT_NODE" -> true;
            case "EXCEL_FILE" -> false;
            default -> throw unavailable(
                boundaryId,
                null,
                "The source binding type is unsupported"
            );
        };
    }

    private static List<GenerationDrift> fromPins(List<AvailabilityPin> pins, String reasonCode) {
        return pins
            .stream()
            .map(pin ->
                new GenerationDrift(
                    pin.sourceBindingId(),
                    pin.assetType() == null ? null : pin.assetType().name(),
                    pin.epoch(),
                    pin.sourceSequence(),
                    pin.eventId(),
                    null,
                    -1L,
                    -1L,
                    null,
                    reasonCode
                )
            )
            .toList();
    }

    private void enqueueUpstreamInputs(
        InputSnapshot owner,
        JsonNode inputs,
        UUID boundaryId,
        Map<ModelPin, InputSnapshot> candidateInputs,
        ArrayDeque<InputSnapshot> pending,
        boolean allowPublishedHead
    ) {
        for (JsonNode input : inputs) {
            UUID modelSpecId = uuid(input.path("modelSpecId").asText(null));
            int revision = input.path("revision").asInt(0);
            String checksum = input.path("checksum").asText(null);
            if (modelSpecId == null || revision <= 0 || checksum == null || checksum.isBlank()) {
                throw unavailable(boundaryId, owner.modelSpecId(), "An upstream model pin is invalid");
            }
            ModelPin pin = new ModelPin(modelSpecId, revision, checksum);
            InputSnapshot snapshot = candidateInputs.get(pin);
            if (snapshot == null) {
                if (!allowPublishedHead) {
                    throw unavailable(boundaryId, owner.modelSpecId(), "An upstream model is outside the fixed operational scope");
                }
                List<InputSnapshot> published = snapshots.findPublishedInput(
                    owner.tenantId(),
                    modelSpecId,
                    revision,
                    checksum
                );
                if (published.size() != 1) {
                    throw unavailable(boundaryId, owner.modelSpecId(), "An upstream implementation snapshot is stale");
                }
                snapshot = published.getFirst();
            }
            pending.addLast(snapshot);
        }
    }

    private JsonNode parseInputs(InputSnapshot snapshot, UUID boundaryId) {
        if (snapshot.inputMode() == null || snapshot.inputsJson() == null) {
            throw unavailable(boundaryId, snapshot.modelSpecId(), "Implementation input snapshot is incomplete");
        }
        try {
            JsonNode inputs = objectMapper.readTree(snapshot.inputsJson());
            if (!inputs.isArray() || inputs.isEmpty()) {
                throw unavailable(boundaryId, snapshot.modelSpecId(), "Implementation inputs are empty");
            }
            return inputs;
        } catch (ModelReleaseCandidateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unavailable(boundaryId, snapshot.modelSpecId(), "Implementation inputs are unreadable");
        }
    }

    private ModelReleaseCandidateException unavailable(UUID boundaryId, UUID modelSpecId, String message) {
        Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("boundaryId", boundaryId);
        if (modelSpecId != null) {
            details.put("modelSpecId", modelSpecId);
        }
        return new ModelReleaseCandidateException(SOURCE_UNAVAILABLE, message, Kind.UNPROCESSABLE, Map.copyOf(details));
    }

    private UUID uuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private record ModelPin(UUID modelSpecId, int revision, String checksum) {
        private static ModelPin of(InputSnapshot snapshot) {
            return new ModelPin(snapshot.modelSpecId(), snapshot.modelRevision(), snapshot.modelChecksum());
        }
    }

    private record PhysicalSourceRequest(
        String tenantId,
        UUID planId,
        UUID modelSpecId,
        UUID sourceBindingId,
        String resolvedVersion
    ) {}

    public record PinnedSourceDefinition(
        UUID sourceBindingId,
        String resolvedVersion,
        String sourceName,
        String schemaName,
        String tableName
    ) {
        public PinnedSourceDefinition {
            if (
                sourceBindingId == null ||
                resolvedVersion == null ||
                resolvedVersion.isBlank() ||
                !DBT_IDENTIFIER.matcher(sourceName == null ? "" : sourceName).matches() ||
                !DBT_IDENTIFIER.matcher(schemaName == null ? "" : schemaName).matches() ||
                !DBT_IDENTIFIER.matcher(tableName == null ? "" : tableName).matches()
            ) {
                throw new IllegalArgumentException("Pinned dbt source definition is invalid");
            }
            resolvedVersion = resolvedVersion.trim();
        }
    }

    public record GenerationCheck(boolean current, String reasonCode, List<GenerationDrift> drift) {
        public GenerationCheck {
            drift = drift == null ? List.of() : List.copyOf(drift);
            if (current && (reasonCode != null || !drift.isEmpty())) {
                throw new IllegalArgumentException("current generation cannot contain drift");
            }
            if (!current && (reasonCode == null || reasonCode.isBlank())) {
                throw new IllegalArgumentException("stale generation requires a reason code");
            }
        }

        public static GenerationCheck currentCheck() {
            return new GenerationCheck(true, null, List.of());
        }

        public static GenerationCheck stale(String reasonCode, List<GenerationDrift> drift) {
            return new GenerationCheck(false, reasonCode, drift);
        }
    }

    public record GenerationDrift(
        UUID sourceBindingId,
        String assetType,
        long pinnedEpoch,
        long pinnedSourceSequence,
        String pinnedEventId,
        String currentStatus,
        long currentEpoch,
        long currentSourceSequence,
        String currentEventId,
        String reasonCode
    ) {
        private static GenerationDrift fromCatalog(
            CatalogMaterializationSourceAvailabilityPort.GenerationDrift drift
        ) {
            return new GenerationDrift(
                drift.sourceBindingId(),
                drift.assetType().name(),
                drift.expectedEpoch(),
                drift.expectedSourceSequence(),
                drift.expectedEventId(),
                drift.currentStatus(),
                drift.currentEpoch(),
                drift.currentSourceSequence(),
                drift.currentEventId(),
                SOURCE_GENERATION_STALE
            );
        }
    }
}
