package com.yuzhi.dts.platform.service.modeling;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Canonical, cross-industry contract for objectless model specifications. */
public final class ModelSpecContract {

    public static final int CONTRACT_VERSION = 2;

    public static final Set<String> CREATE_FIELDS = Set.of(
        "planId",
        "domainId",
        "modelType",
        "layer",
        "name",
        "description",
        "implementationMode",
        "materialization",
        "businessActivityRef",
        "consumptionScenario",
        "grain",
        "factShape",
        "timeSemantics",
        "fields",
        "sourceRefs",
        "dependsOn",
        "dimensionRefs",
        "metricRefs",
        "standardBindings",
        "generationStrategy",
        "dimensionProfile",
        "dimensionDefinitionRef",
        "idempotencyKey",
        "dataMartId",
        "variantCode",
        "implementationPolicy",
        "warehouseLayerCode"
    );

    public static final Set<String> INTERACTIVE_CREATE_FIELDS = Set.of(
        "planId",
        "domainId",
        "modelType",
        "name",
        "description",
        "dimensionDefinitionRef",
        "idempotencyKey",
        "dataMartId",
        "variantCode",
        "warehouseLayerCode"
    );

    public static final Set<String> UPDATE_FIELDS = Set.of(
        "planId",
        "domainId",
        "modelType",
        "layer",
        "name",
        "description",
        "implementationMode",
        "materialization",
        "businessActivityRef",
        "consumptionScenario",
        "grain",
        "factShape",
        "timeSemantics",
        "fields",
        "sourceRefs",
        "dependsOn",
        "dimensionRefs",
        "metricRefs",
        "standardBindings",
        "generationStrategy",
        "dimensionProfile",
        "dataMartId",
        "variantCode",
        "warehouseLayerCode"
    );

    public static final Map<String, String> REQUIRED_FIELD_CODES = Map.of(
        "planId",
        "MODEL_SPEC_PLAN_REQUIRED",
        "domainId",
        "MODEL_SPEC_DOMAIN_REQUIRED",
        "modelType",
        "MODEL_SPEC_TYPE_REQUIRED",
        "layer",
        "MODEL_SPEC_LAYER_REQUIRED",
        "name",
        "MODEL_SPEC_NAME_REQUIRED",
        "implementationMode",
        "MODEL_SPEC_IMPLEMENTATION_MODE_REQUIRED",
        "idempotencyKey",
        "MODEL_SPEC_IDEMPOTENCY_KEY_REQUIRED"
    );

    public static final Set<String> COLLECTION_FIELDS = Set.of(
        "fields",
        "sourceRefs",
        "dependsOn",
        "dimensionRefs",
        "metricRefs",
        "standardBindings"
    );

    private static final Map<String, Set<String>> NESTED_COLLECTION_FIELDS = Map.of(
        "fields",
        Set.of(
            "name",
            "displayName",
            "dataType",
            "nullable",
            "sourceFieldRef",
            "role",
            "securityLevel",
            "dimensionAttributeCode",
            "redundant",
            "redundancySourceRef"
        ),
        "sourceRefs",
        Set.of(
            "kind",
            "ref",
            "layer",
            "role",
            "alias",
            "joinType",
            "joinExpression",
            "sortOrder",
            "sourceBindingId",
            "resolvedVersion"
        ),
        "dependsOn",
        Set.of("modelSpecId", "revision"),
        "dimensionRefs",
        Set.of("modelSpecId", "revision"),
        "metricRefs",
        Set.of("metricId", "version"),
        "standardBindings",
        Set.of(
            "fieldName",
            "standardElementId",
            "standardElementVersion",
            "referenceCode",
            "referenceCodeVersion",
            "measurementUnitId",
            "measurementUnitVersion",
            "securityLevel"
        )
    );

    private static final Map<String, String> NESTED_COLLECTION_ISSUE_CODES = Map.of(
        "fields",
        "MODEL_SPEC_FIELD_INVALID",
        "sourceRefs",
        "MODEL_SPEC_SOURCE_INVALID",
        "dependsOn",
        "MODEL_SPEC_DEPENDENCY_INVALID",
        "dimensionRefs",
        "MODEL_SPEC_DIMENSION_REF_INVALID",
        "metricRefs",
        "MODEL_SPEC_METRIC_REF_INVALID",
        "standardBindings",
        "MODEL_SPEC_STANDARD_BINDING_INVALID"
    );

    private static final Set<String> LAYERS = enumNames(Layer.values());
    private static final Set<String> MODEL_TYPES = enumNames(ModelType.values());
    private static final Set<String> IMPLEMENTATION_MODES = enumNames(ImplementationMode.values());
    private static final Set<String> FACT_SHAPES = enumNames(FactShape.values());
    private static final Set<String> TIME_SEMANTICS_TYPES = enumNames(TimeSemanticsType.values());
    private static final Set<String> FIELD_ROLES = enumNames(FieldRole.values());
    private static final Set<String> SOURCE_KINDS = enumNames(SourceKind.values());
    private static final Set<String> SOURCE_ROLES = enumNames(SourceRole.values());
    private static final Set<String> JOIN_TYPES = enumNames(JoinType.values());
    private static final Set<String> INTERACTIVE_DRAFT_DEFERRED_ISSUE_CODES = Set.of(
        "MODEL_SPEC_GRAIN_REQUIRED",
        "MODEL_SPEC_DIMENSION_KEY_REQUIRED",
        "MODEL_SPEC_UPSTREAM_REQUIRED",
        "MODEL_SPEC_CONSUMPTION_SCENARIO_REQUIRED"
    );
    private static final Set<String> GRAIN_FIELDS = Set.of("statement", "keys");
    private static final Set<String> TIME_SEMANTICS_FIELDS = Set.of("type", "fields");
    private static final Set<String> GENERATION_STRATEGY_FIELDS = Set.of("type", "reference");
    private static final Set<String> DIMENSION_PROFILE_FIELDS = Set.of("hierarchies", "scdPolicy");
    private static final Set<String> DIMENSION_DEFINITION_REF_FIELDS = Set.of("dimensionDefinitionId", "revision");
    private static final Set<String> DIMENSION_HIERARCHY_FIELDS = Set.of("code", "name", "levels");
    private static final Set<String> DIMENSION_LEVEL_FIELDS = Set.of("fieldName", "order");
    private static final Set<String> SCD_POLICY_FIELDS = Set.of(
        "type",
        "effectiveFromField",
        "effectiveToField",
        "currentFlagField"
    );
    private static final Set<String> IMPLEMENTATION_POLICY_FIELDS = Set.of(
        "physicalName",
        "loadStrategy",
        "retentionDays",
        "partitionFields"
    );
    private static final Pattern DIMENSION_CODE = Pattern.compile("^[A-Z][A-Z0-9_]{0,63}$");
    private static final Pattern VARIANT_CODE = Pattern.compile("^[A-Z][A-Z0-9_]{0,31}$");
    private static final Pattern PHYSICAL_NAME = Pattern.compile("^[a-z][a-z0-9_]{0,62}$");

    private ModelSpecContract() {}

    public enum Layer {
        ODS,
        STG,
        DWD,
        DWS,
        ADS,
    }

    public enum ModelType {
        FACT,
        DIMENSION,
        SUMMARY,
        APPLICATION,
    }

    public static Layer targetLayer(ModelType modelType) {
        if (modelType == null) return null;
        return switch (modelType) {
            case DIMENSION, FACT -> Layer.DWD;
            case SUMMARY -> Layer.DWS;
            case APPLICATION -> Layer.ADS;
        };
    }

    static boolean matchesTargetLayer(ModelType modelType, Layer layer) {
        return modelType != null && layer != null && targetLayer(modelType) == layer;
    }

    static boolean isCanonicalModel(ModelSpecView view) {
        return (
            view != null &&
            view.contractVersion() == CONTRACT_VERSION &&
            view.compatibilityMode() == CompatibilityMode.CANONICAL &&
            matchesTargetLayer(view.modelType(), view.layer())
        );
    }

    static boolean isCanonicalDimension(ModelSpecView view) {
        return isCanonicalReferenceTarget(view) && view.modelType() == ModelType.DIMENSION && view.layer() == Layer.DWD;
    }

    static boolean allowsUpstreamModel(ModelType ownerType, ModelType upstreamType, Layer upstreamLayer) {
        if (ownerType == null || upstreamType == null || upstreamLayer == null) return false;
        if (!matchesTargetLayer(upstreamType, upstreamLayer)) return false;
        return switch (ownerType) {
            case DIMENSION -> false;
            case FACT -> upstreamType == ModelType.FACT;
            case SUMMARY -> upstreamType != ModelType.APPLICATION;
            case APPLICATION -> true;
        };
    }

    static boolean hasHistoricalTypeBoundaryViolation(ModelSpecView view) {
        if (!isCanonicalModel(view)) return true;
        return validateView(view)
            .stream()
            .map(FieldIssue::code)
            .anyMatch(code ->
                switch (code) {
                    case "MODEL_SPEC_TYPE_LAYER_MISMATCH",
                        "MODEL_SPEC_BUSINESS_ACTIVITY_NOT_ALLOWED",
                        "MODEL_SPEC_CONSUMPTION_SCENARIO_NOT_ALLOWED",
                        "MODEL_SPEC_DIMENSION_PROFILE_NOT_ALLOWED",
                        "MODEL_SPEC_INPUT_KIND_NOT_ALLOWED",
                        "MODEL_SPEC_UPSTREAM_LAYER_NOT_ALLOWED" -> true;
                    default -> false;
                }
            );
    }

    static boolean isCanonicalReferenceTarget(ModelSpecView view) {
        return (
            isCanonicalModel(view) &&
            view.status() != ModelStatus.ARCHIVED &&
            !hasHistoricalTypeBoundaryViolation(view)
        );
    }

    public enum ImplementationMode {
        DESIGNER_GENERATED,
        DBT_MANAGED,
    }

    public enum FactShape {
        TRANSACTION,
        PERIODIC_SNAPSHOT,
        ACCUMULATING_SNAPSHOT,
    }

    public enum TimeSemanticsType {
        EVENT_TIME,
        SNAPSHOT_DATE,
        PERIOD,
        MILESTONE_DATES,
    }

    public enum FieldRole {
        KEY,
        ATTRIBUTE,
        TIME,
        MEASURE,
    }

    public enum SourceKind {
        TABLE,
        DBT_MODEL,
        DATASET,
    }

    public enum SourceRole {
        PRIMARY,
        JOINED,
    }

    public enum JoinType {
        INNER,
        LEFT,
        RIGHT,
        FULL,
    }

    public enum ScdType {
        NONE,
        TYPE1,
        TYPE2,
    }

    public enum LoadStrategy {
        FULL,
        INCREMENTAL,
        SNAPSHOT,
    }

    public enum ReuseScope {
        PLAN,
        DOMAIN,
        TENANT,
    }

    public enum ModelStatus {
        DRAFT,
        DESIGNING,
        VALIDATING,
        READY_TO_PUBLISH,
        PUBLISHED,
        ARCHIVED,
    }

    public enum CompatibilityMode {
        CANONICAL,
        LEGACY_READONLY,
    }

    public enum IssueSeverity {
        ERROR,
        WARNING,
    }

    public record LegacySourceRef(String kind, String ref, String layer) {}

    public record LegacyStandardRef(
        String fieldName,
        String standardElementId,
        String referenceCode,
        String securityLevel
    ) {}

    /** Read-only preservation bucket; it is never accepted by create/update decoders. */
    public record LegacyRefs(
        String legacyModelRef,
        List<String> unresolvedDependencyRefs,
        List<LegacySourceRef> unresolvedSourceRefs,
        List<LegacyStandardRef> unresolvedStandardRefs
    ) {
        public LegacyRefs {
            legacyModelRef = trimToNull(legacyModelRef);
            unresolvedDependencyRefs = immutable(unresolvedDependencyRefs);
            unresolvedSourceRefs = immutable(unresolvedSourceRefs);
            unresolvedStandardRefs = immutable(unresolvedStandardRefs);
        }
    }

    public record Grain(String statement, List<String> keys) {
        public Grain {
            statement = trimToNull(statement);
            keys = immutable(keys);
        }
    }

    public record TimeSemantics(TimeSemanticsType type, List<String> fields) {
        public TimeSemantics {
            fields = immutable(fields);
        }
    }

    public record ModelField(
        String name,
        String displayName,
        String dataType,
        Boolean nullable,
        String sourceFieldRef,
        FieldRole role,
        String securityLevel,
        String dimensionAttributeCode,
        Boolean redundant,
        String redundancySourceRef
    ) {
        public ModelField(
            String name,
            String dataType,
            Boolean nullable,
            String sourceFieldRef,
            FieldRole role,
            String securityLevel,
            String dimensionAttributeCode,
            Boolean redundant,
            String redundancySourceRef
        ) {
            this(
                name,
                name,
                dataType,
                nullable,
                sourceFieldRef,
                role,
                securityLevel,
                dimensionAttributeCode,
                redundant,
                redundancySourceRef
            );
        }

        public ModelField(
            String name,
            String dataType,
            Boolean nullable,
            String sourceFieldRef,
            FieldRole role,
            String securityLevel
        ) {
            this(name, name, dataType, nullable, sourceFieldRef, role, securityLevel, null, false, null);
        }

        public ModelField {
            name = trimToNull(name);
            displayName = trimToNull(displayName);
            dataType = trimToNull(dataType);
            sourceFieldRef = trimToNull(sourceFieldRef);
            securityLevel = trimToNull(securityLevel);
            dimensionAttributeCode = trimToNull(dimensionAttributeCode);
            redundant = redundant == null ? Boolean.FALSE : redundant;
            redundancySourceRef = trimToNull(redundancySourceRef);
        }
    }

    public record SourceRef(
        SourceKind kind,
        String ref,
        Layer layer,
        SourceRole role,
        String alias,
        JoinType joinType,
        String joinExpression,
        Integer sortOrder,
        UUID sourceBindingId,
        String resolvedVersion
    ) {
        public SourceRef {
            ref = trimToNull(ref);
            alias = trimToNull(alias);
            joinExpression = trimToNull(joinExpression);
            resolvedVersion = trimToNull(resolvedVersion);
        }
    }

    public record ModelRevisionRef(UUID modelSpecId, int revision) {}

    public record MetricRef(String metricId, int version) {
        public MetricRef {
            metricId = trimToNull(metricId);
        }
    }

    public record StandardBinding(
        String fieldName,
        UUID standardElementId,
        Integer standardElementVersion,
        String referenceCode,
        Integer referenceCodeVersion,
        UUID measurementUnitId,
        Integer measurementUnitVersion,
        String securityLevel
    ) {
        public StandardBinding {
            fieldName = trimToNull(fieldName);
            referenceCode = trimToNull(referenceCode);
            securityLevel = trimToNull(securityLevel);
        }
    }

    public record GenerationStrategy(String type, String reference) {
        public GenerationStrategy {
            type = trimToNull(type);
            reference = trimToNull(reference);
        }
    }

    public record DimensionLevel(String fieldName, Integer order) {
        public DimensionLevel {
            fieldName = trimToNull(fieldName);
        }
    }

    public record DimensionHierarchy(String code, String name, List<DimensionLevel> levels) {
        public DimensionHierarchy {
            code = trimToNull(code);
            name = trimToNull(name);
            levels = immutable(levels);
        }
    }

    public record ScdPolicy(
        ScdType type,
        String effectiveFromField,
        String effectiveToField,
        String currentFlagField
    ) {
        public ScdPolicy {
            effectiveFromField = trimToNull(effectiveFromField);
            effectiveToField = trimToNull(effectiveToField);
            currentFlagField = trimToNull(currentFlagField);
        }
    }

    public record ImplementationPolicy(
        String physicalName,
        LoadStrategy loadStrategy,
        Integer retentionDays,
        List<String> partitionFields
    ) {
        public ImplementationPolicy {
            physicalName = trimToNull(physicalName);
            partitionFields = immutable(partitionFields);
        }
    }

    public record DimensionProfile(
        String dimensionCode,
        List<DimensionHierarchy> hierarchies,
        ScdPolicy scdPolicy,
        ReuseScope reuseScope
    ) {
        public DimensionProfile {
            dimensionCode = trimToNull(dimensionCode);
            hierarchies = immutable(hierarchies);
        }
    }

    public record DimensionDefinitionRef(UUID dimensionDefinitionId, int revision) {}

    public record CreateModelSpecCommand(
        UUID planId,
        UUID domainId,
        ModelType modelType,
        Layer layer,
        String name,
        String description,
        ImplementationMode implementationMode,
        String materialization,
        String businessActivityRef,
        String consumptionScenario,
        Grain grain,
        FactShape factShape,
        TimeSemantics timeSemantics,
        List<ModelField> fields,
        List<SourceRef> sourceRefs,
        List<ModelRevisionRef> dependsOn,
        List<ModelRevisionRef> dimensionRefs,
        List<MetricRef> metricRefs,
        List<StandardBinding> standardBindings,
        GenerationStrategy generationStrategy,
        @JsonInclude(JsonInclude.Include.NON_NULL) DimensionProfile dimensionProfile,
        @JsonInclude(JsonInclude.Include.NON_NULL) DimensionDefinitionRef dimensionDefinitionRef,
        String idempotencyKey,
        @JsonInclude(JsonInclude.Include.NON_NULL) UUID dataMartId,
        String variantCode,
        @JsonInclude(JsonInclude.Include.NON_NULL) ImplementationPolicy implementationPolicy,
        String warehouseLayerCode
    ) {
        /** Compatibility constructor for callers created before data-mart and implementation policy metadata. */
        public CreateModelSpecCommand(
            UUID planId,
            UUID domainId,
            ModelType modelType,
            Layer layer,
            String name,
            String description,
            ImplementationMode implementationMode,
            String materialization,
            String businessActivityRef,
            String consumptionScenario,
            Grain grain,
            FactShape factShape,
            TimeSemantics timeSemantics,
            List<ModelField> fields,
            List<SourceRef> sourceRefs,
            List<ModelRevisionRef> dependsOn,
            List<ModelRevisionRef> dimensionRefs,
            List<MetricRef> metricRefs,
            List<StandardBinding> standardBindings,
            GenerationStrategy generationStrategy,
            DimensionProfile dimensionProfile,
            DimensionDefinitionRef dimensionDefinitionRef,
            String idempotencyKey
        ) {
            this(
                planId, domainId, modelType, layer, name, description, implementationMode, materialization,
                businessActivityRef, consumptionScenario, grain, factShape, timeSemantics, fields, sourceRefs,
                dependsOn, dimensionRefs, metricRefs, standardBindings, generationStrategy, dimensionProfile,
                dimensionDefinitionRef, idempotencyKey, null, null, null, null
            );
        }

        /** Compatibility constructor for callers that predate the revision-pinned dimension definition reference. */
        public CreateModelSpecCommand(
            UUID planId,
            UUID domainId,
            ModelType modelType,
            Layer layer,
            String name,
            String description,
            ImplementationMode implementationMode,
            String materialization,
            String businessActivityRef,
            String consumptionScenario,
            Grain grain,
            FactShape factShape,
            TimeSemantics timeSemantics,
            List<ModelField> fields,
            List<SourceRef> sourceRefs,
            List<ModelRevisionRef> dependsOn,
            List<ModelRevisionRef> dimensionRefs,
            List<MetricRef> metricRefs,
            List<StandardBinding> standardBindings,
            GenerationStrategy generationStrategy,
            DimensionProfile dimensionProfile,
            String idempotencyKey
        ) {
            this(
                planId, domainId, modelType, layer, name, description, implementationMode, materialization,
                businessActivityRef, consumptionScenario, grain, factShape, timeSemantics, fields, sourceRefs,
                dependsOn, dimensionRefs, metricRefs, standardBindings, generationStrategy, dimensionProfile, null,
                idempotencyKey, null, null, null, null
            );
        }

        /** Compatibility constructor for callers that predate the optional Phase-B dimension profile. */
        public CreateModelSpecCommand(
            UUID planId,
            UUID domainId,
            ModelType modelType,
            Layer layer,
            String name,
            String description,
            ImplementationMode implementationMode,
            String materialization,
            String businessActivityRef,
            String consumptionScenario,
            Grain grain,
            FactShape factShape,
            TimeSemantics timeSemantics,
            List<ModelField> fields,
            List<SourceRef> sourceRefs,
            List<ModelRevisionRef> dependsOn,
            List<ModelRevisionRef> dimensionRefs,
            List<MetricRef> metricRefs,
            List<StandardBinding> standardBindings,
            GenerationStrategy generationStrategy,
            String idempotencyKey
        ) {
            this(
                planId,
                domainId,
                modelType,
                layer,
                name,
                description,
                implementationMode,
                materialization,
                businessActivityRef,
                consumptionScenario,
                grain,
                factShape,
                timeSemantics,
                fields,
                sourceRefs,
                dependsOn,
                dimensionRefs,
                metricRefs,
                standardBindings,
                generationStrategy,
                null,
                null,
                idempotencyKey,
                null,
                null,
                null,
                null
            );
        }

        public CreateModelSpecCommand {
            name = trimToNull(name);
            description = trimToNull(description);
            materialization = trimToNull(materialization);
            businessActivityRef = trimToNull(businessActivityRef);
            consumptionScenario = trimToNull(consumptionScenario);
            fields = immutable(fields);
            sourceRefs = immutable(sourceRefs);
            dependsOn = immutable(dependsOn);
            dimensionRefs = immutable(dimensionRefs);
            metricRefs = immutable(metricRefs);
            standardBindings = immutable(standardBindings);
            idempotencyKey = trimToNull(idempotencyKey);
            variantCode = trimToNull(variantCode);
            warehouseLayerCode = trimToNull(warehouseLayerCode == null ? (layer == null ? null : layer.name()) : warehouseLayerCode);
        }

        /** Returns a copy with the canonical execution layer and the persisted warehouse-layer selection fixed. */
        public CreateModelSpecCommand withLayerSelection(Layer canonicalLayer, String layerCode) {
            return new CreateModelSpecCommand(
                planId, domainId, modelType, canonicalLayer, name, description, implementationMode, materialization,
                businessActivityRef, consumptionScenario, grain, factShape, timeSemantics, fields, sourceRefs,
                dependsOn, dimensionRefs, metricRefs, standardBindings, generationStrategy, dimensionProfile,
                dimensionDefinitionRef, idempotencyKey, dataMartId, variantCode, implementationPolicy, layerCode
            );
        }
    }

    /** Full replacement payload for a CAS update. Server-managed and create-only fields are intentionally absent. */
    public record UpdateModelSpecCommand(
        UUID planId,
        UUID domainId,
        ModelType modelType,
        Layer layer,
        String name,
        String description,
        ImplementationMode implementationMode,
        String materialization,
        String businessActivityRef,
        String consumptionScenario,
        Grain grain,
        FactShape factShape,
        TimeSemantics timeSemantics,
        List<ModelField> fields,
        List<SourceRef> sourceRefs,
        List<ModelRevisionRef> dependsOn,
        List<ModelRevisionRef> dimensionRefs,
        List<MetricRef> metricRefs,
        List<StandardBinding> standardBindings,
        GenerationStrategy generationStrategy,
        @JsonInclude(JsonInclude.Include.NON_NULL) DimensionProfile dimensionProfile,
        @JsonInclude(JsonInclude.Include.NON_NULL) UUID dataMartId,
        String variantCode,
        @JsonInclude(JsonInclude.Include.NON_NULL) ImplementationPolicy implementationPolicy,
        String warehouseLayerCode
    ) {
        /** Compatibility constructor for callers created before data-mart and implementation policy metadata. */
        public UpdateModelSpecCommand(
            UUID planId,
            UUID domainId,
            ModelType modelType,
            Layer layer,
            String name,
            String description,
            ImplementationMode implementationMode,
            String materialization,
            String businessActivityRef,
            String consumptionScenario,
            Grain grain,
            FactShape factShape,
            TimeSemantics timeSemantics,
            List<ModelField> fields,
            List<SourceRef> sourceRefs,
            List<ModelRevisionRef> dependsOn,
            List<ModelRevisionRef> dimensionRefs,
            List<MetricRef> metricRefs,
            List<StandardBinding> standardBindings,
            GenerationStrategy generationStrategy,
            DimensionProfile dimensionProfile
        ) {
            this(
                planId, domainId, modelType, layer, name, description, implementationMode, materialization,
                businessActivityRef, consumptionScenario, grain, factShape, timeSemantics, fields, sourceRefs,
                dependsOn, dimensionRefs, metricRefs, standardBindings, generationStrategy, dimensionProfile,
                null, null, null, null
            );
        }

        /** Compatibility constructor for callers that predate the optional Phase-B dimension profile. */
        public UpdateModelSpecCommand(
            UUID planId,
            UUID domainId,
            ModelType modelType,
            Layer layer,
            String name,
            String description,
            ImplementationMode implementationMode,
            String materialization,
            String businessActivityRef,
            String consumptionScenario,
            Grain grain,
            FactShape factShape,
            TimeSemantics timeSemantics,
            List<ModelField> fields,
            List<SourceRef> sourceRefs,
            List<ModelRevisionRef> dependsOn,
            List<ModelRevisionRef> dimensionRefs,
            List<MetricRef> metricRefs,
            List<StandardBinding> standardBindings,
            GenerationStrategy generationStrategy
        ) {
            this(
                planId,
                domainId,
                modelType,
                layer,
                name,
                description,
                implementationMode,
                materialization,
                businessActivityRef,
                consumptionScenario,
                grain,
                factShape,
                timeSemantics,
                fields,
                sourceRefs,
                dependsOn,
                dimensionRefs,
                metricRefs,
                standardBindings,
                generationStrategy,
                null,
                null,
                null,
                null,
                null
            );
        }

        public UpdateModelSpecCommand {
            name = trimToNull(name);
            description = trimToNull(description);
            materialization = trimToNull(materialization);
            businessActivityRef = trimToNull(businessActivityRef);
            consumptionScenario = trimToNull(consumptionScenario);
            fields = immutable(fields);
            sourceRefs = immutable(sourceRefs);
            dependsOn = immutable(dependsOn);
            dimensionRefs = immutable(dimensionRefs);
            metricRefs = immutable(metricRefs);
            standardBindings = immutable(standardBindings);
            variantCode = trimToNull(variantCode);
            warehouseLayerCode = trimToNull(warehouseLayerCode == null ? (layer == null ? null : layer.name()) : warehouseLayerCode);
        }

        /** Returns a copy with the canonical execution layer and the persisted warehouse-layer selection fixed. */
        public UpdateModelSpecCommand withLayerSelection(Layer canonicalLayer, String layerCode) {
            return new UpdateModelSpecCommand(
                planId, domainId, modelType, canonicalLayer, name, description, implementationMode, materialization,
                businessActivityRef, consumptionScenario, grain, factShape, timeSemantics, fields, sourceRefs,
                dependsOn, dimensionRefs, metricRefs, standardBindings, generationStrategy, dimensionProfile,
                dataMartId, variantCode, implementationPolicy, layerCode
            );
        }
    }

    public record ModelSpecView(
        int contractVersion,
        UUID id,
        UUID planId,
        UUID domainId,
        ModelType modelType,
        Layer layer,
        String name,
        String description,
        ImplementationMode implementationMode,
        String materialization,
        String businessActivityRef,
        String consumptionScenario,
        Grain grain,
        FactShape factShape,
        TimeSemantics timeSemantics,
        List<ModelField> fields,
        List<SourceRef> sourceRefs,
        List<ModelRevisionRef> dependsOn,
        List<ModelRevisionRef> dimensionRefs,
        List<MetricRef> metricRefs,
        List<StandardBinding> standardBindings,
        GenerationStrategy generationStrategy,
        @JsonInclude(JsonInclude.Include.NON_NULL) DimensionProfile dimensionProfile,
        @JsonInclude(JsonInclude.Include.NON_NULL) DimensionDefinitionRef dimensionDefinitionRef,
        ModelStatus status,
        int revision,
        String checksum,
        Instant createdAt,
        Instant updatedAt,
        CompatibilityMode compatibilityMode,
        LegacyRefs legacyRefs,
        @JsonInclude(JsonInclude.Include.NON_NULL) UUID dataMartId,
        String variantCode,
        @JsonInclude(JsonInclude.Include.NON_NULL) ImplementationPolicy implementationPolicy,
        String warehouseLayerCode
    ) {
        /** Compatibility constructor for snapshots created before data-mart and implementation policy metadata. */
        public ModelSpecView(
            int contractVersion,
            UUID id,
            UUID planId,
            UUID domainId,
            ModelType modelType,
            Layer layer,
            String name,
            String description,
            ImplementationMode implementationMode,
            String materialization,
            String businessActivityRef,
            String consumptionScenario,
            Grain grain,
            FactShape factShape,
            TimeSemantics timeSemantics,
            List<ModelField> fields,
            List<SourceRef> sourceRefs,
            List<ModelRevisionRef> dependsOn,
            List<ModelRevisionRef> dimensionRefs,
            List<MetricRef> metricRefs,
            List<StandardBinding> standardBindings,
            GenerationStrategy generationStrategy,
            DimensionProfile dimensionProfile,
            DimensionDefinitionRef dimensionDefinitionRef,
            ModelStatus status,
            int revision,
            String checksum,
            Instant createdAt,
            Instant updatedAt,
            CompatibilityMode compatibilityMode,
            LegacyRefs legacyRefs
        ) {
            this(
                contractVersion, id, planId, domainId, modelType, layer, name, description, implementationMode,
                materialization, businessActivityRef, consumptionScenario, grain, factShape, timeSemantics, fields,
                sourceRefs, dependsOn, dimensionRefs, metricRefs, standardBindings, generationStrategy, dimensionProfile,
                dimensionDefinitionRef, status, revision, checksum, createdAt, updatedAt, compatibilityMode, legacyRefs,
                null, null, null, null
            );
        }

        /** Compatibility constructor for callers and fixtures created before the dimension definition reference. */
        public ModelSpecView(
            int contractVersion,
            UUID id,
            UUID planId,
            UUID domainId,
            ModelType modelType,
            Layer layer,
            String name,
            String description,
            ImplementationMode implementationMode,
            String materialization,
            String businessActivityRef,
            String consumptionScenario,
            Grain grain,
            FactShape factShape,
            TimeSemantics timeSemantics,
            List<ModelField> fields,
            List<SourceRef> sourceRefs,
            List<ModelRevisionRef> dependsOn,
            List<ModelRevisionRef> dimensionRefs,
            List<MetricRef> metricRefs,
            List<StandardBinding> standardBindings,
            GenerationStrategy generationStrategy,
            DimensionProfile dimensionProfile,
            ModelStatus status,
            int revision,
            String checksum,
            Instant createdAt,
            Instant updatedAt,
            CompatibilityMode compatibilityMode,
            LegacyRefs legacyRefs
        ) {
            this(
                contractVersion, id, planId, domainId, modelType, layer, name, description, implementationMode,
                materialization, businessActivityRef, consumptionScenario, grain, factShape, timeSemantics, fields,
                sourceRefs, dependsOn, dimensionRefs, metricRefs, standardBindings, generationStrategy, dimensionProfile,
                null, status, revision, checksum, createdAt, updatedAt, compatibilityMode, legacyRefs, null, null, null, null
            );
        }

        /** Compatibility constructor for callers and fixtures created before Phase-B dimension metadata. */
        public ModelSpecView(
            int contractVersion,
            UUID id,
            UUID planId,
            UUID domainId,
            ModelType modelType,
            Layer layer,
            String name,
            String description,
            ImplementationMode implementationMode,
            String materialization,
            String businessActivityRef,
            String consumptionScenario,
            Grain grain,
            FactShape factShape,
            TimeSemantics timeSemantics,
            List<ModelField> fields,
            List<SourceRef> sourceRefs,
            List<ModelRevisionRef> dependsOn,
            List<ModelRevisionRef> dimensionRefs,
            List<MetricRef> metricRefs,
            List<StandardBinding> standardBindings,
            GenerationStrategy generationStrategy,
            ModelStatus status,
            int revision,
            String checksum,
            Instant createdAt,
            Instant updatedAt,
            CompatibilityMode compatibilityMode,
            LegacyRefs legacyRefs
        ) {
            this(
                contractVersion,
                id,
                planId,
                domainId,
                modelType,
                layer,
                name,
                description,
                implementationMode,
                materialization,
                businessActivityRef,
                consumptionScenario,
                grain,
                factShape,
                timeSemantics,
                fields,
                sourceRefs,
                dependsOn,
                dimensionRefs,
                metricRefs,
                standardBindings,
                generationStrategy,
                null,
                null,
                status,
                revision,
                checksum,
                createdAt,
                updatedAt,
                compatibilityMode,
                legacyRefs,
                null,
                null,
                null,
                null
            );
        }

        public ModelSpecView {
            warehouseLayerCode = trimToNull(
                warehouseLayerCode == null ? (layer == null ? null : layer.name()) : warehouseLayerCode
            );
        }
    }

    public record FieldIssue(String code, String field, IssueSeverity severity, String message) {}

    static List<FieldIssue> validateCreateFieldNames(Set<String> fieldNames) {
        if (fieldNames == null) return List.of(issue("MODEL_SPEC_REQUEST_INVALID", "$", "Request must be a JSON object"));
        return fieldNames.stream()
            .filter(field -> !CREATE_FIELDS.contains(field))
            .sorted()
            .map(field -> issue("MODEL_SPEC_FIELD_NOT_ALLOWED", field, "Field is not part of the canonical ModelSpec create contract"))
            .toList();
    }

    static List<FieldIssue> validateInteractiveCreateFieldNames(Set<String> fieldNames) {
        if (fieldNames == null) return List.of(issue("MODEL_SPEC_REQUEST_INVALID", "$", "Request must be a JSON object"));
        return fieldNames.stream()
            .filter(field -> !INTERACTIVE_CREATE_FIELDS.contains(field))
            .sorted()
            .map(field -> issue("MODEL_SPEC_FIELD_NOT_ALLOWED", field, "Field is not part of the minimal ModelSpec create contract"))
            .toList();
    }

    static List<FieldIssue> validateCreateShape(Map<String, ?> fields) {
        if (fields == null) return List.of(issue("MODEL_SPEC_REQUEST_INVALID", "$", "Request must be a JSON object"));
        List<FieldIssue> issues = new ArrayList<>(validateCreateFieldNames(fields.keySet()));
        COLLECTION_FIELDS.stream()
            .sorted()
            .filter(field -> fields.containsKey(field) && !(fields.get(field) instanceof List<?>))
            .map(field -> issue("MODEL_SPEC_COLLECTION_INVALID", field, "Collection field must be an array when present"))
            .forEach(issues::add);
        REQUIRED_FIELD_CODES.forEach((field, code) -> required(fields.get(field), field, code, issues));
        rawUuid(fields.get("planId"), "MODEL_SPEC_PLAN_INVALID", "planId", issues);
        rawUuid(fields.get("domainId"), "MODEL_SPEC_DOMAIN_INVALID", "domainId", issues);
        rawUuid(fields.get("dataMartId"), "MODEL_SPEC_DATA_MART_INVALID", "dataMartId", issues);
        rawEnum(fields.get("modelType"), MODEL_TYPES, "MODEL_SPEC_TYPE_INVALID", "modelType", issues);
        rawEnum(fields.get("layer"), LAYERS, "MODEL_SPEC_LAYER_INVALID", "layer", issues);
        rawText(fields.get("name"), false, "MODEL_SPEC_NAME_INVALID", "name", issues);
        rawEnum(
            fields.get("implementationMode"),
            IMPLEMENTATION_MODES,
            "MODEL_SPEC_IMPLEMENTATION_MODE_INVALID",
            "implementationMode",
            issues
        );
        rawText(fields.get("idempotencyKey"), false, "MODEL_SPEC_IDEMPOTENCY_KEY_INVALID", "idempotencyKey", issues);
        rawText(fields.get("variantCode"), true, "MODEL_SPEC_VARIANT_CODE_INVALID", "variantCode", issues);
        rawText(fields.get("description"), true, "MODEL_SPEC_FIELD_INVALID", "description", issues);
        rawText(fields.get("materialization"), true, "MODEL_SPEC_FIELD_INVALID", "materialization", issues);
        rawText(
            fields.get("businessActivityRef"),
            true,
            "MODEL_SPEC_BUSINESS_ACTIVITY_INVALID",
            "businessActivityRef",
            issues
        );
        rawText(
            fields.get("consumptionScenario"),
            true,
            "MODEL_SPEC_CONSUMPTION_SCENARIO_INVALID",
            "consumptionScenario",
            issues
        );
        rawEnum(fields.get("factShape"), FACT_SHAPES, "MODEL_SPEC_FACT_SHAPE_INVALID", "factShape", issues);

        if (invalidObject(fields.get("grain"), GRAIN_FIELDS, ModelSpecContract::invalidRawGrain)) {
            addIssueOnce(issues, "MODEL_SPEC_GRAIN_INVALID", "grain", "Grain requires a statement and non-empty keys");
        }
        if (invalidObject(fields.get("timeSemantics"), TIME_SEMANTICS_FIELDS, ModelSpecContract::invalidRawTimeSemantics)) {
            addIssueOnce(issues, "MODEL_SPEC_TIME_SEMANTICS_INVALID", "timeSemantics", "Time semantics are invalid");
        }
        if (
            invalidObject(
                fields.get("generationStrategy"),
                GENERATION_STRATEGY_FIELDS,
                ModelSpecContract::invalidRawGenerationStrategy
            )
        ) {
            addIssueOnce(
                issues,
                "MODEL_SPEC_GENERATION_STRATEGY_INVALID",
                "generationStrategy",
                "Generation strategy is invalid"
            );
        }
        if (
            invalidObject(
                fields.get("dimensionDefinitionRef"),
                DIMENSION_DEFINITION_REF_FIELDS,
                ModelSpecContract::invalidRawDimensionDefinitionRef
            )
        ) {
            addIssueOnce(
                issues,
                "MODEL_SPEC_DIMENSION_DEFINITION_INVALID",
                "dimensionDefinitionRef",
                "Dimension definition references require an id and positive revision"
            );
        }
        if (
            invalidObject(
                fields.get("dimensionProfile"),
                DIMENSION_PROFILE_FIELDS,
                ModelSpecContract::invalidRawDimensionProfile
            )
        ) {
            addIssueOnce(
                issues,
                "MODEL_SPEC_DIMENSION_PROFILE_INVALID",
                "dimensionProfile",
                "Dimension profile is invalid"
            );
        }
        if (
            invalidObject(
                fields.get("implementationPolicy"),
                IMPLEMENTATION_POLICY_FIELDS,
                ModelSpecContract::invalidRawImplementationPolicy
            )
        ) {
            addIssueOnce(
                issues,
                "MODEL_SPEC_IMPLEMENTATION_POLICY_INVALID",
                "implementationPolicy",
                "Implementation policy is invalid"
            );
        }

        rawCollection(fields, "fields", ModelSpecContract::invalidRawField, issues);
        rawCollection(fields, "sourceRefs", ModelSpecContract::invalidRawSource, issues);
        rawCollection(fields, "dependsOn", ModelSpecContract::invalidRawRevisionRef, issues);
        rawCollection(fields, "dimensionRefs", ModelSpecContract::invalidRawRevisionRef, issues);
        rawCollection(fields, "metricRefs", ModelSpecContract::invalidRawMetricRef, issues);
        rawCollection(fields, "standardBindings", ModelSpecContract::invalidRawStandardBinding, issues);
        return List.copyOf(issues);
    }

    static List<FieldIssue> validateUpdateShape(Map<String, ?> fields) {
        if (fields == null) return List.of(issue("MODEL_SPEC_REQUEST_INVALID", "$", "Request must be a JSON object"));
        List<FieldIssue> issues = fields
            .keySet()
            .stream()
            .filter(field -> !UPDATE_FIELDS.contains(field))
            .sorted()
            .map(field -> issue("MODEL_SPEC_FIELD_NOT_ALLOWED", field, "Field is not part of the canonical ModelSpec update contract"))
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        Map<String, Object> createShape = new LinkedHashMap<>();
        fields.forEach(createShape::put);
        createShape.put("idempotencyKey", "__model_spec_update__");
        validateCreateShape(createShape)
            .stream()
            .filter(issue -> !"MODEL_SPEC_FIELD_NOT_ALLOWED".equals(issue.code()))
            .forEach(issues::add);
        return List.copyOf(issues);
    }

    static List<FieldIssue> validateCreate(CreateModelSpecCommand command) {
        if (command == null) return List.of(issue("MODEL_SPEC_REQUEST_INVALID", "$", "ModelSpec create request is required"));
        List<FieldIssue> issues = new ArrayList<>();
        required(command.planId(), "planId", REQUIRED_FIELD_CODES.get("planId"), issues);
        required(command.domainId(), "domainId", REQUIRED_FIELD_CODES.get("domainId"), issues);
        required(command.modelType(), "modelType", REQUIRED_FIELD_CODES.get("modelType"), issues);
        required(command.layer(), "layer", REQUIRED_FIELD_CODES.get("layer"), issues);
        required(command.name(), "name", REQUIRED_FIELD_CODES.get("name"), issues);
        required(command.implementationMode(), "implementationMode", REQUIRED_FIELD_CODES.get("implementationMode"), issues);
        required(command.idempotencyKey(), "idempotencyKey", REQUIRED_FIELD_CODES.get("idempotencyKey"), issues);
        if (command.name() != null && command.name().length() > 256) {
            issues.add(issue("MODEL_SPEC_NAME_INVALID", "name", "Model name must not exceed 256 characters"));
        }
        if (command.idempotencyKey() != null && command.idempotencyKey().length() > 128) {
            issues.add(issue("MODEL_SPEC_IDEMPOTENCY_KEY_INVALID", "idempotencyKey", "Idempotency key must not exceed 128 characters"));
        }
        if (command.variantCode() != null && !VARIANT_CODE.matcher(command.variantCode()).matches()) {
            issues.add(
                issue(
                    "MODEL_SPEC_VARIANT_CODE_INVALID",
                    "variantCode",
                    "Variant code must use upper-case letters, digits and underscores"
                )
            );
        }
        if (command.businessActivityRef() != null && command.modelType() != ModelType.FACT) {
            issues.add(issue("MODEL_SPEC_BUSINESS_ACTIVITY_NOT_ALLOWED", "businessActivityRef", "Business activity is optional FACT context only"));
        }
        if (command.consumptionScenario() != null && command.modelType() != ModelType.APPLICATION) {
            issues.add(
                issue(
                    "MODEL_SPEC_CONSUMPTION_SCENARIO_NOT_ALLOWED",
                    "consumptionScenario",
                    "Consumption scenario belongs to APPLICATION models only"
                )
            );
        }
        validateNested(command, issues);
        validateImplementationPolicy(command.implementationPolicy(), command.fields(), issues);
        validateDimensionDefinitionRef(command, issues);
        validateTypeBoundary(command, issues);
        return List.copyOf(issues);
    }

    /**
     * The interactive create drawer persists only the initial draft identity. Logical design
     * completeness remains owned by update validation and the stage gates.
     */
    static List<FieldIssue> validateInteractiveCreate(CreateModelSpecCommand command) {
        return validateCreate(command)
            .stream()
            .filter(issue -> !INTERACTIVE_DRAFT_DEFERRED_ISSUE_CODES.contains(issue.code()))
            .toList();
    }

    static List<FieldIssue> validateUpdate(UpdateModelSpecCommand command) {
        if (command == null) return List.of(issue("MODEL_SPEC_REQUEST_INVALID", "$", "ModelSpec update request is required"));
        return validateCreate(asCreate(command))
            .stream()
            .filter(issue -> !"MODEL_SPEC_DIMENSION_DEFINITION_REQUIRED".equals(issue.code()))
            .toList();
    }

    public static List<FieldIssue> validateView(ModelSpecView view) {
        if (view == null) return List.of(issue("MODEL_SPEC_REQUEST_INVALID", "$", "ModelSpec view is required"));
        DimensionProfile viewProfile = view.dimensionProfile() == null
            ? null
            : new DimensionProfile(
                null,
                view.dimensionProfile().hierarchies(),
                view.dimensionProfile().scdPolicy(),
                null
            );
        return validateCreate(
            new CreateModelSpecCommand(
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
                viewProfile,
                view.dimensionDefinitionRef(),
                "__model_spec_view__",
                view.dataMartId(),
                view.variantCode(),
                view.implementationPolicy(),
                view.warehouseLayerCode()
            )
        );
    }

    static CreateModelSpecCommand asCreate(UpdateModelSpecCommand command) {
        return new CreateModelSpecCommand(
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
            null,
            "__model_spec_update__",
            command.dataMartId(),
            command.variantCode(),
            command.implementationPolicy(),
            command.warehouseLayerCode()
        );
    }

    private static void validateNested(CreateModelSpecCommand command, List<FieldIssue> issues) {
        validateNoNulls(command.fields(), "fields", "MODEL_SPEC_FIELD_INVALID", issues);
        validateNoNulls(command.sourceRefs(), "sourceRefs", "MODEL_SPEC_SOURCE_INVALID", issues);
        validateNoNulls(command.dependsOn(), "dependsOn", "MODEL_SPEC_DEPENDENCY_INVALID", issues);
        validateNoNulls(command.dimensionRefs(), "dimensionRefs", "MODEL_SPEC_DIMENSION_REF_INVALID", issues);
        validateNoNulls(command.metricRefs(), "metricRefs", "MODEL_SPEC_METRIC_REF_INVALID", issues);
        validateNoNulls(command.standardBindings(), "standardBindings", "MODEL_SPEC_STANDARD_BINDING_INVALID", issues);
        if (command.fields().size() > 500) {
            addIssueOnce(
                issues,
                "MODEL_SPEC_FIELD_LIMIT_EXCEEDED",
                "fields",
                "A ModelSpec can contain at most 500 fields"
            );
        }
        Set<String> fieldNames = new HashSet<>();
        for (ModelField field : command.fields()) {
            if (field == null) continue;
            if (
                field.name() == null ||
                field.dataType() == null ||
                field.nullable() == null ||
                field.role() == null ||
                !fieldNames.add(field.name())
            ) {
                issues.add(issue("MODEL_SPEC_FIELD_INVALID", "fields", "Fields require unique names, data types and roles"));
            }
            if (
                field != null &&
                Boolean.TRUE.equals(field.redundant()) &&
                (field.redundancySourceRef() == null || field.dimensionAttributeCode() == null)
            ) {
                addIssueOnce(
                    issues,
                    "MODEL_SPEC_REDUNDANCY_EVIDENCE_REQUIRED",
                    "fields",
                    "Redundant dimension fields require both a dimension attribute code and a source reference"
                );
            }
            if (field != null && !Boolean.TRUE.equals(field.redundant()) && field.redundancySourceRef() != null) {
                addIssueOnce(
                    issues,
                    "MODEL_SPEC_REDUNDANCY_EVIDENCE_NOT_ALLOWED",
                    "fields",
                    "Non-redundant fields cannot declare a redundancy source"
                );
            }
        }
        Set<String> sourceKeys = new HashSet<>();
        for (SourceRef source : command.sourceRefs()) {
            if (source == null) continue;
            String key = source.kind() + ":" + source.ref();
            if (
                source.kind() == null ||
                source.ref() == null ||
                source.layer() == null ||
                source.role() == null ||
                source.sortOrder() == null ||
                source.sortOrder() < 0 ||
                source.sourceBindingId() == null ||
                source.resolvedVersion() == null ||
                !sourceKeys.add(key)
            ) {
                issues.add(issue("MODEL_SPEC_SOURCE_INVALID", "sourceRefs", "Sources require unique kind/ref pairs and complete metadata"));
            }
        }
        validateRevisionRefs(command.dependsOn(), "dependsOn", "MODEL_SPEC_DEPENDENCY_INVALID", issues);
        validateRevisionRefs(command.dimensionRefs(), "dimensionRefs", "MODEL_SPEC_DIMENSION_REF_INVALID", issues);
        for (MetricRef metric : command.metricRefs()) {
            if (metric != null && (metric.metricId() == null || metric.version() < 1)) {
                issues.add(issue("MODEL_SPEC_METRIC_REF_INVALID", "metricRefs", "Metric references require an id and positive version"));
            }
        }
        Set<String> standardBindingFields = new HashSet<>();
        for (StandardBinding binding : command.standardBindings()) {
            if (
                binding != null &&
                (invalidStandardBinding(binding) ||
                    (binding.fieldName() != null && !standardBindingFields.add(binding.fieldName())))
            ) {
                addIssueOnce(
                    issues,
                    "MODEL_SPEC_STANDARD_BINDING_INVALID",
                    "standardBindings",
                    "Standard bindings require one unique binding per field and complete positive-version reference pairs"
                );
            }
            if (binding != null && binding.fieldName() != null && !fieldNames.contains(binding.fieldName())) {
                addIssueOnce(
                    issues,
                    "MODEL_SPEC_STANDARD_BINDING_INVALID",
                    "standardBindings",
                    "Standard bindings must reference declared fields"
                );
            }
        }
        if (command.generationStrategy() != null && command.generationStrategy().type() == null) {
            issues.add(
                issue(
                    "MODEL_SPEC_GENERATION_STRATEGY_INVALID",
                    "generationStrategy",
                    "Generation strategy requires a type"
                )
            );
        }
        validateDimensionProfile(command.dimensionProfile(), issues);
    }

    private static void validateDimensionDefinitionRef(CreateModelSpecCommand command, List<FieldIssue> issues) {
        DimensionDefinitionRef ref = command.dimensionDefinitionRef();
        if (command.modelType() == ModelType.DIMENSION) {
            if (ref == null) {
                issues.add(
                    issue(
                        "MODEL_SPEC_DIMENSION_DEFINITION_REQUIRED",
                        "dimensionDefinitionRef",
                        "DIMENSION models require a revision-pinned dimension definition"
                    )
                );
            } else if (ref.dimensionDefinitionId() == null || ref.revision() < 1) {
                issues.add(
                    issue(
                        "MODEL_SPEC_DIMENSION_DEFINITION_INVALID",
                        "dimensionDefinitionRef",
                        "Dimension definition references require an id and positive revision"
                    )
                );
            }
        } else if (ref != null) {
            issues.add(
                issue(
                    "MODEL_SPEC_DIMENSION_DEFINITION_NOT_ALLOWED",
                    "dimensionDefinitionRef",
                    "Dimension definition references belong to DIMENSION models only"
                )
            );
        }
    }

    private static void validateRevisionRefs(
        List<ModelRevisionRef> refs,
        String field,
        String code,
        List<FieldIssue> issues
    ) {
        for (ModelRevisionRef ref : refs) {
            if (ref != null && (ref.modelSpecId() == null || ref.revision() < 1)) {
                issues.add(issue(code, field, "Model references require an id and positive revision"));
            }
        }
    }

    private static boolean invalidStandardBinding(StandardBinding binding) {
        return (
            binding.fieldName() == null ||
            incompleteVersionedRef(binding.standardElementId(), binding.standardElementVersion()) ||
            incompleteVersionedRef(binding.referenceCode(), binding.referenceCodeVersion()) ||
            incompleteVersionedRef(binding.measurementUnitId(), binding.measurementUnitVersion()) ||
            (binding.standardElementId() == null &&
                binding.referenceCode() == null &&
                binding.measurementUnitId() == null &&
                binding.securityLevel() == null)
        );
    }

    private static boolean incompleteVersionedRef(Object reference, Integer version) {
        return reference == null ? version != null : version == null || version < 1;
    }

    private static void validateTypeBoundary(CreateModelSpecCommand command, List<FieldIssue> issues) {
        if (command.modelType() == null) return;
        if (command.layer() != null && !matchesTargetLayer(command.modelType(), command.layer())) {
            issues.add(
                issue(
                    "MODEL_SPEC_TYPE_LAYER_MISMATCH",
                    "layer",
                    "Four-table models use DIMENSION/FACT DWD, SUMMARY DWS and APPLICATION ADS; ODS/STG belong to ingestion"
                )
            );
        }
        boolean acceptsPhysicalSources = command.modelType() == ModelType.DIMENSION || command.modelType() == ModelType.FACT;
        boolean acceptsModelDependencies = command.modelType() != ModelType.DIMENSION;
        boolean acceptsGenerationStrategy = command.modelType() == ModelType.DIMENSION;
        if (!acceptsPhysicalSources && !command.sourceRefs().isEmpty()) {
            issues.add(
                issue(
                    "MODEL_SPEC_INPUT_KIND_NOT_ALLOWED",
                    "sourceRefs",
                    "SUMMARY and APPLICATION models must use revision-pinned upstream ModelSpecs"
                )
            );
        }
        if (!acceptsModelDependencies && !command.dependsOn().isEmpty()) {
            issues.add(
                issue(
                    "MODEL_SPEC_INPUT_KIND_NOT_ALLOWED",
                    "dependsOn",
                    "DIMENSION uses physical sources or a generation strategy instead of model dependencies"
                )
            );
        }
        if (!acceptsGenerationStrategy && command.generationStrategy() != null) {
            issues.add(
                issue(
                    "MODEL_SPEC_INPUT_KIND_NOT_ALLOWED",
                    "generationStrategy",
                    "Generation strategy belongs to DIMENSION models only"
                )
            );
        }
        boolean acceptsFactInputs = command.modelType() == ModelType.FACT;
        if (!acceptsFactInputs && !command.dimensionRefs().isEmpty()) {
            issues.add(
                issue(
                    "MODEL_SPEC_INPUT_KIND_NOT_ALLOWED",
                    "dimensionRefs",
                    "Dimension references belong to FACT models only"
                )
            );
        }
        if (!acceptsFactInputs && command.factShape() != null) {
            issues.add(
                issue(
                    "MODEL_SPEC_INPUT_KIND_NOT_ALLOWED",
                    "factShape",
                    "Fact shape belongs to FACT models only"
                )
            );
        }
        if (!acceptsFactInputs && command.timeSemantics() != null) {
            issues.add(
                issue(
                    "MODEL_SPEC_INPUT_KIND_NOT_ALLOWED",
                    "timeSemantics",
                    "Business time semantics belong to FACT models only"
                )
            );
        }
        if (
            acceptsPhysicalSources &&
            command
                .sourceRefs()
                .stream()
                .filter(java.util.Objects::nonNull)
                .map(SourceRef::layer)
                .filter(java.util.Objects::nonNull)
                .anyMatch(layer -> layer != Layer.ODS && layer != Layer.STG && layer != Layer.DWD)
        ) {
            issues.add(
                issue(
                    "MODEL_SPEC_UPSTREAM_LAYER_NOT_ALLOWED",
                    "sourceRefs",
                    "DWD dimensions and facts may only consume ODS, STG or DWD physical inputs"
                )
            );
        }
        switch (command.modelType()) {
            case DIMENSION -> {
                if (!hasGrain(command.grain())) {
                    issues.add(issue("MODEL_SPEC_GRAIN_REQUIRED", "grain", "Dimension requires a grain statement and keys"));
                }
                boolean hasKey = command.fields().stream().anyMatch(field -> field != null && field.role() == FieldRole.KEY);
                if (!hasKey) {
                    issues.add(issue("MODEL_SPEC_DIMENSION_KEY_REQUIRED", "fields", "Dimension requires at least one key field"));
                }
            }
            case FACT -> {
                if (command.dimensionProfile() != null) {
                    issues.add(issue("MODEL_SPEC_DIMENSION_PROFILE_NOT_ALLOWED", "dimensionProfile", "Dimension profile belongs to DIMENSION models only"));
                }
                if (!hasGrain(command.grain())) issues.add(issue("MODEL_SPEC_GRAIN_REQUIRED", "grain", "FACT requires a grain statement and keys"));
            }
            case SUMMARY, APPLICATION -> {
                if (command.dimensionProfile() != null) {
                    issues.add(issue("MODEL_SPEC_DIMENSION_PROFILE_NOT_ALLOWED", "dimensionProfile", "Dimension profile belongs to DIMENSION models only"));
                }
                if (command.dependsOn().isEmpty()) {
                    issues.add(issue("MODEL_SPEC_UPSTREAM_REQUIRED", "dependsOn", "Derived models require a revision-pinned upstream model"));
                }
                if (!hasGrain(command.grain())) {
                    issues.add(issue("MODEL_SPEC_GRAIN_REQUIRED", "grain", "Derived models require an output grain"));
                }
                if (command.modelType() == ModelType.APPLICATION && command.consumptionScenario() == null) {
                    issues.add(
                        issue(
                            "MODEL_SPEC_CONSUMPTION_SCENARIO_REQUIRED",
                            "consumptionScenario",
                            "APPLICATION requires a consumption scenario"
                        )
                    );
                }
            }
        }
    }

    private static boolean hasGrain(Grain grain) {
        return grain != null && grain.statement() != null && grain.keys().stream().anyMatch(ModelSpecContract::notBlank);
    }

    private static <T> void validateNoNulls(List<T> values, String field, String code, List<FieldIssue> issues) {
        if (values.stream().anyMatch(java.util.Objects::isNull)) {
            issues.add(issue(code, field, "Collection items must not be null"));
        }
    }

    private static void rawCollection(
        Map<String, ?> fields,
        String field,
        Predicate<Map<?, ?>> invalidItem,
        List<FieldIssue> issues
    ) {
        Object rawValue = fields.get(field);
        if (!(rawValue instanceof List<?> values)) return;
        Set<String> allowedFields = NESTED_COLLECTION_FIELDS.get(field);
        boolean invalid = values.stream().anyMatch(value -> {
            if (!(value instanceof Map<?, ?> item)) return true;
            return !containsOnly(item, allowedFields) || invalidItem.test(item);
        });
        if (invalid) {
            addIssueOnce(
                issues,
                NESTED_COLLECTION_ISSUE_CODES.get(field),
                field,
                "Collection item is outside the canonical contract"
            );
        }
    }

    private static boolean invalidObject(Object rawValue, Set<String> allowedFields, Predicate<Map<?, ?>> invalidValue) {
        if (rawValue == null) return false;
        if (!(rawValue instanceof Map<?, ?> value)) return true;
        return !containsOnly(value, allowedFields) || invalidValue.test(value);
    }

    private static boolean containsOnly(Map<?, ?> value, Set<String> allowedFields) {
        return value.keySet().stream().allMatch(key -> key instanceof String field && allowedFields.contains(field));
    }

    private static boolean invalidRawGrain(Map<?, ?> grain) {
        return !isNonBlankText(grain.get("statement")) || !isNonEmptyTextList(grain.get("keys"));
    }

    private static boolean invalidRawTimeSemantics(Map<?, ?> timeSemantics) {
        return !isEnumText(timeSemantics.get("type"), TIME_SEMANTICS_TYPES) || !isNonEmptyTextList(timeSemantics.get("fields"));
    }

    private static boolean invalidRawGenerationStrategy(Map<?, ?> strategy) {
        return !isNonBlankText(strategy.get("type")) || !isNullableText(strategy.get("reference"));
    }

    private static boolean invalidRawImplementationPolicy(Map<?, ?> policy) {
        Object retentionDays = policy.get("retentionDays");
        Object partitionFields = policy.get("partitionFields");
        return (
            !isNullableText(policy.get("physicalName")) ||
            !isEnumText(policy.get("loadStrategy"), enumNames(LoadStrategy.values())) ||
            (retentionDays != null &&
                (!isNonNegativeInteger(retentionDays) || ((Number) retentionDays).longValue() > 36_000)) ||
            !(partitionFields instanceof List<?> values) ||
            values.stream().anyMatch(value -> !isNonBlankText(value))
        );
    }

    private static boolean invalidRawDimensionProfile(Map<?, ?> profile) {
        Object hierarchies = profile.get("hierarchies");
        if (!(hierarchies instanceof List<?> values)) return true;
        if (
            values.stream().anyMatch(value -> {
                if (!(value instanceof Map<?, ?> hierarchy) || !containsOnly(hierarchy, DIMENSION_HIERARCHY_FIELDS)) return true;
                if (!isNonBlankText(hierarchy.get("code")) || !isNonBlankText(hierarchy.get("name"))) return true;
                Object levels = hierarchy.get("levels");
                return !(levels instanceof List<?> levelValues) || levelValues.isEmpty() || levelValues.stream().anyMatch(level -> {
                    if (!(level instanceof Map<?, ?> item) || !containsOnly(item, DIMENSION_LEVEL_FIELDS)) return true;
                    return !isNonBlankText(item.get("fieldName")) || !isPositiveInteger(item.get("order"));
                });
            })
        ) return true;
        Object scdPolicy = profile.get("scdPolicy");
        if (!(scdPolicy instanceof Map<?, ?> policy) || !containsOnly(policy, SCD_POLICY_FIELDS)) return true;
        return (
            !isEnumText(policy.get("type"), enumNames(ScdType.values())) ||
            !isNullableText(policy.get("effectiveFromField")) ||
            !isNullableText(policy.get("effectiveToField")) ||
            !isNullableText(policy.get("currentFlagField"))
        );
    }

    private static boolean invalidRawDimensionDefinitionRef(Map<?, ?> ref) {
        return !isUuidText(ref.get("dimensionDefinitionId")) || !isPositiveInteger(ref.get("revision"));
    }

    private static void validateDimensionProfile(DimensionProfile profile, List<FieldIssue> issues) {
        if (profile == null) return;
        if (profile.dimensionCode() != null || profile.reuseScope() != null) {
            addIssueOnce(
                issues,
                "MODEL_SPEC_DIMENSION_PROFILE_INVALID",
                "dimensionProfile",
                "Dimension code and reuse scope are legacy read fields, not canonical write inputs"
            );
        }
        if (profile.scdPolicy() == null || profile.scdPolicy().type() == null) {
            addIssueOnce(issues, "MODEL_SPEC_DIMENSION_PROFILE_INVALID", "dimensionProfile", "Dimension profile requires an SCD policy");
        }
        Set<String> hierarchyCodes = new HashSet<>();
        for (DimensionHierarchy hierarchy : profile.hierarchies()) {
            if (
                hierarchy == null ||
                hierarchy.code() == null ||
                hierarchy.name() == null ||
                !hierarchyCodes.add(hierarchy.code()) ||
                hierarchy.levels().isEmpty()
            ) {
                addIssueOnce(issues, "MODEL_SPEC_DIMENSION_HIERARCHY_INVALID", "dimensionProfile", "Dimension hierarchies require unique codes and levels");
                continue;
            }
            Set<String> levelFields = new HashSet<>();
            Set<Integer> orders = new HashSet<>();
            for (DimensionLevel level : hierarchy.levels()) {
                if (
                    level == null ||
                    level.fieldName() == null ||
                    level.order() == null ||
                    level.order() < 1 ||
                    !levelFields.add(level.fieldName()) ||
                    !orders.add(level.order())
                ) {
                    addIssueOnce(issues, "MODEL_SPEC_DIMENSION_HIERARCHY_INVALID", "dimensionProfile", "Hierarchy levels require unique fields and positive order");
                }
            }
        }
        ScdPolicy policy = profile.scdPolicy();
        if (policy == null || policy.type() == null) return;
        boolean hasType2Fields = policy.effectiveFromField() != null || policy.effectiveToField() != null || policy.currentFlagField() != null;
        if (policy.type() != ScdType.TYPE2 && hasType2Fields) {
            addIssueOnce(issues, "MODEL_SPEC_DIMENSION_SCD_INVALID", "dimensionProfile", "TYPE2 field references are not allowed for NONE or TYPE1");
        }
    }

    private static void validateImplementationPolicy(
        ImplementationPolicy policy,
        List<ModelField> fields,
        List<FieldIssue> issues
    ) {
        if (policy == null) return;
        if (policy.loadStrategy() == null) {
            addIssueOnce(
                issues,
                "MODEL_SPEC_IMPLEMENTATION_POLICY_INVALID",
                "implementationPolicy.loadStrategy",
                "Implementation policy requires a load strategy"
            );
        }
        if (policy.physicalName() != null && !PHYSICAL_NAME.matcher(policy.physicalName()).matches()) {
            addIssueOnce(
                issues,
                "MODEL_SPEC_PHYSICAL_NAME_INVALID",
                "implementationPolicy.physicalName",
                "Physical name must use lower-case snake_case and contain at most 63 characters"
            );
        }
        if (policy.retentionDays() != null && (policy.retentionDays() < 0 || policy.retentionDays() > 36_000)) {
            addIssueOnce(
                issues,
                "MODEL_SPEC_RETENTION_INVALID",
                "implementationPolicy.retentionDays",
                "Retention days must be between 0 and 36000"
            );
        }
        Set<String> declaredFields = fields
            .stream()
            .filter(java.util.Objects::nonNull)
            .map(ModelField::name)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
        Set<String> partitionFields = new HashSet<>();
        for (String field : policy.partitionFields()) {
            if (field == null || !declaredFields.contains(field) || !partitionFields.add(field)) {
                addIssueOnce(
                    issues,
                    "MODEL_SPEC_PARTITION_FIELD_INVALID",
                    "implementationPolicy.partitionFields",
                    "Partition fields must be unique declared model fields"
                );
            }
        }
    }

    public static List<FieldIssue> validatePhysicalName(String physicalName) {
        if (physicalName == null || !PHYSICAL_NAME.matcher(physicalName).matches()) {
            return List.of(
                issue(
                    "MODEL_SPEC_PHYSICAL_NAME_INVALID",
                    "physicalName",
                    "Physical name must use lower-case snake_case and contain at most 63 characters"
                )
            );
        }
        return List.of();
    }

    private static boolean invalidRawField(Map<?, ?> field) {
        return (
            !isNonBlankText(field.get("name")) ||
            !isNullableText(field.get("displayName")) ||
            !isNonBlankText(field.get("dataType")) ||
            !(field.get("nullable") instanceof Boolean) ||
            !isEnumText(field.get("role"), FIELD_ROLES) ||
            !isNullableText(field.get("sourceFieldRef")) ||
            !isNullableText(field.get("securityLevel")) ||
            !isNullableText(field.get("dimensionAttributeCode")) ||
            !(field.get("redundant") == null || field.get("redundant") instanceof Boolean) ||
            !isNullableText(field.get("redundancySourceRef"))
        );
    }

    private static boolean invalidRawSource(Map<?, ?> source) {
        Object joinType = source.get("joinType");
        return (
            !isEnumText(source.get("kind"), SOURCE_KINDS) ||
            !isNonBlankText(source.get("ref")) ||
            !isEnumText(source.get("layer"), LAYERS) ||
            !isEnumText(source.get("role"), SOURCE_ROLES) ||
            (joinType != null && !isEnumText(joinType, JOIN_TYPES)) ||
            !isNullableText(source.get("alias")) ||
            !isNullableText(source.get("joinExpression")) ||
            !isNonNegativeInteger(source.get("sortOrder")) ||
            !isUuidText(source.get("sourceBindingId")) ||
            !isNonBlankText(source.get("resolvedVersion"))
        );
    }

    private static boolean invalidRawRevisionRef(Map<?, ?> ref) {
        return !isUuidText(ref.get("modelSpecId")) || !isPositiveInteger(ref.get("revision"));
    }

    private static boolean invalidRawMetricRef(Map<?, ?> ref) {
        return !isNonBlankText(ref.get("metricId")) || !isPositiveInteger(ref.get("version"));
    }

    private static boolean invalidRawStandardBinding(Map<?, ?> binding) {
        return (
            !isNonBlankText(binding.get("fieldName")) ||
            !isNullableUuidText(binding.get("standardElementId")) ||
            !isNullablePositiveInteger(binding.get("standardElementVersion")) ||
            !isNullableText(binding.get("referenceCode")) ||
            !isNullablePositiveInteger(binding.get("referenceCodeVersion")) ||
            !isNullableUuidText(binding.get("measurementUnitId")) ||
            !isNullablePositiveInteger(binding.get("measurementUnitVersion")) ||
            !isNullableText(binding.get("securityLevel"))
        );
    }

    private static void rawEnum(Object value, Set<String> allowed, String code, String field, List<FieldIssue> issues) {
        if (value != null && !isEnumText(value, allowed)) addIssueOnce(issues, code, field, "Value is not supported");
    }

    private static void rawUuid(Object value, String code, String field, List<FieldIssue> issues) {
        if (value instanceof String text && text.isBlank()) return;
        if (value != null && !isUuidText(value)) addIssueOnce(issues, code, field, "Value must be a UUID");
    }

    private static void rawText(Object value, boolean nullable, String code, String field, List<FieldIssue> issues) {
        if (value == null && nullable) return;
        if (value != null && !(value instanceof String)) addIssueOnce(issues, code, field, "Value must be text or null");
    }

    private static void addIssueOnce(List<FieldIssue> issues, String code, String field, String message) {
        if (issues.stream().noneMatch(candidate -> candidate.code().equals(code) && candidate.field().equals(field))) {
            issues.add(issue(code, field, message));
        }
    }

    private static boolean isEnumText(Object value, Set<String> allowed) {
        return value instanceof String text && allowed.contains(text);
    }

    private static boolean isNonBlankText(Object value) {
        return value instanceof String text && !text.isBlank();
    }

    private static boolean isNullableText(Object value) {
        return value == null || value instanceof String;
    }

    private static boolean isUuidText(Object value) {
        if (!(value instanceof String text)) return false;
        try {
            return UUID.fromString(text).toString().equalsIgnoreCase(text);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean isNullableUuidText(Object value) {
        return value == null || isUuidText(value);
    }

    private static boolean isNonEmptyTextList(Object value) {
        return value instanceof List<?> values && !values.isEmpty() && values.stream().allMatch(ModelSpecContract::isNonBlankText);
    }

    private static boolean isInteger(Object value) {
        if (!(value instanceof Number number)) return false;
        double doubleValue = number.doubleValue();
        return Double.isFinite(doubleValue) && doubleValue == Math.rint(doubleValue);
    }

    private static boolean isNonNegativeInteger(Object value) {
        return isInteger(value) && ((Number) value).doubleValue() >= 0 && ((Number) value).doubleValue() <= Integer.MAX_VALUE;
    }

    private static boolean isPositiveInteger(Object value) {
        return isInteger(value) && ((Number) value).doubleValue() >= 1 && ((Number) value).doubleValue() <= Integer.MAX_VALUE;
    }

    private static boolean isNullablePositiveInteger(Object value) {
        return value == null || isPositiveInteger(value);
    }

    private static <E extends Enum<E>> Set<String> enumNames(E[] values) {
        Set<String> names = new HashSet<>();
        for (E value : values) names.add(value.name());
        return Set.copyOf(names);
    }

    private static void required(Object value, String field, String code, List<FieldIssue> issues) {
        if (value == null || value instanceof String text && text.isBlank()) {
            issues.add(issue(code, field, "Required field is missing"));
        }
    }

    private static FieldIssue issue(String code, String field, String message) {
        return new FieldIssue(code, field, IssueSeverity.ERROR, message);
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(values));
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
