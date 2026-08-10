package com.yuzhi.dts.platform.service.modeling.warehouse;

import static com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver.ResolutionStatus.AVAILABLE;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.ConfirmationStatus.CONFIRMED;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.OnboardingMode.BUSINESS_FIRST;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceAction.EXCLUDE;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceAction.RECONFIRM;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceChangeImpact.BREAKING;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceChangeImpact.COMPATIBLE;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceType.CATALOG_TABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.IntegrationTest;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver.AccessContext;
import com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver.ResolvedSource;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanApplicationService.WarehousePlanException;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.CreateWarehousePlanCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceAction;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceBindingCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceInventoryCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceInventoryView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceLocator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class Sprint89WarehousePlanSourceApplicationServiceIT {

    private static final AccessContext ACCESS = new AccessContext("ignored", "owner-89", "department-89");

    @Autowired
    private WarehousePlanApplicationService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private SourceReferenceResolver sourceReferenceResolver;

    @MockBean
    private CatalogDomainResolutionPort catalogDomainResolutionPort;

    @MockBean
    private AuditService auditService;

    private String tenant;
    private UUID planId;
    private UUID datasetId;
    private UUID tableId;

    @BeforeEach
    void setUp() {
        tenant = "sprint89-source-" + UUID.randomUUID();
        datasetId = UUID.randomUUID();
        tableId = UUID.randomUUID();
        when(sourceReferenceResolver.resolve(eq(CATALOG_TABLE), any(SourceLocator.class), any(AccessContext.class)))
            .thenReturn(ResolvedSource.available("orders", "schema-v1"));
        planId = service
            .create(
                tenant,
                new CreateWarehousePlanCommand(
                    "Sprint 89 source contract",
                    "Metadata drift handling",
                    null,
                    "owner-89",
                    "department-89",
                    BUSINESS_FIRST,
                    List.of(),
                    "sprint89-" + UUID.randomUUID()
                )
            )
            .planId();
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from catalog_schema_drift_event where dataset_id = ?", datasetId);
        jdbcTemplate.update("delete from catalog_table_schema where id = ?", tableId);
        jdbcTemplate.update("delete from catalog_dataset where id = ?", datasetId);
        jdbcTemplate.update("delete from modeling_warehouse_plan_source_mapping where tenant_id = ?", tenant);
        jdbcTemplate.update("delete from modeling_warehouse_plan_source where tenant_id = ?", tenant);
        jdbcTemplate.update("delete from modeling_warehouse_plan_policy where tenant_id = ?", tenant);
        jdbcTemplate.update("delete from modeling_warehouse_plan where tenant_id = ?", tenant);
    }

    @Test
    void exposesConsumerAwareDiffAndReconfirmsOnceWithVersionCas() {
        SourceInventoryView saved = service.saveSources(
            tenant,
            planId,
            1,
            new SourceInventoryCommand(
                List.of(
                    new SourceBindingCommand(
                        null,
                        CATALOG_TABLE,
                        new SourceLocator(tableId, null, null, null, null, null, null),
                        CONFIRMED,
                        null
                    )
                )
            ),
            ACCESS
        );
        UUID bindingId = saved.bindings().getFirst().bindingId();
        seedCompatibleDrift();
        when(sourceReferenceResolver.resolve(eq(CATALOG_TABLE), any(SourceLocator.class), any(AccessContext.class)))
            .thenReturn(ResolvedSource.available("orders", "schema-v2"));

        SourceInventoryView stale = service.getSources(tenant, planId, ACCESS, 0, 200, true);

        assertThat(stale.page()).isZero();
        assertThat(stale.size()).isEqualTo(200);
        assertThat(stale.totalElements()).isEqualTo(1);
        assertThat(stale.totalPages()).isEqualTo(1);
        assertThat(stale.bindings().getFirst().currentVersion()).isEqualTo("schema-v2");
        assertThat(stale.bindings().getFirst().changeImpact()).isEqualTo(COMPATIBLE);
        assertThat(stale.bindings().getFirst().diffSummary().added()).isEqualTo(1);
        assertThat(stale.bindings().getFirst().changes()).hasSize(1);
        assertThat(stale.bindings().getFirst().allowedActions()).containsExactly(RECONFIRM, EXCLUDE);
        assertThat(stale.bindings().getFirst().reasonCode()).isEqualTo("SOURCE_DRIFT_COMPATIBLE");

        SourceInventoryCommand reconfirm = new SourceInventoryCommand(
            List.of(
                new SourceBindingCommand(
                    bindingId,
                    null,
                    null,
                    CONFIRMED,
                    null,
                    SourceAction.RECONFIRM,
                    "schema-v1",
                    "schema-v2"
                )
            )
        );
        SourceInventoryView confirmed = service.saveSources(tenant, planId, 2, reconfirm, ACCESS);
        SourceInventoryView replayed = service.saveSources(tenant, planId, 2, reconfirm, ACCESS);

        assertThat(confirmed.version()).isEqualTo(3);
        assertThat(replayed.version()).isEqualTo(3);
        assertThat(replayed.bindings().getFirst().confirmedVersion()).isEqualTo("schema-v2");
        assertThat(
            jdbcTemplate.queryForObject(
                "select ticket_status from catalog_schema_drift_event where dataset_id = ?",
                String.class,
                datasetId
            )
        ).isEqualTo("RESOLVED");
        ArgumentCaptor<java.util.Map<String, Object>> auditPayload = ArgumentCaptor.captor();
        verify(auditService, times(2)).auditAction(
            eq("MODELING_WAREHOUSE_SOURCE_INVENTORY_SAVE"),
            eq(AuditStage.SUCCESS),
            eq(planId.toString()),
            auditPayload.capture()
        );
        assertThat(auditPayload.getAllValues().getLast())
            .containsEntry("tenantId", tenant)
            .containsEntry("actor", "owner-89")
            .satisfies(payload -> {
                assertThat(payload.get("correlationId")).asString().isNotBlank();
                assertThat(String.valueOf(payload.get("reconfirmations")))
                    .contains("assetKey=source:unknown/schema:public/table:orders")
                    .contains("oldVersion=schema-v1")
                    .contains("newVersion=schema-v2")
                    .doesNotContain("\u0000");
            });

        assertThatThrownBy(() ->
            service.saveSources(
                tenant,
                planId,
                3,
                new SourceInventoryCommand(
                    List.of(
                        new SourceBindingCommand(
                            bindingId,
                            null,
                            null,
                            CONFIRMED,
                            null,
                            RECONFIRM,
                            "schema-v2",
                            "schema-v3"
                        )
                    )
                ),
                ACCESS
            )
        ).isInstanceOfSatisfying(WarehousePlanException.class, error ->
            assertThat(error.code()).isEqualTo("WAREHOUSE_PLAN_SOURCE_VERSION_CHANGED")
        );
    }

    @Test
    void paginatesExplicitRequestsWhileKeepingTheLegacyReadUnpaged() {
        for (int index = 0; index < 205; index++) {
            UUID bindingId = UUID.randomUUID();
            UUID assetId = UUID.randomUUID();
            jdbcTemplate.update(
                """
                insert into modeling_warehouse_plan_source
                    (id, tenant_id, plan_id, source_type, source_id, source_version, locator_json,
                     confirmation_status, resolution_status, created_date, last_modified_date)
                values (?, ?, ?, 'CATALOG_TABLE', ?, 'schema-v1', cast(? as jsonb),
                        'CONFIRMED', 'AVAILABLE', current_timestamp, current_timestamp)
                """,
                bindingId,
                tenant,
                planId,
                assetId.toString(),
                "{\"assetId\":\"" + assetId + "\"}"
            );
        }

        SourceInventoryView secondPage = service.getSources(tenant, planId, ACCESS, 1, 200, false);
        SourceInventoryView legacy = service.getSources(tenant, planId, ACCESS);

        assertThat(secondPage.bindings()).hasSize(5);
        assertThat(secondPage.totalElements()).isEqualTo(205);
        assertThat(secondPage.totalPages()).isEqualTo(2);
        assertThat(secondPage.bindings()).allSatisfy(binding -> assertThat(binding.allowedActions()).isEmpty());
        assertThat(legacy.bindings()).hasSize(205);
        assertThat(legacy.totalElements()).isEqualTo(205);
        assertThat(legacy.totalPages()).isEqualTo(1);
    }

    @Test
    void blocksBreakingDriftFromBlindReconfirmation() {
        SourceInventoryView saved = service.saveSources(
            tenant,
            planId,
            1,
            new SourceInventoryCommand(
                List.of(
                    new SourceBindingCommand(
                        null,
                        CATALOG_TABLE,
                        new SourceLocator(tableId, null, null, null, null, null, null),
                        CONFIRMED,
                        null
                    )
                )
            ),
            ACCESS
        );
        UUID bindingId = saved.bindings().getFirst().bindingId();
        seedCompatibleDrift();
        jdbcTemplate.update(
            """
            update catalog_schema_drift_event
               set added_count = 0, removed_count = 1,
                   details_json = cast(? as text)
             where dataset_id = ?
            """,
            "{\"contractVersion\":1,\"impactLevel\":\"BREAKING\",\"changes\":[{\"field\":\"customer_id\",\"kind\":\"FIELD_REMOVED\",\"impact\":\"BREAKING\"}]}",
            datasetId
        );
        when(sourceReferenceResolver.resolve(eq(CATALOG_TABLE), any(SourceLocator.class), any(AccessContext.class)))
            .thenReturn(ResolvedSource.available("orders", "schema-v2"));

        SourceInventoryView stale = service.getSources(tenant, planId, ACCESS, 0, 200, true);

        assertThat(stale.bindings().getFirst().changeImpact()).isEqualTo(BREAKING);
        assertThat(stale.bindings().getFirst().allowedActions()).containsExactly(EXCLUDE);
        assertThat(stale.bindings().getFirst().reasonCode()).isEqualTo("SOURCE_DRIFT_BREAKING");
        assertThatThrownBy(() ->
            service.saveSources(
                tenant,
                planId,
                2,
                new SourceInventoryCommand(
                    List.of(
                        new SourceBindingCommand(
                            bindingId,
                            null,
                            null,
                            CONFIRMED,
                            null,
                            RECONFIRM,
                            "schema-v1",
                            "schema-v2"
                        )
                    )
                ),
                ACCESS
            )
        ).isInstanceOfSatisfying(WarehousePlanException.class, error -> {
            assertThat(error.code()).isEqualTo("WAREHOUSE_PLAN_SOURCE_INVENTORY_INVALID");
            assertThat(error.getMessage()).contains("SOURCE_RECONFIRM_BREAKING_CHANGE");
        });
        assertThat(
            jdbcTemplate.queryForObject(
                "select ticket_status from catalog_schema_drift_event where dataset_id = ?",
                String.class,
                datasetId
            )
        ).isEqualTo("OPEN");
    }

    private void seedCompatibleDrift() {
        jdbcTemplate.update(
            """
            insert into catalog_dataset (
                id, name, type, hive_database, hive_table, enabled, lifecycle_status, harvest_status,
                created_date, last_modified_date
            ) values (?, 'orders', 'POSTGRES', 'public', 'orders', true, 'DISCOVERED', 'SYNCED',
                      current_timestamp, current_timestamp)
            """,
            datasetId
        );
        jdbcTemplate.update(
            """
            insert into catalog_table_schema (id, dataset_id, name, created_date, last_modified_date)
            values (?, ?, 'orders', current_timestamp, current_timestamp)
            """,
            tableId,
            datasetId
        );
        jdbcTemplate.update(
            """
            insert into catalog_schema_drift_event (
                id, integration, dataset_id, hive_database, hive_table,
                added_count, removed_count, changed_count, details_json,
                ticket_status, created_by, created_date, last_modified_by, last_modified_date
            ) values (?, 'JDBC', ?, 'public', 'orders', 1, 0, 0, cast(? as text),
                      'OPEN', 'sprint89', current_timestamp, 'sprint89', current_timestamp)
            """,
            UUID.randomUUID(),
            datasetId,
            "{\"contractVersion\":1,\"impactLevel\":\"COMPATIBLE\",\"changes\":[{\"field\":\"note\",\"kind\":\"FIELD_ADDED\",\"impact\":\"COMPATIBLE\"}]}"
        );
    }
}
