package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
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
}
