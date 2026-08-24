package com.yuzhi.dts.platform.service.modeling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.GeneratedInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.InputMode;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.PhysicalAssetInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.SaveImplementationCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.UpstreamModelInput;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.CreateModelSpecCommand;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.DimensionDefinitionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.DimensionProfile;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.FactShape;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.FieldRole;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.GenerationStrategy;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Grain;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelField;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelRevisionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ScdPolicy;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ScdType;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.SourceKind;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.SourceRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.SourceRole;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.StandardBinding;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.TimeSemantics;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.TimeSemanticsType;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.Column;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.PackageModel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Converts one validated package model into the exact commands consumed by F3 apply. */
@Component
public class ModelPackageCanonicalProjector {

    private static final Set<String> DATA_TYPES = Set.of(
        "varchar",
        "string",
        "text",
        "integer",
        "int",
        "bigint",
        "decimal",
        "numeric",
        "date",
        "timestamp",
        "boolean",
        "float",
        "double",
        "json"
    );

    private final ObjectMapper objectMapper;
    private final ModelSpecSnapshotCodec modelSpecCodec;
    private final ModelImplementationChecksumCodec implementationCodec;

    public ModelPackageCanonicalProjector(
        ObjectMapper objectMapper,
        ModelSpecSnapshotCodec modelSpecCodec,
        ModelImplementationChecksumCodec implementationCodec
    ) {
        this.objectMapper = objectMapper;
        this.modelSpecCodec = modelSpecCodec;
        this.implementationCodec = implementationCodec;
    }

    public Projection project(ProjectRequest request) {
        List<CanonicalIssue> issues = new ArrayList<>();
        PackageModel model = request.model();
        ModelType modelType = enumValue(ModelType.class, semantic(model, "modelType"), "MODEL_SPEC_TYPE_INVALID", "modelType", issues);
        Layer layer = enumValue(Layer.class, semantic(model, "layer"), "MODEL_SPEC_LAYER_INVALID", "layer", issues);
        List<ModelField> fields = fields(model, issues);
        List<ModelRevisionRef> dependsOn = new ArrayList<>();
        List<ModelRevisionRef> dimensionRefs = new ArrayList<>();
        List<DependencyTarget> dependencies = request.dependencies()
            .stream()
            .sorted(Comparator.comparing(DependencyTarget::dbtUniqueId))
            .toList();
        for (DependencyTarget dependency : dependencies) {
            if (dependency.modelSpecId() == null || dependency.revision() < 1 || dependency.modelType() == null) {
                issues.add(new CanonicalIssue("MODEL_SPEC_DEPENDENCY_INVALID", "dependsOn", "Dependency is not revision-pinned"));
                continue;
            }
            ModelRevisionRef ref = new ModelRevisionRef(dependency.modelSpecId(), dependency.revision());
            if (modelType == ModelType.FACT && dependency.modelType() == ModelType.DIMENSION) {
                dimensionRefs.add(ref);
            } else {
                dependsOn.add(ref);
            }
        }
        List<SourceRef> sourceRefs = modelType == ModelType.DIMENSION || modelType == ModelType.FACT
            ? sources(request.sources(), issues)
            : List.of();
        Grain grain = grain(model);
        FactShape factShape = enumValue(
            FactShape.class,
            model.semantics() == null ? null : model.semantics().factShape(),
            "MODEL_SPEC_FACT_SHAPE_INVALID",
            "factShape",
            issues
        );
        TimeSemantics timeSemantics = timeSemantics(model, issues);
        String consumptionScenario = modelType == ModelType.APPLICATION && model.semantics() != null
            ? safe(model.semantics().consumptionScenarios()).stream().findFirst().orElse(null)
            : null;
        DimensionDefinitionRef dimensionDefinitionRef = modelType == ModelType.DIMENSION
            ? request.dimensionDefinitionRef()
            : null;
        CreateModelSpecCommand modelCommand = new CreateModelSpecCommand(
            request.planId(),
            request.domainId(),
            modelType,
            layer,
            model.name(),
            model.description(),
            request.externalDbtOwned() ? ImplementationMode.DBT_MANAGED : ImplementationMode.DESIGNER_GENERATED,
            model.materialization(),
            null,
            consumptionScenario,
            grain,
            modelType == ModelType.FACT ? factShape : null,
            modelType == ModelType.FACT ? timeSemantics : null,
            fields,
            sourceRefs,
            dependsOn,
            dimensionRefs,
            List.of(),
            request.standardBindings(),
            generationStrategy(modelType, model),
            dimensionProfile(modelType, model),
            dimensionDefinitionRef,
            idempotencyKey(request),
            request.dataMartId(),
            null,
            null,
            null,
            request.businessProcessId(),
            request.subjectDomainId()
        );
        ModelSpecContract.validateDeliverableCreate(modelCommand)
            .forEach(issue -> issues.add(new CanonicalIssue(issue.code(), issue.field(), issue.message())));

        SaveImplementationCommand implementationCommand = implementation(request, modelCommand, dependencies, issues);
        String artifactChecksum = implementationCodec.artifactChecksum(
            model.sql() == null ? null : model.sql().effectiveSql()
        );
        if (request.externalDbtOwned() && artifactChecksum == null) {
            issues.add(
                new CanonicalIssue(
                    "MODEL_IMPORT_EFFECTIVE_SQL_REQUIRED",
                    "sql.effectiveSql",
                    "DBT_MANAGED external implementation requires effective SQL"
                )
            );
        }
        if (
            model.sql() != null &&
            model.sql().effectiveSqlChecksum() != null &&
            !model.sql().effectiveSqlChecksum().equalsIgnoreCase(artifactChecksum)
        ) {
            issues.add(
                new CanonicalIssue(
                    "MODEL_IMPORT_EFFECTIVE_SQL_CHECKSUM_MISMATCH",
                    "sql.effectiveSqlChecksum",
                    "effectiveSql checksum does not match the effective SQL payload"
                )
            );
        }
        String modelChecksum = issues.isEmpty() ? modelSpecCodec.contentChecksum(modelCommand) : null;
        String implementationChecksum = issues.isEmpty()
            ? implementationCodec.contentChecksum(implementationCommand)
            : null;
        return new Projection(
            modelCommand,
            implementationCommand,
            new ArtifactProjection(
                model.resourcePath(),
                model.sql() == null ? null : model.sql().effectiveSql(),
                artifactChecksum
            ),
            artifactChecksum,
            modelChecksum,
            implementationChecksum,
            objectMapper.valueToTree(modelCommand),
            objectMapper.valueToTree(
                Map.of(
                    "command",
                    implementationCommand,
                    "implementationChecksum",
                    implementationChecksum == null ? "" : implementationChecksum,
                    "effectiveSqlChecksum",
                    artifactChecksum == null ? "" : artifactChecksum
                )
            ),
            List.copyOf(issues)
        );
    }

    private SaveImplementationCommand implementation(
        ProjectRequest request,
        CreateModelSpecCommand modelCommand,
        List<DependencyTarget> dependencies,
        List<CanonicalIssue> issues
    ) {
        InputMode inputMode;
        List<ImplementationInput> inputs;
        try {
            if (request.externalDbtOwned()) {
                inputMode = InputMode.GENERATED;
                inputs = List.of(
                    new GeneratedInput(
                        "DBT",
                        Map.of(
                            "projectKey",
                            request.projectKey(),
                            "dbtUniqueId",
                            request.model().dbtUniqueId(),
                            "resourcePath",
                            request.model().resourcePath(),
                            "effectiveSqlChecksum",
                            java.util.Objects.requireNonNullElse(
                                implementationCodec.artifactChecksum(
                                    request.model().sql() == null ? null : request.model().sql().effectiveSql()
                                ),
                                ""
                            )
                        )
                    )
                );
            } else if (!dependencies.isEmpty()) {
                inputMode = InputMode.UPSTREAM_MODEL;
                inputs = dependencies
                    .stream()
                    .map(dependency ->
                        (ImplementationInput) new UpstreamModelInput(
                            dependency.modelSpecId(),
                            dependency.revision(),
                            dependency.modelChecksum(),
                            dependency.implementationRevision(),
                            dependency.implementationChecksum(),
                            dependency.dbtUniqueId()
                        )
                    )
                    .toList();
            } else {
                inputMode = InputMode.PHYSICAL_ASSET;
                inputs = request
                    .sources()
                    .stream()
                    .map(source -> (ImplementationInput) new PhysicalAssetInput(source.sourceBindingId(), source.resolvedVersion()))
                    .toList();
            }
            String materialization = safeMaterialization(request.model().materialization());
            Map<String, Object> settings = Map.of(
                "targetPhysicalName",
                targetPhysicalName(request.model()),
                "loadStrategy",
                "incremental".equals(materialization) ? "INCREMENTAL" : "FULL",
                "partitionFields",
                List.of()
            );
            SaveImplementationCommand command = new SaveImplementationCommand(
                inputMode,
                inputs,
                List.of(),
                settings,
                modelCommand.implementationMode(),
                materialization,
                idempotencyKey(request) + ":impl"
            );
            ModelImplementationExecutionPlanner.ValidationResult execution = ModelImplementationExecutionPlanner.plan(
                modelCommand
                    .fields()
                    .stream()
                    .filter(field -> field.role() == FieldRole.KEY)
                    .map(ModelField::name)
                    .toList(),
                settings,
                materialization,
                request.model().dbtUniqueId(),
                ModelImplementationExecutionPlanner.DEFAULT_ADAPTER
            );
            if (!execution.valid()) {
                ModelImplementationExecutionPlanner.Blocker blocker = execution.blockers().getFirst();
                issues.add(new CanonicalIssue(execution.code(), blocker.field(), blocker.message()));
            }
            return command;
        } catch (RuntimeException exception) {
            issues.add(
                new CanonicalIssue(
                    "MODEL_IMPORT_IMPLEMENTATION_INVALID",
                    "implementation",
                    exception.getMessage() == null ? "Implementation projection is invalid" : exception.getMessage()
                )
            );
            return new SaveImplementationCommand(
                InputMode.GENERATED,
                List.of(new GeneratedInput("INVALID_PREVIEW", Map.of("dbtUniqueId", request.model().dbtUniqueId()))),
                List.of(),
                Map.of(),
                modelCommand.implementationMode(),
                safeMaterialization(request.model().materialization()),
                idempotencyKey(request) + ":invalid"
            );
        }
    }

    private static List<ModelField> fields(PackageModel model, List<CanonicalIssue> issues) {
        List<ModelField> result = new ArrayList<>();
        for (Column column : model.columns() == null ? List.<Column>of() : model.columns()) {
            String dataType = normalizeDataType(column.dataType());
            if (dataType == null) {
                issues.add(new CanonicalIssue("MODEL_SPEC_FIELD_INVALID", "fields." + column.name(), "Unsupported field data type"));
            }
            FieldRole role = fieldRole(column, model, issues);
            boolean nullable = safe(column.tests()).stream().noneMatch(ModelPackageCanonicalProjector::isNotNullTest);
            result.add(
                new ModelField(
                    column.name(),
                    displayName(column),
                    dataType,
                    nullable,
                    null,
                    role,
                    null,
                    column.dimensionAttributeCode(),
                    false,
                    null
                )
            );
        }
        return List.copyOf(result);
    }

    private static boolean isNotNullTest(String test) {
        if (test == null) {
            return false;
        }
        String normalized = test.trim().toLowerCase(Locale.ROOT);
        return "not_null".equals(normalized) || normalized.startsWith("not_null_");
    }

    private static String displayName(Column column) {
        if (column.description() == null || column.description().isBlank()) {
            return column.name();
        }
        return column.description().trim();
    }

    private static FieldRole fieldRole(Column column, PackageModel model, List<CanonicalIssue> issues) {
        String raw = column.role();
        if ((raw == null || raw.isBlank()) && model.semantics() != null && model.semantics().fieldRoles() != null) {
            raw = model.semantics().fieldRoles().get(column.name());
        }
        if (raw == null) {
            issues.add(new CanonicalIssue("MODEL_SPEC_FIELD_INVALID", "fields." + column.name(), "Field role is required"));
            return null;
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "KEY", "BUSINESS_KEY" -> FieldRole.KEY;
            case "ATTRIBUTE", "DIMENSION" -> FieldRole.ATTRIBUTE;
            case "TIME", "DATE", "TIMESTAMP" -> FieldRole.TIME;
            case "MEASURE", "METRIC" -> FieldRole.MEASURE;
            default -> {
                issues.add(new CanonicalIssue("MODEL_SPEC_FIELD_INVALID", "fields." + column.name(), "Unsupported field role"));
                yield null;
            }
        };
    }

    private static List<SourceRef> sources(List<SourceTarget> sources, List<CanonicalIssue> issues) {
        List<SourceRef> result = new ArrayList<>();
        int sortOrder = 0;
        for (SourceTarget source : sources.stream().sorted(Comparator.comparing(item -> item.sourceBindingId().toString())).toList()) {
            SourceKind kind = enumValue(
                SourceKind.class,
                source.kind(),
                "MODEL_SPEC_SOURCE_INVALID",
                "sourceRefs",
                issues
            );
            Layer layer = enumValue(Layer.class, source.layer(), "MODEL_SPEC_SOURCE_INVALID", "sourceRefs", issues);
            result.add(
                new SourceRef(
                    kind,
                    source.ref(),
                    layer,
                    sortOrder == 0 ? SourceRole.PRIMARY : SourceRole.JOINED,
                    null,
                    null,
                    null,
                    sortOrder++,
                    source.sourceBindingId(),
                    source.resolvedVersion()
                )
            );
        }
        return List.copyOf(result);
    }

    private static Grain grain(PackageModel model) {
        if (model.semantics() == null || model.semantics().grain() == null) {
            return null;
        }
        return new Grain(model.semantics().grain().statement(), model.semantics().grain().keys());
    }

    private static TimeSemantics timeSemantics(PackageModel model, List<CanonicalIssue> issues) {
        if (model.semantics() == null || model.semantics().timeSemantics() == null) {
            return null;
        }
        TimeSemanticsType type = enumValue(
            TimeSemanticsType.class,
            model.semantics().timeSemantics().type(),
            "MODEL_SPEC_TIME_SEMANTICS_INVALID",
            "timeSemantics",
            issues
        );
        return new TimeSemantics(type, model.semantics().timeSemantics().fields());
    }

    private static GenerationStrategy generationStrategy(ModelType modelType, PackageModel model) {
        if (modelType != ModelType.DIMENSION || model.semantics() == null) {
            return null;
        }
        String strategy = model.semantics().dimensionStrategy();
        if (strategy == null || strategy.isBlank()) {
            return null;
        }
        return new GenerationStrategy(strategy, model.dbtUniqueId());
    }

    private static DimensionProfile dimensionProfile(ModelType modelType, PackageModel model) {
        if (modelType != ModelType.DIMENSION || model.semantics() == null) {
            return null;
        }
        String strategy = model.semantics().dimensionStrategy();
        if (strategy == null || strategy.isBlank()) {
            return null;
        }
        String normalized = strategy.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        ScdType scdType = normalized.contains("TYPE_2") || normalized.contains("TYPE2") ? ScdType.TYPE2 : ScdType.TYPE1;
        return new DimensionProfile(null, List.of(), new ScdPolicy(scdType, null, null, null), null);
    }

    private static String semantic(PackageModel model, String name) {
        if (model.semantics() == null) {
            return null;
        }
        return "modelType".equals(name) ? model.semantics().modelType() : model.semantics().layer();
    }

    private static <T extends Enum<T>> T enumValue(
        Class<T> type,
        String value,
        String code,
        String field,
        List<CanonicalIssue> issues
    ) {
        try {
            return value == null ? null : Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            issues.add(new CanonicalIssue(code, field, "Unsupported canonical value: " + value));
            return null;
        }
    }

    private static String idempotencyKey(ProjectRequest request) {
        UUID stable = UUID.nameUUIDFromBytes(
            (request.planId() + ":" + request.projectKey() + ":" + request.model().dbtUniqueId()).getBytes(StandardCharsets.UTF_8)
        );
        return "model-import:" + stable;
    }

    private static String lower(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeDataType(String value) {
        String normalized = lower(value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.replaceAll("\\s+", " ");
        if (normalized.startsWith("timestamp ")) {
            normalized = "timestamp";
        } else if (normalized.startsWith("character varying") || normalized.startsWith("varchar(")) {
            normalized = "varchar";
        } else if (normalized.startsWith("numeric(") || normalized.startsWith("decimal(")) {
            normalized = "numeric";
        } else if ("double precision".equals(normalized)) {
            normalized = "double";
        }
        if (!DATA_TYPES.contains(normalized)) {
            return null;
        }
        return switch (normalized) {
            case "string", "text" -> "varchar";
            case "int" -> "integer";
            case "numeric" -> "decimal";
            default -> normalized;
        };
    }

    private static String safeMaterialization(String value) {
        String normalized = lower(value);
        return normalized != null && Set.of("table", "view", "incremental").contains(normalized) ? normalized : "table";
    }

    private static String targetPhysicalName(PackageModel model) {
        Object configuredAlias = model.config() == null ? null : model.config().get("alias");
        String alias = configuredAlias instanceof String value ? value.trim() : "";
        return alias.isEmpty() ? java.util.Objects.toString(model.name(), "").trim() : alias;
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    public record ProjectRequest(
        UUID planId,
        UUID domainId,
        String projectKey,
        PackageModel model,
        boolean externalDbtOwned,
        List<SourceTarget> sources,
        List<DependencyTarget> dependencies,
        DimensionDefinitionRef dimensionDefinitionRef,
        List<StandardBinding> standardBindings,
        UUID businessProcessId,
        UUID dataMartId,
        UUID subjectDomainId
    ) {
        public ProjectRequest {
            sources = sources == null ? List.of() : List.copyOf(sources);
            dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
            standardBindings = standardBindings == null ? List.of() : List.copyOf(standardBindings);
        }

        /** Compatibility constructor for callers created before stable business-context overrides. */
        public ProjectRequest(
            UUID planId,
            UUID domainId,
            String projectKey,
            PackageModel model,
            boolean externalDbtOwned,
            List<SourceTarget> sources,
            List<DependencyTarget> dependencies,
            DimensionDefinitionRef dimensionDefinitionRef,
            List<StandardBinding> standardBindings
        ) {
            this(
                planId,
                domainId,
                projectKey,
                model,
                externalDbtOwned,
                sources,
                dependencies,
                dimensionDefinitionRef,
                standardBindings,
                null,
                null,
                null
            );
        }

        /** Compatibility constructor for canonical callers without target-environment semantic overrides. */
        public ProjectRequest(
            UUID planId,
            UUID domainId,
            String projectKey,
            PackageModel model,
            boolean externalDbtOwned,
            List<SourceTarget> sources,
            List<DependencyTarget> dependencies,
            DimensionDefinitionRef dimensionDefinitionRef
        ) {
            this(
                planId,
                domainId,
                projectKey,
                model,
                externalDbtOwned,
                sources,
                dependencies,
                dimensionDefinitionRef,
                List.of(),
                null,
                null,
                null
            );
        }
    }

    public record SourceTarget(UUID sourceBindingId, String kind, String ref, String layer, String resolvedVersion) {}

    public record DependencyTarget(
        String dbtUniqueId,
        UUID modelSpecId,
        int revision,
        String modelChecksum,
        int implementationRevision,
        String implementationChecksum,
        ModelType modelType
    ) {}

    public record CanonicalIssue(String code, String field, String message) {}

    /** Apply-only artifact payload; preview response projections expose its checksum, not SQL text. */
    public record ArtifactProjection(String resourcePath, String effectiveSql, String effectiveSqlChecksum) {}

    public record Projection(
        CreateModelSpecCommand modelSpecCommand,
        SaveImplementationCommand implementationCommand,
        ArtifactProjection artifact,
        String effectiveSqlChecksum,
        String modelSpecChecksum,
        String implementationChecksum,
        JsonNode modelSpecProjection,
        JsonNode implementationProjection,
        List<CanonicalIssue> issues
    ) {}
}
