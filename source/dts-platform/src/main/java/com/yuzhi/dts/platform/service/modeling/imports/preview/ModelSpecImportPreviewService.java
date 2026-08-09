package com.yuzhi.dts.platform.service.modeling.imports.preview;

import static com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.Action.BLOCKED;
import static com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.Action.CONFLICT;
import static com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.Kind.BAD_REQUEST;
import static com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.Kind.NOT_FOUND;
import static com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.Kind.UNPROCESSABLE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.platform.service.modeling.ModelPackageCanonicalProjector;
import com.yuzhi.dts.platform.service.modeling.ModelPackageCanonicalProjector.CanonicalIssue;
import com.yuzhi.dts.platform.service.modeling.ModelPackageCanonicalProjector.DependencyTarget;
import com.yuzhi.dts.platform.service.modeling.ModelPackageCanonicalProjector.ProjectRequest;
import com.yuzhi.dts.platform.service.modeling.ModelPackageCanonicalProjector.Projection;
import com.yuzhi.dts.platform.service.modeling.ModelPackageCanonicalProjector.SourceTarget;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.CreateModelSpecCommand;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.DimensionDefinitionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.StandardBinding;
import com.yuzhi.dts.platform.service.modeling.ModelSpecSnapshotCodec;
import com.yuzhi.dts.platform.service.modeling.imports.checksum.ModelPackageChecksum;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyPayloadCodec;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyPayloadCodec.SanitizedPayload;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyPlanContract.ApplyPlan;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyPlanContract.Candidate;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ModelPackage;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.PackageModel;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.SourceNode;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.SourceRef;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.TechnicalNode;
import com.yuzhi.dts.platform.service.modeling.imports.dimension.DimensionDefinitionImportResolver;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.Action;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.ConversionMode;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.ModelSpecImportPreviewException;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.PreviewContext;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.PreviewIssue;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.PreviewItem;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.PreviewRequest;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.PreviewResponse;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.PreviewRunResponse;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.PreviewSummary;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.RunStatus;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.Severity;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.SemanticOverride;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewCommitPort.PreviewAuditOutcome;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewCommitPort.PreviewAuditFacts;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewCommitPort.PreviewFailureFact;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportSemanticOverrideResolver.ResolvedSemanticOverride;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewRepository.DomainBindingSnapshot;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewRepository.ModelOwnershipSnapshot;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewRepository.PersistedItem;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewRepository.PersistedRun;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewRepository.PlanSnapshot;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewRepository.SourceBindingSnapshot;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.DriftAction;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.DriftDecision;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.DriftInput;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.MappingPin;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.RenameMapping;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.TechnicalPin;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportThreeWayReconciler;
import com.yuzhi.dts.platform.service.modeling.imports.validator.ModelPackageValidator;
import com.yuzhi.dts.platform.service.modeling.imports.validator.ModelPackageValidator.ModelPackageValidationException;
import com.yuzhi.dts.platform.service.modeling.warehouse.CatalogDomainResolutionPort;
import com.yuzhi.dts.platform.service.modeling.warehouse.CatalogDomainResolutionPort.DomainResolution;
import com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver;
import com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver.AccessContext;
import com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver.ResolvedSource;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider.WarehousePlanActor;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanAuthorizationGuard;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.LifecycleStatus;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceFreshness;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceLocator;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceType;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftCorrelation;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Builds persisted, recoverable model-package previews without mutating modeling truth.
 *
 * <p>All target UUIDs, actor facts, plan permissions and source versions are resolved on
 * the server. The request mappings are comparison hints only.
 */
@Service
public class ModelSpecImportPreviewService {

    private static final Duration PREVIEW_TTL = Duration.ofHours(2);
    private static final Set<LifecycleStatus> IMPORTABLE_LIFECYCLES = Set.of(
        LifecycleStatus.DRAFT,
        LifecycleStatus.BASELINE_READY,
        LifecycleStatus.DESIGNING,
        LifecycleStatus.VALIDATING,
        LifecycleStatus.READY_TO_PUBLISH
    );
    private static final Set<String> AUDIT_RECOVERY_ACTIONS = Set.of(
        "REUPLOAD",
        "COMPLETE_MAPPING",
        "INCLUDE_DEPENDENCY",
        "RESOLVE_CONFLICT",
        "REAUTHORIZE",
        "REFRESH_PREVIEW",
        "RETRY",
        "OPEN_MODEL",
        "CONTACT_ADMIN",
        "NONE"
    );
    private static final Comparator<PreviewIssue> ISSUE_ORDER = Comparator.comparing(
        PreviewIssue::severity
    )
        .thenComparing(PreviewIssue::code, Comparator.nullsFirst(String::compareTo))
        .thenComparing(PreviewIssue::fieldPath, Comparator.nullsFirst(String::compareTo))
        .thenComparing(PreviewIssue::modelUniqueId, Comparator.nullsFirst(String::compareTo));

    private final ModelSpecImportPreviewRepository repository;
    private final ModelSpecImportPreviewCommitPort commits;
    private final CatalogDomainResolutionPort domainResolver;
    private final SourceReferenceResolver sourceResolver;
    private final WarehousePlanActorProvider actorProvider;
    private final WarehousePlanAuthorizationGuard authorizationGuard;
    private final ObjectMapper objectMapper;
    private final ModelPackageCanonicalProjector canonicalProjector;
    private final DimensionDefinitionImportResolver dimensionDefinitionResolver;
    private final ModelSpecImportApplyPayloadCodec applyPayloadCodec;
    private final ModelPackageValidator packageValidator;
    private final ModelSpecImportThreeWayReconciler threeWayReconciler;
    private final ModelSpecSnapshotCodec modelSpecSnapshotCodec;
    private final ModelSpecImportSemanticOverrideResolver semanticOverrideResolver =
        new ModelSpecImportSemanticOverrideResolver();
    private final String serverTenantId;
    private final Clock clock;

    @Autowired
    public ModelSpecImportPreviewService(
        ModelSpecImportPreviewRepository repository,
        ModelSpecImportPreviewCommitPort commits,
        CatalogDomainResolutionPort domainResolver,
        SourceReferenceResolver sourceResolver,
        WarehousePlanActorProvider actorProvider,
        WarehousePlanAuthorizationGuard authorizationGuard,
        ObjectMapper objectMapper,
        ModelPackageCanonicalProjector canonicalProjector,
        DimensionDefinitionImportResolver dimensionDefinitionResolver,
        ModelSpecImportApplyPayloadCodec applyPayloadCodec,
        @Value("${dts.platform.modeling.default-tenant-id:default}") String serverTenantId
    ) {
        this(
            repository,
            commits,
            domainResolver,
            sourceResolver,
            actorProvider,
            authorizationGuard,
            objectMapper,
            canonicalProjector,
            dimensionDefinitionResolver,
            applyPayloadCodec,
            serverTenantId,
            Clock.systemUTC()
        );
    }

    ModelSpecImportPreviewService(
        ModelSpecImportPreviewRepository repository,
        CatalogDomainResolutionPort domainResolver,
        SourceReferenceResolver sourceResolver,
        WarehousePlanActorProvider actorProvider,
        WarehousePlanAuthorizationGuard authorizationGuard,
        ObjectMapper objectMapper,
        ModelPackageCanonicalProjector canonicalProjector,
        DimensionDefinitionImportResolver dimensionDefinitionResolver,
        ModelSpecImportApplyPayloadCodec applyPayloadCodec,
        String serverTenantId,
        Clock clock
    ) {
        this(
            repository,
            (run, items, facts) -> repository.save(run, items),
            domainResolver,
            sourceResolver,
            actorProvider,
            authorizationGuard,
            objectMapper,
            canonicalProjector,
            dimensionDefinitionResolver,
            applyPayloadCodec,
            serverTenantId,
            clock
        );
    }

    ModelSpecImportPreviewService(
        ModelSpecImportPreviewRepository repository,
        ModelSpecImportPreviewCommitPort commits,
        CatalogDomainResolutionPort domainResolver,
        SourceReferenceResolver sourceResolver,
        WarehousePlanActorProvider actorProvider,
        WarehousePlanAuthorizationGuard authorizationGuard,
        ObjectMapper objectMapper,
        ModelPackageCanonicalProjector canonicalProjector,
        DimensionDefinitionImportResolver dimensionDefinitionResolver,
        ModelSpecImportApplyPayloadCodec applyPayloadCodec,
        String serverTenantId,
        Clock clock
    ) {
        this.repository = repository;
        this.commits = commits;
        this.domainResolver = domainResolver;
        this.sourceResolver = sourceResolver;
        this.actorProvider = actorProvider;
        this.authorizationGuard = authorizationGuard;
        this.objectMapper = objectMapper;
        this.canonicalProjector = canonicalProjector;
        this.dimensionDefinitionResolver = dimensionDefinitionResolver;
        this.applyPayloadCodec = applyPayloadCodec;
        this.packageValidator = new ModelPackageValidator(objectMapper);
        this.threeWayReconciler = new ModelSpecImportThreeWayReconciler(objectMapper);
        this.modelSpecSnapshotCodec = new ModelSpecSnapshotCodec(objectMapper);
        this.serverTenantId = requireText(serverTenantId, "MODEL_IMPORT_SERVER_TENANT_REQUIRED", "Server tenant context is required");
        this.clock = clock;
    }

    public PreviewResponse preview(PreviewRequest request) {
        ValidatedInput input = validateRequest(request);
        WarehousePlanActor actor = actorProvider.currentActor();
        PlanSnapshot plan = repository
            .findPlan(serverTenantId, input.planId())
            .orElseThrow(ModelSpecImportPreviewService::notFound);
        authorizationGuard.requirePlanMaintenance(plan.header(), actor);
        requireImportable(plan);
        requireRenameOwnership(input, plan);

        ResolvedContext context = resolveContext(input, plan, actor);
        PreviewBuild preview = buildPreview(input, plan, actor, context);
        persist(input, plan, actor, preview);
        return new PreviewResponse(
            preview.runId(),
            preview.previewHash(),
            preview.applyPayload().checksum(),
            preview.summary(),
            preview.items()
        );
    }

    public PreviewRunResponse get(UUID runId) {
        if (runId == null) {
            throw notFound();
        }
        ModelSpecImportPreviewRepository.StoredRun run = repository.findRun(serverTenantId, runId).orElseThrow(
            ModelSpecImportPreviewService::notFound
        );
        PlanSnapshot plan = repository.findPlan(serverTenantId, run.planId()).orElseThrow(ModelSpecImportPreviewService::notFound);
        authorizationGuard.requirePlanRead(plan.header(), actorProvider.currentActor());
        RunStatus status = run.expiresAt() == null || !run.expiresAt().isAfter(clock.instant())
            ? RunStatus.EXPIRED
            : run.status();
        return new PreviewRunResponse(
            run.id(),
            run.planId(),
            run.previewHash(),
            run.applyPayloadChecksum(),
            status,
            run.expiresAt(),
            run.summary(),
            run.items()
        );
    }

    private ValidatedInput validateRequest(PreviewRequest request) {
        if (request == null || request.modelPackage() == null || !request.modelPackage().isObject()) {
            throw badRequest("MODEL_PACKAGE_SCHEMA_INVALID", "Request field package must be a JSON object", null);
        }
        if (request.context() == null || request.context().planId() == null) {
            throw badRequest("MODEL_IMPORT_CONVERSION_BLOCKED", "context.planId is required", null);
        }
        ModelPackage modelPackage;
        try {
            modelPackage = packageValidator.parseAndValidate(objectMapper.writeValueAsBytes(request.modelPackage()));
        } catch (ModelPackageValidationException exception) {
            List<PreviewIssue> details = exception
                .issues()
                .stream()
                .map(issue ->
                    new PreviewIssue(
                        issue.code(),
                        Severity.ERROR,
                        issue.fieldPath(),
                        null,
                        issue.message(),
                        issue.recoveryAction()
                    )
                )
                .sorted(ISSUE_ORDER)
                .toList();
            String code = details.isEmpty() ? "MODEL_PACKAGE_SCHEMA_INVALID" : details.getFirst().code();
            String message = details.isEmpty() ? "Model package validation failed" : details.getFirst().message();
            throw badRequest(code, message, details);
        } catch (JsonProcessingException exception) {
            throw badRequest("MODEL_PACKAGE_SCHEMA_INVALID", "Request field package is not serializable", null);
        }

        Map<String, PackageModel> inspectedModels = new TreeMap<>();
        for (PackageModel model : safe(modelPackage.models())) {
            inspectedModels.put(model.dbtUniqueId(), model);
        }
        Map<String, SemanticOverride> semanticOverrides = semanticOverrideResolver.index(
            request.semanticOverrides(),
            inspectedModels
        );
        Map<String, PackageModel> models = new TreeMap<>();
        Map<String, List<StandardBinding>> standardBindings = new TreeMap<>();
        Map<String, ModelBusinessContext> businessContexts = new TreeMap<>();
        inspectedModels.forEach((uniqueId, inspected) -> {
            ResolvedSemanticOverride resolved = semanticOverrideResolver.resolve(
                inspected,
                semanticOverrides.get(uniqueId)
            );
            models.put(uniqueId, resolved.model());
            standardBindings.put(uniqueId, resolved.standardBindings());
            businessContexts.put(
                uniqueId,
                new ModelBusinessContext(
                    resolved.businessProcessId(),
                    resolved.dataMartId(),
                    resolved.subjectDomainId()
                )
            );
        });
        TreeSet<String> selected = new TreeSet<>();
        for (String uniqueId : safe(request.selectedUniqueIds())) {
            if (uniqueId != null && !uniqueId.isBlank()) {
                selected.add(uniqueId.trim());
            }
        }
        if (selected.isEmpty()) {
            selected.addAll(models.keySet());
        }
        List<String> unknown = selected.stream().filter(uniqueId -> !models.containsKey(uniqueId)).toList();
        if (!unknown.isEmpty()) {
            throw badRequest(
                "MODEL_IMPORT_DEPENDENCY_MISSING",
                "Selected model is not present in the package",
                List.of(
                    issue(
                        "MODEL_IMPORT_DEPENDENCY_MISSING",
                        "$.selectedUniqueIds",
                        null,
                        "Selected model is not present in the package: " + String.join(", ", unknown),
                        "Select only canonical models contained in the validated package"
                    )
                )
            );
        }
        Map<String, String> renameSourcesByTarget = validateRenameMappings(
            request.renameMappings(),
            inspectedModels,
            selected
        );
        PreviewContext context = request.context();
        return new ValidatedInput(
            modelPackage,
            context.planId(),
            List.copyOf(selected),
            normalizedHints(context.domainMappings()),
            normalizedHints(context.sourceMappings()),
            models,
            Map.copyOf(standardBindings),
            Map.copyOf(businessContexts),
            renameSourcesByTarget,
            Set.copyOf(semanticOverrides.keySet())
        );
    }

    private Map<String, String> validateRenameMappings(
        List<RenameMapping> requested,
        Map<String, PackageModel> packageModels,
        Set<String> selected
    ) {
        Map<String, String> oldToNew;
        try {
            oldToNew = threeWayReconciler.validateRenameMappings(requested);
        } catch (IllegalArgumentException invalid) {
            throw badRequest("MODEL_IMPORT_RENAME_MAPPING_INVALID", invalid.getMessage(), null);
        }
        if (oldToNew.isEmpty()) return Map.of();
        TreeMap<String, String> sourceByTarget = new TreeMap<>();
        for (Map.Entry<String, String> mapping : oldToNew.entrySet()) {
            String oldUniqueId = mapping.getKey();
            String newUniqueId = mapping.getValue();
            boolean valid = packageModels.containsKey(newUniqueId) &&
                !packageModels.containsKey(oldUniqueId) &&
                selected.contains(newUniqueId) &&
                Objects.equals(dbtPackageIdentity(oldUniqueId), dbtPackageIdentity(newUniqueId));
            if (!valid) {
                throw badRequest(
                    "MODEL_IMPORT_RENAME_IDENTITY_INVALID",
                    "Rename mappings must identify a selected replacement in the same dbt package",
                    Map.of("oldUniqueId", oldUniqueId, "newUniqueId", newUniqueId)
                );
            }
            sourceByTarget.put(newUniqueId, oldUniqueId);
        }
        return Map.copyOf(sourceByTarget);
    }

    private void requireRenameOwnership(ValidatedInput input, PlanSnapshot plan) {
        if (input.renameSourcesByTarget().isEmpty()) return;
        String projectKey = input.modelPackage().dbt() == null ? null : input.modelPackage().dbt().projectName();
        for (Map.Entry<String, String> mapping : input.renameSourcesByTarget().entrySet()) {
            ModelOwnershipSnapshot source = repository
                .findOwnership(serverTenantId, projectKey, mapping.getValue())
                .orElseThrow(() ->
                    badRequest(
                        "MODEL_IMPORT_RENAME_IDENTITY_INVALID",
                        "Rename source is not owned by the selected tenant and warehouse plan",
                        mapping
                    )
                );
            if (!plan.id().equals(source.planId()) || repository.findOwnership(serverTenantId, projectKey, mapping.getKey()).isPresent()) {
                throw badRequest(
                    "MODEL_IMPORT_RENAME_IDENTITY_INVALID",
                    "Rename target is already owned or the source belongs to another warehouse plan",
                    mapping
                );
            }
        }
    }

    private static String dbtPackageIdentity(String uniqueId) {
        if (uniqueId == null) return null;
        int first = uniqueId.indexOf('.');
        int second = first < 0 ? -1 : uniqueId.indexOf('.', first + 1);
        return second < 0 ? null : uniqueId.substring(0, second);
    }

    private ResolvedContext resolveContext(ValidatedInput input, PlanSnapshot plan, WarehousePlanActor actor) {
        List<ResolvedDomain> domains = repository
            .findDomainBindings(serverTenantId, plan.id())
            .stream()
            .map(this::resolveDomain)
            .sorted(Comparator.comparing(domain -> domain.domainId().toString()))
            .toList();
        AccessContext accessContext = new AccessContext(
            serverTenantId,
            actor == null ? null : actor.ownerId(),
            actor == null ? null : actor.ownerDepartmentId()
        );
        List<ResolvedBinding> sources = repository
            .findSourceBindings(serverTenantId, plan.id())
            .stream()
            .map(binding -> resolveSource(binding, accessContext))
            .sorted(
                Comparator.comparing(ResolvedBinding::sourceType, Comparator.nullsLast(String::compareTo))
                    .thenComparing(ResolvedBinding::sourceId, Comparator.nullsLast(String::compareTo))
                    .thenComparing(binding -> binding.bindingId().toString())
            )
            .toList();
        return new ResolvedContext(domains, sources);
    }

    private ResolvedDomain resolveDomain(DomainBindingSnapshot binding) {
        DomainResolution resolution = domainResolver.resolve(binding.domainId());
        return new ResolvedDomain(
            binding.domainId(),
            binding.confirmationStatus(),
            resolution == null || resolution.status() == null ? "MISSING" : resolution.status().name(),
            resolution == null ? null : resolution.code(),
            resolution == null ? null : resolution.name(),
            binding.lastValidatedAt()
        );
    }

    private ResolvedBinding resolveSource(SourceBindingSnapshot binding, AccessContext accessContext) {
        SourceType sourceType = parseSourceType(binding.sourceType());
        SourceLocator locator = parseLocator(binding.locatorJson());
        ResolvedSource resolution = sourceType == null
            ? ResolvedSource.providerError()
            : sourceResolver.resolve(sourceType, locator, accessContext);
        SourceReferenceResolver.ResolutionStatus status = resolution == null || resolution.status() == null
            ? SourceReferenceResolver.ResolutionStatus.PROVIDER_ERROR
            : resolution.status();
        String resolvedVersion = resolution == null ? null : resolution.resolvedVersion();
        SourceFreshness freshness;
        if (status != SourceReferenceResolver.ResolutionStatus.AVAILABLE || isBlank(resolvedVersion) || isBlank(binding.sourceVersion())) {
            freshness = SourceFreshness.UNKNOWN;
        } else if (binding.sourceVersion().equals(resolvedVersion)) {
            freshness = SourceFreshness.CURRENT;
        } else {
            freshness = SourceFreshness.STALE;
        }
        return new ResolvedBinding(
            binding.id(),
            binding.sourceType(),
            binding.sourceId(),
            locator == null ? null : locator.objectName(),
            locator == null ? null : locator.uniqueId(),
            binding.sourceVersion(),
            resolvedVersion,
            binding.confirmationStatus(),
            status.name(),
            freshness,
            status == SourceReferenceResolver.ResolutionStatus.AVAILABLE && resolution != null ? resolution.displayName() : null,
            binding.lastValidatedAt()
        );
    }

    private PreviewBuild buildPreview(
        ValidatedInput input,
        PlanSnapshot plan,
        WarehousePlanActor actor,
        ResolvedContext context
    ) {
        PackageGraph graph = PackageGraph.from(input.modelPackage());
        Set<String> cycleNodes = graph.cycleNodes();
        Map<String, TreeSet<String>> canonicalDependencies = new TreeMap<>();
        Map<String, TreeSet<String>> reachableSources = new TreeMap<>();
        for (String uniqueId : input.selectedUniqueIds()) {
            canonicalDependencies.put(uniqueId, graph.canonicalDependencies(uniqueId, input.selectedUniqueIds()));
            reachableSources.put(uniqueId, graph.reachableSources(uniqueId));
        }
        List<String> order = stableTopologicalOrder(input.selectedUniqueIds(), canonicalDependencies);

        List<PendingItem> pending = new ArrayList<>();
        Map<String, PendingItem> projectedDependencies = new HashMap<>();
        for (String uniqueId : order) {
            PackageModel model = input.models().get(uniqueId);
            PendingItem item = evaluateItem(
                input,
                plan,
                context,
                graph,
                cycleNodes,
                model,
                canonicalDependencies.getOrDefault(uniqueId, new TreeSet<>()),
                reachableSources.getOrDefault(uniqueId, new TreeSet<>()),
                projectedDependencies
            );
            pending.add(item);
            projectedDependencies.put(uniqueId, item);
        }
        PreviewSummary summary = summarize(pending);
        RunStatus status = summary.ready() > 0 ? RunStatus.PREVIEWED : RunStatus.BLOCKED;
        Instant createdAt = clock.instant();
        Instant expiresAt = createdAt.plus(PREVIEW_TTL);
        UUID runId = UUID.randomUUID();
        JsonNode contextSnapshot = contextSnapshot(plan, actor, context, pending);
        SanitizedPayload applyPayload = applyPayloadCodec.sanitize(input.modelPackage());
        List<Candidate> candidates = applyPlanCandidates(pending);
        String previewHash = previewHash(
            input,
            plan,
            actor,
            contextSnapshot,
            pending,
            applyPayload.checksum(),
            candidates
        );
        ApplyPlan applyPlan = buildApplyPlan(
            runId,
            plan.id(),
            previewHash,
            input.modelPackage().packageChecksum(),
            applyPayload.checksum(),
            pending,
            candidates
        );
        String applyPlanChecksum = applyPayloadCodec.checksum(objectMapper.valueToTree(applyPlan));
        List<PreviewItem> responseItems = pending
            .stream()
            .map(item ->
                new PreviewItem(
                    item.dbtUniqueId(),
                    item.action(),
                    item.conversionMode(),
                    item.proposedModelSpec(),
                    item.proposedImplementation(),
                    item.issues()
                )
            )
            .toList();
        return new PreviewBuild(
            runId,
            previewHash,
            status,
            createdAt,
            expiresAt,
            summary,
            responseItems,
            pending,
            contextSnapshot,
            applyPayload,
            applyPlan,
            applyPlanChecksum
        );
    }

    private PendingItem evaluateItem(
        ValidatedInput input,
        PlanSnapshot plan,
        ResolvedContext context,
        PackageGraph graph,
        Set<String> cycleNodes,
        PackageModel model,
        TreeSet<String> dependencies,
        TreeSet<String> sourceNodeIds,
        Map<String, PendingItem> projectedDependencies
    ) {
        List<PreviewIssue> issues = new ArrayList<>();
        String renameSourceUniqueId = input.renameSourcesByTarget().get(model.dbtUniqueId());
        boolean renameBlocked = renameSourceUniqueId != null;
        if (renameBlocked) {
            issues.add(
                issue(
                    "MODEL_IMPORT_RENAME_REQUIRES_CANONICAL_REASSOCIATION",
                    "$.renameMappings",
                    model.dbtUniqueId(),
                    "The explicit rename is valid but canonical dbt ownership reassociation is not available",
                    "Keep the current model and retry after canonical ownership reassociation is enabled"
                )
            );
        }
        ConversionMode conversionMode = conversionMode(model);
        if (conversionMode == ConversionMode.BLOCKED) {
            issues.add(
                issue(
                    "MODEL_IMPORT_CONVERSION_BLOCKED",
                    "$.package.models[" + model.dbtUniqueId() + "].conversion",
                    model.dbtUniqueId(),
                    "The model package classified this model as not convertible",
                    "Complete semantic metadata or retain it as a technical-only node"
                )
            );
        }

        ResolvedDomain domain = resolveDomainForModel(input, context.domains(), model, issues);
        List<ResolvedBinding> bindings = resolveSourcesForModel(input, context.sources(), graph, model, sourceNodeIds, issues);

        Set<String> selected = new HashSet<>(input.selectedUniqueIds());
        List<String> unselectedDependencies = graph
            .canonicalDependencies(model.dbtUniqueId(), input.models().keySet())
            .stream()
            .filter(dependency -> !selected.contains(dependency))
            .toList();
        if (!unselectedDependencies.isEmpty()) {
            issues.add(
                issue(
                    "MODEL_IMPORT_DEPENDENCY_MISSING",
                    "$.selectedUniqueIds",
                    model.dbtUniqueId(),
                    "Required canonical dependencies were not selected: " + String.join(", ", unselectedDependencies),
                    "Select the complete canonical dependency closure"
                )
            );
        }
        Set<String> reachable = graph.reachableNodes(model.dbtUniqueId());
        List<String> reachableCycles = cycleNodes.stream().filter(reachable::contains).sorted().toList();
        if (!reachableCycles.isEmpty()) {
            issues.add(
                issue(
                    "MODEL_IMPORT_DEPENDENCY_CYCLE",
                    "$.package.models[" + model.dbtUniqueId() + "].dependencies",
                    model.dbtUniqueId(),
                    "Dependency cycle contains: " + String.join(" -> ", reachableCycles),
                    "Break the dbt dependency cycle and regenerate the package"
                )
            );
        }
        addReverseLayerIssues(model, dependencies, input.models(), issues);
        addPackageIssues(input.modelPackage(), model.dbtUniqueId(), issues);
        List<String> blockedDependencies = dependencies
            .stream()
            .filter(dependency -> {
                PendingItem upstream = projectedDependencies.get(dependency);
                return upstream != null && (upstream.action() == BLOCKED || upstream.action() == CONFLICT);
            })
            .sorted()
            .toList();
        if (!blockedDependencies.isEmpty()) {
            issues.add(
                issue(
                    "MODEL_IMPORT_DEPENDENCY_BLOCKED",
                    "$.package.models[" + model.dbtUniqueId() + "].dependencies",
                    model.dbtUniqueId(),
                    "Canonical dependencies are not applicable: " + String.join(", ", blockedDependencies),
                    "Resolve the upstream preview issues before applying this model"
                )
            );
        }

        ArrayNode sourceProjection = sourceProjection(bindings);
        DimensionDefinitionRef dimensionDefinitionRef = resolveDimensionDefinition(plan, model, domain, issues);
        Projection canonical = canonicalProjector.project(
            new ProjectRequest(
                plan.id(),
                domain == null ? null : domain.domainId(),
                input.modelPackage().dbt() == null ? null : input.modelPackage().dbt().projectName(),
                model,
                true,
                canonicalSources(model, bindings),
                canonicalDependencies(dependencies, projectedDependencies, input.models()),
                dimensionDefinitionRef,
                input.standardBindings().getOrDefault(model.dbtUniqueId(), List.of()),
                input.businessContexts().getOrDefault(model.dbtUniqueId(), ModelBusinessContext.EMPTY).businessProcessId(),
                input.businessContexts().getOrDefault(model.dbtUniqueId(), ModelBusinessContext.EMPTY).dataMartId(),
                input.businessContexts().getOrDefault(model.dbtUniqueId(), ModelBusinessContext.EMPTY).subjectDomainId()
            )
        );
        addCanonicalIssues(model, canonical.issues(), issues);
        JsonNode proposedModelSpec = canonical.modelSpecProjection();
        JsonNode proposedImplementation = canonical.implementationProjection();
        String modelSpecChecksum = canonical.modelSpecChecksum();

        String projectKey = input.modelPackage().dbt() == null ? null : input.modelPackage().dbt().projectName();
        String expectedOwnerIdentity = "dbt:" + projectKey + ":" + model.dbtUniqueId();
        ModelOwnershipSnapshot current = repository
            .findOwnership(serverTenantId, projectKey, model.dbtUniqueId())
            .orElse(null);
        DriftDecision reconciliation = null;
        if (current != null && canonical.implementationChecksum() != null) {
            JsonNode currentModelSpec = repository
                .findCurrentModelSpecSnapshot(serverTenantId, current.modelSpecId(), current.currentRevision())
                .orElse(null);
            if (currentModelSpec == null) {
                issues.add(
                    issue(
                        "MODEL_IMPORT_CURRENT_SNAPSHOT_MISSING",
                        "$.package.models[" + model.dbtUniqueId() + "]",
                        model.dbtUniqueId(),
                        "The current mapped ModelSpec revision snapshot is unavailable",
                        "Open the mapped model and repair its revision history before re-importing"
                    )
                );
            } else {
                reconciliation = threeWayReconciler.evaluate(
                    new DriftInput(
                        repository
                            .findLatestMergeCheckpoint(
                                serverTenantId,
                                plan.id(),
                                current.modelSpecId(),
                                current.implementationId(),
                                projectKey,
                                model.dbtUniqueId()
                            )
                            .orElse(null),
                        new TechnicalPin(current.implementationRevision(), current.currentImplementationChecksum()),
                        canonical.implementationChecksum(),
                        new MappingPin(current.currentRevision(), current.currentChecksum()),
                        currentModelSpec,
                        proposedModelSpec,
                        input.semanticOverrideIds().contains(model.dbtUniqueId())
                    )
                );
                proposedModelSpec = reconciliation.proposedModelSpec();
                modelSpecChecksum = checksumModelSpecProjection(proposedModelSpec);
                if (reconciliation.action() == DriftAction.CONFLICT) {
                    issues.add(
                        issue(
                            "MODEL_IMPORT_" + reconciliation.reasonCode(),
                            "$.package.models[" + model.dbtUniqueId() + "]",
                            model.dbtUniqueId(),
                            "The re-import cannot overwrite divergent or unbased model state",
                            "Choose KEEP_CURRENT, ACCEPT_INCOMING, or CANCEL for this candidate"
                        )
                    );
                } else if (reconciliation.action() == DriftAction.BLOCKED_REMAP) {
                    issues.add(
                        issue(
                            "MODEL_IMPORT_BLOCKED_REMAP",
                            "$.package.models[" + model.dbtUniqueId() + "]",
                            model.dbtUniqueId(),
                            "The mapped ModelSpec revision changed after the accepted import checkpoint",
                            "Revalidate the model mapping before applying this re-import"
                        )
                    );
                }
            }
        }
        String expectedOwnership = canonical.modelSpecCommand().implementationMode().name();
        boolean ownershipConflict = current != null &&
        (
            !plan.id().equals(current.planId()) ||
            !expectedOwnership.equals(current.ownership()) ||
            current.implementationModelRevision() != current.currentRevision() ||
            !Objects.equals(current.implementationModelChecksum(), current.currentChecksum())
        );
        boolean dimensionBoundaryConflict = current != null &&
        (
            "DIMENSION".equals(current.currentModelType()) ||
            canonical.modelSpecCommand().modelType() == ModelType.DIMENSION
        ) &&
        (
            !"DIMENSION".equals(current.currentModelType()) ||
            canonical.modelSpecCommand().modelType() != ModelType.DIMENSION ||
            canonical.modelSpecCommand().dimensionDefinitionRef() == null ||
            !Objects.equals(
                current.currentDimensionDefinitionId(),
                canonical.modelSpecCommand().dimensionDefinitionRef().dimensionDefinitionId()
            ) ||
            !Objects.equals(
                current.currentDimensionDefinitionRevision(),
                canonical.modelSpecCommand().dimensionDefinitionRef().revision()
            )
        );
        boolean modelChanged = current != null && !Objects.equals(current.currentChecksum(), modelSpecChecksum);
        boolean implementationChanged = current != null &&
        !Objects.equals(current.currentImplementationChecksum(), canonical.implementationChecksum());
        boolean effectiveSqlChanged = current != null &&
        conversionMode == ConversionMode.DBT_BACKED &&
        !Objects.equals(current.effectiveSqlChecksum(), canonical.effectiveSqlChecksum());
        boolean modelStatusConflict = current != null &&
        !"DRAFT".equals(current.currentModelStatus()) &&
        (modelChanged || implementationChanged || effectiveSqlChanged);
        if (ownershipConflict) {
            issues.add(
                issue(
                    "MODEL_IMPORT_NODE_ALREADY_OWNED",
                    "$.package.models[" + model.dbtUniqueId() + "].dbtUniqueId",
                    model.dbtUniqueId(),
                    "The dbt uniqueId is already owned by another package or warehouse plan",
                    "Import with the owning package and plan, or retire the conflicting ownership first"
                )
            );
        }
        if (dimensionBoundaryConflict) {
            issues.add(
                issue(
                    "MODEL_IMPORT_DIMENSION_DEFINITION_IMMUTABLE",
                    "$.package.models[" + model.dbtUniqueId() + "].semantics.dimensionDefinitionCode",
                    model.dbtUniqueId(),
                    "The existing model cannot replace its create-only dimension definition reference",
                    "Retire the existing model or import with its currently pinned dimension definition"
                )
            );
        }
        if (modelStatusConflict) {
            issues.add(
                issue(
                    "MODEL_IMPORT_MODEL_STATUS_READONLY",
                    "$.package.models[" + model.dbtUniqueId() + "]",
                    model.dbtUniqueId(),
                    "The existing model is not DRAFT and cannot be changed by import",
                    "Create a new draft revision or retire the existing model before importing changes"
                )
            );
        }

        List<PreviewIssue> orderedIssues = issues.stream().distinct().sorted(ISSUE_ORDER).toList();
        Action action;
        if (renameBlocked) {
            action = BLOCKED;
        } else if (
            ownershipConflict ||
            dimensionBoundaryConflict ||
            modelStatusConflict ||
            (reconciliation != null && reconciliation.action() == DriftAction.CONFLICT)
        ) {
            action = CONFLICT;
        } else if (reconciliation != null && reconciliation.action() == DriftAction.BLOCKED_REMAP) {
            action = BLOCKED;
        } else if (orderedIssues.stream().anyMatch(issue -> issue.severity() == Severity.ERROR)) {
            action = BLOCKED;
        } else if (current == null) {
            action = Action.CREATE;
        } else if (reconciliation != null) {
            action = switch (reconciliation.action()) {
                case SKIP -> Action.SKIP;
                case UPDATE -> Action.UPDATE;
                case CONFLICT -> CONFLICT;
                case BLOCKED_REMAP -> BLOCKED;
            };
        } else if (
            Objects.equals(current.currentChecksum(), modelSpecChecksum) &&
            Objects.equals(current.currentImplementationChecksum(), canonical.implementationChecksum()) &&
            (
                conversionMode != ConversionMode.DBT_BACKED ||
                Objects.equals(current.effectiveSqlChecksum(), canonical.effectiveSqlChecksum())
            )
        ) {
            action = Action.SKIP;
        } else {
            action = Action.UPDATE;
        }
        if (action == BLOCKED || action == CONFLICT) {
            conversionMode = ConversionMode.BLOCKED;
        }

        ObjectNode dependencyProjection = objectMapper.createObjectNode();
        dependencyProjection.set("canonicalDependencies", objectMapper.valueToTree(dependencies));
        dependencyProjection.set("technicalPathNodes", objectMapper.valueToTree(graph.technicalPathNodes(model.dbtUniqueId())));
        dependencyProjection.set("sourceNodes", objectMapper.valueToTree(sourceNodeIds));
        ObjectNode evidence = evidenceProjection(canonical, orderedIssues);
        if (reconciliation != null) {
            evidence.put("reconciliationAction", reconciliation.action().name());
            evidence.put("reconciliationReason", reconciliation.reasonCode());
            evidence.put("mappingRevalidationRequired", reconciliation.mappingRevalidationRequired());
        }
        if (renameBlocked) {
            evidence.put("reconciliationAction", DriftAction.BLOCKED_REMAP.name());
            evidence.put("reconciliationReason", "MODEL_IMPORT_RENAME_REQUIRES_CANONICAL_REASSOCIATION");
            evidence.put("mappingRevalidationRequired", true);
            evidence.put("renameSourceUniqueId", renameSourceUniqueId);
            evidence.put("renameTargetUniqueId", model.dbtUniqueId());
        }
        return new PendingItem(
            model.dbtUniqueId(),
            action,
            conversionMode,
            proposedModelSpec,
            proposedImplementation,
            orderedIssues,
            modelSpecChecksum,
            current == null ? null : current.modelSpecId(),
            current == null ? null : current.currentRevision(),
            dependencyProjection,
            sourceProjection,
            expectedOwnerIdentity,
            canonical.implementationChecksum(),
            canonical.effectiveSqlChecksum(),
            targetModelSpecId(plan, input.modelPackage(), model, current),
            targetRevision(current, modelSpecChecksum),
            targetImplementationRevision(current, canonical.implementationChecksum()),
            current == null ? 0 : current.currentRevision(),
            current == null ? null : current.currentChecksum(),
            current == null ? null : current.currentModelStatus(),
            current == null ? 0 : current.implementationRevision(),
            current == null ? null : current.currentImplementationChecksum(),
            canonical.modelSpecCommand().modelType(),
            objectMapper.valueToTree(canonical.artifact()),
            evidence
        );
    }

    private DimensionDefinitionRef resolveDimensionDefinition(
        PlanSnapshot plan,
        PackageModel model,
        ResolvedDomain domain,
        List<PreviewIssue> issues
    ) {
        if (model.semantics() == null || !"DIMENSION".equalsIgnoreCase(trim(model.semantics().modelType()))) {
            return null;
        }
        String systemCode = trim(model.semantics().dimensionDefinitionCode());
        if (systemCode == null || domain == null) {
            issues.add(
                issue(
                    "MODEL_SPEC_DIMENSION_DEFINITION_REQUIRED",
                    "$.package.models[" + model.dbtUniqueId() + "].semantics.dimensionDefinitionCode",
                    model.dbtUniqueId(),
                    "DIMENSION models require a visible CURRENT dimension definition code",
                    "Provide dimensionDefinitionCode and make the definition CURRENT in the mapped domain"
                )
            );
            return null;
        }
        return dimensionDefinitionResolver
            .resolveCurrent(serverTenantId, plan.id(), domain.domainId(), systemCode)
            .map(definition -> new DimensionDefinitionRef(definition.id(), definition.revision()))
            .orElseGet(() -> {
                issues.add(
                    issue(
                        "MODEL_SPEC_DIMENSION_DEFINITION_REQUIRED",
                        "$.package.models[" + model.dbtUniqueId() + "].semantics.dimensionDefinitionCode",
                        model.dbtUniqueId(),
                        "The dimension definition is missing, invisible, or not CURRENT",
                        "Publish the definition in the mapped tenant/domain and retry preview"
                    )
                );
                return null;
            });
    }

    private List<SourceTarget> canonicalSources(PackageModel model, List<ResolvedBinding> bindings) {
        return bindings
            .stream()
            .map(binding ->
                new SourceTarget(
                    binding.bindingId(),
                    "DBT_NODE".equals(binding.sourceType()) ? "DBT_MODEL" : "TABLE",
                    binding.sourceId(),
                    sourceLayer(model, binding),
                    binding.resolvedVersion()
                )
            )
            .toList();
    }

    private static String sourceLayer(PackageModel model, ResolvedBinding binding) {
        if (model.semantics() == null) {
            return null;
        }
        return safe(model.semantics().sourceRefs())
            .stream()
            .filter(ref ->
                ref != null &&
                (
                    sameHint(ref.ref(), binding.sourceId()) ||
                    sameHint(ref.ref(), binding.objectName()) ||
                    sameHint(ref.ref(), binding.dbtUniqueId())
                )
            )
            .map(SourceRef::layer)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    }

    private static List<DependencyTarget> canonicalDependencies(
        Collection<String> dependencies,
        Map<String, PendingItem> projectedDependencies,
        Map<String, PackageModel> models
    ) {
        return dependencies
            .stream()
            .map(uniqueId -> {
                PendingItem item = projectedDependencies.get(uniqueId);
                PackageModel dependency = models.get(uniqueId);
                ModelType type = item == null ? parseModelType(dependency) : item.modelType();
                if (item == null) {
                    return new DependencyTarget(uniqueId, null, 0, null, 0, null, type);
                }
                return new DependencyTarget(
                    uniqueId,
                    item.targetModelSpecId(),
                    item.targetRevision(),
                    item.modelSpecChecksum(),
                    item.targetImplementationRevision(),
                    item.implementationChecksum(),
                    type
                );
            })
            .toList();
    }

    private static void addCanonicalIssues(
        PackageModel model,
        List<CanonicalIssue> canonicalIssues,
        List<PreviewIssue> issues
    ) {
        canonicalIssues.forEach(item ->
            issues.add(
                issue(
                    item.code(),
                    "$.package.models[" + model.dbtUniqueId() + "]." + item.field(),
                    model.dbtUniqueId(),
                    item.message(),
                    "Correct the canonical model metadata and regenerate the package"
                )
            )
        );
    }

    private static UUID targetModelSpecId(
        PlanSnapshot plan,
        ModelPackage modelPackage,
        PackageModel model,
        ModelOwnershipSnapshot current
    ) {
        if (current != null) {
            return current.modelSpecId();
        }
        String projectKey = modelPackage.dbt() == null ? "" : modelPackage.dbt().projectName();
        return UUID.nameUUIDFromBytes(
            (plan.tenantId() + ":" + plan.id() + ":" + projectKey + ":" + model.dbtUniqueId()).getBytes(
                    java.nio.charset.StandardCharsets.UTF_8
                )
        );
    }

    private static int targetRevision(ModelOwnershipSnapshot current, String proposedChecksum) {
        if (current == null) {
            return 1;
        }
        return Objects.equals(current.currentChecksum(), proposedChecksum)
            ? current.currentRevision()
            : current.currentRevision() + 1;
    }

    private static int targetImplementationRevision(ModelOwnershipSnapshot current, String proposedChecksum) {
        if (current == null) {
            return 1;
        }
        return Objects.equals(current.currentImplementationChecksum(), proposedChecksum)
            ? current.implementationRevision()
            : current.implementationRevision() + 1;
    }

    private static ModelType parseModelType(PackageModel model) {
        try {
            return model == null || model.semantics() == null || model.semantics().modelType() == null
                ? null
                : ModelType.valueOf(model.semantics().modelType().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private ResolvedDomain resolveDomainForModel(
        ValidatedInput input,
        List<ResolvedDomain> domains,
        PackageModel model,
        List<PreviewIssue> issues
    ) {
        String packageDomain = model.semantics() == null ? null : trim(model.semantics().domainCode());
        if (packageDomain == null) {
            issues.add(
                issue(
                    "MODEL_PACKAGE_SEMANTIC_METADATA_REQUIRED",
                    "$.package.models[" + model.dbtUniqueId() + "].semantics.domainCode",
                    model.dbtUniqueId(),
                    "A stable domainCode is required",
                    "Add semantic domainCode and regenerate the package"
                )
            );
            return null;
        }
        String hint = input.domainMappings().getOrDefault(packageDomain, packageDomain);
        List<ResolvedDomain> matches = domains
            .stream()
            .filter(domain ->
                sameHint(hint, domain.domainId().toString()) ||
                sameHint(hint, domain.code()) ||
                sameHint(hint, domain.name())
            )
            .toList();
        if (matches.size() != 1) {
            issues.add(
                issue(
                    "MODEL_IMPORT_CONVERSION_BLOCKED",
                    "$.context.domainMappings." + packageDomain,
                    model.dbtUniqueId(),
                    matches.isEmpty()
                        ? "No target domain matches the package domain code"
                        : "The domain mapping is ambiguous in the current plan",
                    "Choose one visible confirmed domain code in the import wizard"
                )
            );
            return null;
        }
        ResolvedDomain match = matches.getFirst();
        if (!"CONFIRMED".equals(match.confirmationStatus()) || !"AVAILABLE".equals(match.resolutionStatus())) {
            issues.add(
                issue(
                    "MODEL_IMPORT_CONVERSION_BLOCKED",
                    "$.context.domainMappings." + packageDomain,
                    model.dbtUniqueId(),
                    "The mapped target domain is not confirmed and available",
                    "Confirm and revalidate the domain in the warehouse plan"
                )
            );
        }
        return match;
    }

    private List<ResolvedBinding> resolveSourcesForModel(
        ValidatedInput input,
        List<ResolvedBinding> availableBindings,
        PackageGraph graph,
        PackageModel model,
        Set<String> sourceNodeIds,
        List<PreviewIssue> issues
    ) {
        TreeMap<String, RelationHint> relations = new TreeMap<>();
        for (String sourceNodeId : sourceNodeIds) {
            SourceNode sourceNode = graph.sources().get(sourceNodeId);
            if (sourceNode != null) {
                relations.put(
                    sourceNodeId,
                    new RelationHint(sourceNodeId, sourceNode.name(), input.sourceMappings().get(sourceNodeId))
                );
            }
        }
        if (model.semantics() != null) {
            for (SourceRef sourceRef : safe(model.semantics().sourceRefs())) {
                if (sourceRef == null || isInternalSourceRef(sourceRef, graph)) {
                    continue;
                }
                String reference = trim(sourceRef.ref());
                if (reference != null) {
                    relations.putIfAbsent(
                        reference,
                        new RelationHint(reference, reference, input.sourceMappings().get(reference))
                    );
                }
            }
        }

        LinkedHashMap<UUID, ResolvedBinding> resolved = new LinkedHashMap<>();
        for (RelationHint relation : relations.values()) {
            String requestedIdentity = trim(relation.mappingHint());
            List<ResolvedBinding> matches = availableBindings
                .stream()
                .filter(binding ->
                    sameHint(requestedIdentity, binding.bindingId().toString()) ||
                    binding.matches(requestedIdentity == null ? relation.uniqueId() : requestedIdentity) ||
                    binding.matches(requestedIdentity == null ? relation.name() : requestedIdentity)
                )
                .toList();
            if (matches.size() != 1) {
                issues.add(
                    issue(
                        "MODEL_IMPORT_SOURCE_NOT_CONFIRMED",
                        "$.context.sourceMappings." + relation.uniqueId(),
                        model.dbtUniqueId(),
                        matches.isEmpty()
                            ? "No plan source binding matches relation " + relation.uniqueId()
                            : "More than one plan source binding matches relation " + relation.uniqueId(),
                        "Choose one visible confirmed source binding in the import wizard"
                    )
                );
                continue;
            }
            ResolvedBinding binding = matches.getFirst();
            resolved.put(binding.bindingId(), binding);
            if (
                !"CONFIRMED".equals(binding.confirmationStatus()) ||
                !"AVAILABLE".equals(binding.resolutionStatus()) ||
                binding.freshness() == SourceFreshness.UNKNOWN
            ) {
                issues.add(
                    issue(
                        "MODEL_IMPORT_SOURCE_NOT_CONFIRMED",
                        "$.context.sourceMappings." + relation.uniqueId(),
                        model.dbtUniqueId(),
                        "The matched source is not CONFIRMED and AVAILABLE",
                        "Confirm and revalidate the source in the warehouse plan"
                    )
                );
            } else if (binding.freshness() == SourceFreshness.STALE) {
                issues.add(
                    issue(
                        "MODEL_IMPORT_SOURCE_VERSION_STALE",
                        "$.context.sourceMappings." + relation.uniqueId(),
                        model.dbtUniqueId(),
                        "The confirmed source version differs from the current resolved version",
                        "Review the source change and explicitly reconfirm its current version"
                    )
                );
            }
        }
        return resolved.values().stream().sorted(Comparator.comparing(binding -> binding.bindingId().toString())).toList();
    }

    private static boolean isInternalSourceRef(SourceRef sourceRef, PackageGraph graph) {
        if (sourceRef == null || isBlank(sourceRef.ref())) {
            return false;
        }
        String kind = sourceRef.kind() == null ? "" : sourceRef.kind().trim().toUpperCase(Locale.ROOT);
        return "DBT_MODEL".equals(kind) && graph.nodes().contains(sourceRef.ref());
    }

    private void addReverseLayerIssues(
        PackageModel model,
        Collection<String> dependencies,
        Map<String, PackageModel> models,
        List<PreviewIssue> issues
    ) {
        int modelLayer = layerRank(model.semantics() == null ? null : model.semantics().layer());
        for (String dependency : dependencies) {
            PackageModel upstream = models.get(dependency);
            int upstreamLayer = layerRank(upstream == null || upstream.semantics() == null ? null : upstream.semantics().layer());
            if (modelLayer >= 0 && upstreamLayer > modelLayer) {
                issues.add(
                    issue(
                        "MODEL_IMPORT_CONVERSION_BLOCKED",
                        "$.package.models[" + model.dbtUniqueId() + "].dependencies",
                        model.dbtUniqueId(),
                        "Dependency points from layer " +
                        model.semantics().layer() +
                        " to downstream layer " +
                        upstream.semantics().layer(),
                        "Restore upstream-to-downstream warehouse layer ordering"
                    )
                );
            }
        }
    }

    private void addPackageIssues(ModelPackage modelPackage, String modelUniqueId, List<PreviewIssue> issues) {
        for (ModelPackageContract.ImportIssue packageIssue : safe(modelPackage.issues())) {
            if (
                packageIssue != null &&
                (isBlank(packageIssue.modelUniqueId()) || modelUniqueId.equals(packageIssue.modelUniqueId()))
            ) {
                issues.add(
                    new PreviewIssue(
                        packageIssue.code(),
                        parseSeverity(packageIssue.severity()),
                        packageIssue.fieldPath(),
                        modelUniqueId,
                        safeMessage(packageIssue.message()),
                        safeRecovery(packageIssue.recoveryAction())
                    )
                );
            }
        }
    }

    private ArrayNode sourceProjection(Collection<ResolvedBinding> bindings) {
        ArrayNode result = objectMapper.createArrayNode();
        for (ResolvedBinding binding : bindings) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("bindingId", binding.bindingId().toString());
            node.put("sourceType", binding.sourceType());
            if ("AVAILABLE".equals(binding.resolutionStatus())) {
                node.put("sourceId", binding.sourceId());
                node.put("displayName", binding.displayName());
            }
            node.put("confirmedVersion", binding.confirmedVersion());
            node.put("resolvedVersion", binding.resolvedVersion());
            node.put("resolutionStatus", binding.resolutionStatus());
            node.put("freshness", binding.freshness().name());
            result.add(node);
        }
        return result;
    }

    private JsonNode contextSnapshot(
        PlanSnapshot plan,
        WarehousePlanActor actor,
        ResolvedContext context,
        List<PendingItem> items
    ) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode planNode = root.putObject("plan");
        planNode.put("tenantId", plan.tenantId());
        planNode.put("planId", plan.id().toString());
        planNode.put("lifecycleStatus", plan.lifecycleStatus().name());
        planNode.put("version", plan.version());
        planNode.put("businessScopeVersion", plan.businessScopeVersion());
        planNode.put("sourcesVersion", plan.sourcesVersion());
        ObjectNode actorNode = root.putObject("actor");
        actorNode.put("actorId", actor == null ? null : actor.ownerId());
        actorNode.put("actorDepartmentId", actor == null ? null : actor.ownerDepartmentId());

        ArrayNode domainsNode = root.putArray("domains");
        for (ResolvedDomain domain : context.domains()) {
            ObjectNode node = domainsNode.addObject();
            node.put("domainId", domain.domainId().toString());
            node.put("confirmationStatus", domain.confirmationStatus());
            node.put("resolutionStatus", domain.resolutionStatus());
            if ("AVAILABLE".equals(domain.resolutionStatus())) {
                node.put("code", domain.code());
                node.put("name", domain.name());
            }
            if (domain.lastValidatedAt() != null) {
                node.put("lastValidatedAt", domain.lastValidatedAt().toString());
            }
        }
        ArrayNode sourcesNode = root.putArray("sources");
        Set<UUID> usedBindingIds = new HashSet<>();
        for (PendingItem item : items) {
            item.sourceSnapshot().forEach(node -> {
                if (node.hasNonNull("bindingId")) {
                    usedBindingIds.add(UUID.fromString(node.path("bindingId").asText()));
                }
            });
        }
        for (ResolvedBinding binding : context.sources()) {
            if (!usedBindingIds.contains(binding.bindingId())) {
                continue;
            }
            ObjectNode node = sourcesNode.addObject();
            node.put("bindingId", binding.bindingId().toString());
            node.put("sourceType", binding.sourceType());
            node.put("confirmationStatus", binding.confirmationStatus());
            node.put("resolutionStatus", binding.resolutionStatus());
            node.put("confirmedVersion", binding.confirmedVersion());
            node.put("resolvedVersion", binding.resolvedVersion());
            node.put("freshness", binding.freshness().name());
            if (binding.lastValidatedAt() != null) {
                node.put("lastValidatedAt", binding.lastValidatedAt().toString());
            }
        }
        ArrayNode ownershipNode = root.putArray("ownership");
        for (PendingItem item : items) {
            ObjectNode node = ownershipNode.addObject();
            node.put("dbtUniqueId", item.dbtUniqueId());
            node.put("ownerIdentity", item.ownerIdentity());
            if (item.currentModelSpecId() != null) {
                node.put("currentModelSpecId", item.currentModelSpecId().toString());
                node.put("currentRevision", item.currentRevision());
            }
            node.put("modelSpecChecksum", item.modelSpecChecksum());
            node.put("implementationChecksum", item.implementationChecksum());
            node.put("effectiveSqlChecksum", item.effectiveSqlChecksum());
        }
        root.put("previewRulesVersion", ModelSpecImportPreviewContract.PREVIEW_RULES_VERSION);
        return root;
    }

    private String previewHash(
        ValidatedInput input,
        PlanSnapshot plan,
        WarehousePlanActor actor,
        JsonNode contextSnapshot,
        List<PendingItem> items,
        String applyPayloadChecksum,
        List<Candidate> candidates
    ) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("packageId", input.modelPackage().packageId());
        root.put("packageChecksum", input.modelPackage().packageChecksum());
        root.put("packageSchemaVersion", input.modelPackage().schemaVersion());
        root.put("applyPayloadChecksum", applyPayloadChecksum);
        root.set("selectedUniqueIds", objectMapper.valueToTree(input.selectedUniqueIds()));
        root.set("domainMappingHints", objectMapper.valueToTree(input.domainMappings()));
        root.set("sourceMappingHints", objectMapper.valueToTree(input.sourceMappings()));
        root.set("serverContext", contextSnapshot);
        root.set("applyPlanCandidates", objectMapper.valueToTree(candidates));
        ArrayNode topology = root.putArray("topology");
        for (PendingItem item : items) {
            ObjectNode node = topology.addObject();
            node.put("dbtUniqueId", item.dbtUniqueId());
            node.put("action", item.action().name());
            node.put("conversionMode", item.conversionMode().name());
            node.put("modelSpecChecksum", item.modelSpecChecksum());
            node.put("implementationChecksum", item.implementationChecksum());
            node.put("effectiveSqlChecksum", item.effectiveSqlChecksum());
            node.set("dependency", item.dependencySnapshot());
            node.set("sources", item.sourceSnapshot());
            node.set("issues", objectMapper.valueToTree(item.issues()));
        }
        root.put("tenantId", plan.tenantId());
        root.put("actorId", actor == null ? null : actor.ownerId());
        return hash(root);
    }

    private void persist(
        ValidatedInput input,
        PlanSnapshot plan,
        WarehousePlanActor actor,
        PreviewBuild preview
    ) {
        if (actor == null || isBlank(actor.ownerId())) {
            throw new ModelSpecImportPreviewException(
                "WAREHOUSE_PLAN_AUTHENTICATED_ACTOR_REQUIRED",
                "An authenticated actor is required for model import preview",
                ModelSpecImportPreviewContract.Kind.FORBIDDEN,
                null
            );
        }
        ObjectNode normalizedRequest = objectMapper.createObjectNode();
        normalizedRequest.put("planId", plan.id().toString());
        normalizedRequest.set("selectedUniqueIds", objectMapper.valueToTree(input.selectedUniqueIds()));
        normalizedRequest.set("domainMappings", objectMapper.valueToTree(input.domainMappings()));
        normalizedRequest.set("sourceMappings", objectMapper.valueToTree(input.sourceMappings()));
        normalizedRequest.set(
            "renameMappings",
            objectMapper.valueToTree(
                input.renameSourcesByTarget()
                    .entrySet()
                    .stream()
                    .map(entry -> new RenameMapping(entry.getValue(), entry.getKey()))
                    .toList()
            )
        );

        PersistedRun run = new PersistedRun(
            preview.runId(),
            serverTenantId,
            plan.id(),
            input.modelPackage().packageChecksum(),
            input.modelPackage().schemaVersion(),
            json(input.modelPackage()),
            json(normalizedRequest),
            json(preview.contextSnapshot()),
            preview.applyPayload().json(),
            preview.applyPayload().checksum(),
            json(preview.applyPlan()),
            preview.applyPlanChecksum(),
            preview.previewHash(),
            preview.status(),
            json(preview.summary()),
            preview.expiresAt(),
            actor.ownerId(),
            preview.createdAt()
        );
        List<PersistedItem> items = new ArrayList<>();
        int sequence = 0;
        for (PendingItem item : preview.pendingItems()) {
            items.add(
                new PersistedItem(
                    UUID.randomUUID(),
                    sequence++,
                    item.dbtUniqueId(),
                    "MODEL",
                    true,
                    item.action(),
                    item.conversionMode(),
                    input.modelPackage().packageChecksum(),
                    item.modelSpecChecksum(),
                    item.currentModelSpecId(),
                    item.currentRevision(),
                    json(item.proposedModelSpec()),
                    json(item.proposedImplementation()),
                    json(item.dependencySnapshot()),
                    json(item.sourceSnapshot()),
                    json(item.issues())
                )
            );
        }
        String correlationId = DbtImplementationDraftCorrelation.currentOrCreate();
        commits.commit(
            run,
            items,
            new PreviewAuditFacts(
                preview.runId(),
                serverTenantId,
                plan.id(),
                actor.ownerId(),
                preview.previewHash(),
                input.modelPackage().packageChecksum(),
                preview.summary(),
                previewAuditOutcome(preview.summary()),
                previewAuditFailures(preview.pendingItems(), correlationId),
                correlationId
            )
        );
    }

    private static PreviewAuditOutcome previewAuditOutcome(PreviewSummary summary) {
        if (summary == null) return PreviewAuditOutcome.BLOCKED;
        int unresolved = summary.blocked() + summary.conflict();
        if (unresolved == 0) return PreviewAuditOutcome.SUCCESS;
        return summary.ready() > 0 ? PreviewAuditOutcome.PARTIAL : PreviewAuditOutcome.BLOCKED;
    }

    private static List<PreviewFailureFact> previewAuditFailures(List<PendingItem> items, String correlationId) {
        if (items == null || items.isEmpty()) return List.of();
        return items
            .stream()
            .filter(item -> item != null && (item.action() == BLOCKED || item.action() == CONFLICT))
            .map(item -> previewAuditFailure(item, correlationId))
            .toList();
    }

    private static PreviewFailureFact previewAuditFailure(PendingItem item, String correlationId) {
        PreviewIssue issue = primaryAuditIssue(item.issues());
        String code = safeAuditCode(issue == null ? null : issue.code());
        String category = previewAuditCategory(item.action(), code);
        return new PreviewFailureFact(
            safeAuditIdentity(item.dbtUniqueId()),
            code,
            "PREVIEW",
            category,
            false,
            previewAuditRecoveryAction(item.action(), category, issue == null ? null : issue.recoveryAction()),
            correlationId
        );
    }

    private static PreviewIssue primaryAuditIssue(List<PreviewIssue> issues) {
        if (issues == null || issues.isEmpty()) return null;
        return issues
            .stream()
            .filter(Objects::nonNull)
            .filter(issue -> issue.severity() == Severity.ERROR)
            .findFirst()
            .orElseGet(() -> issues.stream().filter(Objects::nonNull).findFirst().orElse(null));
    }

    private static String safeAuditCode(String code) {
        String normalized = trim(code);
        if (
            normalized == null ||
            normalized.length() > 128 ||
            !normalized.chars().allMatch(value -> value == '_' || Character.isUpperCase(value) || Character.isDigit(value))
        ) {
            return "MODEL_IMPORT_PREVIEW_UNRESOLVED";
        }
        return normalized;
    }

    private static String safeAuditIdentity(String identity) {
        String normalized = trim(identity);
        if (normalized == null) return "unknown";
        StringBuilder safe = new StringBuilder(Math.min(normalized.length(), 256));
        normalized
            .chars()
            .limit(256)
            .forEach(value ->
                safe.append(
                    Character.isLetterOrDigit(value) || value == '.' || value == '_' || value == '-' || value == ':'
                        ? (char) value
                        : '_'
                )
            );
        return safe.toString();
    }

    private static String previewAuditCategory(Action action, String code) {
        if (action == CONFLICT || code.contains("CONFLICT")) return "CONFLICT";
        if (code.contains("DEPENDENC")) return "DEPENDENCY";
        if (code.contains("PERMISSION") || code.contains("FORBIDDEN") || code.contains("AUTHORIZ")) return "PERMISSION";
        if (code.contains("STALE") || code.contains("EXPIRED") || code.contains("REVISION")) return "STALE";
        if (code.contains("SECURITY") || code.contains("SECRET") || code.contains("ZIP")) return "SECURITY";
        if (code.contains("PERSIST") || code.contains("DATABASE") || code.contains("STORAGE") || code.contains("AUDIT")) {
            return "PERSISTENCE";
        }
        if (code.contains("INTERNAL") || code.contains("UNAVAILABLE") || code.contains("TIMEOUT")) return "INTERNAL";
        return "VALIDATION";
    }

    private static String previewAuditRecoveryAction(Action action, String category, String requestedAction) {
        String normalized = trim(requestedAction);
        if (normalized != null) {
            normalized = normalized.toUpperCase(Locale.ROOT);
            if (AUDIT_RECOVERY_ACTIONS.contains(normalized)) return normalized;
        }
        if (action == CONFLICT) return "RESOLVE_CONFLICT";
        return switch (category) {
            case "DEPENDENCY" -> "INCLUDE_DEPENDENCY";
            case "PERMISSION" -> "REAUTHORIZE";
            case "STALE" -> "REFRESH_PREVIEW";
            case "SECURITY" -> "REUPLOAD";
            case "PERSISTENCE", "INTERNAL" -> "CONTACT_ADMIN";
            default -> "COMPLETE_MAPPING";
        };
    }

    private static List<String> stableTopologicalOrder(
        Collection<String> nodes,
        Map<String, ? extends Set<String>> dependencies
    ) {
        TreeMap<String, Integer> indegree = new TreeMap<>();
        TreeMap<String, TreeSet<String>> dependents = new TreeMap<>();
        Set<String> nodeSet = new TreeSet<>(nodes);
        for (String node : nodeSet) {
            Set<String> upstream = dependencies.containsKey(node) ? dependencies.get(node) : Set.of();
            int count = 0;
            for (String dependency : upstream) {
                if (nodeSet.contains(dependency)) {
                    count++;
                    dependents.computeIfAbsent(dependency, ignored -> new TreeSet<>()).add(node);
                }
            }
            indegree.put(node, count);
        }
        PriorityQueue<String> ready = new PriorityQueue<>();
        indegree.forEach((node, count) -> {
            if (count == 0) {
                ready.add(node);
            }
        });
        List<String> result = new ArrayList<>();
        while (!ready.isEmpty()) {
            String node = ready.remove();
            result.add(node);
            for (String dependent : dependents.getOrDefault(node, new TreeSet<>())) {
                int remaining = indegree.computeIfPresent(dependent, (ignored, value) -> value - 1);
                if (remaining == 0) {
                    ready.add(dependent);
                }
            }
        }
        nodeSet.stream().filter(node -> !result.contains(node)).forEach(result::add);
        return List.copyOf(result);
    }

    private PreviewSummary summarize(List<PendingItem> items) {
        int create = count(items, Action.CREATE);
        int update = count(items, Action.UPDATE);
        int skip = count(items, Action.SKIP);
        int conflict = count(items, CONFLICT);
        int blocked = count(items, BLOCKED);
        return new PreviewSummary(items.size(), create + update + skip, blocked, create, update, skip, conflict);
    }

    private static int count(List<PendingItem> items, Action action) {
        return (int) items.stream().filter(item -> item.action() == action).count();
    }

    private ApplyPlan buildApplyPlan(
        UUID runId,
        UUID planId,
        String previewHash,
        String packageChecksum,
        String applyPayloadChecksum,
        List<PendingItem> items,
        List<Candidate> candidates
    ) {
        return new ApplyPlan(
            runId,
            planId,
            previewHash,
            packageChecksum,
            applyPayloadChecksum,
            items.stream().map(PendingItem::dbtUniqueId).toList(),
            candidates
        );
    }

    private List<Candidate> applyPlanCandidates(List<PendingItem> items) {
        return items
            .stream()
            .map(item ->
                new Candidate(
                    item.dbtUniqueId(),
                    item.targetModelSpecId(),
                    item.targetRevision(),
                    item.targetImplementationRevision(),
                    item.expectedModelRevision(),
                    item.expectedModelChecksum(),
                    item.expectedModelStatus(),
                    item.expectedImplementationRevision(),
                    item.expectedImplementationChecksum(),
                    item.modelSpecChecksum(),
                    item.implementationChecksum(),
                    item.action().name(),
                    item.conversionMode().name(),
                    json(item.proposedModelSpec()),
                    json(item.proposedImplementation()),
                    json(item.artifact()),
                    json(item.evidence()),
                    json(item.dependencySnapshot()),
                    json(item.sourceSnapshot()),
                    item.implementationChecksum(),
                    dependencyUniqueIds(item.dependencySnapshot())
                )
            )
            .toList();
    }

    private static List<String> dependencyUniqueIds(JsonNode dependencySnapshot) {
        if (dependencySnapshot == null || !dependencySnapshot.path("canonicalDependencies").isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        dependencySnapshot.path("canonicalDependencies").forEach(node -> {
            String value = node.isTextual() ? node.asText() : node.path("dbtUniqueId").asText(null);
            if (value != null && !value.isBlank()) result.add(value);
        });
        return result.stream().distinct().sorted().toList();
    }

    private ObjectNode evidenceProjection(Projection canonical, List<PreviewIssue> issues) {
        ObjectNode evidence = objectMapper.createObjectNode();
        evidence.put("modelSpecChecksum", canonical.modelSpecChecksum());
        evidence.put("implementationChecksum", canonical.implementationChecksum());
        evidence.put("effectiveSqlChecksum", canonical.effectiveSqlChecksum());
        evidence.set("issues", objectMapper.valueToTree(issues));
        return evidence;
    }

    private void requireImportable(PlanSnapshot plan) {
        if (!IMPORTABLE_LIFECYCLES.contains(plan.lifecycleStatus())) {
            throw new ModelSpecImportPreviewException(
                "MODEL_IMPORT_CONVERSION_BLOCKED",
                "Warehouse plan lifecycle does not allow model package import",
                UNPROCESSABLE,
                Map.of("lifecycleStatus", plan.lifecycleStatus().name())
            );
        }
    }

    private String hash(JsonNode value) {
        try {
            return ModelPackageChecksum.sha256(objectMapper.writeValueAsBytes(canonicalize(value)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Model import preview cannot be canonicalized", exception);
        }
    }

    private JsonNode canonicalize(JsonNode source) {
        if (source == null || source.isNull()) {
            return objectMapper.nullNode();
        }
        if (source.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            TreeMap<String, JsonNode> fields = new TreeMap<>();
            source.fields().forEachRemaining(entry -> fields.put(entry.getKey(), entry.getValue()));
            fields.forEach((name, value) -> result.set(name, canonicalize(value)));
            return result;
        }
        if (source.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            source.forEach(value -> result.add(canonicalize(value)));
            return result;
        }
        return source.deepCopy();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Model import preview snapshot cannot be serialized", exception);
        }
    }

    private String checksumModelSpecProjection(JsonNode projection) {
        try {
            CreateModelSpecCommand command = objectMapper.treeToValue(projection, CreateModelSpecCommand.class);
            return modelSpecSnapshotCodec.contentChecksum(command);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalStateException("Reconciled ModelSpec projection is invalid", exception);
        }
    }

    private SourceLocator parseLocator(String locatorJson) {
        if (isBlank(locatorJson)) {
            return null;
        }
        try {
            return objectMapper.readValue(locatorJson, SourceLocator.class);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private static SourceType parseSourceType(String value) {
        try {
            return value == null ? null : SourceType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static ConversionMode conversionMode(PackageModel model) {
        if (model == null || model.conversion() == null || model.conversion().mode() == null) {
            return ConversionMode.BLOCKED;
        }
        return switch (model.conversion().mode()) {
            case DESIGNER_GENERATED -> ConversionMode.DESIGNER_GENERATED;
            case DBT_BACKED -> ConversionMode.DBT_BACKED;
            case BLOCKED, TECHNICAL_ONLY -> ConversionMode.BLOCKED;
        };
    }

    private static int layerRank(String layer) {
        if (layer == null) {
            return -1;
        }
        return switch (layer.trim().toUpperCase(Locale.ROOT)) {
            case "ODS" -> 0;
            case "STG" -> 1;
            case "DWD" -> 2;
            case "DWS" -> 3;
            case "ADS" -> 4;
            default -> -1;
        };
    }

    private static Severity parseSeverity(String value) {
        try {
            return value == null ? Severity.WARNING : Severity.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return Severity.WARNING;
        }
    }

    private static Map<String, String> normalizedHints(Map<String, String> hints) {
        TreeMap<String, String> result = new TreeMap<>();
        if (hints != null) {
            hints.forEach((key, value) -> {
                String normalizedKey = trim(key);
                String normalizedValue = trim(value);
                if (normalizedKey != null && normalizedValue != null) {
                    result.put(normalizedKey, normalizedValue);
                }
            });
        }
        return Map.copyOf(result);
    }

    private static PreviewIssue issue(
        String code,
        String fieldPath,
        String modelUniqueId,
        String message,
        String recoveryAction
    ) {
        return new PreviewIssue(code, Severity.ERROR, fieldPath, modelUniqueId, safeMessage(message), safeRecovery(recoveryAction));
    }

    private static String safeMessage(String value) {
        return isBlank(value) ? "Model import preview validation failed" : value.trim();
    }

    private static String safeRecovery(String value) {
        return isBlank(value) ? "Review the import context and retry preview" : value.trim();
    }

    private static ModelSpecImportPreviewException badRequest(String code, String message, Object details) {
        return new ModelSpecImportPreviewException(code, message, BAD_REQUEST, details);
    }

    private static ModelSpecImportPreviewException notFound() {
        return new ModelSpecImportPreviewException(
            "MODEL_IMPORT_PREVIEW_NOT_FOUND",
            "Model import preview was not found",
            NOT_FOUND,
            null
        );
    }

    private static String requireText(String value, String code, String message) {
        if (isBlank(value)) {
            throw new ModelSpecImportPreviewException(code, message, BAD_REQUEST, null);
        }
        return value.trim();
    }

    private static boolean sameHint(String left, String right) {
        return !isBlank(left) && !isBlank(right) && left.trim().equalsIgnoreCase(right.trim());
    }

    private static String trim(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record ValidatedInput(
        ModelPackage modelPackage,
        UUID planId,
        List<String> selectedUniqueIds,
        Map<String, String> domainMappings,
        Map<String, String> sourceMappings,
        Map<String, PackageModel> models,
        Map<String, List<StandardBinding>> standardBindings,
        Map<String, ModelBusinessContext> businessContexts,
        Map<String, String> renameSourcesByTarget,
        Set<String> semanticOverrideIds
    ) {}

    private record ModelBusinessContext(UUID businessProcessId, UUID dataMartId, UUID subjectDomainId) {
        private static final ModelBusinessContext EMPTY = new ModelBusinessContext(null, null, null);
    }

    private record ResolvedContext(List<ResolvedDomain> domains, List<ResolvedBinding> sources) {}

    private record ResolvedDomain(
        UUID domainId,
        String confirmationStatus,
        String resolutionStatus,
        String code,
        String name,
        Instant lastValidatedAt
    ) {}

    private record ResolvedBinding(
        UUID bindingId,
        String sourceType,
        String sourceId,
        String objectName,
        String dbtUniqueId,
        String confirmedVersion,
        String resolvedVersion,
        String confirmationStatus,
        String resolutionStatus,
        SourceFreshness freshness,
        String displayName,
        Instant lastValidatedAt
    ) {
        boolean matches(String hint) {
            return (
                sameHint(hint, sourceId) ||
                sameHint(hint, objectName) ||
                sameHint(hint, dbtUniqueId) ||
                sameHint(hint, displayName)
            );
        }
    }

    private record RelationHint(String uniqueId, String name, String mappingHint) {}

    private record PendingItem(
        String dbtUniqueId,
        Action action,
        ConversionMode conversionMode,
        JsonNode proposedModelSpec,
        JsonNode proposedImplementation,
        List<PreviewIssue> issues,
        String modelSpecChecksum,
        UUID currentModelSpecId,
        Integer currentRevision,
        JsonNode dependencySnapshot,
        ArrayNode sourceSnapshot,
        String ownerIdentity,
        String implementationChecksum,
        String effectiveSqlChecksum,
        UUID targetModelSpecId,
        int targetRevision,
        int targetImplementationRevision,
        int expectedModelRevision,
        String expectedModelChecksum,
        String expectedModelStatus,
        int expectedImplementationRevision,
        String expectedImplementationChecksum,
        ModelType modelType,
        JsonNode artifact,
        JsonNode evidence
    ) {}

    private record PreviewBuild(
        UUID runId,
        String previewHash,
        RunStatus status,
        Instant createdAt,
        Instant expiresAt,
        PreviewSummary summary,
        List<PreviewItem> items,
        List<PendingItem> pendingItems,
        JsonNode contextSnapshot,
        SanitizedPayload applyPayload,
        ApplyPlan applyPlan,
        String applyPlanChecksum
    ) {}

    private static final class PackageGraph {

        private final Map<String, PackageModel> models;
        private final Map<String, TechnicalNode> technicalNodes;
        private final Map<String, SourceNode> sources;
        private final Map<String, List<String>> dependencies;
        private final Set<String> nodes;

        private PackageGraph(
            Map<String, PackageModel> models,
            Map<String, TechnicalNode> technicalNodes,
            Map<String, SourceNode> sources,
            Map<String, List<String>> dependencies
        ) {
            this.models = models;
            this.technicalNodes = technicalNodes;
            this.sources = sources;
            this.dependencies = dependencies;
            TreeSet<String> allNodes = new TreeSet<>();
            allNodes.addAll(models.keySet());
            allNodes.addAll(technicalNodes.keySet());
            allNodes.addAll(sources.keySet());
            this.nodes = Set.copyOf(allNodes);
        }

        static PackageGraph from(ModelPackage modelPackage) {
            TreeMap<String, PackageModel> models = new TreeMap<>();
            TreeMap<String, TechnicalNode> technicalNodes = new TreeMap<>();
            TreeMap<String, SourceNode> sources = new TreeMap<>();
            TreeMap<String, List<String>> dependencies = new TreeMap<>();
            for (PackageModel model : safe(modelPackage.models())) {
                models.put(model.dbtUniqueId(), model);
                dependencies.put(model.dbtUniqueId(), sorted(model.dependencies()));
            }
            for (TechnicalNode technicalNode : safe(modelPackage.technicalNodes())) {
                technicalNodes.put(technicalNode.dbtUniqueId(), technicalNode);
                dependencies.put(technicalNode.dbtUniqueId(), sorted(technicalNode.dependencies()));
            }
            for (SourceNode source : safe(modelPackage.sources())) {
                sources.put(source.dbtUniqueId(), source);
                dependencies.put(source.dbtUniqueId(), List.of());
            }
            return new PackageGraph(
                Map.copyOf(models),
                Map.copyOf(technicalNodes),
                Map.copyOf(sources),
                Map.copyOf(dependencies)
            );
        }

        Map<String, SourceNode> sources() {
            return sources;
        }

        Set<String> nodes() {
            return nodes;
        }

        TreeSet<String> canonicalDependencies(String root, Collection<String> canonicalIds) {
            Set<String> canonical = new HashSet<>(canonicalIds);
            TreeSet<String> result = new TreeSet<>();
            HashSet<String> visited = new HashSet<>();
            for (String dependency : dependencies.getOrDefault(root, List.of())) {
                collectCanonical(dependency, canonical, visited, result);
            }
            return result;
        }

        TreeSet<String> reachableSources(String root) {
            TreeSet<String> result = new TreeSet<>();
            HashSet<String> visited = new HashSet<>();
            for (String dependency : dependencies.getOrDefault(root, List.of())) {
                collectSources(dependency, visited, result);
            }
            return result;
        }

        TreeSet<String> technicalPathNodes(String root) {
            TreeSet<String> result = new TreeSet<>();
            HashSet<String> visited = new HashSet<>();
            for (String dependency : dependencies.getOrDefault(root, List.of())) {
                collectTechnical(dependency, visited, result);
            }
            return result;
        }

        Set<String> reachableNodes(String root) {
            TreeSet<String> result = new TreeSet<>();
            collectReachable(root, new HashSet<>(), result);
            return result;
        }

        Set<String> cycleNodes() {
            HashMap<String, Integer> states = new HashMap<>();
            ArrayList<String> stack = new ArrayList<>();
            TreeSet<String> cycles = new TreeSet<>();
            for (String node : new TreeSet<>(nodes)) {
                detectCycles(node, states, stack, cycles);
            }
            return Set.copyOf(cycles);
        }

        private void collectCanonical(
            String node,
            Set<String> canonical,
            Set<String> visited,
            Set<String> result
        ) {
            if (canonical.contains(node)) {
                result.add(node);
                return;
            }
            if (!visited.add(node) || sources.containsKey(node)) {
                return;
            }
            for (String dependency : dependencies.getOrDefault(node, List.of())) {
                collectCanonical(dependency, canonical, visited, result);
            }
        }

        private void collectSources(String node, Set<String> visited, Set<String> result) {
            if (sources.containsKey(node)) {
                result.add(node);
                return;
            }
            if (models.containsKey(node) || !visited.add(node)) {
                return;
            }
            for (String dependency : dependencies.getOrDefault(node, List.of())) {
                collectSources(dependency, visited, result);
            }
        }

        private void collectTechnical(String node, Set<String> visited, Set<String> result) {
            if (models.containsKey(node) || sources.containsKey(node) || !visited.add(node)) {
                return;
            }
            if (technicalNodes.containsKey(node)) {
                result.add(node);
            }
            for (String dependency : dependencies.getOrDefault(node, List.of())) {
                collectTechnical(dependency, visited, result);
            }
        }

        private void collectReachable(String node, Set<String> visited, Set<String> result) {
            if (!visited.add(node)) {
                return;
            }
            result.add(node);
            for (String dependency : dependencies.getOrDefault(node, List.of())) {
                collectReachable(dependency, visited, result);
            }
        }

        private void detectCycles(
            String node,
            Map<String, Integer> states,
            List<String> stack,
            Set<String> cycles
        ) {
            int state = states.getOrDefault(node, 0);
            if (state == 2) {
                return;
            }
            if (state == 1) {
                int start = stack.indexOf(node);
                if (start >= 0) {
                    cycles.addAll(stack.subList(start, stack.size()));
                }
                cycles.add(node);
                return;
            }
            states.put(node, 1);
            stack.add(node);
            for (String dependency : dependencies.getOrDefault(node, List.of())) {
                detectCycles(dependency, states, stack, cycles);
            }
            stack.remove(stack.size() - 1);
            states.put(node, 2);
        }

        private static List<String> sorted(List<String> values) {
            return safe(values).stream().filter(Objects::nonNull).map(String::trim).filter(value -> !value.isEmpty()).sorted().toList();
        }
    }
}
