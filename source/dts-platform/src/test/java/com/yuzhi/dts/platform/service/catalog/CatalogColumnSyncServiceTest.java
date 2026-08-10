package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema;
import com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.service.modeling.GovernedStandardReadPort;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CatalogColumnSyncServiceTest {

    @Mock
    private CatalogColumnSchemaRepository columns;

    @Mock
    private GovernedStandardReadPort governedStandards;

    @Test
    void mapsStandardProjectionWithoutOwningStandardPersistence() {
        UUID standardId = UUID.randomUUID();
        CatalogTableSchema table = new CatalogTableSchema();
        table.setName("budget_execution");
        when(columns.findByTable(table)).thenReturn(List.of());
        when(governedStandards.findDataStandardIdsByLowerCode(Set.of("account_code")))
            .thenReturn(Map.of("account_code", standardId));
        CatalogColumnSyncService service = new CatalogColumnSyncService(columns, governedStandards);

        int updated = service.upsertColumns(
            table,
            List.of(new CatalogColumnSyncService.ColumnSpec("account_code", "varchar", false, null, null, null, "ACCOUNT_CODE", null))
        );

        ArgumentCaptor<CatalogColumnSchema> saved = ArgumentCaptor.forClass(CatalogColumnSchema.class);
        verify(columns).save(saved.capture());
        assertThat(updated).isEqualTo(1);
        assertThat(saved.getValue().getStandardId()).isEqualTo(standardId);
        assertThat(saved.getValue().getStandardMismatchReason()).isNull();
    }

    @Test
    void synchronizesAuthoritativeSnapshotWithoutReplacingStableIdentityOrGovernance() {
        CatalogTableSchema table = new CatalogTableSchema();
        table.setId(UUID.randomUUID());
        table.setName("budget_execution");

        UUID accountId = UUID.randomUUID();
        UUID legacyId = UUID.randomUUID();
        UUID standardId = UUID.randomUUID();
        CatalogColumnSchema account = column(table, accountId, "account_code", "varchar(32)", "ACTIVE");
        account.setTags("finance");
        account.setSensitiveTags("PII:account");
        account.setStandardId(standardId);
        account.setStandardRule("not_blank");
        CatalogColumnSchema legacy = column(table, legacyId, "legacy_code", "varchar(16)", "ACTIVE");
        when(columns.findByTable(table)).thenReturn(List.of(account, legacy));
        CatalogColumnSyncService service = new CatalogColumnSyncService(columns, governedStandards);

        int synchronizedColumns = service.synchronizeSnapshot(
            table,
            List.of(
                new CatalogColumnSyncService.ColumnSpec("account_code", "varchar(64)", false, "账户编码", null, null, null, null),
                new CatalogColumnSyncService.ColumnSpec("amount", "decimal(18,2)", true, "金额", null, null, null, null)
            )
        );

        ArgumentCaptor<CatalogColumnSchema> saved = ArgumentCaptor.forClass(CatalogColumnSchema.class);
        verify(columns, times(3)).save(saved.capture());
        assertThat(synchronizedColumns).isEqualTo(2);
        assertThat(account.getId()).isEqualTo(accountId);
        assertThat(account.getDataType()).isEqualTo("varchar(64)");
        assertThat(account.getStatus()).isEqualTo("ACTIVE");
        assertThat(account.getTags()).isEqualTo("finance");
        assertThat(account.getSensitiveTags()).isEqualTo("PII:account");
        assertThat(account.getStandardId()).isEqualTo(standardId);
        assertThat(account.getStandardRule()).isEqualTo("not_blank");
        assertThat(legacy.getId()).isEqualTo(legacyId);
        assertThat(legacy.getStatus()).isEqualTo("REMOVED");
        assertThat(saved.getAllValues())
            .filteredOn(column -> "amount".equals(column.getName()))
            .singleElement()
            .extracting(CatalogColumnSchema::getStatus)
            .isEqualTo("ACTIVE");
    }

    @Test
    void reactivatesAColumnWithoutChangingItsIdentity() {
        CatalogTableSchema table = new CatalogTableSchema();
        table.setId(UUID.randomUUID());
        table.setName("budget_execution");
        UUID columnId = UUID.randomUUID();
        CatalogColumnSchema existing = column(table, columnId, "account_code", "varchar(32)", "REMOVED");
        when(columns.findByTable(table)).thenReturn(List.of(existing));
        CatalogColumnSyncService service = new CatalogColumnSyncService(columns, governedStandards);

        service.synchronizeSnapshot(
            table,
            List.of(new CatalogColumnSyncService.ColumnSpec("account_code", "varchar(64)", false, null, null, null, null, null))
        );

        assertThat(existing.getId()).isEqualTo(columnId);
        assertThat(existing.getStatus()).isEqualTo("ACTIVE");
        verify(columns).save(existing);
    }

    private static CatalogColumnSchema column(
        CatalogTableSchema table,
        UUID id,
        String name,
        String dataType,
        String status
    ) {
        CatalogColumnSchema column = new CatalogColumnSchema();
        column.setId(id);
        column.setTable(table);
        column.setName(name);
        column.setDataType(dataType);
        column.setNullable(true);
        column.setStatus(status);
        return column;
    }
}
