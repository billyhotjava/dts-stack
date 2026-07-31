package com.yuzhi.dts.platform.web.rest.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.infra.DataSourceSelectionService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InfraDataSourceSelectionResourceTest {

    @Mock
    private DataSourceSelectionService selectionService;

    @Test
    void listDelegatesToSelectionServiceAndReturnsResponseData() {
        UUID sourceId = UUID.randomUUID();
        DataSourceSelectionService.DataSourceSelectionResponse response = new DataSourceSelectionService.DataSourceSelectionResponse(
            "MODELING_LAKEHOUSE",
            sourceId,
            "ADMIN_DEFAULT",
            null,
            List.of(new DataSourceSelectionService.DataSourceSelectionItem(
                sourceId,
                "数据仓库 (biadmin)",
                "POSTGRESQL",
                "postgresql",
                "PostgreSQL",
                "DATABASE",
                "postgresqlwriter",
                "ACTIVE",
                "UP",
                List.of("MODELING_LAKEHOUSE", "DBT_TARGET"),
                true,
                true,
                true,
                "admin-default"
            ))
        );
        when(selectionService.listSelections("MODELING_LAKEHOUSE", "1502")).thenReturn(response);

        InfraDataSourceSelectionResource resource = new InfraDataSourceSelectionResource(selectionService);

        assertThat(resource.list("MODELING_LAKEHOUSE", "1502").getData()).isSameAs(response);
        verify(selectionService).listSelections("MODELING_LAKEHOUSE", "1502");
    }
}
