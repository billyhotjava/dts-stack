package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.repository.modeling.ModelLifecycleRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository;
import com.yuzhi.dts.platform.repository.modeling.DimensionDefinitionRepository;
import com.yuzhi.dts.platform.repository.modeling.DimensionDefinitionRepository.StoredDimensionDefinition;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.StoredModelSpec;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.DimensionHierarchy;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.DimensionLevel;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.DimensionProfile;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Evaluates revision-bound ModelSpec gates without mutating lifecycle state. */
@Service
public class ModelSpecStageGateService {

    private final ModelSpecApplicationService modelSpecs;
    private final ModelSpecRepository repository;
    private final DimensionDefinitionRepository dimensionDefinitions;
    private final ModelSpecStandardEvidencePort standardEvidence;
    private final ModelLifecycleRepository lifecycle;
    private final ModelSpecSourceValidationPort sourceValidation;
    private final ModelSpecDomainReadAccessPort domainReadAccess;
    private final ModelImplementationCompatibilityAdapter implementationCompatibility;

    public ModelSpecStageGateService(
        ModelSpecApplicationService modelSpecs,
        ModelSpecRepository repository,
        ModelSpecStandardEvidencePort standardEvidence
    ) {
        this(modelSpecs, repository, standardEvidence, null, null, null, null, null);
    }

    public ModelSpecStageGateService(
        ModelSpecApplicationService modelSpecs,
        ModelSpecRepository repository,
        ModelSpecStandardEvidencePort standardEvidence,
        ModelLifecycleRepository lifecycle,
        ModelSpecSourceValidationPort sourceValidation
    ) {
        this(modelSpecs, repository, standardEvidence, lifecycle, sourceValidation, null, null, null);
    }

    public ModelSpecStageGateService(
        ModelSpecApplicationService modelSpecs,
        ModelSpecRepository repository,
        ModelSpecStandardEvidencePort standardEvidence,
        ModelLifecycleRepository lifecycle,
        ModelSpecSourceValidationPort sourceValidation,
        DimensionDefinitionRepository dimensionDefinitions
    ) {
        this(modelSpecs, repository, standardEvidence, lifecycle, sourceValidation, dimensionDefinitions, null, null);
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
        ModelImplementationCompatibilityAdapter implementationCompatibility
    ) {
        this.modelSpecs = modelSpecs;
        this.repository = repository;
        this.dimensionDefinitions = dimensionDefinitions;
        this.standardEvidence = standardEvidence;
        this.lifecycle = lifecycle;
        this.sourceValidation = sourceValidation;
        this.domainReadAccess = domainReadAccess;
        this.implementationCompatibility = implementationCompatibility;
    }

    @Transactional(readOnly = true)
    public List<GateView> evaluateAll(String tenantId, UUID modelSpecId) {
        ModelSpecView view = modelSpecs.get(tenantId, modelSpecId);
        GateEvidence evidence = evidence(tenantId, view);
        return List.of(
            evaluate(view, Stage.DRAFT_SAVE, evidence),
            withImplementationInputEvidence(
                tenantId,
                view,
                withDimensionDefinitionEvidence(tenantId, view, evaluate(view, Stage.IMPLEMENTATION_READY, evidence))
            ),
            withImplementationInputEvidence(tenantId, view, withDimensionDefinitionEvidence(tenantId, view, evaluate(view, Stage.RELEASE_READY, evidence)))
        );
    }

    private GateView withImplementationInputEvidence(String tenantId, ModelSpecView view, GateView gate) {
        if (gate.stage() == Stage.DRAFT_SAVE || implementationCompatibility == null) return gate;
        ImplementationView implementation = lifecycle == null ? null : lifecycle.findImplementation(tenantId, view.id()).orElse(null);
        ModelImplementationCompatibilityAdapter.ValidationResult result;
        if (implementation == null) {
            result = ModelImplementationCompatibilityAdapter.ValidationResult.invalid("MODEL_IMPLEMENTATION_REQUIRED");
        } else if (isLegacyClaim(implementation)) {
            result = ModelImplementationCompatibilityAdapter.ValidationResult.invalid("MODEL_IMPLEMENTATION_INPUT_MIGRATION_REQUIRED");
        } else {
            result = implementationCompatibility.validate(
                tenantId,
                view,
                new SaveImplementationCommand(
                    implementation.inputMode(), implementation.inputs(), implementation.fieldMappings(), implementation.settings(),
                    implementation.ownership(), implementation.materialization(), "gate-input-evidence"
                )
            );
        }
        if (result.valid()) return withoutLegacyInputBlockers(view, gate);
        LinkedHashMap<String, GateBlocker> blockers = new LinkedHashMap<>();
        for (GateBlocker blocker : gate.blockers()) add(blockers, blocker);
        add(
            blockers,
            blocker(view, result.code(), "implementation.inputs", "实现输入未通过当前来源与依赖校验", "implementation")
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

    private static boolean isLegacyClaim(ImplementationView implementation) {
        return (
            implementation.inputMode() == ModelLifecycleContract.InputMode.GENERATED &&
            implementation.inputs().size() == 1 &&
            implementation.inputs().get(0) instanceof ModelLifecycleContract.GeneratedInput input &&
            "LEGACY_CLAIM".equals(input.generatorType())
        );
    }

    private GateView withDimensionDefinitionEvidence(String tenantId, ModelSpecView view, GateView gate) {
        if (view.modelType() != ModelType.DIMENSION || dimensionDefinitionCurrent(tenantId, view)) return gate;
        LinkedHashMap<String, GateBlocker> blockers = new LinkedHashMap<>();
        for (GateBlocker blocker : gate.blockers()) add(blockers, blocker);
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
        if (stage != Stage.DRAFT_SAVE) implementationBlockers(view, effectiveEvidence, blockers);
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
        boolean currentArtifacts = implementation != null && (
            implementation.ownership() == ImplementationMode.DBT_MANAGED
                ? artifactTypes.equals(Set.of("SQL", "SCHEMA"))
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
        validateKeyClosure(view, blockers);
        switch (view.modelType()) {
            case DIMENSION -> dimensionBlockers(view, evidence, blockers);
            case FACT -> factBlockers(view, evidence, blockers);
            case SUMMARY -> {
                referenceEvidence(view, evidence.dependencies(), "MODEL_SPEC_UPSTREAM_EVIDENCE", "上游模型版本已变化", blockers);
                boolean hasMeasure = view.fields().stream().anyMatch(field -> field != null && field.role() == FieldRole.MEASURE);
                if (!hasMeasure && view.metricRefs().isEmpty()) {
                    add(blockers, blocker(view, "MODEL_SPEC_SUMMARY_MEASURE_REQUIRED", "fields", "汇总表需声明汇总字段或指标引用", "fields"));
                }
            }
            case APPLICATION -> {
                referenceEvidence(view, evidence.dependencies(), "MODEL_SPEC_UPSTREAM_EVIDENCE", "上游模型版本已变化", blockers);
                if (view.fields().isEmpty()) {
                    add(blockers, blocker(view, "MODEL_SPEC_APPLICATION_OUTPUT_REQUIRED", "fields", "应用表需声明输出字段契约", "fields"));
                }
            }
        }
    }

    private static void dimensionBlockers(
        ModelSpecView view,
        GateEvidence evidence,
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
        if (view.sourceRefs().isEmpty() && view.generationStrategy() == null) {
            add(blockers, blocker(view, "MODEL_SPEC_DIMENSION_INPUT_REQUIRED", "sourceRefs", "请至少选择来源或生成策略", "design"));
        }
        if (!view.sourceRefs().isEmpty()) {
            referenceEvidence(view, evidence.sources(), "MODEL_SPEC_SOURCE_EVIDENCE", "维度来源已失效或版本漂移", blockers);
        }
    }

    private static void factBlockers(
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
                    "design"
                )
            );
        }
        if (view.factShape() == null) {
            add(blockers, blocker(view, "MODEL_SPEC_FACT_SHAPE_REQUIRED", "factShape", "请选择事实形态", "design"));
        }
        if (view.timeSemantics() == null) {
            add(blockers, blocker(view, "MODEL_SPEC_TIME_SEMANTICS_REQUIRED", "timeSemantics", "请声明业务时间语义", "design"));
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
        if (hasDirectSources) {
            referenceEvidence(view, evidence.sources(), "MODEL_SPEC_SOURCE_EVIDENCE", "明细表来源已失效或版本漂移", blockers);
        }
        if (hasUpstreamModels) {
            referenceEvidence(view, evidence.dependencies(), "MODEL_SPEC_UPSTREAM_EVIDENCE", "上游模型版本已变化", blockers);
        }
        referenceEvidence(view, evidence.dimensions(), "MODEL_SPEC_DIMENSION_EVIDENCE", "维度引用已失效或版本漂移", blockers);
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
        evidenceBlocker(view, evidence.quality(), "MODEL_SPEC_QUALITY_EVIDENCE", "质量测试证据不可用", "fields", blockers);
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
        Map<String, StandardBinding> bindings = new HashMap<>();
        for (StandardBinding binding : view.standardBindings()) {
            if (binding != null && binding.fieldName() != null) bindings.put(binding.fieldName(), binding);
        }
        return !view.fields().isEmpty() &&
            view.fields().stream().allMatch(field -> field != null && hasVersionedStandard(bindings.get(field.name())));
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
        return new GateBlocker(code, field, message, "/modeling/models/" + view.id() + "?tab=" + tab);
    }

    private static String repairTab(String field) {
        if ("fields".equals(field)) return "fields";
        if ("standardBindings".equals(field)) return "standards";
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
