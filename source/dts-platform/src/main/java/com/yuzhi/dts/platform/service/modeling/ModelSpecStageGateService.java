package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.repository.modeling.ModelLifecycleRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository;
import com.yuzhi.dts.platform.repository.modeling.DimensionDefinitionRepository;
import com.yuzhi.dts.platform.repository.modeling.DimensionDefinitionRepository.StoredDimensionDefinition;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.StoredModelSpec;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.DimensionHierarchy;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.DimensionLevel;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.DimensionProfile;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.FactShape;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.FieldIssue;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.FieldRole;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelField;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelRevisionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ScdPolicy;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ScdType;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.SourceRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.StandardBinding;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.TimeSemanticsType;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.SaveImplementationCommand;
import com.yuzhi.dts.platform.service.modeling.ModelSpecStandardEvidencePort.StandardEvidence;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Evaluates revision-bound ModelSpec gates without mutating lifecycle state. */
@Service
public class ModelSpecStageGateService {

    private static final Pattern FIELD_CODE = Pattern.compile("^[a-z][a-z0-9_]{0,62}$");

    private final ModelSpecApplicationService modelSpecs;
    private final ModelSpecRepository repository;
    private final DimensionDefinitionRepository dimensionDefinitions;
    private final ModelSpecStandardEvidencePort standardEvidence;
    private final ModelLifecycleRepository lifecycle;
    private final ModelSpecSourceValidationPort sourceValidation;
    private final ModelSpecDomainReadAccessPort domainReadAccess;
    private final ModelImplementationInputPolicy inputPolicy;
    private final ModelClassificationPublishGate classificationGate;
    private final ModelGovernancePolicyPort governancePolicy;

    public ModelSpecStageGateService(
        ModelSpecApplicationService modelSpecs,
        ModelSpecRepository repository,
        ModelSpecStandardEvidencePort standardEvidence
    ) {
        this(modelSpecs, repository, standardEvidence, null, null, null, null, null, null);
    }

    public ModelSpecStageGateService(
        ModelSpecApplicationService modelSpecs,
        ModelSpecRepository repository,
        ModelSpecStandardEvidencePort standardEvidence,
        ModelLifecycleRepository lifecycle,
        ModelSpecSourceValidationPort sourceValidation
    ) {
        this(modelSpecs, repository, standardEvidence, lifecycle, sourceValidation, null, null, null, null);
    }

    public ModelSpecStageGateService(
        ModelSpecApplicationService modelSpecs,
        ModelSpecRepository repository,
        ModelSpecStandardEvidencePort standardEvidence,
        ModelLifecycleRepository lifecycle,
        ModelSpecSourceValidationPort sourceValidation,
        DimensionDefinitionRepository dimensionDefinitions
    ) {
        this(modelSpecs, repository, standardEvidence, lifecycle, sourceValidation, dimensionDefinitions, null, null, null);
    }

    public ModelSpecStageGateService(
        ModelSpecApplicationService modelSpecs,
        ModelSpecRepository repository,
        ModelSpecStandardEvidencePort standardEvidence,
        ModelLifecycleRepository lifecycle,
        ModelSpecSourceValidationPort sourceValidation,
        DimensionDefinitionRepository dimensionDefinitions,
        ModelSpecDomainReadAccessPort domainReadAccess,
        ModelImplementationInputPolicy inputPolicy
    ) {
        this(
            modelSpecs,
            repository,
            standardEvidence,
            lifecycle,
            sourceValidation,
            dimensionDefinitions,
            domainReadAccess,
            inputPolicy,
            null
        );
    }

    public ModelSpecStageGateService(
        ModelSpecApplicationService modelSpecs,
        ModelSpecRepository repository,
        ModelSpecStandardEvidencePort standardEvidence,
        ModelLifecycleRepository lifecycle,
        ModelSpecSourceValidationPort sourceValidation,
        DimensionDefinitionRepository dimensionDefinitions,
        ModelSpecDomainReadAccessPort domainReadAccess,
        ModelImplementationInputPolicy inputPolicy,
        ModelClassificationPublishGate classificationGate
    ) {
        this(
            modelSpecs,
            repository,
            standardEvidence,
            lifecycle,
            sourceValidation,
            dimensionDefinitions,
            domainReadAccess,
            inputPolicy,
            classificationGate,
            null
        );
    }

    @Autowired
    public ModelSpecStageGateService(
        ModelSpecApplicationService modelSpecs,
        ModelSpecRepository repository,
        ModelSpecStandardEvidencePort standardEvidence,
        ModelLifecycleRepository lifecycle,
        ModelSpecSourceValidationPort sourceValidation,
        DimensionDefinitionRepository dimensionDefinitions,
        ModelSpecDomainReadAccessPort domainReadAccess,
        ModelImplementationInputPolicy inputPolicy,
        ModelClassificationPublishGate classificationGate,
        ModelGovernancePolicyPort governancePolicy
    ) {
        this.modelSpecs = modelSpecs;
        this.repository = repository;
        this.dimensionDefinitions = dimensionDefinitions;
        this.standardEvidence = standardEvidence;
        this.lifecycle = lifecycle;
        this.sourceValidation = sourceValidation;
        this.domainReadAccess = domainReadAccess;
        this.inputPolicy = inputPolicy;
        this.classificationGate = classificationGate;
        this.governancePolicy = governancePolicy;
    }

    @Transactional(readOnly = true)
    public List<GateView> evaluateAll(String tenantId, UUID modelSpecId) {
        ModelSpecView view = modelSpecs.get(tenantId, modelSpecId);
        GateEvidence evidence = evidence(tenantId, view);
        GateView release = withGovernancePolicy(
            tenantId,
            view,
            evidence,
            withImplementationInputEvidence(
                tenantId,
                view,
                withDimensionDefinitionEvidence(tenantId, view, evaluate(view, Stage.RELEASE_READY, evidence))
            )
        );
        return List.of(
            evaluate(view, Stage.DRAFT_SAVE, evidence),
            withDimensionDefinitionEvidence(tenantId, view, evaluate(view, Stage.DESIGNED, evidence)),
            withImplementationInputEvidence(
                tenantId,
                view,
                withDimensionDefinitionEvidence(tenantId, view, evaluate(view, Stage.IMPLEMENTATION_READY, evidence))
            ),
            withClassificationEvidence(
                tenantId,
                view,
                release
            )
        );
    }

    private GateView withGovernancePolicy(
        String tenantId,
        ModelSpecView view,
        GateEvidence evidence,
        GateView gate
    ) {
        if (gate.stage() != Stage.RELEASE_READY || gate.blockers().stream().anyMatch(blocker ->
            "MODEL_SPEC_GATE_EVIDENCE_STALE".equals(blocker.code())
        )) {
            return gate;
        }
        ModelGovernancePolicyPort.Policy policy = resolveGovernancePolicy();
        if (!policy.available()) {
            LinkedHashMap<String, GateBlocker> unavailable = blockersWithoutGovernanceEvidence(gate);
            add(
                unavailable,
                blocker(
                    view,
                    "MODEL_GOVERNANCE_POLICY_UNAVAILABLE",
                    "governancePolicy",
                    "平台全局模型发布治理策略不可用，请联系管理员检查系统初始化",
                    "standards"
                )
            );
            return gateWithBlockers(gate, unavailable);
        }

        LinkedHashMap<String, GateBlocker> blockers = blockersWithoutGovernanceEvidence(gate);
        applyStandardCoverage(tenantId, view, policy.standardCoverage(), blockers);
        // Governance data-quality evidence is resolved only after a physical candidate build.
        // The candidate control plane enforces that policy; this ModelSpec gate owns revision-bound engineering tests.
        return gateWithBlockers(gate, blockers);
    }

    private ModelGovernancePolicyPort.Policy resolveGovernancePolicy() {
        if (governancePolicy == null) {
            return ModelGovernancePolicyPort.Policy.available(
                ModelGovernancePolicyPort.StandardCoverage.ALL_FIELDS,
                ModelGovernancePolicyPort.QualityGate.BLOCKING
            );
        }
        try {
            ModelGovernancePolicyPort.Policy policy = governancePolicy.resolve();
            return policy == null
                ? ModelGovernancePolicyPort.Policy.unavailable("PLATFORM_MODEL_GOVERNANCE_POLICY_UNREADABLE")
                : policy;
        } catch (RuntimeException unavailable) {
            return ModelGovernancePolicyPort.Policy.unavailable("PLATFORM_MODEL_GOVERNANCE_POLICY_UNREADABLE");
        }
    }

    private void applyStandardCoverage(
        String tenantId,
        ModelSpecView view,
        ModelGovernancePolicyPort.StandardCoverage coverage,
        LinkedHashMap<String, GateBlocker> blockers
    ) {
        List<ModelField> required = standardRequiredFields(view, coverage);
        Map<String, StandardBinding> bindings = standardBindings(view);
        List<String> missing = required
            .stream()
            .filter(field -> !hasVersionedStandard(bindings.get(field.name())))
            .map(field -> notBlank(field.displayName()) ? field.displayName() + "（" + field.name() + "）" : field.name())
            .toList();
        if (!missing.isEmpty()) {
            add(
                blockers,
                blocker(
                    view,
                    "MODEL_SPEC_STANDARD_EVIDENCE_STALE",
                    "standardBindings",
                    "发布策略要求以下字段绑定标准：" + String.join("、", missing),
                    "standards"
                )
            );
            return;
        }
        if (bindings.values().stream().noneMatch(ModelSpecStageGateService::hasDeclaredStandard)) return;
        StandardEvidence ownerEvidence;
        try {
            ownerEvidence = standardEvidence.evaluate(tenantId, view);
        } catch (RuntimeException unavailable) {
            ownerEvidence = StandardEvidence.UNKNOWN;
        }
        EvidenceState state = switch (ownerEvidence == null ? StandardEvidence.UNKNOWN : ownerEvidence) {
            case CURRENT -> EvidenceState.CURRENT;
            case STALE -> EvidenceState.STALE;
            case UNKNOWN -> EvidenceState.UNKNOWN;
        };
        evidenceBlocker(
            view,
            state,
            "MODEL_SPEC_STANDARD_EVIDENCE",
            "字段标准的专业主数据证据不可用",
            "standards",
            blockers
        );
    }

    private static LinkedHashMap<String, GateBlocker> blockersWithoutGovernanceEvidence(GateView gate) {
        LinkedHashMap<String, GateBlocker> blockers = new LinkedHashMap<>();
        gate.blockers()
            .stream()
            .filter(blocker ->
                !blocker.code().startsWith("MODEL_SPEC_STANDARD_EVIDENCE") &&
                !blocker.code().startsWith("MODEL_SPEC_QUALITY_EVIDENCE") &&
                !"MODEL_GOVERNANCE_POLICY_UNAVAILABLE".equals(blocker.code())
            )
            .forEach(blocker -> add(blockers, blocker));
        return blockers;
    }

    private static GateView gateWithBlockers(GateView gate, LinkedHashMap<String, GateBlocker> blockers) {
        List<GateBlocker> result = List.copyOf(blockers.values());
        return new GateView(
            gate.modelSpecId(),
            gate.revision(),
            gate.checksum(),
            gate.stage(),
            result.isEmpty() ? GateStatus.READY : GateStatus.BLOCKED,
            result
        );
    }

    private GateView withClassificationEvidence(String tenantId, ModelSpecView view, GateView gate) {
        if (classificationGate == null || gate.stage() != Stage.RELEASE_READY) {
            return gate;
        }
        ModelClassificationPublishGate.Decision decision = classificationGate.evaluate(
            tenantId,
            view.id(),
            view.revision(),
            view.checksum()
        );
        if (decision.ready()) {
            return gate;
        }
        LinkedHashMap<String, GateBlocker> blockers = new LinkedHashMap<>();
        for (GateBlocker blocker : gate.blockers()) {
            add(blockers, blocker);
        }
        for (ModelClassificationPublishGate.Blocker blocker : decision.blockers()) {
            add(
                blockers,
                blocker(
                    view,
                    blocker.code(),
                    "classification",
                    blocker.message(),
                    "governance"
                )
            );
        }
        return new GateView(
            view.id(),
            view.revision(),
            view.checksum(),
            gate.stage(),
            GateStatus.BLOCKED,
            List.copyOf(blockers.values())
        );
    }

    private GateView withImplementationInputEvidence(String tenantId, ModelSpecView view, GateView gate) {
        if (gate.stage() == Stage.DRAFT_SAVE || gate.stage() == Stage.DESIGNED || inputPolicy == null) return gate;
        ImplementationView implementation = lifecycle == null ? null : lifecycle.findImplementation(tenantId, view.id()).orElse(null);
        String blockerCode = null;
        String blockerField = "implementation.inputs";
        String blockerMessage = "实现输入未通过当前来源与依赖校验";
        if (implementation == null) {
            blockerCode = "MODEL_IMPLEMENTATION_REQUIRED";
        } else {
            SaveImplementationCommand command = new SaveImplementationCommand(
                implementation.inputMode(),
                implementation.inputs(),
                implementation.fieldMappings(),
                implementation.settings(),
                implementation.ownership(),
                implementation.materialization(),
                "gate-input-evidence"
            );
            if (implementation.ownership() != ImplementationMode.DBT_MANAGED) {
                ModelImplementationInputPolicy.ValidationResult inputValidation =
                    inputPolicy.validate(
                    tenantId,
                    view,
                    command
                );
                if (!inputValidation.valid()) {
                    blockerCode = inputValidation.code();
                }
            }
            if (blockerCode == null) {
                ModelImplementationExecutionPlanner.ValidationResult execution =
                    ModelImplementationExecutionPlanner.plan(view, command, implementation.dbtUniqueId());
                if (!execution.valid()) {
                    ModelImplementationExecutionPlanner.Blocker blocker = execution.blockers().getFirst();
                    blockerCode = blocker.code();
                    blockerField = blocker.field();
                    blockerMessage = blocker.message();
                }
            }
        }
        if (blockerCode == null) return withoutLegacyInputBlockers(view, gate);
        LinkedHashMap<String, GateBlocker> blockers = new LinkedHashMap<>();
        for (GateBlocker blocker : withoutLegacyInputBlockers(view, gate).blockers()) add(blockers, blocker);
        add(
            blockers,
            blocker(
                view,
                blockerCode,
                blockerField,
                blockerMessage,
                "implementation"
            )
        );
        List<GateBlocker> values = List.copyOf(blockers.values());
        return new GateView(view.id(), view.revision(), view.checksum(), gate.stage(), GateStatus.BLOCKED, values);
    }

    private static GateView withoutLegacyInputBlockers(ModelSpecView view, GateView gate) {
        Set<String> obsoleteCodes = switch (view.modelType()) {
            case DIMENSION -> Set.of("MODEL_SPEC_DIMENSION_INPUT_REQUIRED", "MODEL_SPEC_SOURCE_EVIDENCE");
            case FACT -> Set.of("MODEL_SPEC_FACT_INPUT_REQUIRED", "MODEL_SPEC_SOURCE_EVIDENCE", "MODEL_SPEC_UPSTREAM_EVIDENCE");
            case SUMMARY, APPLICATION -> Set.of();
        };
        if (obsoleteCodes.isEmpty()) return gate;
        List<GateBlocker> blockers = gate.blockers().stream().filter(blocker -> !obsoleteCodes.contains(blocker.code())).toList();
        return new GateView(
            gate.modelSpecId(),
            gate.revision(),
            gate.checksum(),
            gate.stage(),
            blockers.isEmpty() ? GateStatus.READY : GateStatus.BLOCKED,
            blockers
        );
    }

    private GateView withDimensionDefinitionEvidence(String tenantId, ModelSpecView view, GateView gate) {
        if (view.modelType() != ModelType.DIMENSION) return gate;
        LinkedHashMap<String, GateBlocker> blockers = new LinkedHashMap<>();
        for (GateBlocker blocker : gate.blockers()) add(blockers, blocker);
        if (!dimensionDefinitionCurrent(tenantId, view)) {
            add(
                blockers,
                blocker(
                    view,
                    "DIMENSION_DEFINITION_NOT_CURRENT",
                    "dimensionDefinitionRef",
                    "锁定的业务维度定义已删除或退役",
                    "design"
                )
            );
        } else {
            addDimensionAttributeMappingBlockers(tenantId, view, blockers);
        }
        List<GateBlocker> result = List.copyOf(blockers.values());
        return new GateView(
            gate.modelSpecId(),
            gate.revision(),
            gate.checksum(),
            gate.stage(),
            result.isEmpty() ? GateStatus.READY : GateStatus.BLOCKED,
            result
        );
    }

    private void addDimensionAttributeMappingBlockers(
        String tenantId,
        ModelSpecView view,
        LinkedHashMap<String, GateBlocker> blockers
    ) {
        ModelSpecContract.DimensionDefinitionRef reference = view.dimensionDefinitionRef();
        if (dimensionDefinitions == null || reference == null) return;
        StoredDimensionDefinition definition = dimensionDefinitions
            .findRevision(tenantId, reference.dimensionDefinitionId(), reference.revision())
            .orElse(null);
        if (definition == null || definition.attributes().isEmpty()) return;
        Map<String, ModelField> fieldByAttribute = new HashMap<>();
        for (ModelField field : view.fields()) {
            if (field != null && field.dimensionAttributeCode() != null) {
                fieldByAttribute.putIfAbsent(field.dimensionAttributeCode(), field);
            }
        }
        List<String> missing = definition
            .attributes()
            .stream()
            .map(DimensionDefinitionContract.AttributeSemantic::code)
            .filter(Objects::nonNull)
            .filter(code -> !fieldByAttribute.containsKey(code))
            .toList();
        if (!missing.isEmpty()) {
            add(
                blockers,
                blocker(
                    view,
                    "DIMENSION_ATTRIBUTE_MAPPING_INCOMPLETE",
                    "fields",
                    "业务维度属性尚未全部映射到模型字段：" + String.join("、", missing),
                    "fields"
                )
            );
        }
        boolean primaryKeyMapped = definition
            .attributes()
            .stream()
            .filter(DimensionDefinitionContract.AttributeSemantic::primaryKey)
            .allMatch(attribute -> {
                ModelField field = fieldByAttribute.get(attribute.code());
                return field != null && field.role() == FieldRole.KEY;
            });
        if (!primaryKeyMapped) {
            add(
                blockers,
                blocker(
                    view,
                    "DIMENSION_PRIMARY_KEY_MAPPING_INVALID",
                    "fields",
                    "业务维度主键属性必须映射到作用为“键（KEY）”的模型字段",
                    "fields"
                )
            );
        }
    }

    private boolean dimensionDefinitionCurrent(String tenantId, ModelSpecView view) {
        if (dimensionDefinitions == null) return true;
        ModelSpecContract.DimensionDefinitionRef reference = view.dimensionDefinitionRef();
        if (reference == null) return false;
        try {
            StoredDimensionDefinition pinned = dimensionDefinitions
                .findRevision(tenantId, reference.dimensionDefinitionId(), reference.revision())
                .orElse(null);
            if (pinned == null || (domainReadAccess != null && !domainReadAccess.canRead(pinned.domainId()))) return false;
            StoredDimensionDefinition current = dimensionDefinitions
                .findCurrent(tenantId, reference.dimensionDefinitionId())
                .orElse(null);
            return current != null && current.status() == DimensionDefinitionContract.Status.CURRENT;
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    public static GateView evaluate(ModelSpecView view, Stage stage, GateEvidence evidence) {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(stage, "stage");
        GateEvidence effectiveEvidence = evidence == null ? GateEvidence.unknownFor(view) : evidence;
        LinkedHashMap<String, GateBlocker> blockers = new LinkedHashMap<>();
        if (
            view.contractVersion() != ModelSpecContract.CONTRACT_VERSION ||
            view.compatibilityMode() != ModelSpecContract.CompatibilityMode.CANONICAL
        ) {
            add(blockers, blocker(view, "MODEL_SPEC_CANONICAL_REQUIRED", "$", "历史模型需迁移后才能进入新门禁", "design"));
        } else {
            for (FieldIssue issue : ModelSpecContract.validateView(view)) {
                add(blockers, blocker(view, issue.code(), issue.field(), issue.message(), repairTab(issue.field())));
            }
        }
        if (stage != Stage.DRAFT_SAVE) designedBlockers(view, blockers);
        if (stage == Stage.IMPLEMENTATION_READY || stage == Stage.RELEASE_READY) {
            implementationBlockers(view, effectiveEvidence, blockers);
        }
        if (stage == Stage.RELEASE_READY) releaseBlockers(view, effectiveEvidence, blockers);
        List<GateBlocker> result = List.copyOf(blockers.values());
        return new GateView(
            view.id(),
            view.revision(),
            view.checksum(),
            stage,
            result.isEmpty() ? GateStatus.READY : GateStatus.BLOCKED,
            result
        );
    }

    private GateEvidence evidence(String tenantId, ModelSpecView view) {
        EvidenceState sources = currentSources(tenantId, view) ? EvidenceState.CURRENT : EvidenceState.STALE;
        EvidenceState dependencies = currentReferences(tenantId, view, view.dependsOn(), false)
            ? EvidenceState.CURRENT
            : EvidenceState.STALE;
        EvidenceState dimensions = currentReferences(tenantId, view, view.dimensionRefs(), true)
            ? EvidenceState.CURRENT
            : EvidenceState.STALE;
        EvidenceState standards = standardsComplete(view)
            ? switch (standardEvidence.evaluate(tenantId, view)) {
                case CURRENT -> EvidenceState.CURRENT;
                case STALE -> EvidenceState.STALE;
                case UNKNOWN -> EvidenceState.UNKNOWN;
            }
            : EvidenceState.STALE;
        EvidenceState permissions = permissionsComplete(view) ? EvidenceState.CURRENT : EvidenceState.STALE;
        ImplementationView implementation = lifecycle == null ? null : lifecycle.findImplementation(tenantId, view.id()).orElse(null);
        Set<String> artifactTypes = lifecycle == null || implementation == null
            ? Set.of()
            : lifecycle.currentArtifactTypes(tenantId, view.id(), implementation);
        boolean passedCompile = lifecycle != null && implementation != null && lifecycle.hasPassedEvidence(
            tenantId,
            view.id(),
            view.revision(),
            implementation.implementationRevision(),
            implementation.implementationChecksum(),
            ModelLifecycleContract.EventType.COMPILE
        );
        Set<String> requiredDbtArtifactTypes = Set.of("SQL", "SCHEMA");
        Set<String> supportedDbtArtifactTypes = Set.of("SQL", "SCHEMA", "CONFIG", "DEPENDENCY");
        boolean currentArtifacts = implementation != null && (
            implementation.ownership() == ImplementationMode.DBT_MANAGED
                ? artifactTypes.containsAll(requiredDbtArtifactTypes) && supportedDbtArtifactTypes.containsAll(artifactTypes)
                : artifactTypes.containsAll(Set.of("SQL", "SCHEMA", "TEST"))
        );
        EvidenceState build = currentArtifacts && passedCompile
            ? EvidenceState.CURRENT
            : EvidenceState.UNKNOWN;
        boolean passedTest = lifecycle != null && implementation != null
            ? lifecycle.hasPassedEvidence(
                tenantId, view.id(), view.revision(), implementation.implementationRevision(),
                implementation.implementationChecksum(), ModelLifecycleContract.EventType.TEST
            )
            : false;
        EvidenceState tests = passedTest
            ? EvidenceState.CURRENT
            : EvidenceState.UNKNOWN;
        return new GateEvidence(
            view.revision(),
            view.checksum(),
            sources,
            dependencies,
            dimensions,
            standards,
            tests,
            permissions,
            build,
            tests
        );
    }

    private boolean currentSources(String tenantId, ModelSpecView view) {
        if (view.sourceRefs().isEmpty()) return true;
        if (sourceValidation == null) return false;
        for (SourceRef source : view.sourceRefs()) {
            try {
                if (!sourceValidation.isCurrentBindingForGate(tenantId, view.planId(), source)) return false;
            } catch (RuntimeException unavailable) {
                return false;
            }
        }
        return true;
    }

    private boolean currentReferences(
        String tenantId,
        ModelSpecView owner,
        List<ModelRevisionRef> references,
        boolean dimensionOnly
    ) {
        for (ModelRevisionRef reference : references) {
            try {
                ModelSpecView target = modelSpecs.revision(tenantId, reference);
                StoredModelSpec current = repository.findCurrent(tenantId, reference.modelSpecId()).orElse(null);
                if (
                    target == null ||
                    current == null ||
                    !Objects.equals(target.id(), reference.modelSpecId()) ||
                    target.revision() != reference.revision() ||
                    current.revision() != reference.revision() ||
                    !ModelSpecContract.isCanonicalReferenceTarget(target)
                ) {
                    return false;
                }
                if (!Objects.equals(owner.planId(), target.planId()) && target.status() != ModelStatus.PUBLISHED) {
                    return false;
                }
                if (dimensionOnly) {
                    if (!ModelSpecContract.isCanonicalDimension(target)) return false;
                } else if (!ModelSpecContract.allowsUpstreamModel(owner.modelType(), target.modelType(), target.layer())) {
                    return false;
                }
            } catch (RuntimeException unavailable) {
                return false;
            }
        }
        return true;
    }

    private static void implementationBlockers(
        ModelSpecView view,
        GateEvidence evidence,
        LinkedHashMap<String, GateBlocker> blockers
    ) {
        validateImplementationKeyContract(view, blockers);
        switch (view.modelType()) {
            case DIMENSION -> dimensionInputBlockers(view, evidence, blockers);
            case FACT -> factInputBlockers(view, evidence, blockers);
            case SUMMARY -> {
                referenceEvidence(view, evidence.dependencies(), "MODEL_SPEC_UPSTREAM_EVIDENCE", "上游模型版本已变化", blockers);
            }
            case APPLICATION ->
                referenceEvidence(view, evidence.dependencies(), "MODEL_SPEC_UPSTREAM_EVIDENCE", "上游模型版本已变化", blockers);
        }
    }

    private static void validateImplementationKeyContract(
        ModelSpecView view,
        LinkedHashMap<String, GateBlocker> blockers
    ) {
        if (view.grain() == null) return;
        List<String> grainKeys = view.grain().keys();
        List<String> keyFields = view.fields().stream()
            .filter(Objects::nonNull)
            .filter(field -> field.role() == FieldRole.KEY)
            .map(ModelField::name)
            .toList();
        Set<String> fieldNames = view.fields().stream()
            .filter(Objects::nonNull)
            .map(ModelField::name)
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
        if (grainKeys.stream().anyMatch(key -> key == null || !FIELD_CODE.matcher(key).matches()) ||
            new HashSet<>(grainKeys).size() != grainKeys.size()) {
            add(blockers, blocker(view, "MODEL_IMPLEMENTATION_GRAIN_KEY_INVALID", "grain.keys", "粒度键必须是唯一有效的字段编码", "fields"));
        }
        if (keyFields.stream().anyMatch(key -> key == null || !FIELD_CODE.matcher(key).matches()) ||
            new HashSet<>(keyFields).size() != keyFields.size()) {
            add(blockers, blocker(view, "MODEL_IMPLEMENTATION_KEY_FIELD_INVALID", "fields", "KEY 字段必须是唯一有效的字段编码", "fields"));
        }
        if (!fieldNames.containsAll(grainKeys) || !fieldNames.containsAll(keyFields)) {
            add(blockers, blocker(view, "MODEL_IMPLEMENTATION_KEY_FIELD_UNKNOWN", "grain.keys", "粒度键和 KEY 字段必须引用已声明字段", "fields"));
        }
        if (!new java.util.LinkedHashSet<>(grainKeys).equals(new java.util.LinkedHashSet<>(keyFields))) {
            add(blockers, blocker(view, "MODEL_IMPLEMENTATION_GRAIN_KEY_MISMATCH", "grain.keys", "粒度键必须与 KEY 字段集合一致", "fields"));
        }
    }

    private static void designedBlockers(
        ModelSpecView view,
        LinkedHashMap<String, GateBlocker> blockers
    ) {
        validateKeyClosure(view, blockers);
        validateFieldNames(view, blockers);
        switch (view.modelType()) {
            case DIMENSION -> dimensionDesignBlockers(view, blockers);
            case FACT -> factDesignBlockers(view, blockers);
            case SUMMARY -> {
                boolean hasMeasure = view.fields().stream().anyMatch(field -> field != null && field.role() == FieldRole.MEASURE);
                if (!hasMeasure && view.metricRefs().isEmpty()) {
                    add(blockers, blocker(view, "MODEL_SPEC_SUMMARY_MEASURE_REQUIRED", "fields", "汇总表需声明汇总字段或指标引用", "fields"));
                }
            }
            case APPLICATION -> {
                if (view.fields().isEmpty()) {
                    add(blockers, blocker(view, "MODEL_SPEC_APPLICATION_OUTPUT_REQUIRED", "fields", "应用表需声明输出字段契约", "fields"));
                }
            }
        }
    }

    private static void dimensionDesignBlockers(
        ModelSpecView view,
        LinkedHashMap<String, GateBlocker> blockers
    ) {
        DimensionProfile profile = view.dimensionProfile();
        if (profile == null) {
            add(blockers, blocker(view, "MODEL_SPEC_DIMENSION_PROFILE_REQUIRED", "dimensionProfile", "请补齐维度编码、SCD 和复用范围", "design"));
        } else {
            validateHierarchyReferences(view, profile, blockers);
            validateScdFields(view, profile.scdPolicy(), blockers);
        }
        if (view.description() == null || view.description().isBlank()) {
            add(blockers, blocker(view, "MODEL_SPEC_DIMENSION_DEFINITION_REQUIRED", "description", "请补充维度定义", "design"));
        }
    }

    private static void dimensionInputBlockers(
        ModelSpecView view,
        GateEvidence evidence,
        LinkedHashMap<String, GateBlocker> blockers
    ) {
        if (view.sourceRefs().isEmpty() && view.generationStrategy() == null) {
            add(blockers, blocker(view, "MODEL_SPEC_DIMENSION_INPUT_REQUIRED", "sourceRefs", "请至少选择来源或生成策略", "design"));
        }
        if (!view.sourceRefs().isEmpty()) {
            referenceEvidence(view, evidence.sources(), "MODEL_SPEC_SOURCE_EVIDENCE", "维度来源已失效或版本漂移", blockers);
        }
    }

    private static void factDesignBlockers(
        ModelSpecView view,
        LinkedHashMap<String, GateBlocker> blockers
    ) {
        // Ordinary detail records need not represent an event or a time-based snapshot.
        // Snapshot-specific requirements apply only when the author explicitly selects that shape.
        if (view.timeSemantics() == null) {
            if (view.factShape() == FactShape.PERIODIC_SNAPSHOT || view.factShape() == FactShape.ACCUMULATING_SNAPSHOT) {
                add(blockers, blocker(view, "MODEL_SPEC_TIME_SEMANTICS_REQUIRED", "timeSemantics", "快照模型需声明对应的业务时间语义；普通明细可不配置", "design"));
            }
        } else {
            boolean compatible = view.factShape() == null ||
                switch (view.factShape()) {
                    case TRANSACTION -> view.timeSemantics().type() == TimeSemanticsType.EVENT_TIME;
                    case PERIODIC_SNAPSHOT ->
                        view.timeSemantics().type() == TimeSemanticsType.SNAPSHOT_DATE ||
                        view.timeSemantics().type() == TimeSemanticsType.PERIOD;
                    case ACCUMULATING_SNAPSHOT -> view.timeSemantics().type() == TimeSemanticsType.MILESTONE_DATES;
                };
            if (!compatible) {
                add(blockers, blocker(view, "MODEL_SPEC_FACT_TIME_SHAPE_MISMATCH", "timeSemantics", "业务时间与事实形态不匹配", "design"));
            }
            Map<String, FieldRole> roles = fieldRoles(view.fields());
            if (view.timeSemantics().fields().stream().anyMatch(field -> roles.get(field) != FieldRole.TIME)) {
                add(blockers, blocker(view, "MODEL_SPEC_TIME_FIELD_INVALID", "timeSemantics", "业务时间必须引用 TIME 字段", "fields"));
            }
        }
    }

    private static void factInputBlockers(
        ModelSpecView view,
        GateEvidence evidence,
        LinkedHashMap<String, GateBlocker> blockers
    ) {
        boolean hasDirectSources = !view.sourceRefs().isEmpty();
        boolean hasUpstreamModels = !view.dependsOn().isEmpty();
        if (!hasDirectSources && !hasUpstreamModels) {
            add(
                blockers,
                blocker(
                    view,
                    "MODEL_SPEC_FACT_INPUT_REQUIRED",
                    "sourceRefs",
                    "请至少选择已确认的上游输入来源或锁定上游模型",
                    "implementation"
                )
            );
        }
        if (hasDirectSources) {
            referenceEvidence(view, evidence.sources(), "MODEL_SPEC_SOURCE_EVIDENCE", "明细表来源已失效或版本漂移", blockers);
        }
        if (hasUpstreamModels) {
            referenceEvidence(view, evidence.dependencies(), "MODEL_SPEC_UPSTREAM_EVIDENCE", "上游模型版本已变化", blockers);
        }
        referenceEvidence(view, evidence.dimensions(), "MODEL_SPEC_DIMENSION_EVIDENCE", "维度引用已失效或版本漂移", blockers);
    }

    private static void validateFieldNames(
        ModelSpecView view,
        LinkedHashMap<String, GateBlocker> blockers
    ) {
        boolean legacyCode = view.fields().stream().anyMatch(field ->
            field != null && field.name() != null && !FIELD_CODE.matcher(field.name()).matches()
        );
        if (legacyCode) {
            add(blockers, blocker(view, "MODEL_SPEC_FIELD_CODE_INVALID", "fields", "字段技术编码必须使用小写英文、数字和下划线", "fields"));
        }
        boolean missingDisplayName = view.fields().stream().anyMatch(field ->
            field != null && (field.displayName() == null || field.displayName().isBlank())
        );
        if (missingDisplayName) {
            add(blockers, blocker(view, "MODEL_SPEC_FIELD_DISPLAY_NAME_REQUIRED", "fields", "请为每个字段填写业务名称", "fields"));
        }
    }

    private static void releaseBlockers(
        ModelSpecView view,
        GateEvidence evidence,
        LinkedHashMap<String, GateBlocker> blockers
    ) {
        if (evidence.revision() != view.revision() || !Objects.equals(evidence.checksum(), view.checksum())) {
            blockers.clear();
            add(blockers, blocker(view, "MODEL_SPEC_GATE_EVIDENCE_STALE", "$", "门禁证据不属于当前模型版本", "design"));
            return;
        }
        evidenceBlocker(view, evidence.standards(), "MODEL_SPEC_STANDARD_EVIDENCE", "字段标准未覆盖当前版本", "standards", blockers);
        evidenceBlocker(view, evidence.permissions(), "MODEL_SPEC_PERMISSION_EVIDENCE", "字段权限分级未完成", "fields", blockers);
        evidenceBlocker(view, evidence.build(), "MODEL_SPEC_BUILD_EVIDENCE", "当前版本尚无完整构建产物", "design", blockers);
        evidenceBlocker(view, evidence.tests(), "MODEL_SPEC_TEST_EVIDENCE", "当前版本尚无测试产物", "design", blockers);
    }

    private static void validateKeyClosure(ModelSpecView view, LinkedHashMap<String, GateBlocker> blockers) {
        if (view.grain() == null) return;
        Set<String> keys = new HashSet<>();
        Map<String, Long> keyFields = new HashMap<>();
        for (ModelField field : view.fields()) {
            if (field != null && field.role() == FieldRole.KEY && field.name() != null) {
                keyFields.merge(field.name(), 1L, Long::sum);
            }
        }
        boolean invalid = view.grain().keys().stream().anyMatch(key -> key == null || !keys.add(key) || keyFields.getOrDefault(key, 0L) != 1L);
        if (invalid) {
            add(blockers, blocker(view, "MODEL_SPEC_GRAIN_KEY_MAPPING_INVALID", "fields", "粒度键必须唯一命中一个 KEY 字段", "fields"));
        }
    }

    private static void validateHierarchyReferences(
        ModelSpecView view,
        DimensionProfile profile,
        LinkedHashMap<String, GateBlocker> blockers
    ) {
        Set<String> fields = fieldRoles(view.fields()).keySet();
        Map<String, Set<String>> graph = new HashMap<>();
        for (DimensionHierarchy hierarchy : profile.hierarchies()) {
            List<DimensionLevel> levels = hierarchy.levels().stream().sorted(java.util.Comparator.comparing(DimensionLevel::order)).toList();
            if (levels.stream().anyMatch(level -> !fields.contains(level.fieldName()))) {
                add(blockers, blocker(view, "MODEL_SPEC_DIMENSION_HIERARCHY_FIELD_INVALID", "dimensionProfile", "层级必须引用已声明字段", "fields"));
            }
            for (int index = 0; index + 1 < levels.size(); index++) {
                graph.computeIfAbsent(levels.get(index).fieldName(), ignored -> new HashSet<>()).add(levels.get(index + 1).fieldName());
            }
        }
        if (hasCycle(graph)) {
            add(blockers, blocker(view, "MODEL_SPEC_DIMENSION_HIERARCHY_CYCLE", "dimensionProfile", "维度层级存在循环关系", "design"));
        }
    }

    private static boolean hasCycle(Map<String, Set<String>> graph) {
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String node : graph.keySet()) if (hasCycle(node, graph, visiting, visited)) return true;
        return false;
    }

    private static boolean hasCycle(String node, Map<String, Set<String>> graph, Set<String> visiting, Set<String> visited) {
        if (visited.contains(node)) return false;
        if (!visiting.add(node)) return true;
        for (String next : graph.getOrDefault(node, Set.of())) if (hasCycle(next, graph, visiting, visited)) return true;
        visiting.remove(node);
        visited.add(node);
        return false;
    }

    private static void validateScdFields(
        ModelSpecView view,
        ScdPolicy policy,
        LinkedHashMap<String, GateBlocker> blockers
    ) {
        if (policy == null || policy.type() == null) {
            add(blockers, blocker(view, "MODEL_SPEC_DIMENSION_SCD_REQUIRED", "dimensionProfile", "请选择维度历史保留策略", "design"));
            return;
        }
        if (policy.type() != ScdType.TYPE2) return;
        Set<String> fields = fieldRoles(view.fields()).keySet();
        if (
            policy.effectiveFromField() == null ||
            policy.effectiveToField() == null ||
            policy.currentFlagField() == null ||
            !fields.contains(policy.effectiveFromField()) ||
            !fields.contains(policy.effectiveToField()) ||
            !fields.contains(policy.currentFlagField())
        ) {
            add(blockers, blocker(view, "MODEL_SPEC_DIMENSION_SCD_TYPE2_FIELDS_REQUIRED", "dimensionProfile", "TYPE2 需绑定生效起止和当前标志字段", "fields"));
        }
    }

    private static Map<String, FieldRole> fieldRoles(List<ModelField> fields) {
        Map<String, FieldRole> roles = new HashMap<>();
        for (ModelField field : fields) if (field != null && field.name() != null) roles.put(field.name(), field.role());
        return roles;
    }

    private static boolean standardsComplete(ModelSpecView view) {
        Map<String, StandardBinding> bindings = standardBindings(view);
        return !view.fields().isEmpty() &&
            view.fields().stream().allMatch(field -> field != null && hasVersionedStandard(bindings.get(field.name())));
    }

    private static Map<String, StandardBinding> standardBindings(ModelSpecView view) {
        Map<String, StandardBinding> bindings = new HashMap<>();
        for (StandardBinding binding : view.standardBindings()) {
            if (binding != null && binding.fieldName() != null) bindings.put(binding.fieldName(), binding);
        }
        return bindings;
    }

    private static List<ModelField> standardRequiredFields(
        ModelSpecView view,
        ModelGovernancePolicyPort.StandardCoverage coverage
    ) {
        if (coverage == ModelGovernancePolicyPort.StandardCoverage.NONE) {
            return List.of();
        }
        return view
            .fields()
            .stream()
            .filter(Objects::nonNull)
            .filter(field ->
                coverage == ModelGovernancePolicyPort.StandardCoverage.ALL_FIELDS ||
                field.role() == FieldRole.KEY ||
                field.role() == FieldRole.MEASURE
            )
            .toList();
    }

    private static boolean hasVersionedStandard(StandardBinding binding) {
        if (binding == null) return false;
        return (
            binding.standardElementId() != null && binding.standardElementVersion() != null
        ) || (
            notBlank(binding.referenceCode()) && binding.referenceCodeVersion() != null
        ) || (
            binding.measurementUnitId() != null && binding.measurementUnitVersion() != null
        );
    }

    private static boolean hasDeclaredStandard(StandardBinding binding) {
        return binding != null && (
            binding.standardElementId() != null ||
            notBlank(binding.referenceCode()) ||
            binding.measurementUnitId() != null
        );
    }

    private static boolean permissionsComplete(ModelSpecView view) {
        Map<String, StandardBinding> bindings = new HashMap<>();
        for (StandardBinding binding : view.standardBindings()) if (binding != null && binding.fieldName() != null) bindings.put(binding.fieldName(), binding);
        return !view.fields().isEmpty() &&
            view.fields().stream().allMatch(field -> {
                if (field == null) return false;
                StandardBinding binding = bindings.get(field.name());
                return notBlank(field.securityLevel()) || binding != null && notBlank(binding.securityLevel());
            });
    }

    private static void referenceEvidence(
        ModelSpecView view,
        EvidenceState state,
        String code,
        String message,
        LinkedHashMap<String, GateBlocker> blockers
    ) {
        evidenceBlocker(view, state, code, message, "design", blockers);
    }

    private static void evidenceBlocker(
        ModelSpecView view,
        EvidenceState state,
        String code,
        String message,
        String tab,
        LinkedHashMap<String, GateBlocker> blockers
    ) {
        if (state == EvidenceState.CURRENT) return;
        String suffix = state == EvidenceState.UNKNOWN ? "_UNKNOWN" : "_STALE";
        add(blockers, blocker(view, code + suffix, "$", message, tab));
    }

    private static GateBlocker blocker(ModelSpecView view, String code, String field, String message, String tab) {
        String query = "implementation".equals(tab) || "physical".equals(tab)
            ? "activeStage=" + tab
            : "activeStage=logical&tab=" + tab;
        return new GateBlocker(code, field, message, "/modeling/models/" + view.id() + "?" + query);
    }

    private static String repairTab(String field) {
        if ("fields".equals(field)) return "fields";
        if ("standardBindings".equals(field)) return "standards";
        if (field != null && field.startsWith("implementationPolicy")) return "design";
        return "design";
    }

    private static void add(LinkedHashMap<String, GateBlocker> blockers, GateBlocker blocker) {
        blockers.putIfAbsent(blocker.code() + "\u0000" + blocker.field(), blocker);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    public enum Stage {
        DRAFT_SAVE,
        DESIGNED,
        IMPLEMENTATION_READY,
        RELEASE_READY,
    }

    public enum GateStatus {
        READY,
        BLOCKED,
    }

    public enum EvidenceState {
        CURRENT,
        STALE,
        UNKNOWN,
    }

    public record GateEvidence(
        int revision,
        String checksum,
        EvidenceState sources,
        EvidenceState dependencies,
        EvidenceState dimensions,
        EvidenceState standards,
        EvidenceState quality,
        EvidenceState permissions,
        EvidenceState build,
        EvidenceState tests
    ) {
        public static GateEvidence currentFor(ModelSpecView view) {
            return new GateEvidence(
                view.revision(),
                view.checksum(),
                EvidenceState.CURRENT,
                EvidenceState.CURRENT,
                EvidenceState.CURRENT,
                EvidenceState.CURRENT,
                EvidenceState.CURRENT,
                EvidenceState.CURRENT,
                EvidenceState.CURRENT,
                EvidenceState.CURRENT
            );
        }

        public static GateEvidence unknownFor(ModelSpecView view) {
            return new GateEvidence(
                view.revision(),
                view.checksum(),
                EvidenceState.UNKNOWN,
                EvidenceState.UNKNOWN,
                EvidenceState.UNKNOWN,
                EvidenceState.UNKNOWN,
                EvidenceState.UNKNOWN,
                EvidenceState.UNKNOWN,
                EvidenceState.UNKNOWN,
                EvidenceState.UNKNOWN
            );
        }

        public GateEvidence withRevision(int replacementRevision, String replacementChecksum) {
            return new GateEvidence(
                replacementRevision,
                replacementChecksum,
                sources,
                dependencies,
                dimensions,
                standards,
                quality,
                permissions,
                build,
                tests
            );
        }
    }

    public record GateBlocker(String code, String field, String message, String repairRoute) {}

    public record GateView(
        UUID modelSpecId,
        int revision,
        String checksum,
        Stage stage,
        GateStatus status,
        List<GateBlocker> blockers
    ) {
        public GateView {
            blockers = blockers == null ? List.of() : List.copyOf(blockers);
        }
    }
}
