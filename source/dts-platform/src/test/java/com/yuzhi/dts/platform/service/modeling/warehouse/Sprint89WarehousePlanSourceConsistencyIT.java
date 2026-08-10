package com.yuzhi.dts.platform.service.modeling.warehouse;

import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.ConfirmationStatus.CANDIDATE;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.ConfirmationStatus.CONFIRMED;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.ConfirmationStatus.EXCLUDED;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.OnboardingMode.BUSINESS_FIRST;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceAction.CONFIRM;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceAction.EXCLUDE;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceAction.RECONFIRM;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceChangeImpact.BREAKING;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceChangeImpact.REVIEW_REQUIRED;
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
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.ConfirmationStatus;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.CreateWarehousePlanCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceBindingCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceInventoryCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceInventoryView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceLocator;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class Sprint89WarehousePlanSourceConsistencyIT {

    private static final AccessContext ACCESS = new AccessContext("ignored", "owner-89-fix", "department-89-fix");
    private static final String COMPATIBLE_DETAILS =
        "{\"contractVersion\":1,\"impactLevel\":\"COMPATIBLE\",\"changes\":[{\"field\":\"note\",\"kind\":\"FIELD_ADDED\",\"impact\":\"COMPATIBLE\"}]}";
    private static final String BREAKING_DETAILS =
        "{\"contractVersion\":1,\"impactLevel\":\"BREAKING\",\"changes\":[{\"field\":\"customer_id\",\"kind\":\"FIELD_REMOVED\",\"impact\":\"BREAKING\"}]}";
    private static final String REVIEW_DETAILS = "{\"contractVersion\":1,\"changes\":[]}";

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
        tenant = "sprint89-consistency-" + UUID.randomUUID();
        datasetId = UUID.randomUUID();
        tableId = UUID.randomUUID();
        when(sourceReferenceResolver.resolve(eq(CATALOG_TABLE), any(SourceLocator.class), any(AccessContext.class)))
            .thenReturn(ResolvedSource.available("orders", "schema-v1"));
        planId = service
            .create(
                tenant,
                new CreateWarehousePlanCommand(
                    "Sprint 89 consistency",
                    "Metadata reconfirmation consistency",
                    null,
                    "owner-89-fix",
                    "department-89-fix",
                    BUSINESS_FIRST,
                    List.of(),
                    "sprint89-fix-" + UUID.randomUUID()
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
    void reviewRequiredDriftCannotBeReconfirmed() {
        UUID bindingId = saveSingleSource(CONFIRMED).bindings().getFirst().bindingId();
        seedCatalog();
        insertDrift(1, 0, 0, REVIEW_DETAILS, Instant.now());
        resolveMainSourceAs("schema-v2");

        SourceInventoryView stale = service.getSources(tenant, planId, ACCESS, 0, 200, true);

        assertThat(stale.bindings().getFirst().changeImpact()).isEqualTo(REVIEW_REQUIRED);
        assertThat(stale.bindings().getFirst().allowedActions()).containsExactly(EXCLUDE);
        assertThatThrownBy(() -> service.saveSources(tenant, planId, 2, reconfirm(bindingId), ACCESS))
            .isInstanceOfSatisfying(WarehousePlanException.class, error -> {
                assertThat(error.code()).isEqualTo("WAREHOUSE_PLAN_SOURCE_INVENTORY_INVALID");
                assertThat(error.getMessage()).contains("SOURCE_RECONFIRM_REVIEW_REQUIRED");
            });
        assertThat(openTicketCount()).isEqualTo(1);
    }

    @Test
    void aggregatesAllOpenDriftAndKeepsAnEarlierBreakingChangeVisible() {
        UUID bindingId = saveSingleSource(CONFIRMED).bindings().getFirst().bindingId();
        seedCatalog();
        insertDrift(0, 1, 0, BREAKING_DETAILS, Instant.now().minusSeconds(60));
        insertDrift(1, 0, 0, COMPATIBLE_DETAILS, Instant.now());
        resolveMainSourceAs("schema-v2");

        SourceInventoryView stale = service.getSources(tenant, planId, ACCESS, 0, 200, true);

        assertThat(stale.bindings().getFirst().changeImpact()).isEqualTo(BREAKING);
        assertThat(stale.bindings().getFirst().diffSummary().added()).isEqualTo(1);
        assertThat(stale.bindings().getFirst().diffSummary().removed()).isEqualTo(1);
        assertThat(stale.bindings().getFirst().changes()).hasSize(2);
        assertThat(stale.bindings().getFirst().allowedActions()).containsExactly(EXCLUDE);
        assertThatThrownBy(() -> service.saveSources(tenant, planId, 2, reconfirm(bindingId), ACCESS))
            .isInstanceOfSatisfying(WarehousePlanException.class, error ->
                assertThat(error.getMessage()).contains("SOURCE_RECONFIRM_BREAKING_CHANGE")
            );
        assertThat(openTicketCount()).isEqualTo(2);
    }

    @Test
    void revalidatesTheCurrentVersionInsideTheWriteTransaction() {
        UUID bindingId = saveSingleSource(CONFIRMED).bindings().getFirst().bindingId();
        seedCatalog();
        insertDrift(1, 0, 0, COMPATIBLE_DETAILS, Instant.now());
        when(sourceReferenceResolver.resolve(eq(CATALOG_TABLE), any(SourceLocator.class), any(AccessContext.class)))
            .thenReturn(
                ResolvedSource.available("orders", "schema-v2"),
                ResolvedSource.available("orders", "schema-v3")
            );

        assertThatThrownBy(() -> service.saveSources(tenant, planId, 2, reconfirm(bindingId), ACCESS))
            .isInstanceOfSatisfying(WarehousePlanException.class, error ->
                assertThat(error.code()).isEqualTo("WAREHOUSE_PLAN_SOURCE_VERSION_CHANGED")
            );
        assertThat(
            jdbcTemplate.queryForObject(
                "select sources_version from modeling_warehouse_plan where tenant_id = ? and id = ?",
                Integer.class,
                tenant,
                planId
            )
        ).isEqualTo(2);
        assertThat(
            jdbcTemplate.queryForObject(
                "select source_version from modeling_warehouse_plan_source where tenant_id = ? and plan_id = ? and id = ?",
                String.class,
                tenant,
                planId,
                bindingId
            )
        ).isEqualTo("schema-v1");
        assertThat(openTicketCount()).isEqualTo(1);
    }

    @Test
    void replaysAReconfirmationWhenUnchangedCompanionBindingsArePresent() {
        UUID companionTableId = UUID.randomUUID();
        SourceInventoryView saved = service.saveSources(
            tenant,
            planId,
            1,
            new SourceInventoryCommand(
                List.of(
                    new SourceBindingCommand(
                        null,
                        CATALOG_TABLE,
                        locator(tableId),
                        CONFIRMED,
                        null
                    ),
                    new SourceBindingCommand(
                        null,
                        CATALOG_TABLE,
                        locator(companionTableId),
                        CONFIRMED,
                        null
                    )
                )
            ),
            ACCESS
        );
        UUID driftedBindingId = saved
            .bindings()
            .stream()
            .filter(binding -> tableId.toString().equals(binding.sourceId()))
            .findFirst()
            .orElseThrow()
            .bindingId();
        UUID companionBindingId = saved
            .bindings()
            .stream()
            .filter(binding -> companionTableId.toString().equals(binding.sourceId()))
            .findFirst()
            .orElseThrow()
            .bindingId();
        seedCatalog();
        insertDrift(1, 0, 0, COMPATIBLE_DETAILS, Instant.now());
        when(sourceReferenceResolver.resolve(eq(CATALOG_TABLE), any(SourceLocator.class), any(AccessContext.class)))
            .thenAnswer(invocation -> {
                SourceLocator requested = invocation.getArgument(1);
                return tableId.equals(requested.assetId())
                    ? ResolvedSource.available("orders", "schema-v2")
                    : ResolvedSource.available("customers", "schema-v1");
            });
        SourceInventoryCommand command = new SourceInventoryCommand(
            List.of(
                new SourceBindingCommand(
                    driftedBindingId,
                    null,
                    null,
                    CONFIRMED,
                    null,
                    RECONFIRM,
                    "schema-v1",
                    "schema-v2"
                ),
                new SourceBindingCommand(companionBindingId, null, null, CONFIRMED, null)
            )
        );

        SourceInventoryView confirmed = service.saveSources(tenant, planId, 2, command, ACCESS);
        SourceInventoryView replayed = service.saveSources(tenant, planId, 2, command, ACCESS);

        assertThat(confirmed.version()).isEqualTo(3);
        assertThat(replayed.version()).isEqualTo(3);
        assertThat(replayed.bindings()).hasSize(2);
        assertThat(openTicketCount()).isZero();
        verify(auditService, times(2)).auditAction(
            eq("MODELING_WAREHOUSE_SOURCE_INVENTORY_SAVE"),
            eq(AuditStage.SUCCESS),
            eq(planId.toString()),
            any()
        );
    }

    @Test
    void executesTheConfirmAndExcludeActionsAdvertisedByTheReadContract() {
        SourceInventoryView candidate = saveSingleSource(CANDIDATE);
        UUID bindingId = candidate.bindings().getFirst().bindingId();
        assertThat(candidate.bindings().getFirst().allowedActions()).containsExactly(CONFIRM, EXCLUDE);

        SourceInventoryView confirmed = service.saveSources(
            tenant,
            planId,
            2,
            new SourceInventoryCommand(
                List.of(new SourceBindingCommand(bindingId, null, null, CONFIRMED, null, CONFIRM, null, null))
            ),
            ACCESS
        );
        SourceInventoryView excluded = service.saveSources(
            tenant,
            planId,
            3,
            new SourceInventoryCommand(
                List.of(new SourceBindingCommand(bindingId, null, null, EXCLUDED, "not in scope", EXCLUDE, null, null))
            ),
            ACCESS
        );

        assertThat(confirmed.bindings().getFirst().confirmationStatus()).isEqualTo(CONFIRMED);
        assertThat(excluded.version()).isEqualTo(4);
        assertThat(excluded.bindings().getFirst().confirmationStatus()).isEqualTo(EXCLUDED);
    }

    private SourceInventoryView saveSingleSource(ConfirmationStatus status) {
        return service.saveSources(
            tenant,
            planId,
            1,
            new SourceInventoryCommand(
                List.of(new SourceBindingCommand(null, CATALOG_TABLE, locator(tableId), status, null))
            ),
            ACCESS
        );
    }

    private SourceInventoryCommand reconfirm(UUID bindingId) {
        return new SourceInventoryCommand(
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
        );
    }

    private void resolveMainSourceAs(String version) {
        when(sourceReferenceResolver.resolve(eq(CATALOG_TABLE), any(SourceLocator.class), any(AccessContext.class)))
            .thenReturn(ResolvedSource.available("orders", version));
    }

    private void seedCatalog() {
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
    }

    private void insertDrift(int added, int removed, int changed, String details, Instant createdAt) {
        jdbcTemplate.update(
            """
            insert into catalog_schema_drift_event (
                id, integration, dataset_id, hive_database, hive_table,
                added_count, removed_count, changed_count, details_json,
                ticket_status, created_by, created_date, last_modified_by, last_modified_date
            ) values (?, 'JDBC', ?, 'public', 'orders', ?, ?, ?, cast(? as text),
                      'OPEN', 'sprint89', ?, 'sprint89', ?)
            """,
            UUID.randomUUID(),
            datasetId,
            added,
            removed,
            changed,
            details,
            Timestamp.from(createdAt),
            Timestamp.from(createdAt)
        );
    }

    private int openTicketCount() {
        return jdbcTemplate.queryForObject(
            "select count(*) from catalog_schema_drift_event where dataset_id = ? and ticket_status = 'OPEN'",
            Integer.class,
            datasetId
        );
    }

    private static SourceLocator locator(UUID assetId) {
        return new SourceLocator(assetId, null, null, null, null, null, null);
    }
}
