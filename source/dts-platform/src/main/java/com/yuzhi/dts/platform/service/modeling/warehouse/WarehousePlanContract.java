package com.yuzhi.dts.platform.service.modeling.warehouse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Pure domain contract for the canonical warehouse planning aggregate. */
public final class WarehousePlanContract {

    public static final String BUSINESS_SCOPE_INCOMPLETE = "BUSINESS_SCOPE_INCOMPLETE";
    public static final String DOMAIN_PROCESS_CONFIRMATION_INCOMPLETE = "DOMAIN_PROCESS_CONFIRMATION_INCOMPLETE";
    public static final String CATEGORY_SCOPE_INCOMPLETE = "CATEGORY_SCOPE_INCOMPLETE";
    public static final String SOURCE_INVENTORY_INCOMPLETE = "SOURCE_INVENTORY_INCOMPLETE";
    public static final String PLANNING_POLICY_INCOMPLETE = "PLANNING_POLICY_INCOMPLETE";

    private WarehousePlanContract() {}

    public enum OnboardingMode {
        BUSINESS_FIRST,
        ASSET_FIRST,
    }

    public enum LifecycleStatus {
        DRAFT,
        BASELINE_READY,
        DESIGNING,
        VALIDATING,
        READY_TO_PUBLISH,
        PUBLISHED,
        ARCHIVED,
    }

    public enum EditUnit {
        PLAN_HEAD("plan-head"),
        BUSINESS_SCOPE("business-scope"),
        CATEGORY_SCOPE("category-scope"),
        SOURCES("sources"),
        SOURCE_MAPPINGS("source-mappings"),
        POLICY("policy");

        private final String resourceKey;

        EditUnit(String resourceKey) {
            this.resourceKey = resourceKey;
        }

        public String resourceKey() {
            return resourceKey;
        }
    }

    public enum ConfirmationStatus {
        CANDIDATE,
        CONFIRMED,
        EXCLUDED,
    }

    public enum CategoryReadiness {
        DRAFT,
        READY,
        BLOCKED,
    }

    public enum LayerScheme {
        CLASSIC_ODS_DWD_DWS_ADS,
    }

    public enum NamingPolicy {
        CLASSIC_LOWER_SNAKE,
        CLASSIC_UPPER_SNAKE,
    }

    public enum HistoryPolicy {
        PRESERVE_BUSINESS_HISTORY,
        LATEST_STATE_ONLY,
    }

    public enum StandardCoverage {
        NONE,
        KEY_AND_MEASURE,
        ALL_FIELDS,
    }

    public enum QualityGate {
        ADVISORY,
        BLOCKING,
    }

    public enum PlanningPolicyReadiness {
        DRAFT,
        MODEL_DESIGN_READY,
        IMPLEMENTATION_READY,
    }

    public enum SourceFreshness {
        CURRENT,
        STALE,
        UNKNOWN,
    }

    public enum SourceInventoryReadiness {
        DRAFT,
        READY,
        BLOCKED,
        NOT_REQUIRED_YET,
    }

    public enum SourceAction {
        CONFIRM,
        RECONFIRM,
        EXCLUDE,
    }

    public enum SourceChangeImpact {
        NONE,
        COMPATIBLE,
        BREAKING,
        REVIEW_REQUIRED,
    }

    public enum SourceType {
        CONNECTION_TABLE,
        CATALOG_TABLE,
        EXCEL_FILE,
        DBT_NODE,
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record SourceLocator(
        UUID assetId,
        UUID fileId,
        String projectKey,
        String uniqueId,
        UUID connectionId,
        String namespace,
        String objectName
    ) {
        public SourceLocator {
            projectKey = trimToNull(projectKey);
            uniqueId = trimToNull(uniqueId);
            namespace = trimToNull(namespace);
            objectName = trimToNull(objectName);
        }
    }

    public record InitialSourceRef(SourceType sourceType, String sourceId, String sourceVersion) {
        public InitialSourceRef {
            sourceId = trimToNull(sourceId);
            sourceVersion = trimToNull(sourceVersion);
        }
    }

    public record CreateWarehousePlanCommand(
        String name,
        String objective,
        String scope,
        String ownerId,
        String ownerDepartmentId,
        OnboardingMode onboardingMode,
        List<InitialSourceRef> initialSourceRefs,
        String idempotencyKey
    ) {
        public CreateWarehousePlanCommand {
            name = trimToNull(name);
            objective = trimToNull(objective);
            scope = trimToNull(scope);
            ownerId = trimToNull(ownerId);
            ownerDepartmentId = trimToNull(ownerDepartmentId);
            initialSourceRefs = initialSourceRefs == null
                ? List.of()
                : java.util.Collections.unmodifiableList(new ArrayList<>(initialSourceRefs));
            idempotencyKey = trimToNull(idempotencyKey);
        }
    }

    public record WarehousePlanHeader(
        UUID id,
        String tenantId,
        String code,
        String name,
        String objective,
        String scope,
        String ownerId,
        String ownerDepartmentId,
        OnboardingMode onboardingMode,
        LifecycleStatus lifecycleStatus,
        int version
    ) {}

    public record CreateWarehousePlanResult(
        UUID planId,
        WarehousePlanHeader plan,
        int version,
        String etag,
        List<SourceBinding> initialSourceBindings,
        String nextAction,
        boolean replayed
    ) {
        public CreateWarehousePlanResult {
            initialSourceBindings = immutable(initialSourceBindings);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record DomainBinding(UUID domainId, ConfirmationStatus confirmationStatus) {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record CategoryScopeCommand(List<DomainBinding> domainBindings) {
        public CategoryScopeCommand {
            domainBindings = immutable(domainBindings);
        }
    }

    public record CategoryBindingView(
        UUID domainId,
        ConfirmationStatus confirmationStatus,
        CatalogDomainResolutionPort.ResolutionStatus resolutionStatus,
        String name,
        String code,
        Instant lastValidatedAt
    ) {}

    public record CategoryScopeView(
        List<CategoryBindingView> domainBindings,
        CategoryReadiness readiness,
        List<DomainIssue> issues,
        Instant lastValidatedAt
    ) {
        public CategoryScopeView {
            domainBindings = immutable(domainBindings);
            issues = immutable(issues);
        }
    }

    public record ProcessBinding(
        String processId,
        UUID domainId,
        String processShape,
        ConfirmationStatus confirmationStatus
    ) {}

    public record MetricRequirement(
        UUID id,
        String metricRef,
        String name,
        String definition,
        UUID domainId,
        String processId,
        ConfirmationStatus confirmationStatus
    ) {}

    public record BusinessScope(
        boolean confirmed,
        List<DomainBinding> domainBindings,
        List<ProcessBinding> processBindings,
        List<MetricRequirement> metricRequirements
    ) {
        public BusinessScope {
            domainBindings = immutable(domainBindings);
            processBindings = immutable(processBindings);
            metricRequirements = immutable(metricRequirements);
        }
    }

    public record SourceBinding(
        UUID id,
        SourceType sourceType,
        String sourceId,
        String sourceVersion,
        ConfirmationStatus confirmationStatus,
        String exclusionReason
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record SourceBindingCommand(
        UUID bindingId,
        SourceType sourceType,
        SourceLocator locator,
        ConfirmationStatus confirmationStatus,
        String exclusionReason,
        SourceAction action,
        String expectedConfirmedVersion,
        String expectedCurrentVersion
    ) {
        public SourceBindingCommand(
            UUID bindingId,
            SourceType sourceType,
            SourceLocator locator,
            ConfirmationStatus confirmationStatus,
            String exclusionReason
        ) {
            this(bindingId, sourceType, locator, confirmationStatus, exclusionReason, null, null, null);
        }

        public SourceBindingCommand {
            exclusionReason = trimToNull(exclusionReason);
            expectedConfirmedVersion = trimToNull(expectedConfirmedVersion);
            expectedCurrentVersion = trimToNull(expectedCurrentVersion);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record SourceInventoryCommand(List<SourceBindingCommand> bindings) {
        public SourceInventoryCommand {
            bindings = immutable(bindings);
        }
    }

    public record SourceDiffSummary(int added, int removed, int changed) {
        public static SourceDiffSummary empty() {
            return new SourceDiffSummary(0, 0, 0);
        }
    }

    public record SourceSchemaChange(String field, String kind, Object before, Object after, String impact) {}

    public record SourceBindingView(
        UUID bindingId,
        SourceType sourceType,
        SourceLocator locator,
        String sourceId,
        ConfirmationStatus confirmationStatus,
        String exclusionReason,
        String displayName,
        String confirmedVersion,
        String resolvedVersion,
        SourceReferenceResolver.ResolutionStatus resolutionStatus,
        SourceFreshness freshness,
        Instant lastValidatedAt,
        String currentVersion,
        SourceChangeImpact changeImpact,
        SourceDiffSummary diffSummary,
        List<SourceSchemaChange> changes,
        List<SourceAction> allowedActions,
        String reasonCode,
        String statusSummary
    ) {
        public SourceBindingView(
            UUID bindingId,
            SourceType sourceType,
            SourceLocator locator,
            String sourceId,
            ConfirmationStatus confirmationStatus,
            String exclusionReason,
            String displayName,
            String confirmedVersion,
            String resolvedVersion,
            SourceReferenceResolver.ResolutionStatus resolutionStatus,
            SourceFreshness freshness,
            Instant lastValidatedAt
        ) {
            this(
                bindingId,
                sourceType,
                locator,
                sourceId,
                confirmationStatus,
                exclusionReason,
                displayName,
                confirmedVersion,
                resolvedVersion,
                resolutionStatus,
                freshness,
                lastValidatedAt,
                resolvedVersion,
                SourceChangeImpact.NONE,
                SourceDiffSummary.empty(),
                List.of(),
                List.of(),
                null,
                null
            );
        }

        public SourceBindingView {
            changes = immutable(changes);
            allowedActions = immutable(allowedActions);
        }
    }

    public record SourceInventoryView(
        List<SourceBindingView> bindings,
        SourceInventoryReadiness readiness,
        List<DomainIssue> issues,
        int version,
        String etag,
        Instant checkedAt,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
        public SourceInventoryView(
            List<SourceBindingView> bindings,
            SourceInventoryReadiness readiness,
            List<DomainIssue> issues,
            int version,
            String etag,
            Instant checkedAt
        ) {
            this(
                bindings,
                readiness,
                issues,
                version,
                etag,
                checkedAt,
                0,
                bindings == null ? 0 : bindings.size(),
                bindings == null ? 0 : bindings.size(),
                1
            );
        }

        public SourceInventoryView {
            bindings = immutable(bindings);
            issues = immutable(issues);
            if (page < 0 || size < 0 || totalElements < 0 || totalPages < 0) {
                throw new IllegalArgumentException("Source inventory pagination cannot be negative");
            }
        }
    }

    public record SourceBusinessMapping(
        UUID id,
        UUID sourceBindingId,
        UUID domainId,
        String processId,
        ConfirmationStatus confirmationStatus,
        String notes
    ) {}

    public record PlanningPolicy(
        String layerPolicyCode,
        String namingPolicyRef,
        String historyPolicy,
        String defaultTimeZone
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record PlanningPolicyCommand(
        String layerScheme,
        String namingPolicy,
        String historyPolicy,
        String defaultTimeZone,
        Boolean conceptualDesignAllowed,
        String standardCoverage,
        String qualityGate
    ) {
        public PlanningPolicyCommand(String layerScheme, String namingPolicy, String historyPolicy, String defaultTimeZone) {
            this(layerScheme, namingPolicy, historyPolicy, defaultTimeZone, false, null, null);
        }

        public PlanningPolicyCommand(
            String layerScheme,
            String namingPolicy,
            String historyPolicy,
            String defaultTimeZone,
            Boolean conceptualDesignAllowed
        ) {
            this(layerScheme, namingPolicy, historyPolicy, defaultTimeZone, conceptualDesignAllowed, null, null);
        }

        public PlanningPolicyCommand {
            layerScheme = trimToNull(layerScheme);
            namingPolicy = trimToNull(namingPolicy);
            historyPolicy = trimToNull(historyPolicy);
            defaultTimeZone = trimToNull(defaultTimeZone);
            conceptualDesignAllowed = Boolean.TRUE.equals(conceptualDesignAllowed);
            standardCoverage = trimToNull(standardCoverage);
            qualityGate = trimToNull(qualityGate);
        }
    }

    public record PlanningPolicyView(
        LayerScheme layerScheme,
        NamingPolicy namingPolicy,
        HistoryPolicy historyPolicy,
        String defaultTimeZone,
        boolean conceptualDesignAllowed,
        StandardCoverage standardCoverage,
        QualityGate qualityGate,
        PlanningPolicyReadiness readiness,
        List<DomainIssue> issues
    ) {
        public PlanningPolicyView(
            LayerScheme layerScheme,
            NamingPolicy namingPolicy,
            HistoryPolicy historyPolicy,
            String defaultTimeZone,
            PlanningPolicyReadiness readiness,
            List<DomainIssue> issues
        ) {
            this(
                layerScheme,
                namingPolicy,
                historyPolicy,
                defaultTimeZone,
                false,
                StandardCoverage.KEY_AND_MEASURE,
                QualityGate.BLOCKING,
                readiness,
                issues
            );
        }

        public PlanningPolicyView(
            LayerScheme layerScheme,
            NamingPolicy namingPolicy,
            HistoryPolicy historyPolicy,
            String defaultTimeZone,
            boolean conceptualDesignAllowed,
            PlanningPolicyReadiness readiness,
            List<DomainIssue> issues
        ) {
            this(
                layerScheme,
                namingPolicy,
                historyPolicy,
                defaultTimeZone,
                conceptualDesignAllowed,
                StandardCoverage.KEY_AND_MEASURE,
                QualityGate.BLOCKING,
                readiness,
                issues
            );
        }

        public PlanningPolicyView {
            issues = immutable(issues);
        }
    }

    public record PlanningBaseline(boolean ready, List<String> missingCodes) {
        public PlanningBaseline {
            missingCodes = immutable(missingCodes);
            ready = missingCodes.isEmpty();
        }
    }

    public record DomainIssue(String code, String message, String field) {}

    public record Versioned<T>(T value, int version) {
        public Versioned {
            if (version < 1) {
                throw new IllegalArgumentException("version must be positive");
            }
        }
    }

    public static List<DomainIssue> validateCreate(CreateWarehousePlanCommand command) {
        List<DomainIssue> issues = new ArrayList<>();
        if (command == null) {
            issues.add(new DomainIssue("WAREHOUSE_PLAN_REQUEST_REQUIRED", "Warehouse plan request is required", null));
            return List.copyOf(issues);
        }
        if (isBlank(command.name())) {
            issues.add(new DomainIssue("WAREHOUSE_PLAN_NAME_REQUIRED", "Warehouse plan name is required", "name"));
        } else if (command.name().length() > 128) {
            issues.add(new DomainIssue("WAREHOUSE_PLAN_NAME_TOO_LONG", "Warehouse plan name must not exceed 128 characters", "name"));
        }
        if (isBlank(command.ownerId())) {
            issues.add(new DomainIssue("WAREHOUSE_PLAN_OWNER_REQUIRED", "Warehouse plan owner is required", "ownerId"));
        }
        if (command.onboardingMode() == null) {
            issues.add(
                new DomainIssue("WAREHOUSE_PLAN_ONBOARDING_MODE_REQUIRED", "Warehouse plan onboarding mode is required", "onboardingMode")
            );
        } else if (command.onboardingMode() == OnboardingMode.BUSINESS_FIRST && isBlank(command.objective())) {
            issues.add(
                new DomainIssue(
                    "WAREHOUSE_PLAN_OBJECTIVE_REQUIRED",
                    "A business-first warehouse plan requires an objective",
                    "objective"
                )
            );
        } else if (command.onboardingMode() == OnboardingMode.ASSET_FIRST && command.initialSourceRefs().isEmpty()) {
            issues.add(
                new DomainIssue(
                    "WAREHOUSE_PLAN_INITIAL_SOURCE_REQUIRED",
                    "An asset-first warehouse plan requires at least one initial source",
                    "initialSourceRefs"
                )
            );
        }
        if (isBlank(command.idempotencyKey())) {
            issues.add(
                new DomainIssue(
                    "WAREHOUSE_PLAN_IDEMPOTENCY_KEY_REQUIRED",
                    "An idempotency key is required when creating a warehouse plan",
                    "idempotencyKey"
                )
            );
        } else if (command.idempotencyKey().length() > 128) {
            issues.add(
                new DomainIssue(
                    "WAREHOUSE_PLAN_IDEMPOTENCY_KEY_TOO_LONG",
                    "The idempotency key must not exceed 128 characters",
                    "idempotencyKey"
                )
            );
        }
        Set<String> sourceRefs = new HashSet<>();
        for (InitialSourceRef source : command.initialSourceRefs()) {
            if (
                source == null ||
                source.sourceType() == null ||
                isBlank(source.sourceId()) ||
                !sourceRefs.add(source.sourceType().name() + "\u0000" + source.sourceId())
            ) {
                issues.add(
                    new DomainIssue(
                        "WAREHOUSE_PLAN_INITIAL_SOURCE_INVALID",
                        "Initial sources must be unique and complete",
                        "initialSourceRefs"
                    )
                );
                break;
            }
            if (source.sourceId().length() > 256) {
                issues.add(
                    new DomainIssue(
                        "WAREHOUSE_PLAN_INITIAL_SOURCE_ID_TOO_LONG",
                        "An initial source identifier must not exceed 256 characters",
                        "initialSourceRefs"
                    )
                );
            }
            if (source.sourceVersion() != null && source.sourceVersion().length() > 128) {
                issues.add(
                    new DomainIssue(
                        "WAREHOUSE_PLAN_INITIAL_SOURCE_VERSION_TOO_LONG",
                        "An initial source version must not exceed 128 characters",
                        "initialSourceRefs"
                    )
                );
            }
        }
        return List.copyOf(issues);
    }

    public static List<DomainIssue> validateSourceInventoryCommand(SourceInventoryCommand command) {
        if (command == null) {
            return List.of(new DomainIssue("SOURCE_INVENTORY_REQUIRED", "Source inventory is required", null));
        }
        List<DomainIssue> issues = new ArrayList<>();
        Set<UUID> bindingIds = new HashSet<>();
        Set<String> identities = new HashSet<>();
        for (int index = 0; index < command.bindings().size(); index++) {
            SourceBindingCommand binding = command.bindings().get(index);
            String field = "bindings[" + index + "]";
            if (binding == null || binding.confirmationStatus() == null) {
                issues.add(new DomainIssue("SOURCE_BINDING_INVALID", "Source bindings must be complete", field));
                continue;
            }
            if (binding.bindingId() != null && !bindingIds.add(binding.bindingId())) {
                issues.add(new DomainIssue("SOURCE_BINDING_DUPLICATE", "bindingId must be unique", field + ".bindingId"));
            }
            boolean identityProvided = binding.sourceType() != null || binding.locator() != null;
            if (binding.bindingId() == null && !identityProvided) {
                issues.add(new DomainIssue("SOURCE_BINDING_INVALID", "New source bindings require an identity", field));
                continue;
            }
            if (identityProvided) {
                if (!validLocator(binding.sourceType(), binding.locator())) {
                    issues.add(
                        new DomainIssue("SOURCE_LOCATOR_INVALID", "Source locator does not match sourceType", field + ".locator")
                    );
                    continue;
                }
                String sourceId = canonicalSourceId(binding.sourceType(), binding.locator());
                if (!identities.add(binding.sourceType().name() + "\u0000" + sourceId)) {
                    issues.add(
                        new DomainIssue("SOURCE_REFERENCE_DUPLICATE", "Source references must be unique", field + ".locator")
                    );
                }
            }
            if (binding.confirmationStatus() == ConfirmationStatus.EXCLUDED && isBlank(binding.exclusionReason())) {
                issues.add(
                    new DomainIssue(
                        "SOURCE_EXCLUSION_REASON_REQUIRED",
                        "Excluded sources require a reason",
                        field + ".exclusionReason"
                    )
                );
            }
            if (binding.action() == SourceAction.RECONFIRM) {
                if (
                    binding.bindingId() == null ||
                    binding.confirmationStatus() != ConfirmationStatus.CONFIRMED ||
                    isBlank(binding.expectedConfirmedVersion()) ||
                    isBlank(binding.expectedCurrentVersion())
                ) {
                    issues.add(
                        new DomainIssue(
                            "SOURCE_RECONFIRM_VERSION_REQUIRED",
                            "Reconfirmation requires the binding and both observed source versions",
                            field + ".action"
                        )
                    );
                }
            } else if (binding.action() == SourceAction.CONFIRM) {
                validateSourceActionState(binding, ConfirmationStatus.CONFIRMED, field, issues);
            } else if (binding.action() == SourceAction.EXCLUDE) {
                validateSourceActionState(binding, ConfirmationStatus.EXCLUDED, field, issues);
            }
        }
        return List.copyOf(issues);
    }

    private static void validateSourceActionState(
        SourceBindingCommand binding,
        ConfirmationStatus expectedStatus,
        String field,
        List<DomainIssue> issues
    ) {
        if (binding.bindingId() == null) {
            issues.add(
                new DomainIssue(
                    "SOURCE_ACTION_BINDING_REQUIRED",
                    "Explicit source actions require an existing binding",
                    field + ".bindingId"
                )
            );
        }
        if (binding.confirmationStatus() != expectedStatus) {
            issues.add(
                new DomainIssue(
                    "SOURCE_ACTION_STATUS_INVALID",
                    "Source action does not match confirmationStatus",
                    field + ".confirmationStatus"
                )
            );
        }
        if (!isBlank(binding.expectedConfirmedVersion()) || !isBlank(binding.expectedCurrentVersion())) {
            issues.add(
                new DomainIssue(
                    "SOURCE_ACTION_VERSION_NOT_ALLOWED",
                    "Only reconfirmation accepts observed source versions",
                    field + ".action"
                )
            );
        }
    }

    public static String canonicalSourceId(SourceType sourceType, SourceLocator locator) {
        if (!validLocator(sourceType, locator)) {
            throw new IllegalArgumentException("Source locator does not match sourceType");
        }
        return switch (sourceType) {
            case CATALOG_TABLE -> locator.assetId().toString();
            case EXCEL_FILE -> locator.fileId().toString();
            case DBT_NODE -> locator.projectKey() + ":" + locator.uniqueId();
            case CONNECTION_TABLE ->
                locator.connectionId() + ":" + locator.namespace() + "." + locator.objectName();
        };
    }

    public static List<DomainIssue> validateRequestedActor(
        String requestedOwnerId,
        String requestedOwnerDepartmentId,
        String authenticatedOwnerId,
        String authenticatedOwnerDepartmentId
    ) {
        if (isBlank(authenticatedOwnerId)) {
            return List.of(
                new DomainIssue(
                    "WAREHOUSE_PLAN_AUTHENTICATED_ACTOR_REQUIRED",
                    "An authenticated user is required to create a warehouse plan",
                    "ownerId"
                )
            );
        }
        if (!isBlank(requestedOwnerId) && !requestedOwnerId.trim().equals(authenticatedOwnerId.trim())) {
            return List.of(
                new DomainIssue(
                    "WAREHOUSE_PLAN_OWNER_FORBIDDEN",
                    "The warehouse plan owner must be the current authenticated user",
                    "ownerId"
                )
            );
        }
        if (
            !isBlank(requestedOwnerDepartmentId) &&
            (isBlank(authenticatedOwnerDepartmentId) ||
                !requestedOwnerDepartmentId.trim().equals(authenticatedOwnerDepartmentId.trim()))
        ) {
            return List.of(
                new DomainIssue(
                    "WAREHOUSE_PLAN_OWNER_DEPARTMENT_FORBIDDEN",
                    "The warehouse plan department must match the authenticated context",
                    "ownerDepartmentId"
                )
            );
        }
        return List.of();
    }

    public static List<DomainIssue> validateRequestedTenant(String requestedTenantId) {
        if (isBlank(requestedTenantId)) {
            return List.of();
        }
        return List.of(
            new DomainIssue(
                "WAREHOUSE_PLAN_TENANT_NOT_ACCEPTED",
                "tenantId is resolved by the server and must not be supplied by the request",
                "tenantId"
            )
        );
    }

    public static PlanningBaseline evaluateBaseline(
        BusinessScope businessScope,
        List<SourceBinding> sourceBindings,
        List<SourceBusinessMapping> sourceBusinessMappings,
        PlanningPolicy planningPolicy
    ) {
        List<SourceBinding> sources = immutable(sourceBindings);
        List<String> missing = new ArrayList<>(3);

        if (!confirmedDomainAndProcessScope(businessScope)) {
            missing.add(CATEGORY_SCOPE_INCOMPLETE);
        }
        if (!confirmedSourceInventory(sources)) {
            missing.add(SOURCE_INVENTORY_INCOMPLETE);
        }
        if (!completePolicy(planningPolicy)) {
            missing.add(PLANNING_POLICY_INCOMPLETE);
        }
        return new PlanningBaseline(missing.isEmpty(), missing);
    }

    public static PlanningBaseline evaluateBaseline(
        CategoryScopeView categoryScope,
        List<SourceBinding> sourceBindings,
        List<SourceBusinessMapping> sourceBusinessMappings,
        PlanningPolicyView planningPolicy
    ) {
        List<SourceBinding> sources = immutable(sourceBindings);
        List<String> missing = new ArrayList<>(3);

        if (categoryScope == null || categoryScope.readiness() != CategoryReadiness.READY) {
            missing.add(CATEGORY_SCOPE_INCOMPLETE);
        }
        if (!confirmedSourceInventory(sources)) {
            missing.add(SOURCE_INVENTORY_INCOMPLETE);
        }
        if (
            planningPolicy == null ||
            (planningPolicy.readiness() != PlanningPolicyReadiness.MODEL_DESIGN_READY &&
                planningPolicy.readiness() != PlanningPolicyReadiness.IMPLEMENTATION_READY)
        ) {
            missing.add(PLANNING_POLICY_INCOMPLETE);
        }
        return new PlanningBaseline(missing.isEmpty(), missing);
    }

    public static PlanningBaseline evaluateBaseline(
        CategoryScopeView categoryScope,
        SourceInventoryView sourceInventory,
        PlanningPolicyView planningPolicy,
        OnboardingMode onboardingMode
    ) {
        List<String> missing = new ArrayList<>(3);
        if (categoryScope == null || categoryScope.readiness() != CategoryReadiness.READY) {
            missing.add(CATEGORY_SCOPE_INCOMPLETE);
        }
        boolean sourceReady = sourceInventory != null && sourceInventory.readiness() == SourceInventoryReadiness.READY;
        boolean conceptualSourceDeferral =
            onboardingMode == OnboardingMode.BUSINESS_FIRST &&
            sourceInventory != null &&
            sourceInventory.readiness() == SourceInventoryReadiness.NOT_REQUIRED_YET &&
            planningPolicy != null &&
            planningPolicy.conceptualDesignAllowed();
        if (!sourceReady && !conceptualSourceDeferral) {
            missing.add(SOURCE_INVENTORY_INCOMPLETE);
        }
        if (
            planningPolicy == null ||
            (planningPolicy.readiness() != PlanningPolicyReadiness.MODEL_DESIGN_READY &&
                planningPolicy.readiness() != PlanningPolicyReadiness.IMPLEMENTATION_READY)
        ) {
            missing.add(PLANNING_POLICY_INCOMPLETE);
        }
        return new PlanningBaseline(missing.isEmpty(), missing);
    }

    public static SourceInventoryView evaluateSourceInventory(
        List<SourceBindingView> bindings,
        OnboardingMode onboardingMode,
        int version,
        Instant checkedAt
    ) {
        List<SourceBindingView> sources = immutable(bindings);
        List<DomainIssue> issues = new ArrayList<>();
        if (sources.isEmpty()) {
            SourceInventoryReadiness readiness = onboardingMode == OnboardingMode.BUSINESS_FIRST
                ? SourceInventoryReadiness.NOT_REQUIRED_YET
                : SourceInventoryReadiness.DRAFT;
            if (readiness == SourceInventoryReadiness.DRAFT) {
                issues.add(new DomainIssue("SOURCE_INVENTORY_REQUIRED", "At least one source is required", "bindings"));
            }
            return new SourceInventoryView(sources, readiness, issues, version, sourceEtag(version), checkedAt);
        }

        boolean blocked = false;
        boolean allResolved = true;
        boolean anyConfirmedCurrent = false;
        for (int index = 0; index < sources.size(); index++) {
            SourceBindingView source = sources.get(index);
            String field = "bindings[" + index + "]";
            if (
                source == null ||
                source.bindingId() == null ||
                source.sourceType() == null ||
                source.confirmationStatus() == null ||
                source.resolutionStatus() == null ||
                source.freshness() == null ||
                (source.resolutionStatus() == SourceReferenceResolver.ResolutionStatus.AVAILABLE && source.locator() == null)
            ) {
                blocked = true;
                allResolved = false;
                issues.add(new DomainIssue("SOURCE_BINDING_INVALID", "Source binding is incomplete", field));
                continue;
            }
            if (source.confirmationStatus() == ConfirmationStatus.EXCLUDED) {
                if (isBlank(source.exclusionReason())) {
                    blocked = true;
                    issues.add(
                        new DomainIssue(
                            "SOURCE_EXCLUSION_REASON_REQUIRED",
                            "Excluded sources require a reason",
                            field + ".exclusionReason"
                        )
                    );
                }
                continue;
            }
            if (source.confirmationStatus() != ConfirmationStatus.CONFIRMED) {
                allResolved = false;
                issues.add(
                    new DomainIssue(
                        "SOURCE_CONFIRMATION_REQUIRED",
                        "Included sources must be confirmed",
                        field + ".confirmationStatus"
                    )
                );
                continue;
            }
            if (source.freshness() == SourceFreshness.STALE) {
                blocked = true;
                issues.add(new DomainIssue("SOURCE_STALE", "The source changed or was deleted", field));
            } else if (
                source.freshness() == SourceFreshness.UNKNOWN ||
                source.resolutionStatus() == SourceReferenceResolver.ResolutionStatus.FORBIDDEN ||
                source.resolutionStatus() == SourceReferenceResolver.ResolutionStatus.PROVIDER_ERROR
            ) {
                blocked = true;
                issues.add(new DomainIssue("SOURCE_UNKNOWN", "The source cannot be verified", field));
            } else if (
                source.freshness() == SourceFreshness.CURRENT &&
                source.resolutionStatus() == SourceReferenceResolver.ResolutionStatus.AVAILABLE
            ) {
                anyConfirmedCurrent = true;
            } else {
                blocked = true;
                issues.add(new DomainIssue("SOURCE_UNKNOWN", "The source cannot be verified", field));
            }
        }

        SourceInventoryReadiness readiness = blocked
            ? SourceInventoryReadiness.BLOCKED
            : allResolved && anyConfirmedCurrent
                ? SourceInventoryReadiness.READY
                : SourceInventoryReadiness.DRAFT;
        return new SourceInventoryView(sources, readiness, issues, version, sourceEtag(version), checkedAt);
    }

    public static CategoryScopeView evaluateCategoryScope(List<CategoryBindingView> bindings, Instant lastValidatedAt) {
        List<CategoryBindingView> categories = immutable(bindings);
        List<DomainIssue> issues = new ArrayList<>();
        boolean blocked = false;
        boolean anyIncluded = false;
        boolean anyConfirmedAvailable = false;
        boolean allIncludedConfirmedAvailable = true;

        for (int index = 0; index < categories.size(); index++) {
            CategoryBindingView binding = categories.get(index);
            String field = "domainBindings[" + index + "]";
            if (binding == null || binding.domainId() == null || binding.confirmationStatus() == null) {
                blocked = true;
                allIncludedConfirmedAvailable = false;
                issues.add(new DomainIssue("CATEGORY_BINDING_INVALID", "Category bindings must be complete", field));
                continue;
            }
            if (binding.confirmationStatus() == ConfirmationStatus.EXCLUDED) {
                continue;
            }
            anyIncluded = true;
            if (binding.resolutionStatus() != CatalogDomainResolutionPort.ResolutionStatus.AVAILABLE) {
                blocked = true;
                allIncludedConfirmedAvailable = false;
                issues.add(categoryResolutionIssue(binding.resolutionStatus(), field));
                continue;
            }
            if (binding.confirmationStatus() != ConfirmationStatus.CONFIRMED) {
                allIncludedConfirmedAvailable = false;
                issues.add(
                    new DomainIssue(
                        "CATEGORY_CONFIRMATION_REQUIRED",
                        "An included category must be confirmed before model design",
                        field + ".confirmationStatus"
                    )
                );
                continue;
            }
            anyConfirmedAvailable = true;
        }

        if (!anyIncluded) {
            issues.add(new DomainIssue("CATEGORY_SCOPE_REQUIRED", "At least one business category is required", "domainBindings"));
        }
        CategoryReadiness readiness = blocked
            ? CategoryReadiness.BLOCKED
            : anyIncluded && anyConfirmedAvailable && allIncludedConfirmedAvailable
                ? CategoryReadiness.READY
                : CategoryReadiness.DRAFT;
        return new CategoryScopeView(categories, readiness, issues, lastValidatedAt);
    }

    public static PlanningPolicyView evaluatePlanningPolicy(PlanningPolicyCommand command) {
        PlanningPolicyCommand value = command == null ? new PlanningPolicyCommand(null, null, null, null) : command;
        List<DomainIssue> issues = new ArrayList<>();
        LayerScheme layerScheme = parseEnum(
            LayerScheme.class,
            value.layerScheme(),
            "LAYER_SCHEME_REQUIRED",
            "LAYER_SCHEME_UNSUPPORTED",
            "layerScheme",
            issues
        );
        NamingPolicy namingPolicy = parseEnum(
            NamingPolicy.class,
            value.namingPolicy(),
            "NAMING_POLICY_REQUIRED",
            "NAMING_POLICY_UNSUPPORTED",
            "namingPolicy",
            issues
        );
        HistoryPolicy historyPolicy = parseEnum(
            HistoryPolicy.class,
            value.historyPolicy(),
            "HISTORY_POLICY_REQUIRED",
            "HISTORY_POLICY_UNSUPPORTED",
            "historyPolicy",
            issues
        );
        boolean validTimeZone = validZoneId(value.defaultTimeZone());
        if (!validTimeZone) {
            issues.add(
                new DomainIssue(
                    "DEFAULT_TIME_ZONE_INVALID",
                    "defaultTimeZone must be a valid IANA ZoneId when supplied",
                    "defaultTimeZone"
                )
            );
        }
        StandardCoverage standardCoverage = parseOptionalEnum(
            StandardCoverage.class,
            value.standardCoverage(),
            StandardCoverage.KEY_AND_MEASURE,
            "STANDARD_COVERAGE_UNSUPPORTED",
            "standardCoverage",
            issues
        );
        QualityGate qualityGate = parseOptionalEnum(
            QualityGate.class,
            value.qualityGate(),
            QualityGate.BLOCKING,
            "QUALITY_GATE_UNSUPPORTED",
            "qualityGate",
            issues
        );

        PlanningPolicyReadiness readiness = layerScheme == null
            ? PlanningPolicyReadiness.DRAFT
            : namingPolicy != null && historyPolicy != null && validTimeZone
                ? PlanningPolicyReadiness.IMPLEMENTATION_READY
                : PlanningPolicyReadiness.MODEL_DESIGN_READY;
        return new PlanningPolicyView(
            layerScheme,
            namingPolicy,
            historyPolicy,
            value.defaultTimeZone(),
            Boolean.TRUE.equals(value.conceptualDesignAllowed()),
            standardCoverage,
            qualityGate,
            readiness,
            issues
        );
    }

    public static boolean hasInvalidPolicyValues(PlanningPolicyView policy) {
        if (policy == null) {
            return false;
        }
        return policy
            .issues()
            .stream()
            .map(DomainIssue::code)
            .anyMatch(code -> code.endsWith("_UNSUPPORTED") || code.endsWith("_INVALID"));
    }

    public static boolean canTransition(LifecycleStatus current, LifecycleStatus target) {
        if (current == null || target == null || current == target || current == LifecycleStatus.ARCHIVED) {
            return false;
        }
        if (target == LifecycleStatus.ARCHIVED) {
            return true;
        }
        return switch (current) {
            case DRAFT -> target == LifecycleStatus.BASELINE_READY;
            case BASELINE_READY -> target == LifecycleStatus.DESIGNING;
            case DESIGNING -> target == LifecycleStatus.VALIDATING;
            case VALIDATING -> target == LifecycleStatus.READY_TO_PUBLISH;
            case READY_TO_PUBLISH -> target == LifecycleStatus.PUBLISHED;
            case PUBLISHED, ARCHIVED -> false;
        };
    }

    /** Legacy helper name retained during compatibility; process bindings do not participate in the canonical gate. */
    private static boolean confirmedDomainAndProcessScope(BusinessScope scope) {
        if (scope == null || scope.domainBindings().isEmpty()) {
            return false;
        }
        return scope.domainBindings().stream().allMatch(binding ->
            binding != null && binding.domainId() != null && binding.confirmationStatus() == ConfirmationStatus.CONFIRMED
        );
    }

    private static boolean confirmedSourceInventory(List<SourceBinding> sources) {
        if (sources.isEmpty()) {
            return false;
        }
        EnumSet<ConfirmationStatus> resolvedStatuses = EnumSet.of(ConfirmationStatus.CONFIRMED, ConfirmationStatus.EXCLUDED);
        boolean anyConfirmed = sources.stream().anyMatch(source ->
            source != null && source.confirmationStatus() == ConfirmationStatus.CONFIRMED
        );
        boolean allResolved = sources.stream().allMatch(source ->
            source != null &&
            source.id() != null &&
            source.sourceType() != null &&
            !isBlank(source.sourceId()) &&
            resolvedStatuses.contains(source.confirmationStatus()) &&
            (source.confirmationStatus() != ConfirmationStatus.EXCLUDED || !isBlank(source.exclusionReason()))
        );
        return anyConfirmed && allResolved;
    }

    private static boolean completePolicy(PlanningPolicy policy) {
        if (policy == null || isBlank(policy.layerPolicyCode())) {
            return false;
        }
        try {
            LayerScheme.valueOf(policy.layerPolicyCode().trim());
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static DomainIssue categoryResolutionIssue(
        CatalogDomainResolutionPort.ResolutionStatus status,
        String field
    ) {
        if (status == CatalogDomainResolutionPort.ResolutionStatus.ARCHIVED) {
            return new DomainIssue("CATEGORY_DOMAIN_ARCHIVED", "The business category is archived and must be replaced", field);
        }
        if (status == CatalogDomainResolutionPort.ResolutionStatus.FORBIDDEN) {
            return new DomainIssue("CATEGORY_DOMAIN_FORBIDDEN", "The business category is no longer accessible", field);
        }
        return new DomainIssue("CATEGORY_DOMAIN_MISSING", "The business category no longer exists", field);
    }

    private static <E extends Enum<E>> E parseEnum(
        Class<E> type,
        String value,
        String requiredCode,
        String unsupportedCode,
        String field,
        List<DomainIssue> issues
    ) {
        if (isBlank(value)) {
            issues.add(new DomainIssue(requiredCode, field + " is required for this readiness level", field));
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            issues.add(new DomainIssue(unsupportedCode, "Unsupported " + field, field));
            return null;
        }
    }

    private static <E extends Enum<E>> E parseOptionalEnum(
        Class<E> type,
        String value,
        E defaultValue,
        String unsupportedCode,
        String field,
        List<DomainIssue> issues
    ) {
        if (isBlank(value)) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            issues.add(new DomainIssue(unsupportedCode, "Unsupported " + field, field));
            return null;
        }
    }

    private static boolean validZoneId(String value) {
        if (isBlank(value)) {
            return true;
        }
        return ZoneId.getAvailableZoneIds().contains(value);
    }

    private static String sourceEtag(int version) {
        return "\"sources:" + version + "\"";
    }

    private static boolean validLocator(SourceType sourceType, SourceLocator locator) {
        if (sourceType == null || locator == null) {
            return false;
        }
        return switch (sourceType) {
            case CATALOG_TABLE ->
                locator.assetId() != null &&
                locator.fileId() == null &&
                locator.connectionId() == null &&
                isBlank(locator.projectKey()) &&
                isBlank(locator.uniqueId()) &&
                isBlank(locator.namespace()) &&
                isBlank(locator.objectName());
            case EXCEL_FILE ->
                locator.fileId() != null &&
                locator.assetId() == null &&
                locator.connectionId() == null &&
                isBlank(locator.projectKey()) &&
                isBlank(locator.uniqueId()) &&
                isBlank(locator.namespace()) &&
                isBlank(locator.objectName());
            case DBT_NODE ->
                !isBlank(locator.projectKey()) &&
                !isBlank(locator.uniqueId()) &&
                locator.assetId() == null &&
                locator.fileId() == null &&
                locator.connectionId() == null &&
                isBlank(locator.namespace()) &&
                isBlank(locator.objectName());
            case CONNECTION_TABLE ->
                locator.connectionId() != null &&
                !isBlank(locator.namespace()) &&
                !isBlank(locator.objectName()) &&
                locator.assetId() == null &&
                locator.fileId() == null &&
                isBlank(locator.projectKey()) &&
                isBlank(locator.uniqueId());
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
