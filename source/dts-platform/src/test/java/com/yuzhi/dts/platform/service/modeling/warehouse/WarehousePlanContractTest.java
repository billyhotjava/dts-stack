package com.yuzhi.dts.platform.service.modeling.warehouse;

import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.ConfirmationStatus.CONFIRMED;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.LifecycleStatus.BASELINE_READY;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.LifecycleStatus.DRAFT;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.LifecycleStatus.PUBLISHED;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.OnboardingMode.BUSINESS_FIRST;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceType.CATALOG_TABLE;
import static com.yuzhi.dts.platform.service.modeling.warehouse.CatalogDomainResolutionPort.ResolutionStatus.ARCHIVED;
import static com.yuzhi.dts.platform.service.modeling.warehouse.CatalogDomainResolutionPort.ResolutionStatus.AVAILABLE;
import static com.yuzhi.dts.platform.service.modeling.warehouse.CatalogDomainResolutionPort.ResolutionStatus.FORBIDDEN;
import static com.yuzhi.dts.platform.service.modeling.warehouse.CatalogDomainResolutionPort.ResolutionStatus.MISSING;
import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.BusinessScope;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.CategoryBindingView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.CategoryReadiness;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.CategoryScopeView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.CreateWarehousePlanCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.DomainBinding;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.DomainIssue;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.InitialSourceRef;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.MetricRequirement;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.PlanningPolicy;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.PlanningPolicyCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.PlanningPolicyReadiness;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.PlanningPolicyView;
import java.time.Instant;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.ProcessBinding;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceBinding;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceBusinessMapping;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class WarehousePlanContractTest {

    private static final UUID DOMAIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void createCommandExcludesClientCodeAndCarriesIdempotencyAndInitialSources() {
        Set<String> components = java.util.Arrays
            .stream(CreateWarehousePlanCommand.class.getRecordComponents())
            .map(java.lang.reflect.RecordComponent::getName)
            .collect(Collectors.toSet());

        assertThat(components)
            .doesNotContain("code", "planId")
            .contains("name", "objective", "scope", "ownerId", "ownerDepartmentId", "onboardingMode", "initialSourceRefs", "idempotencyKey");
    }

    @Test
    void acceptsNeutralCreateCommandAndRejectsRequestOwnedTenant() {
        CreateWarehousePlanCommand command = new CreateWarehousePlanCommand(
            "Operations warehouse",
            "Create reusable operational analysis",
            "First delivery scope",
            "owner-1",
            "department-1",
            BUSINESS_FIRST,
            List.of(),
            "request-1"
        );

        assertThat(WarehousePlanContract.validateCreate(command)).isEmpty();
        assertThat(WarehousePlanContract.validateRequestedTenant(null)).isEmpty();
        assertThat(WarehousePlanContract.validateRequestedTenant("request-value"))
            .extracting(DomainIssue::code)
            .containsExactly("WAREHOUSE_PLAN_TENANT_NOT_ACCEPTED");
    }

    @Test
    void validatesModeSpecificInputsLengthsSourcesAndAuthenticatedOwner() {
        CreateWarehousePlanCommand businessWithoutObjective = new CreateWarehousePlanCommand(
            "Business plan",
            null,
            null,
            "owner-1",
            null,
            BUSINESS_FIRST,
            List.of(),
            "request-business"
        );
        CreateWarehousePlanCommand assetWithoutSources = new CreateWarehousePlanCommand(
            "Asset plan",
            null,
            null,
            "owner-1",
            null,
            WarehousePlanContract.OnboardingMode.ASSET_FIRST,
            List.of(),
            "request-asset"
        );
        InitialSourceRef duplicate = new InitialSourceRef(CATALOG_TABLE, "asset-1", "schema-v1");
        CreateWarehousePlanCommand duplicateSources = new CreateWarehousePlanCommand(
            "Asset plan",
            null,
            null,
            "owner-1",
            null,
            WarehousePlanContract.OnboardingMode.ASSET_FIRST,
            List.of(duplicate, new InitialSourceRef(CATALOG_TABLE, " asset-1 ", "schema-v2")),
            "request-asset-duplicate"
        );

        assertThat(WarehousePlanContract.validateCreate(businessWithoutObjective))
            .extracting(DomainIssue::code)
            .contains("WAREHOUSE_PLAN_OBJECTIVE_REQUIRED");
        assertThat(WarehousePlanContract.validateCreate(assetWithoutSources))
            .extracting(DomainIssue::code)
            .contains("WAREHOUSE_PLAN_INITIAL_SOURCE_REQUIRED");
        assertThat(WarehousePlanContract.validateCreate(duplicateSources))
            .extracting(DomainIssue::code)
            .contains("WAREHOUSE_PLAN_INITIAL_SOURCE_INVALID");
        CreateWarehousePlanCommand longSourceId = new CreateWarehousePlanCommand(
            "Asset plan",
            null,
            null,
            "owner-1",
            null,
            WarehousePlanContract.OnboardingMode.ASSET_FIRST,
            List.of(new InitialSourceRef(CATALOG_TABLE, "a".repeat(257), null)),
            "request-long-source-id"
        );
        CreateWarehousePlanCommand longSourceVersion = new CreateWarehousePlanCommand(
            "Asset plan",
            null,
            null,
            "owner-1",
            null,
            WarehousePlanContract.OnboardingMode.ASSET_FIRST,
            List.of(new InitialSourceRef(CATALOG_TABLE, "asset-1", "v".repeat(129))),
            "request-long-source-version"
        );
        assertThat(WarehousePlanContract.validateCreate(longSourceId))
            .extracting(DomainIssue::code)
            .contains("WAREHOUSE_PLAN_INITIAL_SOURCE_ID_TOO_LONG");
        assertThat(WarehousePlanContract.validateCreate(longSourceVersion))
            .extracting(DomainIssue::code)
            .contains("WAREHOUSE_PLAN_INITIAL_SOURCE_VERSION_TOO_LONG");
        assertThat(WarehousePlanContract.validateRequestedActor(null, null, "owner-1", "department-1")).isEmpty();
        assertThat(WarehousePlanContract.validateRequestedActor("owner-2", null, "owner-1", "department-1"))
            .extracting(DomainIssue::code)
            .containsExactly("WAREHOUSE_PLAN_OWNER_FORBIDDEN");
        assertThat(WarehousePlanContract.validateRequestedActor(null, "department-2", "owner-1", "department-1"))
            .extracting(DomainIssue::code)
            .containsExactly("WAREHOUSE_PLAN_OWNER_DEPARTMENT_FORBIDDEN");
        assertThat(WarehousePlanContract.validateRequestedActor(null, null, null, null))
            .extracting(DomainIssue::code)
            .containsExactly("WAREHOUSE_PLAN_AUTHENTICATED_ACTOR_REQUIRED");
    }

    @Test
    void reportsANullInitialSourceAsAStableDomainIssue() {
        List<InitialSourceRef> sources = new ArrayList<>();
        sources.add(null);
        CreateWarehousePlanCommand command = new CreateWarehousePlanCommand(
            "Asset plan",
            null,
            null,
            "owner-1",
            null,
            WarehousePlanContract.OnboardingMode.ASSET_FIRST,
            sources,
            "request-null-source"
        );

        assertThat(WarehousePlanContract.validateCreate(command))
            .extracting(DomainIssue::code)
            .containsExactly("WAREHOUSE_PLAN_INITIAL_SOURCE_INVALID");
    }

    @Test
    void evaluatesOneBaselineFromConfirmedBusinessAndSourceFacts() {
        BusinessScope scope = readyScopeWithoutProcesses();
        List<SourceBinding> sources = readySources();
        List<SourceBusinessMapping> mappings = readyMappingsWithoutProcess();
        PlanningPolicy policy = readyPolicy();

        assertThat(WarehousePlanContract.evaluateBaseline(scope, sources, mappings, policy).ready()).isTrue();
        assertThat(WarehousePlanContract.evaluateBaseline(scope, sources, mappings, policy).missingCodes()).isEmpty();
    }

    @Test
    void returnsStableMissingCodesWithoutTreatingCandidatesAsConfirmed() {
        assertThat(
            WarehousePlanContract.evaluateBaseline((BusinessScope) null, List.of(), List.of(), (PlanningPolicy) null).missingCodes()
        )
            .containsExactly(
                "CATEGORY_SCOPE_INCOMPLETE",
                "SOURCE_INVENTORY_INCOMPLETE",
                "PLANNING_POLICY_INCOMPLETE"
            );

        assertThat(WarehousePlanContract.evaluateBaseline(readyScopeWithoutProcesses(), readySources(), List.of(), readyPolicy()).ready())
            .isTrue();
        assertThat(
            WarehousePlanContract.evaluateBaseline(readyScopeWithoutProcesses(), readySources(), List.of(), readyPolicy()).missingCodes()
        )
            .isEmpty();
    }

    @Test
    void usesOnlyConfirmedDomainsForCategoryScopeAndSourceMappings() {
        BusinessScope scope = new BusinessScope(
            false,
            List.of(new DomainBinding(DOMAIN_ID, CONFIRMED)),
            List.of(new ProcessBinding("legacy-process", UUID.randomUUID(), "TRANSACTION", null)),
            List.of(
                new MetricRequirement(
                    UUID.randomUUID(),
                    "legacy-metric",
                    "Legacy metric",
                    "Compatibility-only requirement",
                    DOMAIN_ID,
                    "legacy-process",
                    WarehousePlanContract.ConfirmationStatus.CANDIDATE
                )
            )
        );

        assertThat(WarehousePlanContract.evaluateBaseline(scope, readySources(), readyMappingsWithoutProcess(), readyPolicy()).ready())
            .isTrue();
    }

    @Test
    void ignoresLegacySourceMappingWhenEvaluatingCanonicalBaseline() {
        SourceBusinessMapping wrongDomainMapping = new SourceBusinessMapping(
            UUID.randomUUID(),
            SOURCE_ID,
            UUID.randomUUID(),
            null,
            CONFIRMED,
            null
        );

        assertThat(
            WarehousePlanContract.evaluateBaseline(
                readyScopeWithoutProcesses(),
                readySources(),
                List.of(wrongDomainMapping),
                readyPolicy()
            ).missingCodes()
        )
            .isEmpty();
    }

    @Test
    void evaluatesCategoryDraftReadyAndBlockedFromLiveCatalogFactsOnly() {
        Instant validatedAt = Instant.parse("2026-07-19T00:00:00Z");
        CategoryScopeView draft = WarehousePlanContract.evaluateCategoryScope(
            List.of(category(DOMAIN_ID, WarehousePlanContract.ConfirmationStatus.CANDIDATE, AVAILABLE, "Projects", "PROJECT")),
            validatedAt
        );
        CategoryScopeView ready = WarehousePlanContract.evaluateCategoryScope(
            List.of(category(DOMAIN_ID, CONFIRMED, AVAILABLE, "Projects", "PROJECT")),
            validatedAt
        );

        assertThat(draft.readiness()).isEqualTo(CategoryReadiness.DRAFT);
        assertThat(draft.issues()).extracting(DomainIssue::code).containsExactly("CATEGORY_CONFIRMATION_REQUIRED");
        assertThat(ready.readiness()).isEqualTo(CategoryReadiness.READY);
        assertThat(ready.issues()).isEmpty();
        assertThat(ready.lastValidatedAt()).isEqualTo(validatedAt);

        for (var status : List.of(MISSING, ARCHIVED, FORBIDDEN)) {
            CategoryScopeView blocked = WarehousePlanContract.evaluateCategoryScope(
                List.of(category(DOMAIN_ID, CONFIRMED, status, null, null)),
                validatedAt
            );
            assertThat(blocked.readiness()).isEqualTo(CategoryReadiness.BLOCKED);
            assertThat(blocked.issues()).hasSize(1);
        }
    }

    @Test
    void ignoresExcludedCategoriesButRequiresOneConfirmedAvailableCategory() {
        Instant validatedAt = Instant.parse("2026-07-19T00:00:00Z");
        CategoryScopeView onlyExcluded = WarehousePlanContract.evaluateCategoryScope(
            List.of(category(DOMAIN_ID, WarehousePlanContract.ConfirmationStatus.EXCLUDED, MISSING, null, null)),
            validatedAt
        );
        CategoryScopeView readyWithExcluded = WarehousePlanContract.evaluateCategoryScope(
            List.of(
                category(DOMAIN_ID, CONFIRMED, AVAILABLE, "Projects", "PROJECT"),
                category(UUID.randomUUID(), WarehousePlanContract.ConfirmationStatus.EXCLUDED, FORBIDDEN, null, null)
            ),
            validatedAt
        );

        assertThat(onlyExcluded.readiness()).isEqualTo(CategoryReadiness.DRAFT);
        assertThat(onlyExcluded.issues()).extracting(DomainIssue::code).containsExactly("CATEGORY_SCOPE_REQUIRED");
        assertThat(readyWithExcluded.readiness()).isEqualTo(CategoryReadiness.READY);
        assertThat(readyWithExcluded.issues()).isEmpty();
    }

    @Test
    void evaluatesPlanningPolicyAtModelDesignAndImplementationBoundaries() {
        PlanningPolicyView draft = WarehousePlanContract.evaluatePlanningPolicy(
            new PlanningPolicyCommand(null, null, null, null)
        );
        PlanningPolicyView modelReady = WarehousePlanContract.evaluatePlanningPolicy(
            new PlanningPolicyCommand("CLASSIC_ODS_DWD_DWS_ADS", null, null, null)
        );
        PlanningPolicyView implementationReady = WarehousePlanContract.evaluatePlanningPolicy(
            new PlanningPolicyCommand(
                "CLASSIC_ODS_DWD_DWS_ADS",
                "CLASSIC_LOWER_SNAKE",
                "PRESERVE_BUSINESS_HISTORY",
                "Asia/Shanghai"
            )
        );

        assertThat(draft.readiness()).isEqualTo(PlanningPolicyReadiness.DRAFT);
        assertThat(modelReady.readiness()).isEqualTo(PlanningPolicyReadiness.MODEL_DESIGN_READY);
        assertThat(modelReady.issues())
            .extracting(DomainIssue::code)
            .containsExactly("NAMING_POLICY_REQUIRED", "HISTORY_POLICY_REQUIRED");
        assertThat(implementationReady.readiness()).isEqualTo(PlanningPolicyReadiness.IMPLEMENTATION_READY);
        assertThat(implementationReady.issues()).isEmpty();
    }

    @Test
    void rejectsUnsupportedPolicyEnumsAndInvalidNonBlankZoneIdWithoutInventingADefault() {
        PlanningPolicyView invalid = WarehousePlanContract.evaluatePlanningPolicy(
            new PlanningPolicyCommand("LAKEHOUSE", "CAMEL_CASE", "OVERWRITE_ALL", "UTC+8")
        );

        assertThat(invalid.readiness()).isEqualTo(PlanningPolicyReadiness.DRAFT);
        assertThat(invalid.defaultTimeZone()).isEqualTo("UTC+8");
        assertThat(invalid.issues())
            .extracting(DomainIssue::code)
            .containsExactly(
                "LAYER_SCHEME_UNSUPPORTED",
                "NAMING_POLICY_UNSUPPORTED",
                "HISTORY_POLICY_UNSUPPORTED",
                "DEFAULT_TIME_ZONE_INVALID"
            );
        assertThat(WarehousePlanContract.evaluatePlanningPolicy(
            new PlanningPolicyCommand("CLASSIC_ODS_DWD_DWS_ADS", "CLASSIC_UPPER_SNAKE", "LATEST_STATE_ONLY", null)
        ).readiness()).isEqualTo(PlanningPolicyReadiness.IMPLEMENTATION_READY);
    }

    @Test
    void permitsOnlyExplicitLifecycleTransitions() {
        assertThat(WarehousePlanContract.canTransition(DRAFT, BASELINE_READY)).isTrue();
        assertThat(WarehousePlanContract.canTransition(PUBLISHED, DRAFT)).isFalse();
        assertThat(WarehousePlanContract.canTransition(DRAFT, DRAFT)).isFalse();
    }

    private static BusinessScope readyScope() {
        return new BusinessScope(
            true,
            List.of(new DomainBinding(DOMAIN_ID, CONFIRMED)),
            List.of(new ProcessBinding("process-1", DOMAIN_ID, "ACCUMULATING_SNAPSHOT", CONFIRMED)),
            List.<MetricRequirement>of()
        );
    }

    private static BusinessScope readyScopeWithoutProcesses() {
        return new BusinessScope(true, List.of(new DomainBinding(DOMAIN_ID, CONFIRMED)), List.of(), List.<MetricRequirement>of());
    }

    private static List<SourceBinding> readySources() {
        return List.of(new SourceBinding(SOURCE_ID, CATALOG_TABLE, "asset-1", "schema-v1", CONFIRMED, null));
    }

    private static List<SourceBusinessMapping> readyMappings() {
        return List.of(new SourceBusinessMapping(UUID.randomUUID(), SOURCE_ID, DOMAIN_ID, "process-1", CONFIRMED, null));
    }

    private static List<SourceBusinessMapping> readyMappingsWithoutProcess() {
        return List.of(new SourceBusinessMapping(UUID.randomUUID(), SOURCE_ID, DOMAIN_ID, null, CONFIRMED, null));
    }

    private static PlanningPolicy readyPolicy() {
        return new PlanningPolicy("CLASSIC_ODS_DWD_DWS_ADS", "naming-policy-1", "PRESERVE_BUSINESS_HISTORY", "Asia/Shanghai");
    }

    private static CategoryBindingView category(
        UUID domainId,
        WarehousePlanContract.ConfirmationStatus confirmationStatus,
        CatalogDomainResolutionPort.ResolutionStatus resolutionStatus,
        String name,
        String code
    ) {
        return new CategoryBindingView(domainId, confirmationStatus, resolutionStatus, name, code, null);
    }
}
