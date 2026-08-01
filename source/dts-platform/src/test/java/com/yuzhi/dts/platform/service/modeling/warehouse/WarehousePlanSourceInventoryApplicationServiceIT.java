package com.yuzhi.dts.platform.service.modeling.warehouse;

import static com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver.ResolutionStatus.AVAILABLE;
import static com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver.ResolutionStatus.FORBIDDEN;
import static com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver.ResolutionStatus.MISSING;
import static com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver.ResolutionStatus.PROVIDER_ERROR;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.ConfirmationStatus.CONFIRMED;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.ConfirmationStatus.EXCLUDED;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.OnboardingMode.BUSINESS_FIRST;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceFreshness.STALE;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceInventoryReadiness.NOT_REQUIRED_YET;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceInventoryReadiness.READY;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceType.CATALOG_TABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.IntegrationTest;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver.AccessContext;
import com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver.ResolvedSource;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanApplicationService.WarehousePlanException;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.CategoryScopeCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.CreateWarehousePlanCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.DomainBinding;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.PlanningPolicyCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceBindingCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceInventoryCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceInventoryView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceLocator;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.WarehousePlanHeader;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class WarehousePlanSourceInventoryApplicationServiceIT {

    private static final UUID ASSET_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID DOMAIN_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final AccessContext ACCESS = new AccessContext("ignored-by-server", "owner-1", "department-1");

    @Autowired
    private WarehousePlanApplicationService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockBean
    private SourceReferenceResolver sourceReferenceResolver;

    @MockBean
    private CatalogDomainResolutionPort catalogDomainResolutionPort;

    @MockBean
    private AuditService auditService;

    @BeforeEach
    void resolveOriginalOwners() {
        when(sourceReferenceResolver.resolve(eq(CATALOG_TABLE), any(SourceLocator.class), any(AccessContext.class)))
            .thenAnswer(invocation -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                return ResolvedSource.available("Orders", "schema-v1");
            });
        when(catalogDomainResolutionPort.resolve(DOMAIN_ID)).thenReturn(
            new CatalogDomainResolutionPort.DomainResolution(
                DOMAIN_ID,
                CatalogDomainResolutionPort.ResolutionStatus.AVAILABLE,
                "Projects",
                "PROJECT",
                "owner",
                "description"
            )
        );
    }

    @Test
    void serverOwnsBindingIdentityAndVersionWhileGetRevalidatesDeletion() {
        String tenant = tenant("source-owner");
        String otherTenant = tenant("source-other");
        try {
            WarehousePlanHeader plan = service.create(tenant, createCommand()).plan();
            SourceInventoryCommand command = new SourceInventoryCommand(
                List.of(
                    new SourceBindingCommand(
                        null,
                        CATALOG_TABLE,
                        new SourceLocator(ASSET_ID, null, null, null, null, null, null),
                        CONFIRMED,
                        null
                    )
                )
            );

            SourceInventoryView saved = service.saveSources(tenant, plan.id(), 1, command, ACCESS);

            assertThat(saved.version()).isEqualTo(2);
            assertThat(saved.readiness()).isEqualTo(READY);
            assertThat(saved.bindings().getFirst().bindingId()).isNotNull();
            assertThat(saved.bindings().getFirst().sourceId()).isEqualTo(ASSET_ID.toString());
            assertThat(saved.bindings().getFirst().confirmedVersion()).isEqualTo("schema-v1");
            assertThat(
                jdbcTemplate.queryForObject(
                    "select source_version from modeling_warehouse_plan_source where tenant_id = ? and plan_id = ?",
                    String.class,
                    tenant,
                    plan.id()
                )
            ).isEqualTo("schema-v1");

            when(sourceReferenceResolver.resolve(eq(CATALOG_TABLE), any(SourceLocator.class), any(AccessContext.class)))
                .thenReturn(ResolvedSource.missing());
            SourceInventoryView deleted = service.getSources(tenant, plan.id(), ACCESS);
            assertThat(deleted.bindings().getFirst().freshness()).isEqualTo(STALE);
            assertThat(deleted.bindings().getFirst().resolutionStatus()).isEqualTo(MISSING);
            assertThat(deleted.bindings().getFirst().sourceId()).isNull();
            assertThat(deleted.bindings().getFirst().locator()).isNull();
            assertThat(deleted.bindings().getFirst().displayName()).isNull();
            assertThat(deleted.bindings().getFirst().confirmedVersion()).isNull();
            assertThat(deleted.bindings().getFirst().resolvedVersion()).isNull();

            when(sourceReferenceResolver.resolve(eq(CATALOG_TABLE), any(SourceLocator.class), any(AccessContext.class)))
                .thenReturn(ResolvedSource.providerError());
            SourceInventoryView unavailable = service.getSources(tenant, plan.id(), ACCESS);
            assertThat(unavailable.bindings().getFirst().resolutionStatus()).isEqualTo(PROVIDER_ERROR);
            assertThat(unavailable.bindings().getFirst().sourceId()).isNull();
            assertThat(unavailable.bindings().getFirst().locator()).isNull();
            assertThat(unavailable.bindings().getFirst().displayName()).isNull();
            assertThat(unavailable.bindings().getFirst().confirmedVersion()).isNull();
            assertThat(unavailable.bindings().getFirst().resolvedVersion()).isNull();

            assertThatThrownBy(() -> service.getSources(otherTenant, plan.id(), ACCESS))
                .isInstanceOfSatisfying(WarehousePlanException.class, error ->
                    assertThat(error.code()).isEqualTo("WAREHOUSE_PLAN_NOT_FOUND")
                );
            assertThatThrownBy(() -> service.saveSources(tenant, plan.id(), 1, command, ACCESS))
                .isInstanceOfSatisfying(WarehousePlanException.class, error -> {
                    assertThat(error.code()).isEqualTo("WAREHOUSE_PLAN_EDIT_UNIT_VERSION_CONFLICT");
                    assertThat(error.currentVersion()).isEqualTo(2);
                });
            assertThat(
                jdbcTemplate.queryForObject(
                    "select count(*) from modeling_warehouse_plan_source where tenant_id = ? and plan_id = ?",
                    Long.class,
                    tenant,
                    plan.id()
                )
            ).isEqualTo(1L);
            verify(auditService).auditAction(
                eq("MODELING_WAREHOUSE_SOURCE_INVENTORY_SAVE"),
                eq(AuditStage.SUCCESS),
                eq(plan.id().toString()),
                any()
            );
        } finally {
            deleteTenant(tenant);
            deleteTenant(otherTenant);
        }
    }

    @Test
    void forbiddenSourcesExposeOnlyTheOpaqueBindingAndStillAllowAnExplicitExclusion() {
        String tenant = tenant("source-forbidden");
        try {
            WarehousePlanHeader plan = service.create(tenant, createCommand()).plan();
            SourceInventoryView saved = service.saveSources(
                tenant,
                plan.id(),
                1,
                new SourceInventoryCommand(
                    List.of(
                        new SourceBindingCommand(
                            null,
                            CATALOG_TABLE,
                            new SourceLocator(ASSET_ID, null, null, null, null, null, null),
                            CONFIRMED,
                            null
                        )
                    )
                ),
                ACCESS
            );
            UUID bindingId = saved.bindings().getFirst().bindingId();

            when(sourceReferenceResolver.resolve(eq(CATALOG_TABLE), any(SourceLocator.class), any(AccessContext.class)))
                .thenReturn(ResolvedSource.forbidden());
            SourceInventoryView forbidden = service.getSources(tenant, plan.id(), ACCESS);

            assertThat(forbidden.bindings().getFirst().bindingId()).isEqualTo(bindingId);
            assertThat(forbidden.bindings().getFirst().resolutionStatus()).isEqualTo(FORBIDDEN);
            assertThat(forbidden.bindings().getFirst().sourceId()).isNull();
            assertThat(forbidden.bindings().getFirst().locator()).isNull();
            assertThat(forbidden.bindings().getFirst().displayName()).isNull();
            assertThat(forbidden.bindings().getFirst().confirmedVersion()).isNull();
            assertThat(forbidden.bindings().getFirst().resolvedVersion()).isNull();

            SourceInventoryView excluded = service.saveSources(
                tenant,
                plan.id(),
                2,
                new SourceInventoryCommand(
                    List.of(new SourceBindingCommand(bindingId, null, null, EXCLUDED, "access revoked"))
                ),
                ACCESS
            );

            assertThat(excluded.version()).isEqualTo(3);
            assertThat(excluded.bindings().getFirst().confirmationStatus()).isEqualTo(EXCLUDED);
            assertThat(excluded.bindings().getFirst().sourceId()).isNull();
            Map<String, Object> stored = jdbcTemplate.queryForMap(
                """
                select source_type, source_id, source_version, locator_json::text as locator_json
                  from modeling_warehouse_plan_source
                 where tenant_id = ? and plan_id = ?
                """,
                tenant,
                plan.id()
            );
            assertThat(stored.get("source_type")).isEqualTo(CATALOG_TABLE.name());
            assertThat(stored.get("source_id")).isEqualTo(ASSET_ID.toString());
            assertThat(stored.get("source_version")).isEqualTo("schema-v1");
            assertThat(String.valueOf(stored.get("locator_json"))).contains(ASSET_ID.toString());
        } finally {
            deleteTenant(tenant);
        }
    }

    @Test
    void rejectsUnavailableNewSourcesWithoutPersistingOrAdvancingTheVersion() {
        List<ResolvedSource> unavailableSources = List.of(
            ResolvedSource.missing(),
            ResolvedSource.forbidden(),
            ResolvedSource.providerError()
        );

        for (ResolvedSource unavailableSource : unavailableSources) {
            String tenant = tenant("source-unavailable");
            try {
                WarehousePlanHeader plan = service.create(tenant, createCommand()).plan();
                when(sourceReferenceResolver.resolve(eq(CATALOG_TABLE), any(SourceLocator.class), any(AccessContext.class)))
                    .thenReturn(unavailableSource);

                assertThatThrownBy(() ->
                    service.saveSources(
                        tenant,
                        plan.id(),
                        1,
                        new SourceInventoryCommand(
                            List.of(
                                new SourceBindingCommand(
                                    null,
                                    CATALOG_TABLE,
                                    new SourceLocator(ASSET_ID, null, null, null, null, null, null),
                                    CONFIRMED,
                                    null
                                )
                            )
                        ),
                        ACCESS
                    )
                ).isInstanceOfSatisfying(WarehousePlanException.class, error -> {
                    assertThat(error.code()).isEqualTo("WAREHOUSE_PLAN_SOURCE_INVENTORY_INVALID");
                    assertThat(error.getMessage()).contains("SOURCE_NOT_AVAILABLE");
                    assertThat(error.getMessage()).doesNotContain(unavailableSource.status().name());
                });
                assertThat(service.getSources(tenant, plan.id(), ACCESS).version()).isEqualTo(1);
                assertThat(
                    jdbcTemplate.queryForObject(
                        "select count(*) from modeling_warehouse_plan_source where tenant_id = ? and plan_id = ?",
                        Long.class,
                        tenant,
                        plan.id()
                    )
                ).isZero();
            } finally {
                deleteTenant(tenant);
            }
        }
    }

    @Test
    void businessFirstConceptualDesignCanProceedWithoutSourcesOrLegacyMappings() {
        String tenant = tenant("conceptual");
        try {
            WarehousePlanHeader plan = service.create(tenant, createCommand()).plan();
            service.saveCategoryScope(
                tenant,
                plan.id(),
                1,
                new CategoryScopeCommand(List.of(new DomainBinding(DOMAIN_ID, CONFIRMED)))
            );
            service.savePlanningPolicy(
                tenant,
                plan.id(),
                1,
                new PlanningPolicyCommand("CLASSIC_ODS_DWD_DWS_ADS", null, null, null, true)
            );

            SourceInventoryView inventory = service.getSources(tenant, plan.id(), ACCESS);
            assertThat(inventory.readiness()).isEqualTo(NOT_REQUIRED_YET);
            assertThat(service.getBaseline(tenant, plan.id(), ACCESS).ready()).isTrue();
            assertThat(
                jdbcTemplate.queryForObject(
                    "select conceptual_design_allowed from modeling_warehouse_plan_policy where tenant_id = ? and plan_id = ?",
                    Boolean.class,
                    tenant,
                    plan.id()
                )
            ).isTrue();
            assertThat(
                jdbcTemplate.queryForObject(
                    "select count(*) from modeling_warehouse_plan_source_mapping where tenant_id = ? and plan_id = ?",
                    Long.class,
                    tenant,
                    plan.id()
                )
            ).isZero();
        } finally {
            deleteTenant(tenant);
        }
    }

    private static CreateWarehousePlanCommand createCommand() {
        return new CreateWarehousePlanCommand(
            "Source inventory plan",
            "Design dimensions before implementation",
            null,
            "owner-1",
            "department-1",
            BUSINESS_FIRST,
            List.of(),
            "source-inventory-" + UUID.randomUUID()
        );
    }

    private void deleteTenant(String tenant) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.update("delete from modeling_warehouse_plan_source_mapping where tenant_id = ?", tenant);
            jdbcTemplate.update("delete from modeling_warehouse_plan_source where tenant_id = ?", tenant);
            jdbcTemplate.update("delete from modeling_warehouse_plan_domain where tenant_id = ?", tenant);
            jdbcTemplate.update("delete from modeling_warehouse_plan_policy where tenant_id = ?", tenant);
            jdbcTemplate.update("delete from modeling_warehouse_plan where tenant_id = ?", tenant);
        });
    }

    private static String tenant(String suffix) {
        return "it-source-" + suffix + "-" + UUID.randomUUID();
    }
}
