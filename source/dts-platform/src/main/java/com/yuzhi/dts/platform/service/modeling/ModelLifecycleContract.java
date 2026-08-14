package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import java.lang.reflect.Array;
import java.time.Instant;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Revision-bound contracts for implementation, verification, release and registration. */
public final class ModelLifecycleContract {

    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    private static final Pattern PHYSICAL_NAME = Pattern.compile("^[a-z][a-z0-9_]{0,62}$");
    private static final Pattern SOURCE_FIELD = Pattern.compile("^(?:src_([0-9]+)\\.)?[A-Za-z_][A-Za-z0-9_]*$");
    private static final Pattern SHA_256 = Pattern.compile("^[0-9a-f]{64}$");
    private static final Set<String> MATERIALIZATIONS = Set.of("table", "view", "incremental");
    private static final Set<String> CAST_TYPES = Set.of("string", "integer", "bigint", "decimal", "date", "timestamp", "boolean");
    static final Set<String> IMPLEMENTATION_SETTING_KEYS = Set.of(
        "casts",
        "deduplicateBy",
        "dedupBy",
        "joins",
        "targetPhysicalName",
        "loadStrategy",
        "partitionFields",
        "retentionDays"
    );
    private static final Set<String> LOAD_STRATEGIES = Set.of("FULL", "INCREMENTAL", "SNAPSHOT");
    private static final Set<String> JOIN_TYPES = Set.of("INNER", "LEFT", "RIGHT", "FULL");
    private static final Set<String> JOIN_KEYS = Set.of("inputIndex", "type", "leftField", "rightField");

    private ModelLifecycleContract() {}

    public enum EventType {
        COMPILE,
        TEST,
        REVIEW_SUBMITTED,
        REVIEW_APPROVED,
        RELEASE,
        ROLLBACK,
        RUN,
    }

    /** Immutable evidence categories consumed by the build, quality and release workbench. */
    public enum DeliveryEvidenceType {
        ARTIFACT,
        BUILD_RUN,
        QUALITY_RUN,
        REVIEW,
        PUBLICATION,
        REGISTRATION,
        ROLLBACK,
    }

    /** Candidate-level delivery state. Only a current published candidate completes Stage 6. */
    public enum DeliveryStatus {
        DRAFT,
        BUILDING,
        BUILD_FAILED,
        BUILT,
        QUALITY_RUNNING,
        QUALITY_FAILED,
        QUALITY_PASSED,
        REVIEW_PENDING,
        REJECTED,
        APPROVED,
        PUBLISHING,
        PARTIAL,
        PUBLISHED,
        ROLLED_BACK,
        CANCELLED,
        STALE;

        private static final Map<DeliveryStatus, Set<DeliveryStatus>> TRANSITIONS = transitions();

        public static boolean canTransition(DeliveryStatus from, DeliveryStatus to) {
            return from != null && to != null && TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
        }

        public boolean satisfiesStageSixCompletion() {
            return this == PUBLISHED;
        }

        public List<DeliveryAction> allowedActions() {
            return ACTIONS.get(this);
        }

        private static Map<DeliveryStatus, Set<DeliveryStatus>> transitions() {
            Map<DeliveryStatus, Set<DeliveryStatus>> result = new EnumMap<>(DeliveryStatus.class);
            result.put(DRAFT, EnumSet.of(BUILDING, CANCELLED));
            result.put(BUILDING, EnumSet.of(BUILD_FAILED, BUILT));
            result.put(BUILD_FAILED, EnumSet.of(BUILDING, CANCELLED));
            result.put(BUILT, EnumSet.of(BUILDING, QUALITY_RUNNING, CANCELLED));
            result.put(QUALITY_RUNNING, EnumSet.of(QUALITY_FAILED, QUALITY_PASSED));
            result.put(QUALITY_FAILED, EnumSet.of(QUALITY_RUNNING, CANCELLED));
            result.put(QUALITY_PASSED, EnumSet.of(REVIEW_PENDING, PUBLISHING));
            result.put(REVIEW_PENDING, EnumSet.of(REJECTED, APPROVED, PUBLISHING));
            result.put(APPROVED, EnumSet.of(PUBLISHING));
            result.put(PUBLISHING, EnumSet.of(PARTIAL, PUBLISHED));
            result.put(PARTIAL, EnumSet.of(PUBLISHED, ROLLED_BACK));
            result.put(PUBLISHED, EnumSet.of(ROLLED_BACK));
            for (DeliveryStatus status : values()) {
                if (status != REJECTED && status != ROLLED_BACK && status != CANCELLED && status != STALE) {
                    result.computeIfAbsent(status, ignored -> EnumSet.noneOf(DeliveryStatus.class)).add(STALE);
                }
            }
            return Map.copyOf(result);
        }

        private static final Map<DeliveryStatus, List<DeliveryAction>> ACTIONS = actions();

        private static Map<DeliveryStatus, List<DeliveryAction>> actions() {
            Map<DeliveryStatus, List<DeliveryAction>> result = new EnumMap<>(DeliveryStatus.class);
            result.put(DRAFT, List.of(DeliveryAction.START_BUILD, DeliveryAction.CANCEL_CANDIDATE));
            result.put(BUILDING, List.of());
            result.put(BUILD_FAILED, List.of(DeliveryAction.RETRY_BUILD, DeliveryAction.CANCEL_CANDIDATE));
            result.put(BUILT, List.of(DeliveryAction.RUN_QUALITY, DeliveryAction.CANCEL_CANDIDATE));
            result.put(QUALITY_RUNNING, List.of());
            result.put(QUALITY_FAILED, List.of(DeliveryAction.RUN_QUALITY, DeliveryAction.CANCEL_CANDIDATE));
            result.put(QUALITY_PASSED, List.of(DeliveryAction.SUBMIT_REVIEW, DeliveryAction.PUBLISH));
            result.put(REVIEW_PENDING, List.of(DeliveryAction.APPROVE, DeliveryAction.REJECT, DeliveryAction.PUBLISH));
            result.put(REJECTED, List.of(DeliveryAction.CREATE_REPLACEMENT_CANDIDATE));
            result.put(APPROVED, List.of(DeliveryAction.PUBLISH));
            result.put(PUBLISHING, List.of());
            result.put(PARTIAL, List.of(DeliveryAction.RETRY_PUBLICATION, DeliveryAction.ROLLBACK));
            result.put(PUBLISHED, List.of(DeliveryAction.ROLLBACK));
            result.put(ROLLED_BACK, List.of(DeliveryAction.CREATE_REPLACEMENT_CANDIDATE));
            result.put(CANCELLED, List.of(DeliveryAction.CREATE_REPLACEMENT_CANDIDATE));
            result.put(STALE, List.of(DeliveryAction.CREATE_REPLACEMENT_CANDIDATE));
            return Map.copyOf(result);
        }
    }

    public enum DeliveryActorRole {
        MODEL_MAINTAINER,
        RELEASE_REVIEWER,
        RELEASE_OPERATOR,
    }

    public enum DeliveryAction {
        START_BUILD(DeliveryActorRole.MODEL_MAINTAINER),
        RETRY_BUILD(DeliveryActorRole.MODEL_MAINTAINER),
        RUN_QUALITY(DeliveryActorRole.MODEL_MAINTAINER),
        SUBMIT_REVIEW(DeliveryActorRole.MODEL_MAINTAINER),
        CANCEL_CANDIDATE(DeliveryActorRole.MODEL_MAINTAINER),
        APPROVE(DeliveryActorRole.RELEASE_REVIEWER),
        REJECT(DeliveryActorRole.RELEASE_REVIEWER),
        CREATE_REPLACEMENT_CANDIDATE(DeliveryActorRole.MODEL_MAINTAINER),
        PUBLISH(DeliveryActorRole.RELEASE_OPERATOR),
        RETRY_PUBLICATION(DeliveryActorRole.RELEASE_OPERATOR),
        ROLLBACK(DeliveryActorRole.RELEASE_OPERATOR);

        private final DeliveryActorRole requiredRole;

        DeliveryAction(DeliveryActorRole requiredRole) {
            this.requiredRole = requiredRole;
        }

        public DeliveryActorRole requiredRole() {
            return requiredRole;
        }

        public boolean isAllowedFor(DeliveryStatus status, DeliveryActorRole role, String actorId, DeliveryAuditView audit) {
            if (status == null || !status.allowedActions().contains(this) || role != requiredRole || actorId == null || actorId.isBlank()) {
                return false;
            }
            if (
                this == PUBLISH &&
                (status == DeliveryStatus.QUALITY_PASSED || status == DeliveryStatus.REVIEW_PENDING)
            ) {
                return true;
            }
            if (!requiresSubmitterSeparation()) return true;
            if (audit == null || !audit.canBeApprovedBy(actorId)) return false;
            if (this != PUBLISH) return true;
            return audit.hasApproval() && !actorId.trim().equals(audit.approvedBy());
        }

        private boolean requiresSubmitterSeparation() {
            return this == APPROVE || this == REJECT || this == PUBLISH;
        }
    }

    /** Stable visible outcomes when current release evidence cannot satisfy Stage 6. */
    public enum DeliveryEvidenceErrorCode {
        MODEL_SPEC_PUBLISHED_REQUIRED,
        MODEL_RELEASE_EVIDENCE_REQUIRED,
        MODEL_RELEASE_NOT_PUBLISHED,
        MODEL_RELEASE_EVIDENCE_STALE,
        MODEL_RELEASE_OWNER_UNAVAILABLE,
    }

    /** Immutable actor and timestamp trail for one delivery candidate. */
    public record DeliveryAuditView(
        String createdBy,
        Instant createdAt,
        String submittedBy,
        Instant submittedAt,
        String approvedBy,
        Instant approvedAt,
        String publishedBy,
        Instant publishedAt
    ) {
        public DeliveryAuditView {
            createdBy = requiredText(createdBy, "createdBy");
            if (createdAt == null) throw new IllegalArgumentException("createdAt is required");
            submittedBy = optionalActor(submittedBy, submittedAt, "submitted");
            approvedBy = optionalActor(approvedBy, approvedAt, "approved");
            publishedBy = optionalActor(publishedBy, publishedAt, "published");
        }

        public boolean canBeApprovedBy(String actorId) {
            return submittedBy != null && actorId != null && !actorId.isBlank() && !submittedBy.equals(actorId.trim());
        }

        public boolean hasApproval() {
            return approvedBy != null && approvedAt != null;
        }
    }

    /** Immutable UI projection for a delivery candidate. */
    public record DeliveryView(
        DeliveryStatus status,
        List<DeliveryAction> allowedActions,
        String primaryBlockerCode,
        DeliveryAuditView audit
    ) {
        public DeliveryView {
            if (status == null) throw new IllegalArgumentException("status is required");
            allowedActions = List.copyOf(allowedActions == null ? List.of() : allowedActions);
            if (!allowedActions.equals(status.allowedActions())) {
                throw new IllegalArgumentException("allowedActions must match status");
            }
            primaryBlockerCode = primaryBlockerCode == null || primaryBlockerCode.isBlank() ? null : primaryBlockerCode.trim();
            if (audit == null) throw new IllegalArgumentException("audit is required");
        }

        public static DeliveryView forStatus(DeliveryStatus status, String primaryBlockerCode, DeliveryAuditView audit) {
            if (status == null) throw new IllegalArgumentException("status is required");
            return new DeliveryView(status, status.allowedActions(), primaryBlockerCode, audit);
        }
    }

    /** Stable key for every evidence item belonging to a delivery candidate entry. */
    public record RevisionBoundDeliveryKey(
        UUID planId,
        UUID candidateId,
        UUID modelSpecId,
        int revision,
        String checksum,
        ImplementationMode implementationMode,
        String environment
    ) {
        public RevisionBoundDeliveryKey {
            planId = requiredUuid(planId, "planId");
            candidateId = requiredUuid(candidateId, "candidateId");
            modelSpecId = requiredUuid(modelSpecId, "modelSpecId");
            if (revision < 1) throw new IllegalArgumentException("revision must be positive");
            checksum = requiredText(checksum, "checksum");
            if (implementationMode == null) throw new IllegalArgumentException("implementationMode is required");
            environment = requiredText(environment, "environment");
        }
    }

    private static UUID requiredUuid(UUID value, String name) {
        if (value == null) throw new IllegalArgumentException(name + " is required");
        return value;
    }

    private static String requiredText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }

    private static String requiredIdempotencyKey(String value) {
        String result = requiredText(value, "idempotencyKey");
        if (result.length() > 128) throw new IllegalArgumentException("idempotencyKey must not exceed 128 characters");
        return result;
    }

    private static String optionalActor(String actorId, Instant at, String action) {
        if (actorId == null && at == null) return null;
        if (actorId == null || actorId.isBlank() || at == null) {
            throw new IllegalArgumentException(action + " actor and timestamp must be provided together");
        }
        return actorId.trim();
    }

    /** The single source category used by one implementation revision. */
    public enum InputMode {
        PHYSICAL_ASSET,
        UPSTREAM_MODEL,
        GENERATED,
    }

    /** Typed, mode-bound implementation input persisted as strict JSON. */
    public sealed interface ImplementationInput permits PhysicalAssetInput, UpstreamModelInput, GeneratedInput {
        InputMode mode();
    }

    public record PhysicalAssetInput(UUID sourceBindingId, String resolvedVersion) implements ImplementationInput {
        public PhysicalAssetInput {
            Objects.requireNonNull(sourceBindingId, "sourceBindingId is required");
            resolvedVersion = requiredText(resolvedVersion, "resolvedVersion");
        }

        @Override
        public InputMode mode() {
            return InputMode.PHYSICAL_ASSET;
        }
    }

    public record UpstreamModelInput(
        UUID modelSpecId,
        int revision,
        String checksum,
        int implementationRevision,
        String implementationChecksum,
        String dbtUniqueId
    ) implements ImplementationInput {
        public UpstreamModelInput {
            Objects.requireNonNull(modelSpecId, "modelSpecId is required");
            if (revision < 1) throw new IllegalArgumentException("revision must be positive");
            checksum = requiredText(checksum, "checksum").toLowerCase();
            if (!SHA_256.matcher(checksum).matches()) throw new IllegalArgumentException("checksum must be SHA-256");
            boolean unpinned = implementationRevision == 0 && implementationChecksum == null && dbtUniqueId == null;
            if (!unpinned) {
                if (implementationRevision < 1) throw new IllegalArgumentException("implementationRevision must be positive");
                implementationChecksum = requiredText(implementationChecksum, "implementationChecksum").toLowerCase();
                if (!SHA_256.matcher(implementationChecksum).matches()) {
                    throw new IllegalArgumentException("implementationChecksum must be SHA-256");
                }
                dbtUniqueId = requiredText(dbtUniqueId, "dbtUniqueId");
            }
        }

        public boolean implementationPinned() {
            return implementationRevision > 0 && implementationChecksum != null && dbtUniqueId != null;
        }

        @Override
        public InputMode mode() {
            return InputMode.UPSTREAM_MODEL;
        }
    }

    public record GeneratedInput(String generatorType, Map<String, Object> config) implements ImplementationInput {
        public GeneratedInput {
            generatorType = requiredText(generatorType, "generatorType");
            config = immutableMap(config, "config");
        }

        @Override
        public InputMode mode() {
            return InputMode.GENERATED;
        }
    }

    /** Maps one generated target field to its implementation input field or expression. */
    public record FieldMapping(String sourceField, String targetField) {
        public FieldMapping {
            sourceField = requiredSourceField(sourceField, "sourceField");
            targetField = requiredIdentifier(targetField, "targetField");
        }
    }

    /** Immutable input payload for a new append-only implementation revision. */
    public record SaveImplementationCommand(
        InputMode inputMode,
        List<ImplementationInput> inputs,
        List<FieldMapping> fieldMappings,
        Map<String, Object> settings,
        ImplementationMode ownership,
        String materialization,
        String idempotencyKey
    ) {
        public SaveImplementationCommand {
            Objects.requireNonNull(inputMode, "inputMode is required");
            inputs = immutableInputs(inputs);
            if (inputs.isEmpty()) throw new IllegalArgumentException("inputs are required");
            if (inputs.stream().anyMatch(input -> input.mode() != inputMode)) {
                throw new IllegalArgumentException("inputs must all match inputMode");
            }
            fieldMappings = immutableList(fieldMappings, "fieldMappings");
            settings = validatedSettings(settings);
            validateJoinCoverage(inputMode, inputs, settings);
            Objects.requireNonNull(ownership, "ownership is required");
            materialization = requiredText(materialization, "materialization").toLowerCase();
            if (!MATERIALIZATIONS.contains(materialization)) {
                throw new IllegalArgumentException("materialization is not allowed");
            }
            idempotencyKey = requiredIdempotencyKey(idempotencyKey);
        }
    }

    private static List<ImplementationInput> immutableInputs(List<ImplementationInput> value) {
        List<ImplementationInput> result = immutableList(value, "inputs");
        if (result.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("inputs cannot contain null");
        return result;
    }

    private static <T> List<T> immutableList(List<T> value, String name) {
        if (value == null) throw new IllegalArgumentException(name + " is required");
        if (value.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException(name + " cannot contain null");
        return List.copyOf(value);
    }

    private static Map<String, Object> immutableMap(Map<String, Object> value, String name) {
        if (value == null) throw new IllegalArgumentException(name + " is required");
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : value.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) throw new IllegalArgumentException(name + " cannot contain blank keys");
            result.put(key, deepFreeze(entry.getValue(), name + "." + key));
        }
        return Collections.unmodifiableMap(result);
    }

    private static Object deepFreeze(Object value, String name) {
        if (value == null) throw new IllegalArgumentException(name + " cannot be null");
        if (value instanceof Map<?, ?> values) {
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : values.entrySet()) {
                if (!(entry.getKey() instanceof String key) || key.isBlank()) {
                    throw new IllegalArgumentException(name + " cannot contain non-text or blank keys");
                }
                result.put(key, deepFreeze(entry.getValue(), name + "." + key));
            }
            return Collections.unmodifiableMap(result);
        }
        if (value instanceof List<?> values) {
            ArrayList<Object> result = new ArrayList<>(values.size());
            for (int index = 0; index < values.size(); index++) {
                result.add(deepFreeze(values.get(index), name + "[" + index + "]"));
            }
            return List.copyOf(result);
        }
        if (value instanceof Set<?> values) {
            LinkedHashSet<Object> result = new LinkedHashSet<>();
            int index = 0;
            for (Object item : values) result.add(deepFreeze(item, name + "[" + index++ + "]"));
            return Collections.unmodifiableSet(result);
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            ArrayList<Object> result = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                result.add(deepFreeze(Array.get(value, index), name + "[" + index + "]"));
            }
            return List.copyOf(result);
        }
        if (
            value instanceof String ||
            value instanceof Boolean ||
            value instanceof Character ||
            value instanceof Byte ||
            value instanceof Short ||
            value instanceof Integer ||
            value instanceof Long ||
            value instanceof Float ||
            value instanceof Double ||
            value instanceof BigInteger ||
            value instanceof BigDecimal ||
            value instanceof UUID ||
            value instanceof Enum<?>
        ) {
            return value;
        }
        throw new IllegalArgumentException(name + " contains an unsupported mutable value");
    }

    private static Map<String, Object> validatedSettings(Map<String, Object> value) {
        Map<String, Object> settings = immutableMap(value, "settings");
        if (!IMPLEMENTATION_SETTING_KEYS.containsAll(settings.keySet())) {
            throw new IllegalArgumentException("settings contains an unsupported key");
        }
        Object casts = settings.get("casts");
        if (casts != null) {
            if (!(casts instanceof Map<?, ?> values)) throw new IllegalArgumentException("casts must be an object");
            for (Map.Entry<?, ?> entry : values.entrySet()) {
                String field = entry.getKey() == null ? null : entry.getKey().toString();
                String type = entry.getValue() == null ? null : entry.getValue().toString().toLowerCase();
                requiredIdentifier(field, "casts field");
                if (!CAST_TYPES.contains(type)) throw new IllegalArgumentException("casts type is not allowed");
            }
        }
        validateIdentifierList(settings.get("deduplicateBy"), "deduplicateBy");
        validateIdentifierList(settings.get("dedupBy"), "dedupBy");
        validateIdentifierList(settings.get("partitionFields"), "partitionFields", true);
        validateJoinShape(settings.get("joins"));
        Object targetPhysicalName = settings.get("targetPhysicalName");
        if (
            targetPhysicalName != null &&
            (!(targetPhysicalName instanceof String name) || !PHYSICAL_NAME.matcher(name).matches())
        ) {
            throw new IllegalArgumentException("targetPhysicalName must use lower-case snake_case");
        }
        Object loadStrategy = settings.get("loadStrategy");
        if (
            loadStrategy != null &&
            (!(loadStrategy instanceof String strategy) || !LOAD_STRATEGIES.contains(strategy))
        ) {
            throw new IllegalArgumentException("loadStrategy is not allowed");
        }
        Object retentionDays = settings.get("retentionDays");
        if (
            retentionDays != null &&
            (!(retentionDays instanceof Number days) ||
                days.longValue() != days.doubleValue() ||
                days.longValue() < 0 ||
                days.longValue() > 36_000)
        ) {
            throw new IllegalArgumentException("retentionDays must be between 0 and 36000");
        }
        return settings;
    }

    private static void validateJoinShape(Object value) {
        if (value == null) return;
        if (!(value instanceof List<?> joins)) throw new IllegalArgumentException("joins must be a list");
        for (Object item : joins) {
            if (!(item instanceof Map<?, ?> join) || !JOIN_KEYS.equals(join.keySet())) {
                throw new IllegalArgumentException("each join must contain only inputIndex, type, leftField and rightField");
            }
            Object rawIndex = join.get("inputIndex");
            if (!(rawIndex instanceof Number number) || number.doubleValue() != number.intValue() || number.intValue() < 1) {
                throw new IllegalArgumentException("join inputIndex must be a positive integer");
            }
            String type = requiredText(join.get("type") == null ? null : join.get("type").toString(), "join type").toUpperCase();
            if (!JOIN_TYPES.contains(type)) throw new IllegalArgumentException("join type is not allowed");
            requiredSourceField(join.get("leftField") == null ? null : join.get("leftField").toString(), "join leftField");
            requiredSourceField(join.get("rightField") == null ? null : join.get("rightField").toString(), "join rightField");
        }
    }

    private static void validateJoinCoverage(
        InputMode inputMode,
        List<ImplementationInput> inputs,
        Map<String, Object> settings
    ) {
        Object value = settings.get("joins");
        List<?> joins = value instanceof List<?> values ? values : List.of();
        if (inputMode == InputMode.GENERATED) {
            if (inputs.size() != 1 || !joins.isEmpty()) {
                throw new IllegalArgumentException("generated implementations require exactly one input and no joins");
            }
            return;
        }
        if (inputs.size() == 1) {
            if (!joins.isEmpty()) throw new IllegalArgumentException("single-input implementations cannot define joins");
            return;
        }
        if (joins.size() != inputs.size() - 1) {
            throw new IllegalArgumentException("multi-input implementations require one join for every additional input");
        }
        boolean[] covered = new boolean[inputs.size()];
        for (Object item : joins) {
            Map<?, ?> join = (Map<?, ?>) item;
            int inputIndex = ((Number) join.get("inputIndex")).intValue();
            if (inputIndex >= inputs.size() || covered[inputIndex]) {
                throw new IllegalArgumentException("join inputIndex must reference each additional input exactly once");
            }
            int leftIndex = requiredQualifiedSourceIndex(join.get("leftField").toString(), "join leftField");
            int rightIndex = requiredQualifiedSourceIndex(join.get("rightField").toString(), "join rightField");
            if (leftIndex >= inputIndex || rightIndex != inputIndex) {
                throw new IllegalArgumentException("join fields must connect the new input to an earlier input");
            }
            covered[inputIndex] = true;
        }
        for (int index = 1; index < covered.length; index++) {
            if (!covered[index]) throw new IllegalArgumentException("join coverage is incomplete");
        }
    }

    private static void validateIdentifierList(Object value, String name) {
        validateIdentifierList(value, name, false);
    }

    private static void validateIdentifierList(Object value, String name, boolean allowEmpty) {
        if (value == null) return;
        if (!(value instanceof List<?> values) || (!allowEmpty && values.isEmpty())) {
            throw new IllegalArgumentException(name + " must be a " + (allowEmpty ? "list" : "non-empty list"));
        }
        for (Object item : values) requiredIdentifier(item == null ? null : item.toString(), name);
    }

    private static String requiredIdentifier(String value, String name) {
        String result = requiredText(value, name);
        if (!IDENTIFIER.matcher(result).matches()) throw new IllegalArgumentException(name + " must be a SQL identifier");
        return result;
    }

    private static String requiredSourceField(String value, String name) {
        String result = requiredText(value, name);
        if (!SOURCE_FIELD.matcher(result).matches()) {
            throw new IllegalArgumentException(name + " must be a SQL identifier or a system source alias plus identifier");
        }
        return result;
    }

    private static int requiredQualifiedSourceIndex(String value, String name) {
        java.util.regex.Matcher matcher = SOURCE_FIELD.matcher(requiredText(value, name));
        if (!matcher.matches() || matcher.group(1) == null) {
            throw new IllegalArgumentException(name + " must use a system source alias");
        }
        return Integer.parseInt(matcher.group(1));
    }

    public record ClaimImplementationCommand(
        ImplementationMode ownership,
        String projectKey,
        String dbtUniqueId,
        String idempotencyKey
    ) {
        public ClaimImplementationCommand {
            Objects.requireNonNull(ownership, "ownership is required");
            projectKey = requiredText(projectKey, "projectKey");
            dbtUniqueId = requiredText(dbtUniqueId, "dbtUniqueId");
            idempotencyKey = requiredIdempotencyKey(idempotencyKey);
        }
    }

    public record TestEvidenceCommand(String status, String externalRunId, String comment, String idempotencyKey) {}

    public record PublishCommand(String comment, String idempotencyKey) {}

    public record RollbackCommand(String comment, String idempotencyKey) {}

    public record ImplementationView(
        UUID id,
        UUID modelSpecId,
        UUID planId,
        int revision,
        String modelChecksum,
        ImplementationMode ownership,
        String projectKey,
        String dbtUniqueId,
        String status,
        int implementationRevision,
        String implementationChecksum,
        InputMode inputMode,
        List<ImplementationInput> inputs,
        List<FieldMapping> fieldMappings,
        Map<String, Object> settings,
        String materialization
    ) {}

    public record ArtifactView(
        UUID id,
        UUID modelSpecId,
        UUID planId,
        int revision,
        String modelChecksum,
        ImplementationMode ownership,
        String artifactType,
        String path,
        String checksum,
        String status,
        int implementationRevision,
        String nodeKind,
        String materialization,
        UUID physicalAssetRef
    ) {
        /** Legacy projection retained for artifact readers that do not yet need implementation input detail. */
        public ArtifactView(
            UUID id,
            UUID modelSpecId,
            UUID planId,
            int revision,
            String modelChecksum,
            ImplementationMode ownership,
            String artifactType,
            String path,
            String checksum,
            String status
        ) {
            this(id, modelSpecId, planId, revision, modelChecksum, ownership, artifactType, path, checksum, status, 1, artifactType, "table", null);
        }
    }

    public record ArtifactWrite(
        String artifactType,
        String path,
        String checksum,
        String content,
        String nodeKind,
        String materialization,
        UUID physicalAssetRef
    ) {
        /** Existing compiler output is a dbt node with the ModelSpec materialization. */
        public ArtifactWrite(String artifactType, String path, String checksum, String content) {
            this(artifactType, path, checksum, content, artifactType, null, null);
        }
    }

    public record LifecycleEventView(
        UUID id,
        UUID modelSpecId,
        UUID planId,
        int revision,
        String modelChecksum,
        EventType eventType,
        String status,
        String idempotencyKey,
        String actorId,
        String comment,
        String externalRef,
        Map<String, Object> details,
        Instant createdAt
    ) {}

    public record CompileView(
        ImplementationView implementation,
        LifecycleEventView event,
        List<ArtifactView> artifacts
    ) {}

    public record TimelineView(
        ImplementationView implementation,
        List<ArtifactView> artifacts,
        List<LifecycleEventView> events
    ) {}
}
