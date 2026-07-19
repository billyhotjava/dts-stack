package com.yuzhi.dts.platform.service.modeling.warehouse;

import static com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver.ResolutionStatus.AVAILABLE;
import static com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver.ResolutionStatus.FORBIDDEN;
import static com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver.ResolutionStatus.MISSING;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.ConfirmationStatus.CONFIRMED;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.ConfirmationStatus.EXCLUDED;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.OnboardingMode.ASSET_FIRST;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.OnboardingMode.BUSINESS_FIRST;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceFreshness.CURRENT;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceFreshness.STALE;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceFreshness.UNKNOWN;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceInventoryReadiness.NOT_REQUIRED_YET;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceInventoryReadiness.READY;
import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.CategoryBindingView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.CategoryReadiness;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.CategoryScopeView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.PlanningPolicyCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.PlanningPolicyView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceBindingCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceBindingView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceInventoryCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceInventoryView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceLocator;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WarehousePlanSourceInventoryContractTest {

    private static final UUID DOMAIN_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID BINDING_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID ASSET_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final Instant CHECKED_AT = Instant.parse("2026-07-19T01:00:00Z");

    @Test
    void acceptsOnlyTheLocatorShapeOwnedByEachSourceType() {
        SourceBindingCommand catalog = new SourceBindingCommand(
            null,
            SourceType.CATALOG_TABLE,
            new SourceLocator(ASSET_ID, null, null, null, null, null, null),
            CONFIRMED,
            null
        );
        SourceBindingCommand forgedConnection = new SourceBindingCommand(
            null,
            SourceType.CONNECTION_TABLE,
            new SourceLocator(ASSET_ID, null, null, null, null, null, null),
            CONFIRMED,
            null
        );

        assertThat(WarehousePlanContract.validateSourceInventoryCommand(new SourceInventoryCommand(List.of(catalog)))).isEmpty();
        assertThat(WarehousePlanContract.validateSourceInventoryCommand(new SourceInventoryCommand(List.of(forgedConnection))))
            .extracting(WarehousePlanContract.DomainIssue::code)
            .containsExactly("SOURCE_LOCATOR_INVALID");
    }

    @Test
    void existingBindingsMayUpdateTheirDecisionWithoutRepostingARestrictedLocator() {
        SourceBindingCommand decisionOnly = new SourceBindingCommand(
            BINDING_ID,
            null,
            null,
            EXCLUDED,
            "access revoked"
        );
        SourceBindingCommand incompleteNewBinding = new SourceBindingCommand(
            null,
            null,
            null,
            CONFIRMED,
            null
        );

        assertThat(
            WarehousePlanContract.validateSourceInventoryCommand(new SourceInventoryCommand(List.of(decisionOnly)))
        ).isEmpty();
        assertThat(
            WarehousePlanContract.validateSourceInventoryCommand(new SourceInventoryCommand(List.of(incompleteNewBinding)))
        )
            .extracting(WarehousePlanContract.DomainIssue::code)
            .containsExactly("SOURCE_BINDING_INVALID");
    }

    @Test
    void requiresAllIncludedSourcesToBeConfirmedAndCurrent() {
        SourceBindingView current = source(CONFIRMED, AVAILABLE, CURRENT, null);
        SourceBindingView excludedMissing = source(EXCLUDED, MISSING, STALE, "not used");
        SourceInventoryView ready = WarehousePlanContract.evaluateSourceInventory(
            List.of(current, excludedMissing),
            ASSET_FIRST,
            4,
            CHECKED_AT
        );

        assertThat(ready.readiness()).isEqualTo(READY);
        assertThat(ready.version()).isEqualTo(4);
        assertThat(ready.etag()).isEqualTo("\"sources:4\"");
        assertThat(ready.issues()).isEmpty();

        SourceInventoryView stale = WarehousePlanContract.evaluateSourceInventory(
            List.of(source(CONFIRMED, MISSING, STALE, null)),
            ASSET_FIRST,
            4,
            CHECKED_AT
        );
        SourceInventoryView unknown = WarehousePlanContract.evaluateSourceInventory(
            List.of(source(CONFIRMED, FORBIDDEN, UNKNOWN, null)),
            ASSET_FIRST,
            4,
            CHECKED_AT
        );

        assertThat(stale.readiness()).isEqualTo(WarehousePlanContract.SourceInventoryReadiness.BLOCKED);
        assertThat(stale.issues()).extracting(WarehousePlanContract.DomainIssue::code).containsExactly("SOURCE_STALE");
        assertThat(unknown.readiness()).isEqualTo(WarehousePlanContract.SourceInventoryReadiness.BLOCKED);
        assertThat(unknown.issues()).extracting(WarehousePlanContract.DomainIssue::code).containsExactly("SOURCE_UNKNOWN");
    }

    @Test
    void businessFirstWithoutSourcesIsDeferredAndOnlyConceptualPolicyLetsBaselineProceed() {
        SourceInventoryView deferred = WarehousePlanContract.evaluateSourceInventory(List.of(), BUSINESS_FIRST, 1, CHECKED_AT);
        PlanningPolicyView conceptual = WarehousePlanContract.evaluatePlanningPolicy(
            new PlanningPolicyCommand("CLASSIC_ODS_DWD_DWS_ADS", null, null, null, true)
        );
        PlanningPolicyView ordinary = WarehousePlanContract.evaluatePlanningPolicy(
            new PlanningPolicyCommand("CLASSIC_ODS_DWD_DWS_ADS", null, null, null, false)
        );

        assertThat(deferred.readiness()).isEqualTo(NOT_REQUIRED_YET);
        assertThat(WarehousePlanContract.evaluateBaseline(categoryReady(), deferred, conceptual, BUSINESS_FIRST).ready()).isTrue();
        assertThat(WarehousePlanContract.evaluateBaseline(categoryReady(), deferred, ordinary, BUSINESS_FIRST).missingCodes())
            .containsExactly(WarehousePlanContract.SOURCE_INVENTORY_INCOMPLETE);
        assertThat(
            WarehousePlanContract.evaluateBaseline(
                categoryReady(),
                WarehousePlanContract.evaluateSourceInventory(List.of(), ASSET_FIRST, 1, CHECKED_AT),
                conceptual,
                ASSET_FIRST
            ).missingCodes()
        ).containsExactly(WarehousePlanContract.SOURCE_INVENTORY_INCOMPLETE);
    }

    private static SourceBindingView source(
        WarehousePlanContract.ConfirmationStatus confirmation,
        SourceReferenceResolver.ResolutionStatus resolution,
        WarehousePlanContract.SourceFreshness freshness,
        String exclusionReason
    ) {
        return new SourceBindingView(
            BINDING_ID,
            SourceType.CATALOG_TABLE,
            new SourceLocator(ASSET_ID, null, null, null, null, null, null),
            ASSET_ID.toString(),
            confirmation,
            exclusionReason,
            resolution == FORBIDDEN ? null : "orders",
            "v1",
            resolution == AVAILABLE ? "v1" : null,
            resolution,
            freshness,
            CHECKED_AT
        );
    }

    private static CategoryScopeView categoryReady() {
        return new CategoryScopeView(
            List.of(
                new CategoryBindingView(
                    DOMAIN_ID,
                    CONFIRMED,
                    CatalogDomainResolutionPort.ResolutionStatus.AVAILABLE,
                    "Projects",
                    "PROJECT",
                    CHECKED_AT
                )
            ),
            CategoryReadiness.READY,
            List.of(),
            CHECKED_AT
        );
    }
}
