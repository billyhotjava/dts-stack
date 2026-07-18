package com.yuzhi.dts.platform.service.modeling.warehouse;

import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.ConfirmationStatus.CONFIRMED;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.LifecycleStatus.BASELINE_READY;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.LifecycleStatus.DRAFT;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.LifecycleStatus.PUBLISHED;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.OnboardingMode.BUSINESS_FIRST;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceType.CATALOG_TABLE;
import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.BusinessScope;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.CreateWarehousePlanCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.DomainBinding;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.DomainIssue;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.InitialSourceRef;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.MetricRequirement;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.PlanningPolicy;
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
        assertThat(WarehousePlanContract.evaluateBaseline(null, List.of(), List.of(), null).missingCodes())
            .containsExactly(
                "CATEGORY_SCOPE_INCOMPLETE",
                "SOURCE_INVENTORY_INCOMPLETE",
                "SOURCE_BUSINESS_MAPPING_INCOMPLETE",
                "PLANNING_POLICY_INCOMPLETE"
            );

        assertThat(WarehousePlanContract.evaluateBaseline(readyScopeWithoutProcesses(), readySources(), List.of(), readyPolicy()).ready())
            .isFalse();
        assertThat(
            WarehousePlanContract.evaluateBaseline(readyScopeWithoutProcesses(), readySources(), List.of(), readyPolicy()).missingCodes()
        )
            .containsExactly("SOURCE_BUSINESS_MAPPING_INCOMPLETE");
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
    void rejectsSourceMappingOutsideConfirmedCategory() {
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
            .containsExactly("SOURCE_BUSINESS_MAPPING_INCOMPLETE");
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
}
