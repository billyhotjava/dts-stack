package com.yuzhi.dts.platform.service.modeling.warehouse;

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
    public static final String SOURCE_BUSINESS_MAPPING_INCOMPLETE = "SOURCE_BUSINESS_MAPPING_INCOMPLETE";
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

    public enum SourceType {
        CONNECTION_TABLE,
        CATALOG_TABLE,
        EXCEL_FILE,
        DBT_NODE,
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
            initialSourceRefs = immutable(initialSourceRefs);
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

    public record DomainBinding(UUID domainId, ConfirmationStatus confirmationStatus) {}

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
        List<SourceBusinessMapping> mappings = immutable(sourceBusinessMappings);
        List<String> missing = new ArrayList<>(5);

        if (!confirmedDomainAndProcessScope(businessScope)) {
            missing.add(CATEGORY_SCOPE_INCOMPLETE);
        }
        if (!confirmedSourceInventory(sources)) {
            missing.add(SOURCE_INVENTORY_INCOMPLETE);
        }
        if (!confirmedSourceMappings(businessScope, sources, mappings)) {
            missing.add(SOURCE_BUSINESS_MAPPING_INCOMPLETE);
        }
        if (!completePolicy(planningPolicy)) {
            missing.add(PLANNING_POLICY_INCOMPLETE);
        }
        return new PlanningBaseline(missing.isEmpty(), missing);
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

    private static boolean confirmedSourceMappings(
        BusinessScope scope,
        List<SourceBinding> sources,
        List<SourceBusinessMapping> mappings
    ) {
        if (!confirmedDomainAndProcessScope(scope) || !confirmedSourceInventory(sources)) {
            return false;
        }
        Set<UUID> domainIds = scope.domainBindings().stream().map(DomainBinding::domainId).collect(java.util.stream.Collectors.toSet());
        return sources
            .stream()
            .filter(source -> source.confirmationStatus() == ConfirmationStatus.CONFIRMED)
            .allMatch(source -> mappings.stream().anyMatch(mapping ->
                mapping != null &&
                source.id().equals(mapping.sourceBindingId()) &&
                mapping.confirmationStatus() == ConfirmationStatus.CONFIRMED &&
                domainIds.contains(mapping.domainId())
            ));
    }

    private static boolean completePolicy(PlanningPolicy policy) {
        return policy != null &&
        !isBlank(policy.layerPolicyCode()) &&
        !isBlank(policy.historyPolicy()) &&
        !isBlank(policy.defaultTimeZone());
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
